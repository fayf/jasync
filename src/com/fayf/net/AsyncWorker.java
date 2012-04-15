package com.fayf.net;

import java.net.InetSocketAddress;
import java.nio.channels.ByteChannel;
import java.util.LinkedList;
import java.util.List;

public abstract class AsyncWorker implements Runnable {
	private volatile boolean shutdown = false;
	private List<DataEvent> queue = new LinkedList<DataEvent>();

	public final void shutdown() {
		this.shutdown = true;
		synchronized (this.queue) {
			this.queue.notify();
		}
	}

	public final void queueData(ByteChannel channel, byte[] data, int count, InetSocketAddress sourceAddress) {
		if (!this.shutdown) {
			byte[] dataCopy = new byte[count];
			System.arraycopy(data, 0, dataCopy, 0, count);
			synchronized (this.queue) {
				this.queue.add(new DataEvent(channel, dataCopy, sourceAddress));
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
				if (this.shutdown) break;
				if (this.queue.isEmpty()) {
					continue;
				}

				dataEvent = (DataEvent) this.queue.remove(0);
			}
			processData(dataEvent);
		}
	}

	public abstract void processData(DataEvent dataEvent);
}