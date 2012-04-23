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

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * Interface definition for a callback to be invoked when a connection is accepted.
 *  
 * @author Andrew
 *
 */
public abstract interface OnAcceptListener {
	/**
	 * Callback for when a new connection is accepted on a {@link ServerSocketChannel}.
	 * 
	 * @param selector			The selector that handled the connection.
	 * @param serverChannel		The bound channel that accepted the connection.
	 * @param socketChannel		The new connection.
	 */
	public abstract void onAccept(AsyncSelector selector, ServerSocketChannel serverChannel, SocketChannel socketChannel);
}