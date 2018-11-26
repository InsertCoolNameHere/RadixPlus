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
/**
 * @author Max Roselius: mroseliu@rams.colostate.edu*/
package galileo.comm;

import java.io.IOException;

import galileo.event.Event;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.SerializationOutputStream;

public class IRODSReadyCheck implements Event{

	public enum Type{
		CHECK, REPLY;
	}
	private boolean isReady = false;
	private Type type;
	
	public IRODSReadyCheck(Type type) {
		this.type = type;
	}
	public void setReady(boolean ready) {
		isReady = ready;
	}
	
	public boolean isReady() {
		return isReady;
	}
	
	@Override
	public void serialize(SerializationOutputStream out) throws IOException {
		out.writeInt(fromType(type));
		out.writeBoolean(isReady);
	}
	
	public IRODSReadyCheck(SerializationInputStream in) throws IOException {
		this.type = fromInt(in.readInt());
		this.isReady = in.readBoolean();
	}
	
	public int fromType(Type type) {
		switch (type) {
			case CHECK:
				return 1;
			case REPLY:
				return 2;
			default:
				throw new IllegalArgumentException("Invalid type. Must be either CHECK or REPLY");
		}
	}
	
	public Type fromInt(int type) {
		switch(type) {
			case 1:
				return Type.CHECK;
			case 2:
				return Type.REPLY;
			default:
				throw new IllegalArgumentException("Invalid type, must be either 1 or 2");
		}
	}
	
}
