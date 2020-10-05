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
package galileo.dht;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.xerial.snappy.Snappy;

import galileo.fs.GeospatialFileSystem;

enum Type{
	UNPROCESSED, TO_IRODS, DATA_REQUEST, TO_LOCAL;
}
public class StoreMessage implements Comparable <StoreMessage>{
	private String data;
	private final Type type;
	private GeospatialFileSystem fs;
	private String fsName;
	private int plotID = -1;
	private boolean checkAll = true;
	private String filePath;
	private String sensorType = "vanilla";
	public boolean locked = true;
	
	public StoreMessage(Type type, byte[] data, GeospatialFileSystem fs, String fsName) throws IOException {
		this.data = new String(Snappy.uncompress(data));
		this.type = type;
		this.fs = fs;
		this.fsName = fsName;
	}
	
	public StoreMessage(Type type, byte[] data, GeospatialFileSystem fs, String fsName, String sensorType) throws IOException {
		this.data = new String(Snappy.uncompress(data));
		this.type = type;
		this.fs = fs;
		this.fsName = fsName;
		this.sensorType = sensorType;
	}

	public StoreMessage(Type type, String data, GeospatialFileSystem fs, String fsName, int plotID) {
		this.data = data;
		this.type = type;
		this.fs = fs;
		this.fsName = fsName;
		this.plotID = plotID;
	}
	
	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}
	
	public String getFilePath() {
		return filePath;
	}
	public void setCheckAll(boolean checkAll) {
		this.checkAll = checkAll;
	}
	public boolean checkAll() {
		return this.checkAll;
	}
	public Type getType() {
		return type;
	}
	public String getData() {
		return data;
	}
	public GeospatialFileSystem getFS() {
		return this.fs;
	}
	public String getFSName() {
		return this.fsName;
	}
	public int getPlotID(){
		return this.plotID;
	}

	//Have to implement comparable to add this class to a BlockingQueue. Since order does not matter, simply always return 0
	@Override
	public int compareTo(StoreMessage msg) {
		return 0;
	}
	
	@Override
	public String toString() {
		return "StoreMessage:\n{Type: " + type + ", plotID: " + plotID +"}";
	}

	public String getSensorType() {
		return sensorType;
	}

	public void setSensorType(String sensorType) {
		this.sensorType = sensorType;
	}
	
}
