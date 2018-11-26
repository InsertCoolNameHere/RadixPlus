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
import galileo.dataset.feature.FeatureType;
import galileo.event.Event;
import galileo.serialization.SerializationException;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.SerializationOutputStream;
import galileo.util.Pair;

/**
 * Internal use only. To create or delete file systems in galileo
 * @author roselius
 *
 */
public class TemporalFilesystemEvent implements Event{
	private String name;
	private FilesystemAction action;
	private int nodesPerGroup;
	private List<Pair<String, FeatureType>> featureList;
	private TemporalType temporalType;
	private double bucketDuration;
	public TemporalFilesystemEvent(String name, FilesystemAction action, List<Pair<String, FeatureType>> featureList, double bucketDuration, TemporalType type) {
		if (name == null || name.trim().length() == 0 || !name.matches("[a-z0-9-]{5,50}"))
			throw new IllegalArgumentException(
					"name is required and must be lowercase having length at least 5 and at most 50 characters. "
							+ "alphabets, numbers and hyphens are allowed.");
		if (action == null)
			throw new IllegalArgumentException(
					"action cannot be null. must be one of the actions specified by galileo.comm.FileSystemAction");
		this.name = name;
		this.nodesPerGroup = 0; //zero indicates to make use of the default network organization
		this.action = action;
		this.featureList = featureList;
		this.temporalType = type;//may change later...
		this.bucketDuration = bucketDuration;
	}

	public String getFeatures() {
		if (this.featureList == null)
			return null;
		StringBuffer sb = new StringBuffer();
		for (Pair<String, FeatureType> pair : this.featureList) {
			sb.append(pair.a + ":" + pair.b.toInt() + ",");
		}
		sb.setLength(sb.length() - 1);
		return sb.toString();
	}

	private boolean hasFeatures() {
		return this.featureList != null;
	}
	
	public List<Pair<String, FeatureType>> getFeatureList(){
		return this.featureList;
	}

	private List<Pair<String, FeatureType>> getFeatureList(String features) {
		if (features == null)
			return null;
		String[] pairs = features.split(",");
		this.featureList = new ArrayList<>();
		for (String pair : pairs) {
			String[] pairSplit = pair.split(":");
			this.featureList.add(
					new Pair<String, FeatureType>(pairSplit[0], FeatureType.fromInt(Integer.parseInt(pairSplit[1]))));
		}
		return this.featureList;
	}

	public void setNodesPerGroup(int numNodes) {
		if (numNodes > 0)
			this.nodesPerGroup = numNodes;
	}

	public double getBucketDuration(){
		return this.bucketDuration;
	}
	
	public int getNodesPerGroup() {
		return this.nodesPerGroup;
	}

	public String getName() {
		return this.name;
	}
	
	public TemporalType getTemporalType(){
		return this.temporalType;
	}
	
	public void setTemporalType(TemporalType t){
		if (t != null)
			this.temporalType = t;
	}
	public FilesystemAction getAction() {
		return this.action;
	}

	@Deserialize
	public TemporalFilesystemEvent(SerializationInputStream in) throws IOException, SerializationException {
		this.name = in.readString();
		this.action = FilesystemAction.fromAction(in.readString());
		this.nodesPerGroup = in.readInt();
		this.temporalType = TemporalType.fromType(in.readInt());
		this.bucketDuration = in.readDouble();
		if(in.readBoolean())
			this.featureList = getFeatureList(in.readString());
	}

	@Override
	public void serialize(SerializationOutputStream out) throws IOException {
		out.writeString(this.name);
		out.writeString(this.action.getAction());
		out.writeInt(this.nodesPerGroup);
		out.writeInt(this.temporalType.getType());
		out.writeDouble(this.bucketDuration);
		out.writeBoolean(hasFeatures());
		if (hasFeatures())
			out.writeString(getFeatures());	
	}
}