package com.fayf.net;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public abstract interface OnAcceptListener {
	public abstract void onAccept(AsyncSelector selector, ServerSocketChannel serverChannel, SocketChannel socketChannel);
}