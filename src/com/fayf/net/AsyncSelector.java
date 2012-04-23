/*
 * Copyright 2012 Andrew Ching
 * 
 * This file is part of JAsync.
 *
 * JAsync is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JAsync is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JAsync.  If not, see <http://www.gnu.org/licenses/>
 */

package com.fayf.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A wrapper around Java NIO classes. To get an instance, use
 * {@link #getInstance()}. Use the bind/connect methods to start
 * listening/connecting to servers.
 * 
 * @author Andrew
 * 
 */
public class AsyncSelector implements Runnable {
	private Selector selector;
	private ByteBuffer readBuffer = ByteBuffer.allocate(8192);

	// For changing key states
	private Map<SelectableChannel, Integer> pendingChanges = new HashMap<SelectableChannel, Integer>();

	private Map<SelectableChannel, List<ByteBuffer>> pendingWrites = new HashMap<SelectableChannel, List<ByteBuffer>>();

	// Map of workers/threads
	private Map<SelectableChannel, AsyncWorker> workers = new HashMap<SelectableChannel, AsyncWorker>();
	private Map<AsyncWorker, Thread> workerThreads = new HashMap<AsyncWorker, Thread>();

	// Map of channels to listeners
	private Map<SelectableChannel, OnAcceptListener> onAcceptListeners = new HashMap<SelectableChannel, OnAcceptListener>();
	private Map<SelectableChannel, OnConnectedListener> onConnectedListeners = new HashMap<SelectableChannel, OnConnectedListener>();

	private volatile boolean shutdown = false;
	private static AsyncSelector instance;

	public static AsyncSelector getInstance() {
		if (instance == null) instance = new AsyncSelector();
		return instance;
	}

	private AsyncSelector() {
		new Thread(this).start();
	}

	/**
	 * Do not call directly!
	 */
	public void run() {
		try {
			this.selector = SelectorProvider.provider().openSelector();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			while (true) {
				// Shut down server and clean up
				if (this.shutdown) {
					Iterator<SelectionKey> keys = this.selector.keys().iterator();
					while (keys.hasNext()) {
						SelectionKey key = keys.next();

						key.channel().close();
						key.cancel();
					}

					this.selector.close();
					instance = null;
					break;
				}

				// Loop through and update keys
				synchronized (this.pendingChanges) {
					for (Map.Entry<SelectableChannel, Integer> entry : this.pendingChanges.entrySet()) {
						int keyOps = entry.getValue();
						SelectableChannel channel = (SelectableChannel) entry.getKey();

						SelectionKey key = channel.keyFor(this.selector);
						if (key != null) key.interestOps(keyOps);
						else channel.register(this.selector, keyOps);
					}
					this.pendingChanges.clear();
				}

				// Process keys
				Iterator<SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();
				while (selectedKeys.hasNext()) {
					SelectionKey selectionKey = (SelectionKey) selectedKeys.next();
					selectedKeys.remove();

					if (!selectionKey.isValid()) continue;

					if (selectionKey.isConnectable()) finishConnection(selectionKey);
					else if (selectionKey.isAcceptable()) accept(selectionKey);
					else if (selectionKey.isReadable()) read(selectionKey);
					else if (selectionKey.isWritable()) write(selectionKey);
				}

				this.selector.select();
			}
		} catch (ClosedSelectorException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void read(SelectionKey key) throws IOException {
		if ((key.channel() instanceof ByteChannel)) {
			ByteChannel channel = (ByteChannel) key.channel();

			this.readBuffer.clear();

			int numRead = -1;
			if ((key.channel() instanceof DatagramChannel)) {
				DatagramChannel dChannel = (DatagramChannel) key.channel();

				SocketAddress address = null;
				try {
					address = dChannel.receive(this.readBuffer);
					numRead = this.readBuffer.position() + 1;
				} catch (IOException e) {
					e.printStackTrace();
					key.cancel();
					unregisterWorker(key.channel());
					return;
				}

				AsyncWorker worker = this.workers.get(channel);
				if (worker != null) worker.queueData(this, key.channel(), this.readBuffer.array(), numRead, (InetSocketAddress) address);
			} else {
				try {
					numRead = channel.read(this.readBuffer);
				} catch (IOException e) {
					e.printStackTrace();
					disconnectChannel(key);
					return;
				}

				if (numRead == -1) {
					disconnectChannel(key);
					return;
				}

				AsyncWorker worker = this.workers.get(channel);
				if (worker != null) worker.queueData(this, key.channel(), this.readBuffer.array(), numRead, null);
			}
		} else throw new UnsupportedOperationException("Unreadable channel");
	}

	private void write(SelectionKey key) throws IOException {
		if ((key.channel() instanceof WritableByteChannel)) {
			WritableByteChannel channel = (WritableByteChannel) key.channel();

			synchronized (this.pendingWrites) {
				List<ByteBuffer> queue = this.pendingWrites.get(channel);

				while (!queue.isEmpty()) {
					ByteBuffer buf = (ByteBuffer) queue.get(0);
					try {
						channel.write(buf);
					} catch (ClosedChannelException e) {
						e.printStackTrace();
						disconnectChannel(key);
					}
					if (buf.remaining() > 0) break;
					queue.remove(0);
				}

				if (queue.isEmpty()) key.interestOps(1);
			}
		} else throw new UnsupportedOperationException("Unwritable channel");
	}

	private void accept(SelectionKey key) throws IOException {
		if ((key.channel() instanceof ServerSocketChannel)) {
			ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

			SocketChannel channel = serverChannel.accept();
			channel.configureBlocking(false);

			OnAcceptListener listener = this.onAcceptListeners.get(serverChannel);
			if (listener != null) listener.onAccept(this, serverChannel, channel);

			channel.register(this.selector, 1);
		} else throw new UnsupportedOperationException("Not a ServerSocketChannel");
	}

	private void finishConnection(SelectionKey key) throws IOException {
		if ((key.channel() instanceof SocketChannel)) {
			SocketChannel channel = (SocketChannel) key.channel();
			try {
				channel.finishConnect();
			} catch (IOException e) {
				e.printStackTrace();
				disconnectChannel(key);
				return;
			}

			key.interestOps(SelectionKey.OP_READ);

			OnConnectedListener listener = this.onConnectedListeners.get(channel);
			if (listener != null) {
				listener.onConnected(this, channel);
				this.onConnectedListeners.remove(channel);
			}
		} else throw new UnsupportedOperationException("Not a SocketChannel");
	}

	/**
	 * For sending data through a channel
	 * 
	 * @param channel
	 *            The channel to write to
	 * @param data
	 *            The data
	 */
	public void send(SelectableChannel channel, byte[] data) {
		if (channel == null) throw new NullPointerException("channel is null");
		synchronized (this.pendingChanges) {
			this.pendingChanges.put(channel, SelectionKey.OP_WRITE);

			synchronized (this.pendingWrites) {
				List<ByteBuffer> queue = this.pendingWrites.get(channel);
				if (queue == null) {
					queue = new ArrayList<ByteBuffer>();
					this.pendingWrites.put(channel, queue);
				}
				queue.add(ByteBuffer.wrap(data));
			}
		}

		this.selector.wakeup();
	}

	/**
	 * Connects a {@link SocketChannel}.
	 * 
	 * @param address
	 *            The address to connect to.
	 * @param listener
	 *            Callback when a socket connection is ready to be written to.
	 * @param worker
	 *            The worker that processes data coming from this channel.
	 * @return The {@link SocketChannel} used for binding.
	 * @throws IOException
	 */
	public SocketChannel connectSocketChannel(InetSocketAddress address, OnConnectedListener listener, AsyncWorker worker) throws IOException {
		SocketChannel channel = SocketChannel.open();
		channel.configureBlocking(false);
		channel.connect(address);

		synchronized (this.pendingChanges) {
			this.pendingChanges.put(channel, SelectionKey.OP_CONNECT);
		}

		registerWorker(channel, worker);
		if (listener != null) this.onConnectedListeners.put(channel, listener);
		return channel;
	}

	/**
	 * Binds a {@link ServerSocketChannel} to listen for connections.
	 * 
	 * @param address
	 *            The address to listen on.
	 * @param listener
	 *            Callback when a socket connection is made. Attach a worker
	 *            using {@link #registerWorker} in the callback.
	 * @return The {@link ServerSocketChannel} used for binding.
	 * @throws IOException
	 */
	public ServerSocketChannel bindServerSocketChannel(InetSocketAddress address, OnAcceptListener listener) throws IOException {
		ServerSocketChannel serverChannel = ServerSocketChannel.open();
		serverChannel.configureBlocking(false);
		serverChannel.socket().bind(address);

		synchronized (this.pendingChanges) {
			this.pendingChanges.put(serverChannel, SelectionKey.OP_ACCEPT);
		}

		if (listener != null) this.onAcceptListeners.put(serverChannel, listener);
		return serverChannel;
	}

	/**
	 * Connects a {@link DatagramChannel}.
	 * 
	 * @param address
	 *            The address to connect to.
	 * @param worker
	 *            The worker that processes data coming from this channel.
	 * @return The {@link DatagramChannel} used for binding.
	 * @throws IOException
	 */
	public DatagramChannel connectDatagramChannel(InetSocketAddress address, AsyncWorker worker) throws IOException {
		DatagramChannel datagramChannel = DatagramChannel.open();
		datagramChannel.configureBlocking(false);
		datagramChannel.socket().connect(address);

		synchronized (this.pendingChanges) {
			this.pendingChanges.put(datagramChannel, SelectionKey.OP_READ);
		}

		registerWorker(datagramChannel, worker);
		return datagramChannel;
	}

	/**
	 * Binds a {@link DatagramChannel} to listen for connections.
	 * 
	 * @param address
	 *            The address to listen on.
	 * @param worker
	 *            The worker that processes data coming from this channel.
	 * @return The {@link DatagramChannel} used for binding.
	 * @throws IOException
	 */
	public DatagramChannel bindDatagramChannel(InetSocketAddress address, AsyncWorker worker) throws IOException {
		DatagramChannel datagramChannel = DatagramChannel.open();
		datagramChannel.configureBlocking(false);
		datagramChannel.socket().bind(address);

		synchronized (this.pendingChanges) {
			this.pendingChanges.put(datagramChannel, SelectionKey.OP_READ);
		}

		registerWorker(datagramChannel, worker);
		return datagramChannel;
	}

	/**
	 * Registers a worker to process data coming from a channel, and creates a
	 * thread to run it if it is not already running in one.
	 * <p>
	 * If there is an existing worker registered to the channel, it is
	 * terminated, and replaced by the new worker.
	 * 
	 * @param channel
	 *            The channel to listen for data on.
	 * @param worker
	 *            The worker that processes the data.
	 */
	public void registerWorker(SelectableChannel channel, AsyncWorker worker) {
		if ((channel != null) && (worker != null)) {
			// Check if existing worker exists
			if (this.workers.containsKey(channel)) this.workers.get(channel).shutdown();

			this.workers.put(channel, worker);

			// Start the worker if necessary
			if (!this.workerThreads.containsKey(worker)) {
				Thread thread = new Thread(worker);
				this.workerThreads.put(worker, thread);
				thread.start();
			}
		}
	}

	/**
	 * Unregisters a worker from a channel. The thread containing the worker is
	 * terminated. Does nothing if <code>channel</code> is null
	 * 
	 * @param channel
	 */
	public void unregisterWorker(SelectableChannel channel) {
		if (channel != null) {
			AsyncWorker worker = this.workers.get(channel);
			if (worker != null) {
				this.workers.remove(channel);
				this.workerThreads.remove(worker);
				worker.shutdown();
			}
		}
	}

	/**
	 * Disconnects a channel. Registered workers are NOT terminated.
	 * 
	 * @param channel
	 * @throws IOException
	 */
	public void disconnectChannel(SelectableChannel channel) throws IOException {
		channel.keyFor(this.selector).cancel();
		channel.close();
		// unregisterWorker(channel);
	}

	/**
	 * Disconnects a channel. Registered workers are NOT terminated.
	 * 
	 * @param channel
	 * @throws IOException
	 */
	public void disconnectChannel(SelectionKey key) throws IOException {
		key.cancel();
		key.channel().close();
		// unregisterWorker(key.channel());
	}

	/**
	 * Shuts down the selector. All channels are closed and all workers are
	 * terminated.
	 */
	public void shutdown() {
		this.shutdown = true;

		Iterator<AsyncWorker> iter = this.workers.values().iterator();
		while (iter.hasNext()) {
			iter.next().shutdown();
		}

		this.selector.wakeup();
	}
}