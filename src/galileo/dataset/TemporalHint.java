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
package galileo.dataset;

import java.io.IOException;

import galileo.serialization.ByteSerializable;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.SerializationOutputStream;

public class TemporalHint implements ByteSerializable{
	
	//startHint and endHint are time stamps (milliseconds from Unix epoch (GMT)
	private long startHint;
	private long endHint;
	
	public TemporalHint(long startHint, long endHint) {
		this.startHint = startHint;
		this.endHint = endHint;
	}
	
	public long getStartHint(){
		return this.startHint;
	}
	
	public long getEndHint() {
		return this.endHint;
	}
	
	@Deserialize
    public TemporalHint(SerializationInputStream in)
    throws IOException {
        this.startHint = in.readLong();
        this.endHint = in.readLong();
    }

	@Override
	public void serialize(SerializationOutputStream out) throws IOException {
		out.writeLong(startHint);
		out.writeLong(endHint);
	}

}
