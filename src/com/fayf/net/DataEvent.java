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

/**
 * A container for packets of data
 * 
 * @author Andrew
 *
 */
public class DataEvent {
	public AsyncSelector selector;
	public SelectableChannel channel;
	public byte[] data;
	public InetSocketAddress sourceAddress;

	public DataEvent(AsyncSelector selector, SelectableChannel channel, byte[] data, InetSocketAddress sourceAddress) {
		this.selector = selector;
		this.channel = channel;
		this.data = data;
		this.sourceAddress = sourceAddress;
	}
}