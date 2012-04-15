package com.fayf.net;

import java.nio.channels.SocketChannel;

public abstract interface OnConnectedListener {
	public abstract void onConnected(AsyncSelector selector, SocketChannel socketChannel);
}