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

import galileo.event.Event;
import galileo.serialization.SerializationException;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.SerializationOutputStream;

import java.io.IOException;

import org.json.JSONObject;

/**
 * A client interface to request meta information from galileo such as the file system names and features in a filesystem.
 * <br/>Request must be a JSON string in the following format:<br/>
 * { "kind" : "galileo#filesystem" | "galileo#features" | "galileo#overview",<br/>
 * &nbsp;&nbsp;"filesystem" : ["Array of Strings indicating the names of the filesystem" - required if the kind is galileo#features or 
 * &nbsp;&nbsp;&nbsp;&nbsp;galileo#overview] <br/>
 * }<br/>
 * The response would be an instance of {@link MetadataResponse}
 * @author kachikaran
 */
public class MetadataRequest implements Event{
	
	private JSONObject request;
	
	public MetadataRequest(String reqJSON){
		if(reqJSON == null || reqJSON.trim().length() == 0)
			throw new IllegalArgumentException("Request must be a valid JSON string.");
		this.request = new JSONObject(reqJSON);
	}
	
	public MetadataRequest(JSONObject request) {
		if(request == null) 
			throw new IllegalArgumentException("Request must be a valid JSON object.");
		this.request = request;
	}
	
	public String getRequestString(){
		return this.request.toString();
	}
	
	public JSONObject getRequest(){
		return this.request;
	}
	
	@Deserialize
    public MetadataRequest(SerializationInputStream in)
    throws IOException, SerializationException {
        String reqJSON = in.readString();
        this.request = new JSONObject(reqJSON);
    }

	@Override
	public void serialize(SerializationOutputStream out) throws IOException {
		out.writeString(this.request.toString());
	}

}
