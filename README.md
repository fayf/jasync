Here's a simple web server example using this library. Note that it's meant more for lower level socket communications and not a web server.  

	final AsyncWorker worker = new AsyncWorker() {
		@Override
		public void processData(DataEvent dataEvent) {
			System.out.println("test");
			
			dataEvent.selector.send(dataEvent.channel, "HTTP/1.1 200 OK\nContent-Type: text/html\nContent-Length: 11\n\nHello world!".getBytes());
		}
	};
	
	try {
		AsyncSelector.getInstance().bindServerSocketChannel(new InetSocketAddress("localhost", 80), new OnAcceptListener() {
			
			@Override
			public void onAccept(AsyncSelector selector, ServerSocketChannel serverChannel, SocketChannel socketChannel) {
				selector.registerWorker(socketChannel, worker);
			}
		});
	} catch (IOException e) {
		e.printStackTrace();
	}
	
#### License
This is licensed under the [LGPL](http://www.gnu.org/licenses/lgpl-3.0.en.html).