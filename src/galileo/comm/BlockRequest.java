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
import java.util.ArrayList;
import java.util.List;

import galileo.event.Event;
import galileo.serialization.SerializationException;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.SerializationOutputStream;

public class BlockRequest implements Event {

	private String fsName;
	private List<String> filePaths;

	public BlockRequest(String fsName, String filePath) {
		if (fsName == null || fsName.trim().length() == 0 || filePath == null || filePath.trim().length() == 0)
			throw new IllegalArgumentException(
					"Filesystem name and filepath on the server is needed to retrieve the block");
		this.fsName = fsName;
		this.filePaths = new ArrayList<String>();
		this.filePaths.add(filePath);
	}
	
	public void addFilePath(String filePath){
		this.filePaths.add(filePath);
	}

	public String getFilesystem() {
		return this.fsName;
	}

	public List<String> getFilePaths() {
		return this.filePaths;
	}

	@Deserialize
	public BlockRequest(SerializationInputStream in) throws IOException, SerializationException {
		this.fsName = in.readString();
		this.filePaths = new ArrayList<String>();
		in.readStringCollection(this.filePaths);
	}

	@Override
	public void serialize(SerializationOutputStream out) throws IOException {
		out.writeString(fsName);
		out.writeStringCollection(filePaths);
	}

}
