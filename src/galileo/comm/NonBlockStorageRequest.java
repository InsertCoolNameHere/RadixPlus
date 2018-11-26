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

import galileo.serialization.SerializationException;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.SerializationOutputStream;
import galileo.event.Event;

public class NonBlockStorageRequest implements Event{
	private byte [] data;
	private String fsName;
	private boolean checkAll = true;
    public NonBlockStorageRequest(byte [] data, String fsName) {
        this.data = data;
        this.fsName = fsName;
    }

    public String getFS() {
    	return this.fsName;
    }
    
    public byte[] getData() {
        return data;
    }
    public void setCheckAll(boolean checkAll) {
    	this.checkAll = checkAll;
    }
    public boolean checkAll() {
    	return this.checkAll;
    }
    @Deserialize
    public NonBlockStorageRequest(SerializationInputStream in)
    throws IOException, SerializationException {
        this.fsName = in.readString();
        int dataSize = in.readInt();
        byte [] inData = new byte[dataSize];
        in.readFully(inData);
        this.data = inData;
        this.checkAll = in.readBoolean();
    }

    @Override
    public void serialize(SerializationOutputStream out)
    throws IOException {
    	out.writeString(fsName);
    	out.writeInt(data.length);
        out.write(data);
        out.writeBoolean(checkAll);
    }
}
