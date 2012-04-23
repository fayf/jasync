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

import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.util.LinkedList;
import java.util.List;

/**
 * A worker class to process data coming from a channel.
 * 
 * @author Andrew
 */
public abstract class AsyncWorker implements Runnable {
	private volatile boolean shutdown = false;
	private List<DataEvent> queue = new LinkedList<DataEvent>();

	/**
	 * Terminates the worker and its wrapping thread. Once called, no more data can be queued.
	 */
	public final void shutdown() {
		this.shutdown = true;
		synchronized (this.queue) {
			this.queue.notify();
		}
	}

	final void queueData(AsyncSelector selector, SelectableChannel channel, byte[] data, int count, InetSocketAddress sourceAddress) {
		if (!this.shutdown) {
//			byte[] dataCopy = new byte[count];
//			System.arraycopy(data, 0, dataCopy, 0, count);
			synchronized (this.queue) {
				this.queue.add(new DataEvent(selector, channel, data, sourceAddress));
				this.queue.notify();
			}
		}
	}

	public final void run() {
		while (true) {
			DataEvent dataEvent;
			synchronized (this.queue) {
				try {
					this.queue.wait();
				} catch (InterruptedException e) {
				}
			}

			// Check for shutdown
			if (this.shutdown) break;

			// Check if data queue is empty
			if (this.queue.isEmpty()) continue;

			// Process data
			dataEvent = (DataEvent) this.queue.remove(0);
			processData(dataEvent);
		}
	}

	/**
	 * Override this to read data as it comes from a channel.
	 * 
	 * @param dataEvent
	 */
	public abstract void processData(DataEvent dataEvent);
}