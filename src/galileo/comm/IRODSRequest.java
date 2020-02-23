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
 * @author Max Roselius: mroseliu@rams.colostate.edu
 * A representation of a message for acquiring a lock on a particular polygon (plot).
 * A node that acquires the lock will be responsible for collecting and uploading all
 * data for that plot to IRODS.*/
package galileo.comm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import galileo.event.Event;
import galileo.graph.SummaryStatistics;
import galileo.serialization.SerializationException;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.SerializationOutputStream;

public class IRODSRequest implements Event{
	public enum TYPE{
		LOCK_REQUEST, IGNORE, LOCK_ACQUIRED, DATA_REQUEST, DATA_REPLY, LOCK_RELEASE_REQUEST, LOCK_RELEASED;
	}
	
	private TYPE type;
	private int plotNum;
	private String replyData;
	private String filePath;
	
	private String fs="";
	private String metadata="";
	
	private SummaryStatistics summary = null;
	
	
	public IRODSRequest(TYPE type, int plotNum) {
		this.type = type;
		this.plotNum = plotNum;
		this.replyData = null;
		this.filePath = null;
	}
	
	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}
	
	public String getFilePath() {
		return filePath;
	}
	public int getPlotNum() {
		return this.plotNum;
	}
	
	public TYPE getType() {
		return type;
	}

	public void setData(String data) {
		this.replyData = data;
	}
	
	public String getData() {
		return replyData;
	}
	@Override
	public void serialize(SerializationOutputStream out) throws IOException {
		out.writeInt(fromType(this.type));
		out.writeInt(this.plotNum);
		out.writeBoolean(replyData != null);
		if (replyData != null)
			out.writeString(replyData);
		out.writeBoolean(filePath != null);
		if (filePath != null)
			out.writeString(filePath);
		
		if(summary !=null) {
			out.writeBoolean(true);
			
			List<SummaryStatistics> summaries = new ArrayList<>();
			summaries.add(summary);
			out.writeSerializableCollection(summaries);
		} else {
			out.writeBoolean(false);
		}
		
		out.writeString(fs);
		out.writeString(metadata);
		
	}

	public IRODSRequest(SerializationInputStream in) throws IOException, SerializationException{
		this.type = fromInt(in.readInt());
		this.plotNum = in.readInt();
		if (in.readBoolean())
			this.replyData = in.readString();
		if (in.readBoolean())
			this.filePath = in.readString();
		
		boolean hasSummary = in.readBoolean();
		if(hasSummary) {
			List<SummaryStatistics> summaries = new ArrayList<>();
			in.readSerializableCollection(SummaryStatistics.class, summaries);
			summary = summaries.get(0);
		}
		
		this.fs = in.readString();
		this.metadata = in.readString();
	}
	
	public static int fromType(TYPE type) {
		switch(type) {
			case LOCK_REQUEST:
				return 1;
			case IGNORE:
				return 2;
			case LOCK_ACQUIRED:
				return 3;
			case DATA_REQUEST:
				return 4;
			case DATA_REPLY:
				return 5;
			case LOCK_RELEASE_REQUEST:
				return 6;
			case LOCK_RELEASED:
				return 7;
			default:
				throw new IllegalArgumentException("Invalid type: " + type);
		}
	}
	public static TYPE fromInt(int type) {
		switch(type) {
			case 1:
				return TYPE.LOCK_REQUEST;
			case 2:
				return TYPE.IGNORE;
			case 3: 
				return TYPE.LOCK_ACQUIRED;
			case 4:
				return TYPE.DATA_REQUEST;
			case 5:
				return TYPE.DATA_REPLY;
			case 6:
				return TYPE.LOCK_RELEASE_REQUEST;
			case 7:
				return TYPE.LOCK_RELEASED;
			default:
				throw new IllegalArgumentException("Invalid type: " + type);
		}
	}
	
	@Override
	public String toString() {
		String str = "Type: " + type + ", Plot: " + plotNum;
		if (filePath != null)
			str += ", Filepath: " + filePath;
		return str;
		
	}

	public String getFs() {
		return fs;
	}

	public void setFs(String fs) {
		this.fs = fs;
	}

	public String getMetadata() {
		return metadata;
	}

	public void setMetadata(String metadata) {
		this.metadata = metadata;
	}

	public String getReplyData() {
		return replyData;
	}

	public void setReplyData(String replyData) {
		this.replyData = replyData;
	}

	public SummaryStatistics getSummary() {
		return summary;
	}

	public void setSummary(SummaryStatistics summary) {
		this.summary = summary;
	}

	public void setType(TYPE type) {
		this.type = type;
	}

	public void setPlotNum(int plotNum) {
		this.plotNum = plotNum;
	}

}
