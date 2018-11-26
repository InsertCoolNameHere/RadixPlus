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

/**
 * This class is a specialized version of Metadata, designed specifically for 
 * the ROOTS project. */
public class RootsMetadata extends Metadata implements ByteSerializable {

	private String name = "";
	/**
	 * Metadata attributes: these Features are represented by a 1D array and are
	 * accessed as a simple key-value store.
	 */
	private FeatureSet attributes = new FeatureSet();

	/**
	 * A key-value store for multidimensional {@link FeatureArray}s.
	 */
	private FeatureArraySet features = new FeatureArraySet();
	private int plotID;
	private RuntimeMetadata runtimeMetadata = new RuntimeMetadata();
	/**
	 * Creates an unnamed Metadata instance
	 */
	public RootsMetadata() {
		super();
	}

	/**
	 * Creates a named Metadata instance.
	 */
	public RootsMetadata(String name) {
		super(name);
	}

	public int getPlotID() {
		return this.plotID;
	}
	
	public void setPlotID(int id) {
		this.plotID = id;
	}
	
	@Override
	public String toString() {
		String nl = System.lineSeparator();
		String str = "Name: '" + name + "'" + nl;


		str += "Number of Attributes: " + attributes.size() + nl;
		for (Feature f : attributes) {
			str += f.toString() + nl;
		}

		str += "Number of ND Feature Arrays: " + features.size() + nl;

		return str;
	}

	@Deserialize
	public RootsMetadata(SerializationInputStream in) throws IOException, SerializationException {
		name = in.readString();
		plotID = in.readInt();
		attributes = new FeatureSet(in);
		features = new FeatureArraySet(in);
		runtimeMetadata = new RuntimeMetadata(in);
	}

	@Override
	public void serialize(SerializationOutputStream out) throws IOException {
		out.writeString(name);	
		out.writeInt(plotID);
		out.writeSerializable(attributes);
		out.writeSerializable(features);
		out.writeSerializable(runtimeMetadata);
	}

	
}
