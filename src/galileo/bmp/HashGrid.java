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
 * A class to represent a nested grid structure for georeferencing of coordinates to polygons. Requires
 * a shapefile to be present in {$GALILEO_HOME}/config/grid. File name must be "plots.json" in json format.
 * */

package galileo.bmp;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.json.JSONArray;
import org.json.JSONObject;

import galileo.dataset.Coordinates;
import galileo.dataset.Point;
import galileo.dataset.SpatialRange;
import galileo.util.GeoHash;
import galileo.util.Pair;
import geo.main.java.com.github.davidmoten.geo.GeoHashUtils;

public class HashGrid{


	private static final Logger logger = Logger.getLogger("galileo");

	private int width, height;

	public Bitmap bmp;
	public SortedSet<Integer> pendingUpdates;
	public ConcurrentHashMap<Integer, Integer> plotMapping;//testing purposes, may have to try new approach based on size of this map
	private SpatialRange baseRange;
	private double xDegreesPerPixel;
	private double yDegreesPerPixel;
	private String baseHash;
	private int maxHashValue;
	private int precision;
	public HashMap<Integer, HashMap<Long, Integer>> elevenCharIntersections = new HashMap<>();
	public HashMap<Long, HashMap<Path2D, Integer>> plotShapeToPlotID = new HashMap<>();
	private HashMap<Integer, Pair<Integer, String>> plotIDToRepAndGenotype = new HashMap<>();
	
	
	// HERE LONGITUDE IS X-AXIS AND LATITUDE IS Y-AXIS
	
	public HashGrid(String baseGeohash, int precision, String upperLeftHash, String bottomRightHash) {
		this.baseRange = GeoHash.decodeHash(baseGeohash);
		this.bmp = new Bitmap();
		this.baseHash = baseGeohash;
		this.precision = precision;
		this.plotMapping = new ConcurrentHashMap<>();
		this.pendingUpdates = new TreeSet<>();
		if (precision <= baseGeohash.length())
			throw new IllegalArgumentException("Precision must be finer than the base geohash");
		
		int divisor = precision - baseGeohash.length();
		if (divisor % 2 != 0 && divisor > 1)
			divisor--;
		if (precision % 2 != 0) {
			this.width = (int)Math.pow(8, precision-baseGeohash.length())/divisor;
			this.height = this.width/2;
		}
		else {
			this.width = (int)Math.pow(8,  precision-baseGeohash.length())/divisor;
			this.height = this.width;
		}
		this.maxHashValue = this.width*this.height;
		/*
		 * Determine the number of degrees in the x and y directions for the
		 * base spatial range this geoavailability grid represents
		 */
		double xDegrees = baseRange.getUpperBoundForLongitude() - baseRange.getLowerBoundForLongitude();
		double yDegrees = baseRange.getUpperBoundForLatitude() - baseRange.getLowerBoundForLatitude();
		/* Determine the number of degrees represented by each grid pixel */
		xDegreesPerPixel = xDegrees /  this.width;
		yDegreesPerPixel = yDegrees /  this.height;

	}
	
	
	public int getPrecision() {
		return this.precision;
	}
	public Pair<Integer, String> getPlotInfo(int plotID){
		return this.plotIDToRepAndGenotype.get(plotID);
	}
	
	
	public void initGrid(String filepath) throws HashGridException, IOException, BitmapException{
		
		logger.info("READING GRIDFILE FROM "+filepath);
		//Initialize the grid based on given file (logic in BitmapTester)
		String plots = new String(Files.readAllBytes(Paths.get(filepath)));
		JSONObject plotJson = new JSONObject(plots);
		JSONArray geometries = (JSONArray)plotJson.get("features");
		
		int cnt = 0;
		
		for (Object o : geometries){
			/*
			 * cnt++; if(cnt%10 == 0) System.out.println(cnt);
			 */
			Path2D poly = new Path2D.Double();
			//coords = all coordinates belonging to current object in iteration, length=1
			JSONArray coords = ((JSONArray)((JSONObject)((JSONObject)o).get("geometry")).get("coordinates"));
			JSONArray firstCoord = (JSONArray)((JSONArray)coords.get(0)).get(0);
			poly.moveTo(firstCoord.getDouble(0), firstCoord.getDouble(1));
			
			JSONObject properties = (JSONObject)(((JSONObject)o).get("properties"));
			int plotID = 0;
			
			if(properties.has("plotID")) {
				plotID = ((JSONObject)(((JSONObject)o).get("properties"))).getInt("plotID");
			} else {
				plotID = ((JSONObject)(((JSONObject)o).get("properties"))).getInt("ID_Plot");
			}
			
			
			String genotype = "";
			
			if(properties.has("Genotype")) {
				genotype = ((JSONObject)(((JSONObject)o).get("properties"))).getString("Genotype");
			} else {
				genotype = "GENO"+ThreadLocalRandom.current().nextInt(5);
			}
			//String genotype = ((JSONObject)(((JSONObject)o).get("properties"))).getString("Genotype");
			
			
			int rep = 0;
			
			
			if(properties.has("Rep")) {
				rep = ((JSONObject)(((JSONObject)o).get("properties"))).getInt("Rep");
			} else if(properties.has("rep")){
				rep = ((JSONObject)(((JSONObject)o).get("properties"))).getInt("rep");
			}  else {
				rep = ThreadLocalRandom.current().nextInt(0,5);
			}
			
			
			plotIDToRepAndGenotype.put(plotID, new Pair<>(rep, genotype));
			
			if (genotype.toString().equals("Water")) {
				continue;
			}
			ArrayList<Coordinates> polyPoints = new ArrayList<>();
			polyPoints.add(new Coordinates((double)firstCoord.get(1), (double)firstCoord.get(0)));
			for (int i = 1; i < ((JSONArray)coords.get(0)).length(); i++){
				double lat = ((JSONArray)((JSONArray)coords.get(0)).get(i)).getDouble(0);
				double lon = ((JSONArray)((JSONArray)coords.get(0)).get(i)).getDouble(1);
				polyPoints.add(new Coordinates(lat, lon));
				poly.lineTo(lat, lon);
			}
			poly.closePath();
			double[][] coordArr = listToArr(polyPoints);
			
			// GIVEN A POLYGON, FIND 11 CHAR GEOHASHES THAT LIE INSIDE IT
			// THE COORDINATE ARRAY COULD BE IN ANY CIRCULAR ORDER
			Set<String> coverage = GeoHashUtils.geoHashesForPolygon(precision, coordArr);

			// FOR ALL GEOHASHES CONTAINED IN THE PLOT POLYGON
			for (String ghash : coverage) {

				double [] hashBox = GeoHashUtils.decode_bbox(ghash);

				Path2D hashRect = new Path2D.Double();
				hashRect.moveTo(hashBox[2], hashBox[0]);
				hashRect.lineTo(hashBox[2], hashBox[1]);
				hashRect.lineTo(hashBox[3], hashBox[1]);
				hashRect.lineTo(hashBox[3], hashBox[0]);
				hashRect.lineTo(hashBox[2], hashBox[0]);
				hashRect.closePath();
				if ((int)plotID > 0)//hard-coded value that will need to change...
					this.addPoint(ghash, (int)plotID, poly);
				/*if (poly.intersects(hashRect.getBounds2D())) {
					this.addPoint(ghash, (int)plotID, poly);
				}*/
			}
		}
		/*Once all points are added, */
		applyUpdates();
		logger.info("RIKI: HashGrid fully initialized. TOTAL PLOTS: "+plotIDToRepAndGenotype.size());
	}
	
	public static double[][] listToArr(List<Coordinates> coords){
		double [][] coordArr = new double[coords.size()][2];
		for (int i = 0; i < coords.size(); i++) {
			coordArr[i] = new double[] {coords.get(i).getLongitude(), coords.get(i).getLatitude()};
		}
		return coordArr;
	}
	
	public int locatePoint(int index) {
		if (this.plotMapping.get(index) != null)
			return this.plotMapping.get(index);
		else return -1;
	}
	public int locatePoint(Coordinates coords) throws BitmapException {
		String geohash12 = GeoHash.encode(coords,  precision+1);
		String geohash11 = geohash12.substring(0, precision);
		int elevenCharKey = geohashToIndex(geohash11);

		if (plotMapping.get(elevenCharKey) != null) {
			if (!elevenCharIntersections.containsKey(elevenCharKey))
				return this.plotMapping.get(elevenCharKey);
			else {
				long twelveLong = GeoHash.hashToLong(geohash12);
				if (elevenCharIntersections.get(elevenCharKey).containsKey(twelveLong) && !plotShapeToPlotID.containsKey(twelveLong))
					return this.elevenCharIntersections.get(elevenCharKey).get(twelveLong);
				else {
					if (this.plotShapeToPlotID.containsKey(twelveLong)) {
						for (Map.Entry<Path2D, Integer> entry : plotShapeToPlotID.get(twelveLong).entrySet()) {
							if (entry.getKey().contains(coords.getLongitude(), coords.getLatitude())) {
								return entry.getValue();
							}
						}
					}
					if (elevenCharIntersections.get(elevenCharKey).containsKey(twelveLong))
						return elevenCharIntersections.get(elevenCharKey).get(twelveLong);
				}				
			}
			return plotMapping.get(elevenCharKey);
		}
		else {
			//throw new BitmapException("Could not identify plot for coordinates: " + coords + "(geohash="+geohash11+")");
			//logger.info("Could not identify plot for coordinates: " + coords + "(geohash="+geohash11+")");
		}
		return -1;
	}
	public Set<Integer> locatePoint(String geohash) throws BitmapException {
		if (geohash.length() != precision)
			throw new BitmapException("Must pass 11 character geohash!");
		Set<Integer> intersectingPlots = new HashSet<>();
		int elevenCharKey = geohashToIndex(geohash);

		if (plotMapping.get(elevenCharKey) != null) {
			if (!elevenCharIntersections.containsKey(elevenCharKey))
				intersectingPlots.add(this.plotMapping.get(elevenCharKey));
			else {
				for (char c : GeoHash.charMap) {
					long twelveLong = GeoHash.hashToLong(geohash+c);
					if (elevenCharIntersections.get(elevenCharKey).containsKey(twelveLong) && !plotShapeToPlotID.containsKey(twelveLong))
						intersectingPlots.add(this.elevenCharIntersections.get(elevenCharKey).get(twelveLong));
					else {
						if (this.plotShapeToPlotID.containsKey(twelveLong)) {
							for (Map.Entry<Path2D, Integer> entry : plotShapeToPlotID.get(twelveLong).entrySet()) {
								intersectingPlots.add(entry.getValue());
								
							}
						}
						if (elevenCharIntersections.get(elevenCharKey).containsKey(twelveLong))
							intersectingPlots.add(elevenCharIntersections.get(elevenCharKey).get(twelveLong));
					}
				}
								
			}
			return intersectingPlots;
		}
		else
			throw new BitmapException("Could not identify plot for geohash: " + geohash);
	}
	
	public HashGrid(HashGrid grid){
		this.baseRange = grid.baseRange;
		this.baseHash = grid.baseHash;
		this.width = grid.width;
		this.height = grid.height;
		this.xDegreesPerPixel = grid.xDegreesPerPixel;
		this.yDegreesPerPixel = grid.yDegreesPerPixel;
	}
	
	public static Coordinates [] getCorners(List<Coordinates> coordList) {
		//Should always return array with exactly two coordinate pairs,
		//First pair is top left point, second pair is bottom right point
		//First, find two northern most points(highest longs), take the further west of the two(lowest lat)
		//Second, find two southern most points(lowest longs), take the further east of the two(highest lat)
		HashMap<Double, Coordinates> map = new HashMap<>();
		ArrayList<Double> lats = new ArrayList<>();
		for (Coordinates c : coordList) {
			if (!lats.contains(c.getLatitude())){
				lats.add(c.getLatitude());
				map.put(c.getLatitude(), c);
			}
			
		}
		
		Collections.sort(lats);
		double lowestLong = lats.get(0);
		double secondLowestLong = lats.get(1);
		double highestLong = lats.get(lats.size()-1);
		double secondHighestLong = lats.get(lats.size()-2);
		Coordinates NW_most = (map.get(highestLong).getLongitude() <= map.get(secondHighestLong).getLongitude()) ? map.get(highestLong) : map.get(secondHighestLong);
		Coordinates SE_most = (map.get(lowestLong).getLongitude() >= map.get(secondLowestLong).getLongitude()) ? map.get(lowestLong) : map.get(secondLowestLong);		
		return new Coordinates[] {NW_most, SE_most};
	}
	/**
	 * Adds a new point to this HashGrid.
	 *
	 * @param coords
	 *            The location (coordinates in lat, lon) to add.
	 *            
	 * @param plotID
	 * 			  The plot identifier for the current coordinate pair
	 *
	 * @return true if the point could be added to grid, false otherwise (for
	 *         example, if the point falls outside the purview of the grid)
	 */
	public boolean addPoint(Coordinates coords, int plotID) {
		String geohash = GeoHash.encode(coords, this.precision);
		try {
			return addPoint(geohash, plotID);
		} catch (BitmapException e) {
			e.printStackTrace();
			return false;
		}
	}

	// poly: polygon bounds of the current plot
	public boolean addPoint(String geohash, int plotID, Path2D poly) throws BitmapException {
		// GET THE SUBSTRING AFTER BASEHASH AND CONVERT IT TO LONG
		int elevenCharKey = geohashToIndex(geohash);
		
		//If this geohash is not already associated with a plot, add it to the elevenCharKey->plotID map
		// ELEVEN CHAR KEY -> PLOTID MAP
		if (!this.plotMapping.containsKey(elevenCharKey))
			this.plotMapping.put(elevenCharKey, plotID);
		else {
			// This geohash is already associated with some other plot, add it to multipleIntersections map
			// MultipleIntersections = 11-char index-> Map<12-char index -> plotID>
			
			// IF THE ELEVEN CHAR HAS NEVER BEEN IN DISPUTE BEFORE,
			// THE FINER GEOHASHMAP HAS NEVER SEEN THIS 
			if (elevenCharIntersections.get(elevenCharKey) == null)
				elevenCharIntersections.put(elevenCharKey, new HashMap<>());
			
			// GOING A LEVEL FINER
			for (char c : GeoHash.charMap) {
				String twelveChar = geohash+c;
				double [] hashBox = GeoHashUtils.decode_bbox(twelveChar);

				Path2D twelveCharHashRect = new Path2D.Double();
				twelveCharHashRect.moveTo(hashBox[2], hashBox[0]);
				twelveCharHashRect.lineTo(hashBox[2], hashBox[1]);
				twelveCharHashRect.lineTo(hashBox[3], hashBox[1]);
				twelveCharHashRect.lineTo(hashBox[3], hashBox[0]);
				twelveCharHashRect.lineTo(hashBox[2], hashBox[0]);
				twelveCharHashRect.closePath();
				long twelveLong = GeoHash.hashToLong(twelveChar);
				
				// THE PLOT POLYGON FULLY CONTAINS THE 12 CHAR
				if (poly.contains(twelveCharHashRect.getBounds2D())) {
					// FOR A RESOLUTION FINER, THERE ARE NO PLOTS CLAIMING THE 12 CHAR GEOHASH-ID
					if (!elevenCharIntersections.get(elevenCharKey).containsKey(twelveLong)) 
						elevenCharIntersections.get(elevenCharKey).put(twelveLong, plotID);
					// SINCE THIS POLYGON FULLY CONTAINS THIS 12 CHAR, NO OTHER PLOT SHOULD CLAIM IT
				}
				// IF THE PLOT POLYGON ONLY INTERSECTS WITH THE 12-CHAR GEOHASH
				else if (poly.intersects(twelveCharHashRect.getBounds2D())) {
					// FOR A RESOLUTION FINER, THERE ARE NO PLOTS CLAIMING THE 12 CHAR GEOHASH-ID
					if (!elevenCharIntersections.get(elevenCharKey).containsKey(twelveLong))
						elevenCharIntersections.get(elevenCharKey).put(twelveLong, plotID);
					// EVEN AT 12 CHAR LEVEL, SOME OTHER PLOT HAS CLAIMED THIS GEOHASH-ID
					else {
						// IN THIS CASE< ACTUAL POLY SHAPE IS STORED AS A LAST DITCH SCENARIO
						if(plotShapeToPlotID.get(twelveLong) == null)
							plotShapeToPlotID.put(twelveLong, new HashMap<>());
						plotShapeToPlotID.get(twelveLong).put(poly, plotID);
					}
				}
			}
			
		}
		if (elevenCharKey > this.maxHashValue) {
			logger.warning("This point falls outside the purview of this bitmap");
			return false;
		}
		if (this.bmp.set(elevenCharKey) == false) {
			/* Could not set the bits now; add to pending updates */
			pendingUpdates.add(elevenCharKey);
			return false;
		}
		return true;
	}
	
	public boolean addPoint(String geohash, int plotID) throws BitmapException {
		int index = geohashToIndex(geohash);
		if (!this.plotMapping.containsKey(index))
			this.plotMapping.put(index, plotID);
		if (index > this.maxHashValue) {
			logger.warning("This point falls outside the purview of this bitmap");
			return false;
		}
		if (this.bmp.set(index) == false) {
			/* Could not set the bits now; add to pending updates */
			pendingUpdates.add(index);
			return false;
		}
		return true;
	}
	
	// CONVERTING AN INDEX ID TO CORRESPONDING GEOHASH
	public String indexToGroupHash(int idx) throws HashGridException {
		//Determine how long of a hash an index of this object represents
		int numChars = this.precision-this.baseHash.length();
		if (numChars > 6)
			throw new HashGridException("Integer value cannot represent more than 6 characters of geohash");
		String geohash = "";
		for (int i=0; i < numChars; i++) {
			geohash = GeoHash.charMap[31 & idx] + geohash;
			idx >>= 5;
		}
		return (this.baseHash + geohash);
	}

	public boolean addPoint(Coordinates coords) {
		String geohash = GeoHash.encode(coords, this.precision);
		//logger.info("RIKI: CURRENT POINT: "+coords+" "+geohash);
		try {
			return addPoint(geohash);
		} catch (BitmapException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean addPoint(String geohash) throws BitmapException {
		int index = geohashToIndex(geohash);
		if (index > this.maxHashValue) {
			logger.warning("This point falls outside the purview of this bitmap");
			return false;
		}
		if (this.bmp.set(index) == false) {
			/* Could not set the bits now; add to pending updates */
			pendingUpdates.add(index);
			return false;
		}
		return true;
	}
	public int geohashToIndex(String hash) throws BitmapException {
		if (!matchesBase(hash))
			throw new BitmapException("Point does not fall within purview of bitmap");
		String subhash = hash.substring(this.baseHash.length());
		return (int)GeoHash.hashToLong(subhash);
	}
	
	private boolean matchesBase(String geohash) {
		String subhash = geohash.substring(0, this.baseHash.length());
		return subhash.contentEquals(this.baseHash);
	}
	/**
	 * Applies pending updates that have not yet been integrated into the
	 * GeoavailabilityGrid instance.
	 */
	public void applyUpdates() {
		Bitmap updateBitmap = new Bitmap();
		synchronized(this.bmp) {
			for (int i : pendingUpdates) {
				if (updateBitmap.set(i) == false) {
					logger.warning("Could not set update bit");
					System.out.println("Failed to set a bit");
				}
			}
			pendingUpdates.clear();
	
			this.bmp = this.bmp.or(updateBitmap);
		}
	}

	/**
	 * Reports whether or not the supplied {@link GeoavailabilityQuery} instance
	 * intersects with the bits set in this geoavailability grid. This operation
	 * can be much faster than performing a full inspection of what bits are
	 * actually set.
	 *
	 * @param query
	 *            The query geometry to test for intersection.
	 *
	 * @return true if the supplied {@link GeoavailabilityQuery} intersects with
	 *         the data in the geoavailability grid.
	 */
	public boolean intersects(GeoavailabilityQuery query) throws BitmapException {
		applyUpdates();
		Bitmap queryBitmap = QueryTransform.queryToGridBitmap(query, this);
		return this.bmp.intersects(queryBitmap);
	}
	
	/**
	 * Converts a coordinate pair (defined with latitude, longitude in decimal
	 * degrees) to an x, y location in the grid.
	 *
	 * @param coords
	 *            the Coordinates to convert.
	 *
	 * @return Corresponding x, y location in the grid.
	 */
	public Point<Integer> coordinatesToXY(Coordinates coords) {
		String geohash = GeoHash.encode(coords, this.precision);
		int index = (int)(GeoHash.hashToLong(geohash));
		
		/*Convert a 1D index to a 2D location on grid*/
		return indexToXY(index);
	}
	
	/**
	 * Converts a bitmap index to X, Y coordinates in the grid.
	 */
	public Point<Integer> indexToXY(int index) {
		int x = index % this.width;
		int y = index / this.width;
		return new Point<>(x, y);
	}
	/**
	 * Queries the geoavailability grid, which involves performing a logical AND
	 * operation and reporting the resulting Bitmap.
	 *
	 * @param query
	 *            The query geometry to evaluate against the geoavailability
	 *            grid.
	 *
	 * @return An array of bitmap indices that matched the query.
	 */
	public int[] query(GeoavailabilityQuery query) throws BitmapException {
		applyUpdates();
		Bitmap queryBitmap = QueryTransform.queryToGridBitmap(query, this);
		if(logger.isLoggable(Level.FINEST)){
			BufferedImage b = null;
			try {
				String hostname = InetAddress.getLocalHost().getHostName();
				File queryImg = new File(getBaseHash() + "-" + hostname + "-query.png");
				if (!queryImg.exists()) {
					b = BitmapVisualization.drawBitmap(queryBitmap, getWidth(), getHeight(), Color.RED);
					BitmapVisualization.imageToFile(b, getBaseHash() + "-" + hostname + "-query.png");
				}
				File dataImg = new File(getBaseHash() + "-" + hostname + "-data.png");
				Bitmap thisBmp = this.bmp;
				if (dataImg.exists()) {
					BufferedImage in = ImageIO.read(dataImg);
					BufferedImage newImage = new BufferedImage(in.getWidth(), in.getHeight(),
							BufferedImage.TYPE_BYTE_INDEXED);

					Graphics2D g = newImage.createGraphics();
					g.drawImage(in, 0, 0, null);
					g.dispose();
					DataBufferByte buffer = ((DataBufferByte) newImage.getData().getDataBuffer());
					byte[] data = buffer.getData();
					Bitmap dataBitmap = Bitmap.fromBytes(data, 0, 0, in.getWidth(), in.getHeight(), getWidth(),
							getHeight());
					thisBmp = thisBmp.or(dataBitmap);
				}
				b = BitmapVisualization.drawIterableMap(thisBmp.iterator(), getWidth(), getHeight(), Color.BLUE);
				BitmapVisualization.imageToFile(b, getBaseHash() + "-" + hostname + "-data.png");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return this.bmp.and(queryBitmap).toArray();
	}
	
	public int[] query(Bitmap queryBitmap){
		
		//logger.info("RIKI: MASTER BITMAP: "+ this.bmp);
		//logger.info("RIKI: QUERY BITMAP: "+ queryBitmap);
		
		return this.bmp.and(queryBitmap).toArray();
	}

	/**
	 * Retrieves the underlying Bitmap instance backing this
	 * GeoavailabilityGrid.
	 */
	public Bitmap getBitmap() {
		applyUpdates();
		return bmp;
	}

	public String getBaseHash() {
		return this.baseHash;
	}

	/**
	 * Retrieves the width of this GeoavailabilityGrid, in grid cells.
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * Retrieves the height of this GeoavailabilityGrid, in grid cells.
	 */
	public int getHeight() {
		return height;
	}
	
	// direction se, sw, nw, ne
	public void getIntercepts(String geohash, int direction) {
		int basePrecision = baseHash.length();
		int cellPrecision = geohash.length();
		
		
		
		
		
	}
	
	
	
}
