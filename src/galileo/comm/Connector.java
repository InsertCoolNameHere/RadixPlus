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
package galileo.comm;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

import galileo.client.EventPublisher;
import galileo.event.BasicEventWrapper;
import galileo.event.Event;
import galileo.event.EventWrapper;
import galileo.net.ClientMessageRouter;
import galileo.net.GalileoMessage;
import galileo.net.MessageListener;
import galileo.net.NetworkDestination;

public class Connector implements MessageListener {

	private static final Logger logger = Logger.getLogger(Connector.class.getName());
	private static GalileoEventMap eventMap = new GalileoEventMap();
	private static EventWrapper wrapper = new BasicEventWrapper(eventMap);
	private ClientMessageRouter messageRouter;
	private Event response;
	private CountDownLatch latch;

	public Connector() throws IOException {
		this.messageRouter = new ClientMessageRouter();
		this.messageRouter.addListener(this);
	}
	
	public void publishEvent(NetworkDestination server, Event request) throws IOException {
		messageRouter.sendMessage(server, EventPublisher.wrapEvent(request));
	}
	
	public Event sendMessage(NetworkDestination server, Event request) throws IOException, InterruptedException {
		// RIKI MAJOR CHANGE
		//messageRouter.sendMessage(server, EventPublisher.wrapEvent(request));
		//logger.fine("Request sent. Waiting for response");
		try {
			this.latch = new CountDownLatch(1);
			messageRouter.sendMessage(server, EventPublisher.wrapEvent(request));
			logger.fine("Request sent, latch initialized. Waiting for response");
			this.latch.await();
		} catch (InterruptedException e) {
			throw e;
		}
		return response;
	}

	@Override
	public void onMessage(GalileoMessage message) {
		try {
			//logger.info("RIKI: Obtained response from Galileo");
			
			/*
			 * Event event = this.wrapper.unwrap(message);
			 * 
			 * if(event instanceof IRODSReadyCheckRequest) {
			 * logger.info("RIKI: REQ FROM ANOTHER NODE"); } else if (event instanceof
			 * IRODSReadyCheckResponse) { logger.info("RIKI: RESPONSE RECEIVED"); } else {
			 * logger.info("RIKI: SOMETHING ELSE RECEIVED"); }
			 */
			
			this.response = wrapper.unwrap(message);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "failed to get the response from Galileo", e);
		} finally {
			this.latch.countDown();
		}
	}

	public void close() {
		try {
			this.messageRouter.shutdown();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to shutdown the router", e);
		}
	}

	@Override
	public void onConnect(NetworkDestination destination) {
		logger.fine("Successfully connected to Galileo on " + destination);
	}

	@Override
	public void onDisconnect(NetworkDestination destination) {
		logger.fine("Disconnected from galileo on " + destination);
	}

	public ClientMessageRouter getMessageRouter() {
		return messageRouter;
	}
}

