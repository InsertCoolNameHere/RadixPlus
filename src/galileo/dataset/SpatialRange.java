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
import java.util.ArrayList;
import java.util.List;

import galileo.serialization.ByteSerializable;
import galileo.serialization.SerializationException;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.SerializationOutputStream;
import galileo.util.Pair;

public class SpatialRange implements ByteSerializable {
	private double upperLat;
	private double lowerLat;
	private double upperLon;
	private double lowerLon;

	private boolean hasElevation;
	private double upperElevation;
	private double lowerElevation;

	private List<Coordinates> polygon;

	public SpatialRange(List<Coordinates> polygon) {
		for (Coordinates coords : polygon) {
			if (this.polygon == null) {
				this.lowerLat = this.upperLat = coords.getLatitude();
				this.lowerLon = this.upperLon = coords.getLongitude();
				this.polygon = polygon;
			} else {
				if (coords.getLatitude() < this.lowerLat)
					this.lowerLat = coords.getLatitude();

				if (coords.getLatitude() > this.upperLat)
					this.upperLat = coords.getLatitude();

				if (coords.getLongitude() < this.lowerLon)
					this.lowerLon = coords.getLongitude();

				if (coords.getLongitude() > this.upperLon)
					this.upperLon = coords.getLongitude();
			}
		}
	}

	public SpatialRange(double lowerLat, double upperLat, double lowerLon, double upperLon) {
		this.lowerLat = lowerLat;
		this.upperLat = upperLat;
		this.lowerLon = lowerLon;
		this.upperLon = upperLon;

		hasElevation = false;
	}

	public SpatialRange(double lowerLat, double upperLat, double lowerLon, double upperLon, double upperElevation,
			float lowerElevation) {
		this.lowerLat = lowerLat;
		this.upperLat = upperLat;
		this.lowerLon = lowerLon;
		this.upperLon = upperLon;

		hasElevation = true;
		this.upperElevation = upperElevation;
		this.lowerElevation = lowerElevation;
	}

	public SpatialRange(SpatialRange copyFrom) {
		this.lowerLat = copyFrom.lowerLat;
		this.upperLat = copyFrom.upperLat;
		this.lowerLon = copyFrom.lowerLon;
		this.upperLon = copyFrom.upperLon;

		this.hasElevation = copyFrom.hasElevation;
		this.upperElevation = copyFrom.upperElevation;
		this.lowerElevation = copyFrom.lowerElevation;
	}

	/*
	 * Retrieves the smallest latitude value of this spatial range
	 */
	public double getLowerBoundForLatitude() {
		return lowerLat;
	}

	/*
	 * Retrieves the largest latitude value of this spatial range
	 */
	public double getUpperBoundForLatitude() {
		return upperLat;
	}

	/*
	 * Retrieves the smallest longitude value of this spatial range
	 */
	public double getLowerBoundForLongitude() {
		return lowerLon;
	}

	/*
	 * Retrieves the largest longitude value of this spatial range
	 */
	public double getUpperBoundForLongitude() {
		return upperLon;
	}

	public Coordinates getCenterPoint() {
		double latDifference = upperLat - lowerLat;
		double latDistance = latDifference / 2;

		double lonDifference = upperLon - lowerLon;
		double lonDistance = lonDifference / 2;

		return new Coordinates(lowerLat + latDistance, lowerLon + lonDistance);
	}
	
	public List<Coordinates> getBounds(){
		List<Coordinates> box = new ArrayList<Coordinates>();
		box.add(new Coordinates(upperLat, lowerLon));
		box.add(new Coordinates(upperLat, upperLon));
		box.add(new Coordinates(lowerLat, upperLon));
		box.add(new Coordinates(lowerLat, lowerLon));
		return box;
	}

	/**
	 * Using the upper and lower boundaries for this spatial range, generate two
	 * lat, lon points that represent the upper-left and lower-right coordinates
	 * of the range. Note that this method does not account for the curvature of
	 * the earth (aka the Earth is flat).
	 *
	 * @return a Pair of Coordinates, with the upper-left and lower-right points
	 *         of this spatial range.
	 */
	public Pair<Coordinates, Coordinates> get2DCoordinates() {
		return new Pair<>(new Coordinates(this.getUpperBoundForLatitude(), this.getLowerBoundForLongitude()),
				new Coordinates(this.getLowerBoundForLatitude(), this.getUpperBoundForLongitude()));
	}
	
	public List<Coordinates> getPolygon(){
		return this.polygon;
	}

	public boolean hasElevationBounds() {
		return hasElevation;
	}

	public double getUpperBoundForElevation() {
		return upperElevation;
	}

	public double getLowerBoundForElevation() {
		return lowerElevation;
	}

	public boolean hasPolygon() {
		return this.polygon != null;
	}

	@Override
	public String toString() {
		Pair<Coordinates, Coordinates> p = get2DCoordinates();
		return "[" + p.a + ", " + p.b + "]";
	}

	@Override
	public int hashCode() {
		final long prime = 31;
		long result = 1;
		result = prime * result + (hasElevation ? 1231 : 1237);
		result = prime * result + Double.doubleToLongBits(lowerElevation);
		result = prime * result + Double.doubleToLongBits(lowerLat);
		result = prime * result + Double.doubleToLongBits(lowerLon);
		result = prime * result + Double.doubleToLongBits(upperElevation);
		result = prime * result + Double.doubleToLongBits(upperLat);
		result = prime * result + Double.doubleToLongBits(upperLon);
		return (int)result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		SpatialRange other = (SpatialRange) obj;
		if (hasElevation != other.hasElevation) {
			return false;
		}
		if (Double.doubleToLongBits(lowerElevation) != Double.doubleToLongBits(other.lowerElevation)) {
			return false;
		}
		if (Double.doubleToLongBits(lowerLat) != Double.doubleToLongBits(other.lowerLat)) {
			return false;
		}
		if (Double.doubleToLongBits(lowerLon) != Double.doubleToLongBits(other.lowerLon)) {
			return false;
		}
		if (Double.doubleToLongBits(upperElevation) != Double.doubleToLongBits(other.upperElevation)) {
			return false;
		}
		if (Double.doubleToLongBits(upperLat) != Double.doubleToLongBits(other.upperLat)) {
			return false;
		}
		if (Double.doubleToLongBits(upperLon) != Double.doubleToLongBits(other.upperLon)) {
			return false;
		}
		return true;
	}

	@Deserialize
	public SpatialRange(SerializationInputStream in) throws IOException, SerializationException {
		lowerLat = in.readFloat();
		upperLat = in.readFloat();
		lowerLon = in.readFloat();
		upperLon = in.readFloat();

		hasElevation = in.readBoolean();
		if (hasElevation) {
			lowerElevation = in.readFloat();
			upperElevation = in.readFloat();
		}
		
		boolean hasPolygon = in.readBoolean();
		if(hasPolygon){
			List<Coordinates> poly = new ArrayList<Coordinates>();
			in.readSerializableCollection(Coordinates.class, poly);
			polygon = poly;
		}
	}

	@Override
	public void serialize(SerializationOutputStream out) throws IOException {
		out.writeDouble(lowerLat);
		out.writeDouble(upperLat);
		out.writeDouble(lowerLon);
		out.writeDouble(upperLon);

		out.writeBoolean(hasElevation);
		if (hasElevation) {
			out.writeDouble(lowerElevation);
			out.writeDouble(upperElevation);
		}
		
		out.writeBoolean(hasPolygon());
		if(hasPolygon()){
			out.writeSerializableCollection(polygon);
		}
	}
}
