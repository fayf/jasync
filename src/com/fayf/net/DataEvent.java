package com.fayf.net;

import java.net.InetSocketAddress;
import java.nio.channels.ByteChannel;

public class DataEvent {
	public ByteChannel channel;
	public byte[] data;
	public InetSocketAddress sourceAddress;

	public DataEvent(ByteChannel channel, byte[] data, InetSocketAddress sourceAddress) {
		this.channel = channel;
		this.data = data;
		this.sourceAddress = sourceAddress;
	}
}