/*
Copyright (c) 2018, Computer Science Department, Colorado State University
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

This software is provided by the copyright holders and contributors "as is" and
any express or implied warranties, including, but not limited to, the implied
warranties of merchantability and fitness for a particular purpose are
disclaimed. In no event shall the copyright holder or contributors be liable for
any direct, indirect, incidental, special, exemplary, or consequential damages
(including, but not limited to, procurement of substitute goods or services;
loss of use, data, or profits; or business interruption) however caused and on
any theory of liability, whether in contract, strict liability, or tort
(including negligence or otherwise) arising in any way out of the use of this
software, even if advised of the possibility of such damage.
*/
package web;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

import galileo.bmp.HashGridException;
import galileo.client.EventPublisher;
import galileo.comm.NonBlockStorageRequest;
import galileo.dht.NodeInfo;
import galileo.dht.PartitionException;
import galileo.dht.SpatialHierarchyPartitioner;
import galileo.dht.StorageNode;
import galileo.dht.hash.HashException;
import galileo.fs.GeospatialFileSystem;
import galileo.net.ClientMessageRouter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.xerial.snappy.Snappy;
public class Listener extends Thread{
	/**
	 * Each instance of galileo.dht.StorageNode will maintain one instance of this class.
	 * An object created from this class will listen on a designated port (hard-coded at the moment)
	 * for incoming data. The data received will be sampled and "stamped" to determine which node is 
	 * responsible for processing and storing it.*/
	private StorageNode master;
	private int portNum = 42069;
	public volatile boolean shouldStop = false;
	private ServerSocketChannel serverChannel;
	private Selector selector;
	private Logger logger = Logger.getLogger("galileo");
	private List<Long> stampTimes, avgStamps;
	private Timer throughputTimer = new Timer();
	private ClientMessageRouter messageRouter;
	public Listener(StorageNode master) throws IOException{
		this.selector = initSelector();
		this.master = master;
		this.stampTimes = new ArrayList<>();
		this.avgStamps = new ArrayList<>();
		this.messageRouter = new ClientMessageRouter();
	}
	
	@Override
	public void run() {
		
		throughputTimer.scheduleAtFixedRate(new TimerTask() {
		  @Override
		  public void run() {
			  synchronized(stampTimes) {
				if (stampTimes.size() > 0) {
				    long elapsedTime = stampTimes.get(stampTimes.size()-1) - stampTimes.get(0);
				    logger.info("Average data throughput: " + (.1*((double)stampTimes.size())/((double)elapsedTime/(double)1000)) + " MB/s");// 100KB per message
				    logger.info("stampTimes: " + stampTimes);
				    long sum = 0;
				    synchronized(avgStamps) {
					    for (long l : avgStamps)
					    	sum += l;
					    double avg = sum/avgStamps.size();
					    logger.info("Average stamp time: " + avg + " ms");
				    }
				    
				}
			  }
		  }
		}, 60*1000, 60*1000);
		while (!shouldStop) {
		      try {
		        // Wait for an event one of the registered channels
		        this.selector.select();
		      
	        	// Iterate over the set of keys for which events are available
		        Iterator <SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();
		        while (selectedKeys.hasNext()) {
		          SelectionKey key = (SelectionKey) selectedKeys.next();
		          selectedKeys.remove();

		          if (!key.isValid()) {
		            continue;
		          }
		          // Check what event is available and deal with it
		          if (key.isAcceptable()) {
		            this.accept(key);
		          }
		        
		          if (key.isReadable())
		        	  this.read(key);
		          			          
		          //Should not have to worry about writable keys in server, only threadPool should see those
		        }
		        
		        
		      } catch (Exception e) {
		        logger.severe("Error trying to read incoming data. " + e);
		        logger.severe("Stack trace: " + Arrays.toString(e.getStackTrace()));
		      }
		    }
		try {
			serverChannel.close();
		} catch (IOException e) {
			logger.severe("Error closing listener server channel " + e);
		}
		return;
	}

	public void kill() {
		this.shouldStop = true;
		try {
			this.selector.close();
		} catch (IOException e) {
			logger.info("Failed to kill listener due to " + e);
		}
	}
	private Selector initSelector() throws IOException {
	    // Create a new selector
	    Selector socketSelector = SelectorProvider.provider().openSelector();

	    // Create a new non-blocking server socket channel
	    this.serverChannel = ServerSocketChannel.open();
	    serverChannel.configureBlocking(false);

	    // Bind the server socket to the specified address and port
	    InetSocketAddress isa = new InetSocketAddress(InetAddress.getLocalHost(), this.portNum);
	    serverChannel.socket().bind(isa);

	    // Register the server socket channel, indicating an interest in 
	    // accepting new connections
	    serverChannel.register(socketSelector, SelectionKey.OP_ACCEPT);

	    return socketSelector;
	  }
	
	private void accept(SelectionKey key) throws IOException {
	    // For an accept to be pending the channel must be a server socket channel.
	    ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

	    // Accept the connection and make it non-blocking
	    SocketChannel socketChannel = serverSocketChannel.accept();
	    socketChannel.configureBlocking(false);

	    // Register the new SocketChannel with our Selector, indicating
	    // we'd like to be notified when there's data waiting to be read
	    socketChannel.register(this.selector, SelectionKey.OP_READ);
	  }
	
	//This function will be responsible for reading the data received, sampling the data to determine its
	// storage destination, and forwarding to the appropriate destination.
	 private void read(SelectionKey key) throws IOException, HashException, PartitionException {
		 		 long start = System.currentTimeMillis();
		         SocketChannel channel = (SocketChannel) key.channel();
		         ByteBuffer buffer = ByteBuffer.allocate(1024 * 100); //Read 100 KB at a time
		         buffer.clear(); //clear for safety
		         int numRead = -1;
		         String dataString = "";
		         synchronized(channel) {
			         numRead = channel.read(buffer);
			         
			         //need to read in loop
			         while (numRead > -1) {
			        	 byte[] data = new byte[numRead];
			        	 System.arraycopy(buffer.array(), 0, data, 0, numRead);
			        	 dataString += new String(data);
			        	 buffer.clear(); //important to clear buffer to eliminate leftover data
			        	 numRead = channel.read(buffer);
			         }
		         }
//		         if (numRead == -1) {
//		             Socket socket = channel.socket();
//		             SocketAddress remoteAddr = socket.getRemoteSocketAddress();
		             channel.close();
		             key.cancel();
//		             return;
//		         }
		         Sampler sampler = new Sampler(((SpatialHierarchyPartitioner)((GeospatialFileSystem)this.master.getFS("roots")).getPartitioner()));
		         NodeInfo destination = null;
		         try {
		        	 SamplerResponse response = sampler.sample(this.master.getGlobalGrid(), dataString);
		        	 HashMap<NodeInfo, Integer> dests = response.getNodeMap();
						if (dests.keySet().size() == 1) {//all data belongs to one node
							Map.Entry<NodeInfo,Integer> entry = dests.entrySet().iterator().next();
							NodeInfo dest = entry.getKey();
							if (dest != null)
								sendMessage(dataString, dest, response.checkAll());
						}
						else {
							NodeInfo finalDest = null;
							int count = 0;
							for (NodeInfo node : dests.keySet()){
								if (dests.get(node) > count){
									finalDest = node;
									count = dests.get(node);
								}
							}
							if (finalDest == null || finalDest.equals(null))
								logger.severe("Identified null for a destination");
							if (finalDest!=null)
								if (!finalDest.equals(null))
									sendMessage(dataString, finalDest, response.checkAll());
						}
				} catch (HashGridException e) {
					logger.severe("Unable to process raw data for storage. " + e);
				}

		         long end = System.currentTimeMillis() - start;
//		         logger.info("Time to determine destination: " + end);
		         //Need to send back response indicating success/failure
		         stampTimes.add(System.currentTimeMillis());
		         avgStamps.add(end);
	 }
	 
	 private void sendMessage(String message, NodeInfo dest, boolean checkAll) throws IOException {
		 byte [] compressed = Snappy.compress(message.getBytes());
		 NonBlockStorageRequest request = new NonBlockStorageRequest(compressed, "roots");
		 messageRouter.sendMessage(dest, EventPublisher.wrapEvent(request));
		 
	 }
}
