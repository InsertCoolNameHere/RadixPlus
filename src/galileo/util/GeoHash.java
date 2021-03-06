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
package galileo.util;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import galileo.dataset.Coordinates;
import galileo.dataset.Point;
import galileo.dataset.Point2D;
import galileo.dataset.SpatialRange;
import galileo.dht.hash.TemporalHash;
import geo.main.java.com.github.davidmoten.geo.GeoHashUtils;

/**
 * This class provides an implementation of the GeoHash (http://www.geohash.org)
 * algorithm.
 *
 * See http://en.wikipedia.org/wiki/Geohash for implementation details.
 */
public class GeoHash {

	public final static byte BITS_PER_CHAR = 5;
	public final static int LATITUDE_RANGE = 90;
	public final static int LONGITUDE_RANGE = 180;
	public final static int MAX_PRECISION = 30; // 6 character precision = 30 (~
												// 1.2km x 0.61km)

	/**
	 * This character array maps integer values (array indices) to their GeoHash
	 * base32 alphabet equivalents.
	 */
	public final static char[] charMap = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'b', 'c', 'd', 'e', 'f',
			'g', 'h', 'j', 'k', 'm', 'n', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z' };
	
	
	public static List<Character> evenChars = Arrays.asList('b','c','f','g','u','v','y','z','8','9','d','e','s','t','w','x','2','3','6','7','k','m','q','r','0','1','4','5','h','j','n','p');
	public static char[][] evenMatrix = new char[][]{{'b','c','f','g','u','v','y','z'},{'8','9','d','e','s','t','w','x'},{'2','3','6','7','k','m','q','r'},{'0','1','4','5','h','j','n','p'}};
	
	public static List<Character> oddChars = Arrays.asList('p','r','x','z','n','q','w','y','j','m','t','v','h','k','s','u','5','7','e','g','4','6','d','f','1','3','9','c','0','2','8','b');
	public static char[][] oddMatrix = new char[][]{{'p','r','x','z'},{'n','q','w','y'},{'j','m','t','v'},{'h','k','s','u'},{'5','7','e','g'},{'4','6','d','f'},{'1','3','9','c'},{'0','2','8','b'}};
	
	public static int evenWidth = 8;
	public static int evenHeight = 4;
	public static int oddWidth = 4;
	public static int oddHeight = 8;
	
	
	public static void main(String arg[]) {
		//System.out.println(getCharShift('t', -7, -1, true));
		//System.out.println(-13%4);
		Point2D locateCellInGrid = locateCellInGrid("9", "9b", 2);
		System.out.println(locateCellInGrid);
	}
	
	
	// GIVEN A BIGGER BASEHASH, FIND THE ROW & COLUMN OF THIS GRID IN THAT CONTAINING HASH
	// X - ROW NUM
	// Y - COLUMN NUM
	
	// POINT IS RETURNED AS Y, X - SO, INSIDE POINT, Y is X axis and vice versa
	public static Point2D locateCellInGrid(String baseHash, String gridHash, int dir) {
		
		// X - ROW NUM
		// Y - COLUMN NUM
		int xMax = 1;
		int yMax = 1;
		
		int blen = baseHash.length();
		
		// INTERCEPTS
		int x = 1; 
		int y = 1;
		
		String substr = gridHash.substring(baseHash.length(), gridHash.length());
		
		for(int i=0; i< substr.length(); i++) {
			char c = substr.charAt(i);
			int currentx = 0;
			int currenty = 0;
			
			// BASEHASH/CURRENT HASH IS ODD
			if(blen % 2 != 0) {
				int indx = oddChars.indexOf(c);
				currentx = indx/oddWidth + 1;
				currenty = indx%oddWidth + 1;
				
				x = (x-1)*oddHeight + currentx;
				y = (y-1)*oddWidth+ currenty;
				
				xMax = xMax*oddHeight;
				yMax = yMax*oddWidth;
			} else {
				
				int indx = evenChars.indexOf(c);
				currentx = indx/evenWidth + 1;
				currenty = indx%evenWidth + 1;
				
				x = (x-1)*evenHeight + currentx;
				y = (y-1)*evenWidth+ currenty;
				
				xMax = xMax*evenHeight;
				yMax = yMax*evenWidth;
				
			}
			
			blen++;
		}
		
		
		Point2D p = new Point2D(y, x);
		
		// FOR THE FOLLOWING ZONES
		// NW Zone1
		if(dir == 1) {
			p = new Point2D((yMax-y+1)*-1, xMax-x+1);
		}
		// NE Zone2
		else if(dir == 2) {
			p = new Point2D(y, xMax-x+1);
		}
		// SE Zone3
		else if(dir == 3) {
			p = new Point2D(y,x*-1);
		}
		// NW Zone4
		else if(dir == 4) {
			p = new Point2D((yMax-y+1)*-1,x*-1);
		}
		
		return p;
	}
	
	
	// GIVEN A CELL COORDINATE WRT ORIGIN FIND THE ZONE
	// x,y are wrt origin
	public static int locateZoneFromCell(int x, int y) {
		int zID = -1 ;
		
		if(x < 0 && y > 0)
			return 1;
		else if(x > 0 && y > 0)
			return 2;
		else if(x > 0 && y < 0)
			return 3;
		else if(x < 0 && y < 0)
			return 4;
		
		return zID;
	}
	
	
	

	/**
	 * Allows lookups from a GeoHash character to its integer index value.
	 */
	public final static HashMap<Character, Integer> charLookupTable = new HashMap<Character, Integer>();

	/**
	 * Initialize HashMap for character to integer lookups.
	 */
	static {
		for (int i = 0; i < charMap.length; ++i) {
			charLookupTable.put(charMap[i], i);
		}
	}

	private String binaryHash;
	private Rectangle2D bounds;

	public GeoHash() {
		this("");
	}

	public GeoHash(String binaryString) {
		this.binaryHash = binaryString;
		ArrayList<Boolean> bits = new ArrayList<>();
		for (char bit : this.binaryHash.toCharArray())
			bits.add(bit == '0' ? false : true);
		double[] longitude = decodeBits(bits, false);
		double[] latitude = decodeBits(bits, true);
		SpatialRange range = new SpatialRange(latitude[0], latitude[1], longitude[0], longitude[1]);
		Pair<Coordinates, Coordinates> coordsPair = range.get2DCoordinates();
		Point<Integer> upLeft = coordinatesToXY(coordsPair.a);
		Point<Integer> lowRight = coordinatesToXY(coordsPair.b);
		this.bounds = new Rectangle(upLeft.X(), upLeft.Y(), lowRight.X() - upLeft.X(), lowRight.Y() - upLeft.Y());
	}

	public int getPrecision() {
		return this.binaryHash.length();
	}

	public String getBinaryHash() {
		return this.binaryHash;
	}

	public String[] getValues(int precision) {
		String[] values = null;
		String hash = "";
		for (int i = 0; i < this.binaryHash.length(); i += 5) {
			String hashChar = this.binaryHash.substring(i, java.lang.Math.min(i + 5, this.binaryHash.length()));
			if (hashChar.length() == 5)
				hash += charMap[Integer.parseInt(hashChar, 2)];
			else {
				String beginHash = hashChar;
				String endHash = hashChar;
				while (beginHash.length() < BITS_PER_CHAR) {
					beginHash += "0";
					endHash += "1";
				}
				values = new String[2];
				values[0] = hash + charMap[Integer.parseInt(beginHash, 2)];
				values[1] = hash + charMap[Integer.parseInt(endHash, 2)];
				while (values[0].length() < precision){
					values[0] += "0";
					values[1] += "z";
				}
			}
		}
		if (values == null){
			if (hash.length() < precision){
				String beginHash = hash;
				String endHash = hash;
				while (beginHash.length() < precision){
					beginHash += "0";
					endHash += "z";
				}
				values = new String[] { beginHash, endHash };
			} else {
				values = new String[] {hash};
			}
		}
		return values;
	}

	public Rectangle2D getRectangle() {
		return this.bounds;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof GeoHash) {
			GeoHash other = (GeoHash) obj;
			return this.binaryHash.equals(other.binaryHash);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return this.binaryHash.hashCode();
	}

	/**
	 * Encode a set of {@link Coordinates} into a GeoHash string.
	 *
	 * @param coords
	 *            Coordinates to get GeoHash for.
	 *
	 * @param precision
	 *            Desired number of characters in the returned GeoHash String.
	 *            More characters means more precision.
	 *
	 * @return GeoHash string.
	 */
	public static String encode(Coordinates coords, int precision) {
		return encode(coords.getLatitude(), coords.getLongitude(), precision);
	}

	/**
	 * Encode {@link SpatialRange} into a GeoHash string.
	 *
	 * @param range
	 *            SpatialRange to get GeoHash for.
	 *
	 * @param precision
	 *            Number of characters in the returned GeoHash String. More
	 *            characters is more precise.
	 *
	 * @return GeoHash string.
	 */
	public static String encode(SpatialRange range, int precision) {
		Coordinates rangeCoords = range.getCenterPoint();
		return encode(rangeCoords.getLatitude(), rangeCoords.getLongitude(), precision);
	}

	/**
	 * Encode latitude and longitude into a GeoHash string.
	 *
	 * @param latitude
	 *            Latitude coordinate, in degrees.
	 *
	 * @param longitude
	 *            Longitude coordinate, in degrees.
	 *
	 * @param precision
	 *            Number of characters in the returned GeoHash String. More
	 *            characters is more precise.
	 *
	 * @return resulting GeoHash String.
	 */
	public static String encode(double latitude, double longitude, int precision) {
		while (latitude < -90f || latitude > 90f)
			latitude = latitude < -90f ? 180.0f + latitude : latitude > 90f ? -180f + latitude : latitude;
		while (longitude < -180f || longitude > 180f)
			longitude = longitude < -180f ? 360.0f + longitude : longitude > 180f ? -360f + longitude : longitude;
		/*
		 * Set up 2-element arrays for longitude and latitude that we can flip
		 * between while encoding
		 */
		double[] high = new double[2];
		double[] low = new double[2];
		double[] value = new double[2];

		high[0] = LONGITUDE_RANGE;
		high[1] = LATITUDE_RANGE;
		low[0] = -LONGITUDE_RANGE;
		low[1] = -LATITUDE_RANGE;
		value[0] = longitude;
		value[1] = latitude;

		String hash = "";

		for (int p = 0; p < precision; ++p) {

			double middle = 0.0;
			int charBits = 0;
			for (int b = 0; b < BITS_PER_CHAR; ++b) {
				int bit = (p * BITS_PER_CHAR) + b;

				charBits <<= 1;

				middle = (high[bit % 2] + low[bit % 2]) / 2;
				if (value[bit % 2] > middle) {
					charBits |= 1;
					low[bit % 2] = middle;
				} else {
					high[bit % 2] = middle;
				}
			}

			hash += charMap[charBits];
		}

		return hash;
	}

	/**
	 * Convert a GeoHash String to a long integer.
	 *
	 * @param hash
	 *            GeoHash String to convert.
	 *
	 * @return The GeoHash as a long integer.
	 */
	public static long hashToLong(String hash) {
		long longForm = 0;

		/* Long can fit 12 GeoHash characters worth of precision. */
		if (hash.length() > 12) {
			hash = hash.substring(0, 12);
		}

		for (char c : hash.toCharArray()) {
			longForm <<= BITS_PER_CHAR;
			longForm |= charLookupTable.get(c);
		}

		return longForm;
	}

	/**
	 * Decode a GeoHash to an approximate bounding box that contains the
	 * original GeoHashed point.
	 *
	 * @param geoHash
	 *            GeoHash string
	 *
	 * @return Spatial Range (bounding box) of the GeoHash.
	 */
	public static SpatialRange decodeHash(String geoHash) {
		ArrayList<Boolean> bits = getBits(geoHash);

		double[] longitude = decodeBits(bits, false);
		double[] latitude = decodeBits(bits, true);

		return new SpatialRange(latitude[0], latitude[1], longitude[0], longitude[1]);
	}

	/**
	 * @param geohash
	 *            - geohash of the region for which the neighbors are needed
	 * @param direction
	 *            - one of nw, n, ne, w, e, sw, s, se
	 * @return
	 */
	public static String getNeighbour(String geohash, String direction) {
		if (geohash == null || geohash.trim().length() == 0)
			throw new IllegalArgumentException("Invalid Geohash");
		geohash = geohash.trim();
		int precision = geohash.length();
		SpatialRange boundingBox = decodeHash(geohash);
		Coordinates centroid = boundingBox.getCenterPoint();
		double widthDiff = boundingBox.getUpperBoundForLongitude() - centroid.getLongitude();
		double heightDiff = boundingBox.getUpperBoundForLatitude() - centroid.getLatitude();
		switch (direction) {
		case "nw":
			return encode(boundingBox.getUpperBoundForLatitude() + heightDiff,
					boundingBox.getLowerBoundForLongitude() - widthDiff, precision);
		case "n":
			return encode(boundingBox.getUpperBoundForLatitude() + heightDiff, centroid.getLongitude(), precision);
		case "ne":
			return encode(boundingBox.getUpperBoundForLatitude() + heightDiff,
					boundingBox.getUpperBoundForLongitude() + widthDiff, precision);
		case "w":
			return encode(centroid.getLatitude(), boundingBox.getLowerBoundForLongitude() - widthDiff, precision);
		case "e":
			return encode(centroid.getLatitude(), boundingBox.getUpperBoundForLongitude() + widthDiff, precision);
		case "sw":
			return encode(boundingBox.getLowerBoundForLatitude() - heightDiff,
					boundingBox.getLowerBoundForLongitude() - widthDiff, precision);
		case "s":
			return encode(boundingBox.getLowerBoundForLatitude() - heightDiff, centroid.getLongitude(), precision);
		case "se":
			return encode(boundingBox.getLowerBoundForLatitude() - heightDiff,
					boundingBox.getUpperBoundForLongitude() + widthDiff, precision);
		default:
			return "";
		}
	}

	public static String[] getNeighbours(String geoHash) {
		String[] neighbors = new String[8];
		if (geoHash == null || geoHash.trim().length() == 0)
			throw new IllegalArgumentException("Invalid Geohash");
		geoHash = geoHash.trim();
		int precision = geoHash.length();
		SpatialRange boundingBox = decodeHash(geoHash);
		Coordinates centroid = boundingBox.getCenterPoint();
		double widthDiff = boundingBox.getUpperBoundForLongitude() - centroid.getLongitude();
		double heightDiff = boundingBox.getUpperBoundForLatitude() - centroid.getLatitude();
		neighbors[0] = encode(boundingBox.getUpperBoundForLatitude() + heightDiff,
				boundingBox.getLowerBoundForLongitude() - widthDiff, precision);
		neighbors[1] = encode(boundingBox.getUpperBoundForLatitude() + heightDiff, centroid.getLongitude(), precision);
		neighbors[2] = encode(boundingBox.getUpperBoundForLatitude() + heightDiff,
				boundingBox.getUpperBoundForLongitude() + widthDiff, precision);
		neighbors[3] = encode(centroid.getLatitude(), boundingBox.getLowerBoundForLongitude() - widthDiff, precision);
		neighbors[4] = encode(centroid.getLatitude(), boundingBox.getUpperBoundForLongitude() + widthDiff, precision);
		neighbors[5] = encode(boundingBox.getLowerBoundForLatitude() - heightDiff,
				boundingBox.getLowerBoundForLongitude() - widthDiff, precision);
		neighbors[6] = encode(boundingBox.getLowerBoundForLatitude() - heightDiff, centroid.getLongitude(), precision);
		neighbors[7] = encode(boundingBox.getLowerBoundForLatitude() - heightDiff,
				boundingBox.getUpperBoundForLongitude() + widthDiff, precision);
		return neighbors;
	}

	/**
	 * @param coordinates
	 *            - latitude and longitude values
	 * @return Point - x, y pair obtained from a geohash precision of 12. x,y
	 *         values range from [0, 4096)
	 */
	public static Point<Integer> coordinatesToXY(Coordinates coords) {
		int width = 1 << MAX_PRECISION;
		double xDiff = coords.getLongitude() + 180;
		double yDiff = 90 - coords.getLatitude();
		int x = (int) (xDiff * width / 360);
		int y = (int) (yDiff * width / 180);
		return new Point<>(x, y);
	}

	public static Coordinates xyToCoordinates(int x, int y) {
		int width = 1 << MAX_PRECISION;
		return new Coordinates(90 - y * 180f / width, x * 360f / width - 180f);
	}

	public static String[] getIntersectingGeohashes(Path2D geometry, int precision) {
		Set<String> hashes = new HashSet<String>();
		
		// center may not lie inside polygon so start with any vertex of the
		// polygon
		Coordinates spatialCenter = new Coordinates(geometry.getCurrentPoint().getX(), geometry.getCurrentPoint().getY());
		Rectangle2D box = geometry.getBounds2D();// bounding box for original polygon
//		Area box = new Area(geometry);
		String geohash = encode(spatialCenter, precision);
		Queue<String> hashQue = new LinkedList<String>();
		Set<String> computedHashes = new HashSet<String>();
		hashQue.offer(geohash); //add the geohash of spatialCenter to the queue

//		System.out.println("Original box: " + box.getBounds2D().getMinX() + "," + box.getBounds2D().getMinY() + "\n" + box.getBounds2D().getMaxX() + "," + box.getBounds2D().getMaxY());
		while (!hashQue.isEmpty()) {
			String hash = hashQue.poll();//retrieve first element from queue
			computedHashes.add(hash);//add to set of hashes that have already been computed
//			SpatialRange hashRange = decodeHash(hash);
			double [] hashBox = GeoHashUtils.decode_bbox(hash);
//			SpatialRange hashRange = new SpatialRange(hashBox[0], hashBox[1], hashBox[2], hashBox[3]);
//			Pair<Coordinates, Coordinates> coordsPair = hashRange.get2DCoordinates();
			//Need to compute 4 points of the geohash box. Assume a perfect rectangle shape...
			Path2D hashRect = new Path2D.Double();
			hashRect.moveTo(hashBox[2], hashBox[0]);
			hashRect.lineTo(hashBox[2], hashBox[1]);
			hashRect.lineTo(hashBox[3], hashBox[1]);
			hashRect.lineTo(hashBox[3], hashBox[0]);
			hashRect.lineTo(hashBox[2], hashBox[0]);
			hashRect.closePath();
//			Area hashArea = new Area(hashRect);
//			System.out.println("geometry: " + geometry.getBounds2D() + ", hashRect: " +hashRect.getBounds2D());
//			if (hash.equals(geohash) && box.contains(hashRect.getBounds2D())) {
//				hashes.add(hash);
//				break;
//			}
//			System.out.println("Hashrect bounds: " + hashRect.getBounds2D().getMinX() + "," + hashRect.getBounds2D().getMinY() + "\n" + hashRect.getBounds2D().getMaxX() + "," + hashRect.getBounds2D().getMaxY());
			if (geometry.intersects(hashRect.getBounds2D()) || geometry.contains(hashRect.getBounds2D())) {
				hashes.add(hash);
				String[] neighbors = getNeighbours(hash);
				for (String neighbour : neighbors)
					if (!computedHashes.contains(neighbour) && !hashQue.contains(neighbour)) {
						hashQue.offer(neighbour);
					}
			}
		}
		return hashes.size() > 0 ? hashes.toArray(new String[hashes.size()]) : new String[] {};
	}
	
	public static String[] getIntersectingGeohashes(List<Coordinates> polygon, int precision) {
		Set<String> hashes = new HashSet<String>();
		Polygon geometry = new Polygon();
		for (Coordinates coords : polygon) {
			Point<Integer> point = coordinatesToXY(coords);
			geometry.addPoint(point.X(), point.Y());
		}
		// center may not lie inside polygon so start with any vertex of the
		// polygon
		Coordinates spatialCenter = polygon.get(0);
		Rectangle2D box = geometry.getBounds2D();
		String geohash = encode(spatialCenter, precision);
		Queue<String> hashQue = new LinkedList<String>();
		Set<String> computedHashes = new HashSet<String>();
		hashQue.offer(geohash); //add the geohash of spatialCenter to the queue
		while (!hashQue.isEmpty()) {
			String hash = hashQue.poll();//retrieve first element from queue
			computedHashes.add(hash);//add to set of hashes that have already been computed
			SpatialRange hashRange = decodeHash(hash);
//			double [] hashBox = GeoHashUtils.decode_bbox(hash);
//			SpatialRange hashRange = new SpatialRange(hashBox[0], hashBox[1], hashBox[2], hashBox[3]);
			Pair<Coordinates, Coordinates> coordsPair = hashRange.get2DCoordinates();
			Point<Integer> upLeft = coordinatesToXY(coordsPair.a);
			Point<Integer> lowRight = coordinatesToXY(coordsPair.b);
			Rectangle2D hashRect = new Rectangle(upLeft.X(), upLeft.Y(), lowRight.X() - upLeft.X(),
					lowRight.Y() - upLeft.Y());
			if (hash.equals(geohash) && hashRect.contains(box)) {
				hashes.add(hash);
				break;
			}
			if (geometry.intersects(hashRect) || geometry.contains(hashRect) ) {
				hashes.add(hash);
				String[] neighbors = getNeighbours(hash);
				for (String neighbour : neighbors)
					if (!computedHashes.contains(neighbour) && !hashQue.contains(neighbour)) {
						hashQue.offer(neighbour);
					}
			}
		}
		return hashes.size() > 0 ? hashes.toArray(new String[hashes.size()]) : new String[] {};
	}

	/**
	 * Decode GeoHash bits from a binary GeoHash.
	 *
	 * @param bits
	 *            ArrayList of Booleans containing the GeoHash bits
	 *
	 * @param latitude
	 *            If set to <code>true</code> the latitude bits are decoded. If
	 *            set to <code>false</code> the longitude bits are decoded.
	 *
	 * @return low, high range that the GeoHashed location falls between.
	 */
	private static double[] decodeBits(ArrayList<Boolean> bits, boolean latitude) {
		double low, high, middle;
		int offset;

		if (latitude) {
			offset = 1;
			low = -90.0f;
			high = 90.0f;
		} else {
			offset = 0;
			low = -180.0f;
			high = 180.0f;
		}

		for (int i = offset; i < bits.size(); i += 2) {
			middle = (high + low) / 2;

			if (bits.get(i)) {
				low = middle;
			} else {
				high = middle;
			}
		}

		if (latitude) {
			return new double[] { low, high };
		} else {
			return new double[] { low, high };
		}
	}

	/**
	 * Converts a GeoHash string to its binary representation.
	 *
	 * @param hash
	 *            GeoHash string to convert to binary
	 *
	 * @return The GeoHash in binary form, as an ArrayList of Booleans.
	 */
	private static ArrayList<Boolean> getBits(String hash) {
		hash = hash.toLowerCase();

		/* Create an array of bits, 5 bits per character: */
		ArrayList<Boolean> bits = new ArrayList<Boolean>(hash.length() * BITS_PER_CHAR);

		/* Loop through the hash string, setting appropriate bits. */
		for (int i = 0; i < hash.length(); ++i) {
			int charValue = charLookupTable.get(hash.charAt(i));

			/* Set bit from charValue, then shift over to the next bit. */
			for (int j = 0; j < BITS_PER_CHAR; ++j, charValue <<= 1) {
				bits.add((charValue & 0x10) == 0x10);
			}
		}
		return bits;
	}

	public static String convertToBinaryString(String geohash) {
		ArrayList<Boolean> bitList = getBits(geohash);
		String binString = "";
		for (Boolean bit : bitList) {
			binString += (bit == true) ? "1" : "0"; 
		}
		return binString;
	}
	
	public static Polygon buildAwtPolygon(List<Coordinates> geometry) {
		Polygon polygon = new Polygon();
		for (Coordinates coords : geometry) {
			Point<Integer> point = coordinatesToXY(coords);
			polygon.addPoint(point.X(), point.Y());
		}
		return polygon;
	}

	public static void getGeohashPrefixes(Polygon polygon, GeoHash gh, int precision, Set<GeoHash> intersections) {
		if (gh.getPrecision() >= precision) {
			intersections.add(gh);
		} else {
			if (polygon.contains(gh.getRectangle())) {
				intersections.add(gh);
			} else {
				GeoHash leftGH = new GeoHash(gh.getBinaryHash() + "0");
				GeoHash rightGH = new GeoHash(gh.getBinaryHash() + "1");
				if (polygon.intersects(leftGH.getRectangle()))
					getGeohashPrefixes(polygon, leftGH, precision, intersections);
				if (polygon.intersects(rightGH.getRectangle()))
					getGeohashPrefixes(polygon, rightGH, precision, intersections);
			}
		}
	}
	
	
	
	public static Calendar getCalendarFromTimestamp(String timeString, boolean isEpoch) {
		
		long timeStamp = Long.valueOf(timeString);
		
		if(timeString.length() < 13)
			timeStamp*=1000;
		
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeZone(TemporalHash.TIMEZONE);
		calendar.setTimeInMillis(timeStamp);
		
		return calendar;
		
	}
	
	
	public static void getLongestSubstring(String filepath) throws IOException {
		
		String longestSubstr = "";
		boolean starting = true;
		
		
		String plots = new String(Files.readAllBytes(Paths.get(filepath)));
		JSONObject plotJson = new JSONObject(plots);
		JSONArray geometries = (JSONArray)plotJson.get("features");
		
		for (Object o : geometries){
			//coords = all coordinates belonging to current object in iteration, length=1
			JSONArray coords = ((JSONArray)((JSONObject)((JSONObject)o).get("geometry")).get("coordinates"));
			JSONArray firstCoord = (JSONArray)((JSONArray)coords.get(0)).get(0);
			
			ArrayList<Coordinates> polyPoints = new ArrayList<>();
			polyPoints.add(new Coordinates((double)firstCoord.get(1), (double)firstCoord.get(0)));
			for (int i = 1; i < ((JSONArray)coords.get(0)).length(); i++){
				double lat = ((JSONArray)((JSONArray)coords.get(0)).get(i)).getDouble(0);
				double lon = ((JSONArray)((JSONArray)coords.get(0)).get(i)).getDouble(1);
				polyPoints.add(new Coordinates(lat, lon));
			}
			
			double[][] coordArr = listToArr(polyPoints);
			Set<String> coverage = GeoHashUtils.geoHashesForPolygon(11, coordArr);

			for (String ghash : coverage) {
				
				if(starting) {
					longestSubstr = ghash;
					starting = false;
				} else {
					longestSubstr = greatestCommonPrefix(longestSubstr, ghash);
				}
				
				
			}
		}
		
		System.out.println(longestSubstr);
	}
	
	public static String greatestCommonPrefix(String a, String b) {
	    int minLength = java.lang.Math.min(a.length(), b.length());
	    for (int i = 0; i < minLength; i++) {
	        if (a.charAt(i) != b.charAt(i)) {
	            return a.substring(0, i);
	        }
	    }
	    return a.substring(0, minLength);
	}
	
	public static double[][] listToArr(List<Coordinates> coords){
		double [][] coordArr = new double[coords.size()][2];
		for (int i = 0; i < coords.size(); i++) {
			coordArr[i] = new double[] {coords.get(i).getLongitude(), coords.get(i).getLatitude()};
		}
		return coordArr;
	}
	
	
	public static String[] getIntersectingGeohashesForConvexBoundingPolygon(List<Coordinates> polygon, int precision) {
		Set<String> hashes = new HashSet<String>();
		Polygon geometry = new Polygon();
		for (Coordinates coords : polygon) {
			Point<Integer> point = coordinatesToXY(coords);
			geometry.addPoint(point.X(), point.Y());
		}
		Coordinates spatialCenter = new SpatialRange(polygon).getCenterPoint();
		Rectangle2D box = geometry.getBounds2D();
		String geohash = encode(spatialCenter, precision);
		Queue<String> hashQue = new LinkedList<String>();
		Set<String> computedHashes = new HashSet<String>();
		hashQue.offer(geohash);
		while (!hashQue.isEmpty()) {
			String hash = hashQue.poll();
			computedHashes.add(hash);
			SpatialRange hashRange = decodeHash(hash);
			Pair<Coordinates, Coordinates> coordsPair = hashRange.get2DCoordinates();
			Point<Integer> upLeft = coordinatesToXY(coordsPair.a);
			Point<Integer> lowRight = coordinatesToXY(coordsPair.b);
			Rectangle2D hashRect = new Rectangle(upLeft.X(), upLeft.Y(), lowRight.X() - upLeft.X(),
					lowRight.Y() - upLeft.Y());
			if (hash.equals(geohash) && hashRect.contains(box)) {
				hashes.add(hash);
				break;
			} 
			if (geometry.intersects(hashRect)) {
				hashes.add(hash);
				String[] neighbors = getNeighbours(hash);
				for (String neighbour : neighbors)
					if (!computedHashes.contains(neighbour) && !hashQue.contains(neighbour))
						hashQue.offer(neighbour);
			}
		}
		return hashes.size() > 0 ? hashes.toArray(new String[hashes.size()]) : new String[] {};
	}
	
	
	public static int[] getNecessaryIndices(String sensorType) {
		
		// ORDER IS LAT, LON, TIMESTAMP, ACTUAL_ATTRIBUTE
		
		if(sensorType.equals("vanilla")) {
			
			int[] indices = {11,12,0};
			return indices;
		} else if(sensorType.equals("irt") || sensorType.equals("ndvi")|| sensorType.equals("sonar")) {
			
			int[] indices = {2,3,0,5};
			return indices;
		} else if(sensorType.contains("lidar")) {
			
			int[] indices = {2,3,0,7};
			return indices;
			
		} else {
			
			int[] indices = {11,12};
			return indices;
			
		}
		
		
	}
	
	
	// GIVEN A SET OF GEOHASHES< GET A SET OF FINER RESOLUTION GEOHASHES INSIDE THEM
	public static String[] generateSmallerGeohashes(String[] baseGeohashes, int desiredPrecision) {
		List<String> allGeoHashes = new ArrayList<String>(Arrays.asList(baseGeohashes));
		
		for(int i = 1; i < desiredPrecision; i++) {
			
			List<String> currentGeohashes = new ArrayList<String>();
			
			for(String geoHash : allGeoHashes) {
				
				
				SpatialRange range1 = GeoHash.decodeHash(geoHash);
				
				Coordinates c1 = new Coordinates(range1.getLowerBoundForLatitude(), range1.getLowerBoundForLongitude());
				Coordinates c2 = new Coordinates(range1.getUpperBoundForLatitude(), range1.getLowerBoundForLongitude());
				Coordinates c3 = new Coordinates(range1.getUpperBoundForLatitude(), range1.getUpperBoundForLongitude());
				Coordinates c4 = new Coordinates(range1.getLowerBoundForLatitude(), range1.getUpperBoundForLongitude());
				
				ArrayList<Coordinates> cs1 = new ArrayList<Coordinates>();
				cs1.add(c1);cs1.add(c2);cs1.add(c3);cs1.add(c4);
				
				currentGeohashes.addAll(Arrays.asList(GeoHash.getIntersectingGeohashesForConvexBoundingPolygon(cs1, i+1)));
				
			}
			allGeoHashes = currentGeohashes;
			
		}
		Collections.sort(allGeoHashes);
		String[] returnArray = allGeoHashes.toArray(new String[allGeoHashes.size()]);
		return returnArray;
	}
	
	
	public static List<String> getInternalGeohashes(String geohash, int precision) {
		
		List<String> allGeoHashes = new ArrayList<String>();
		allGeoHashes.add(geohash);
		
		for(int i = geohash.length(); i < precision; i++) {
			
			List<String> currentGeohashes = new ArrayList<String>();
			
			for(String geoHash : allGeoHashes) {
				
				
				SpatialRange range1 = GeoHash.decodeHash(geoHash);
				
				Coordinates c1 = new Coordinates(range1.getLowerBoundForLatitude(), range1.getLowerBoundForLongitude());
				Coordinates c2 = new Coordinates(range1.getUpperBoundForLatitude(), range1.getLowerBoundForLongitude());
				Coordinates c3 = new Coordinates(range1.getUpperBoundForLatitude(), range1.getUpperBoundForLongitude());
				Coordinates c4 = new Coordinates(range1.getLowerBoundForLatitude(), range1.getUpperBoundForLongitude());
				
				ArrayList<Coordinates> cs1 = new ArrayList<Coordinates>();
				cs1.add(c1);cs1.add(c2);cs1.add(c3);cs1.add(c4);
				
				currentGeohashes.addAll(GeoHash.getInternalGeohashes(geoHash));
				
			}
			allGeoHashes = currentGeohashes;
			
		}
		Collections.sort(allGeoHashes);
		//String[] returnArray = allGeoHashes.toArray(new String[allGeoHashes.size()]);
		return allGeoHashes;
	}
	
	public static List<String> getInternalGeohashes(String geohash) {

		List<String> childrenGeohashes = new ArrayList<>();

		for (char c : charMap) {
			childrenGeohashes.add(geohash + c);
		}

		return childrenGeohashes;
	}

	
	
}
