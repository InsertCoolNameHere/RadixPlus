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
import galileo.dataset.feature.Feature;
import galileo.dataset.feature.FeatureArray;
import galileo.dataset.feature.FeatureArraySet;
import galileo.dataset.feature.FeatureSet;
import galileo.serialization.ByteSerializable;
import galileo.serialization.SerializationException;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.SerializationOutputStream;

public class Metadata implements ByteSerializable {

	private String name = "";
	private SpatialProperties spatialProperties;

	/**
	 * Metadata attributes: these Features are represented by a 1D array and are
	 * accessed as a simple key-value store.
	 */
	private FeatureSet attributes = new FeatureSet();

	/**
	 * A key-value store for multidimensional {@link FeatureArray}s.
	 */
	private FeatureArraySet features = new FeatureArraySet();
	

	/**
	 * Temporal information associated with this Metadata (start/end timestamps (actual time))
	 */
	private TemporalProperties startEndTime = null, intervalStartEnd = null, temporalProperties = null;
	
	/**
	 * The duration by which to hash bins. It is assumed that this is in SECONDS*/
	private double duration; 
	/**
	 * Maintains metadata information that is only valid at system run time.
	 */
	private RuntimeMetadata runtimeMetadata = new RuntimeMetadata();

	/**
	 * Creates an unnamed Metadata instance
	 */
	public Metadata() {
	}

	/**
	 * Creates a named Metadata instance.
	 */
	public Metadata(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		if (name == null) {
			this.name = "";
		} else {
			this.name = name;
		}
	}
	

	/**
	 * Places a single feature into this Metadata instance's attribute
	 * FeatureSet.
	 */
	public void putAttribute(Feature feature) {
		attributes.put(feature);
	}

	public Feature getAttribute(String featureName) {
		return attributes.get(featureName);
	}

	public FeatureSet getAttributes() {
		return attributes;
	}
	
	/**
	 * Sets this Metadata container's attribute FeatureSet. This will eliminate
	 * any previously-added attributes.
	 *
	 * @param attributes
	 *            {@link FeatureSet} containing attributes that should be
	 *            associated with this Metadata instance.
	 */
	public void setAttributes(FeatureSet attributes) {
		this.attributes = attributes;
	}
	
	public void setSpatialProperties(SpatialProperties properties){
		this.spatialProperties = properties;
	}
	public void putFeature(FeatureArray feature) {
		features.put(feature);
	}
	
	public void setDuration(double dur)throws IllegalArgumentException{
		if (dur <= 0)
			throw new IllegalArgumentException("Metadata can't have a duration <= 0");
		this.duration = dur;
	}
	
	public double getDuration(){
		return this.duration;
	}
	public FeatureArray getFeature(String featureName) {
		return features.get(featureName);
	}

	/**
	 * Sets this Metadata container's set of Feature arrays. This will eliminate
	 * any previously-added Feature arrays.
	 *
	 * @param features
	 *            {@link FeatureArraySet} containing features that should be
	 *            associated with this Metadata instance.
	 */
	public void setFeatures(FeatureArraySet features) {
		this.features = features;
	}

	public FeatureArraySet getFeatures() {
		return features;
	}

	public void setStartEndTime(TemporalProperties temporalProperties) {
		this.startEndTime = temporalProperties;
	}

	public TemporalProperties getStartEndTime() {
		return this.startEndTime;
	}

	public boolean hasStartEndTime() {
		return this.startEndTime != null;
	}
	
	public void setIntervalStartEnd(TemporalProperties temporalProperties) {
		this.intervalStartEnd = temporalProperties;
	}

	public TemporalProperties getIntervalStartEnd() {
		return this.intervalStartEnd;
	}
	
	public void setTemporalProperties(TemporalProperties properties) {
		this.temporalProperties = properties;
	}
	public TemporalProperties getTemporalProperties() {
		return this.temporalProperties;
	}
	public SpatialProperties getSpatialProperties(){
		return this.spatialProperties;
	}
	public boolean hasIntervalStartEnd() {
		return this.intervalStartEnd != null;
	}
	
	@Override
	public String toString() {
		String nl = System.lineSeparator();
		String str = "Name: '" + name + "'" + nl + "Contains start/end time stamp: " + hasStartEndTime() + nl;
		if (hasStartEndTime()) {
			str += "Start/end Time Block:" + nl + startEndTime.toString() + nl;
		}
		
		str += "Contains interval start/end: " + hasIntervalStartEnd() + nl;
		if (hasIntervalStartEnd()){
			str += "Interval Start/End Time Block:" + nl + intervalStartEnd.toString() + nl;
		}

		str += "Number of Attributes: " + attributes.size() + nl;
		for (Feature f : attributes) {
			str += f.toString() + nl;
		}

		str += "Number of ND Feature Arrays: " + features.size() + nl;

		return str;
	}

	@Deserialize
	public Metadata(SerializationInputStream in) throws IOException, SerializationException {
		name = in.readString();
		attributes = new FeatureSet(in);
		features = new FeatureArraySet(in);
		runtimeMetadata = new RuntimeMetadata(in);
	}

	@Override
	public void serialize(SerializationOutputStream out) throws IOException {
		out.writeString(name);
		out.writeSerializable(attributes);
		out.writeSerializable(features);
		out.writeSerializable(runtimeMetadata);
	}
//	@Deserialize
//	public Metadata(SerializationInputStream in) throws IOException, SerializationException {
//		name = in.readString();
//		this.byteFormat = ByteFormat.fromString(in.readString());
//		this.waveType = Signal.fromString(in.readString());
//		this.duration = in.readDouble();
//		boolean startEndTime = in.readBoolean();
//		if (startEndTime) {
//			this.startEndTime = new TemporalProperties(in);
//		}		
//		boolean intervalStartEnd = in.readBoolean();
//		if (intervalStartEnd)
//			this.intervalStartEnd = new TemporalProperties(in);
//		attributes = new FeatureSet(in);
//		features = new FeatureArraySet(in);
//		runtimeMetadata = new RuntimeMetadata(in);
//	}
//
//	@Override
//	public void serialize(SerializationOutputStream out) throws IOException {
//		out.writeString(name);
//		out.writeString(byteFormat.toString());
//		out.writeString(this.waveType.toString());
//		out.writeDouble(this.duration);
//		out.writeBoolean(hasStartEndTime());
//		if (hasStartEndTime()) {
//			out.writeSerializable(this.startEndTime);
//		}
//		out.writeBoolean(hasIntervalStartEnd());
//		if (hasIntervalStartEnd())
//			out.writeSerializable(this.intervalStartEnd);
//		out.writeSerializable(attributes);
//		out.writeSerializable(features);
//		out.writeSerializable(runtimeMetadata);
//	}
}
