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

import galileo.event.Event;
import galileo.serialization.SerializationException;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.SerializationOutputStream;

public class QueueResponse implements Event {
	
	private int nodeNum;
	private boolean grant = false;

	public QueueResponse(int nodeNum, boolean grant) {
		this.nodeNum = nodeNum;
		this.grant = grant;
	}

	@Deserialize
	public QueueResponse(SerializationInputStream in) throws IOException, SerializationException {
		this.nodeNum = in.readInt();
		this.grant = in.readBoolean();
	}

	@Override
	public void serialize(SerializationOutputStream out) throws IOException {
		out.writeInt(nodeNum);
		out.writeBoolean(grant);
	}

	public int getNodeNum() {
		return nodeNum;
	}

	public void setNodeNum(int nodeNum) {
		this.nodeNum = nodeNum;
	}

	public boolean isGrant() {
		return grant;
	}

	public void setGrant(boolean grant) {
		this.grant = grant;
	}
}
