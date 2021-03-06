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
import java.util.Objects;

import galileo.serialization.ByteSerializable;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.SerializationOutputStream;

/**
 * Encapsulates a point in space with latitude, longitude coordinates.
 */
public class Coordinates implements ByteSerializable {
    private double lat;
    private double lon;

    /**
     * Create Coordinates at the specified latitude and longitude.
     *
     * @param lat
     *     Latitude for this coordinate pair, in degrees.
     * @param lon
     *     Longitude for this coordinate pair, in degrees.
     */
    public Coordinates(double lat, double lon) {
    	if(lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180){
    		this.lat = lat;
        	this.lon = lon;
    	} else if(lat >= -180 && lat <=180 && lon >= -90 && lon <= 90){
    		this.lat = lon;
    		this.lon = lat;
    	} else {
    		throw new IllegalArgumentException("Illegal location: " + lat + ", " + lon + ". Valid range is Latitude [-90, 90] and Longitude[-180, 180].");
    	}
    }

    /**
     * Get the latitude of this coordinate pair.
     *
     * @return latitude, in degrees.
     */
    public double getLatitude() {
        return lat;
    }

    /**
     * Get the longitude of this coordinate pair.
     *
     * @return longitude, in degrees
     */
    public double getLongitude() {
        return lon;
    }
    
    public void setLongitude(double lon) {
    	this.lon = lon;
    }
    
    public void setLatitude(double lat) {
    	this.lat = lat;
    }
    /**
     * Print this coordinate pair's String representation:
     * (lat, lon).
     *
     * @return String representation of the Coordinates
     */
    @Override
    public String toString() {
        return "(" + lat + ", " + lon + ")";
    }

    public Coordinates(SerializationInputStream in)
    throws IOException {
        this.lat = in.readDouble();
        this.lon = in.readDouble();
    }

    @Override
    public void serialize(SerializationOutputStream out)
    throws IOException {
        out.writeDouble(lat);
        out.writeDouble(lon);
    }
    @Override
    public boolean equals(Object o) {
    	if (o instanceof Coordinates) {
    		if (((Coordinates)o).lat == this.lat && ((Coordinates)o).lon == this.lon)
				return true;
    	}
    	return false;
    }
    @Override
    public int hashCode() {
        return Objects.hash(lat, lon);
    }
}
