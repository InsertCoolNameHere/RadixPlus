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
package galileo.net;

import galileo.event.Event;
import galileo.event.EventContext;

/**
 * Interface that classes should implement to to know if the client request has been completed
 * @author jcharles
 *
 */
public interface RequestListener {

	/**
	 * Called when a request is completed by the ClientRequestHandler so as to send back the response to the original client
	 * @param reponse
	 * The collective responses from all the nodes in the network
	 * @param context
	 * The context of the client that originally initiated the request
	 * @param requestHandler
	 * The request handler that is handling the client requests.
	 */
	public void onRequestCompleted(Event response, EventContext context, MessageListener requestHandler);

}
