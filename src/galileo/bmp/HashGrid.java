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
import galileo.dataset.Point2D;
import galileo.dataset.SpatialRange;
import galileo.util.GeoHash;
import galileo.util.Pair;
import geo.main.java.com.github.davidmoten.geo.GeoHashUtils;

public class HashGrid{

	// PATH2D AND ALL JAVA GEOMETRY BASED STUFF CONSIDER LONGITUDE AS X AXIS AND LATITUDE AS Y AXIS
	private static final Logger logger = Logger.getLogger("galileo");

	private int width, height;
	
	
	public String upperLeftHash="";
	public String upperRightHash="";
	public String bottomRightHash="";
	public String bottomLeftHash="";

	public Bitmap bmp;
	public SortedSet<Integer> pendingUpdates;
	public ConcurrentHashMap<Integer, Integer> plotMapping;//testing purposes, may have to try new approach based on size of this map
	//private SpatialRange baseRange;
	//private double xDegreesPerPixel;
	//private double yDegreesPerPixel;
	//private String baseHash;
	//private int maxHashValue;
	private int precision;
	public HashMap<Integer, HashMap<Long, Integer>> elevenCharIntersections = new HashMap<>();
	public HashMap<Long, HashMap<Path2D, Integer>> plotShapeToPlotID = new HashMap<>();
	private HashMap<Integer, Pair<Integer, String>> plotIDToRepAndGenotype = new HashMap<>();
	
	private String zone1 = "";
	private String zone2 = "";
	private String zone3 = "";
	private String zone4 = "";
	
	private int yNorth = 0;
	private int xWest = 0;
	
	// HIGHLAT,LOWLAT,HIGHLON,LOWLON
	private double[] spatialBounds = new double[4];
	
	private int basePrecision = 0;
	
	// HERE LONGITUDE IS X-AXIS AND LATITUDE IS Y-AXIS
	// BASEHASH HAS TO HAVE $ COMMA SEPARATED STRINGS...MAKE THEM EMPTY IF THEY DONT EXIST
	public HashGrid(String baseGeohash, int precision, String upperLeftHash, String upperRightHash, String bottomRightHash, String bottomLeftHash) {
		
		this.upperLeftHash = upperLeftHash;
		this.upperRightHash = upperRightHash;
		this.bottomRightHash = bottomRightHash;
		this.bottomLeftHash = bottomLeftHash;

		SpatialRange topLeft = GeoHash.decodeHash(upperLeftHash);
		SpatialRange bottomRight = GeoHash.decodeHash(bottomRightHash);

		// HIGHLAT,LOWLAT,HIGHLON,LOWLON
		spatialBounds[0] = topLeft.getUpperBoundForLatitude();
		spatialBounds[1] = bottomRight.getLowerBoundForLatitude();
		spatialBounds[2] = bottomRight.getUpperBoundForLongitude();
		spatialBounds[3] = topLeft.getLowerBoundForLongitude();

		// WE HAVE AT MOST 4 BASEHASHES NOW
		String[] tokens = baseGeohash.split(",");

		zone1 = tokens[0];
		if (tokens.length > 1)
			zone2 = tokens[1];
		if (tokens.length > 2)
			zone3 = tokens[2];
		if (tokens.length > 3)
			zone4 = tokens[3];
		basePrecision = zone1.length();

		// INITIALIZING THE FULL WIDTH AND HEIGHT OF THE HASH-GRID
		initializeWidthHeight(upperLeftHash, upperRightHash, bottomRightHash, bottomLeftHash);

		this.bmp = new Bitmap();

		// PRECISION OF THE CELLS IN THE HASHGRID
		this.precision = precision;

		this.plotMapping = new ConcurrentHashMap<>();
		this.pendingUpdates = new TreeSet<>();

		if (precision <= basePrecision)
			throw new IllegalArgumentException("Precision must be finer than the base geohash(es)");

	}
	
	
	
	private void initializeWidthHeight(String upperLeftHash, String upperRightHash, String bottomRightHash, String bottomLeftHash) {
		
		Point2D upLeft = null;
		Point2D botRight = null;
		
		// IF ONLY 1 EXISTS
		if(zone2.isEmpty() && zone3.isEmpty() && zone4.isEmpty()) {
			
			upLeft = GeoHash.locateCellInGrid(zone1, upperLeftHash, 1);
			botRight = GeoHash.locateCellInGrid(zone1, bottomRightHash, 1);
		}
		// IF ONLY 2 EXISTS - E/W
		else if(zone3.isEmpty() && zone4.isEmpty()) {
			upLeft = GeoHash.locateCellInGrid(zone1, upperLeftHash, 1);
			botRight = GeoHash.locateCellInGrid(zone2, bottomRightHash, 2);
		} 
		// IF ONLY 2 EXISTS - N/S
		else if(zone2.isEmpty() && zone4.isEmpty()) {
			upLeft = GeoHash.locateCellInGrid(zone1, upperLeftHash, 1);
			botRight = GeoHash.locateCellInGrid(zone2, bottomRightHash, 2);
		} 
		// IF ALL 4 EXISTS
		else {
			upLeft = GeoHash.locateCellInGrid(zone1, upperLeftHash, 1);
			botRight = GeoHash.locateCellInGrid(zone3, bottomRightHash, 3);
		}
		
		yNorth = java.lang.Math.abs(upLeft.getY());
		xWest = java.lang.Math.abs(upLeft.getX());
		
		height = upLeft.getY()-botRight.getY();
		if(botRight.getY() > 0)
			height = upLeft.getY()-botRight.getY()+1;
		width = botRight.getX()-upLeft.getX();
		if(upLeft.getX() > 0)
			width = botRight.getX()-upLeft.getX()+1;
		
		
	}
	
	// WHICH ZONE DOES THIS HASH LIE IN
	// TO BE USED DURING GEO-LOCATING A POINT
	private int getZone(String hash) {
		
		if(hash.startsWith(zone1))
			return 1;
		else if(!zone2.isEmpty() && hash.startsWith(zone2))
			return 2;
		else if(!zone3.isEmpty() && hash.startsWith(zone3))
			return 3;
		else if(!zone4.isEmpty() && hash.startsWith(zone4))
			return 4;
		else
			return -1;
		
	}
	
	private String getZoneBase(int zID) {
		if(zID == 1)
			return zone1;
		if(zID == 2)
			return zone2;
		if(zID == 3)
			return zone3;
		if(zID == 4)
			return zone4;
		
		return null;
		
		
		
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
				logger.info("RIKI: NO GENOTYPE FOUND FOR: " + plotID);
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
	
	// VALIDATING WHETHER WE THIS POINT FALLS WITHING THE HASHGRID AT ALL
	public boolean validatePoint(Coordinates coords) {
		double lat = coords.getLatitude();
		double lon = coords.getLongitude();
		
		if(lat <= spatialBounds[0] && lat >= spatialBounds[1] &&
				lon <= spatialBounds[2] && lon >= spatialBounds[3])
			return true;
		
		return false;
	}
	
	
	// USED WHILE INSERTING DATA
	public int locatePoint(Coordinates coords) throws BitmapException {
		
		if(!validatePoint(coords)) {
			logger.info("RIKI: THE POINT LIES OUTSIDE THE GRID BOUNDS");
			return -1;
		}
		
		String geohash12 = GeoHash.encode(coords,  precision+1);
		
		String geohash11 = geohash12.substring(0, precision);
		
		// THIS IS THE KEY FOR THIS GEOHASH IN THE GRID
		int elevenCharKey = geohashToIndex(geohash11);

		if (plotMapping.get(elevenCharKey) != null) {
			if (!elevenCharIntersections.containsKey(elevenCharKey))
				return this.plotMapping.get(elevenCharKey);
			else {
				long twelveLong = GeoHash.hashToLong(geohash12);
				//long twelveLong = geohashToIndex(geohash12);
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
	
	// USED WHILE QUERYING/LISTING BLOCKS
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
					//long twelveLong = geohashToIndex(geohash+c);
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
	
	/*public HashGrid(HashGrid grid){
		this.baseRange = grid.baseRange;
		this.baseHash = grid.baseHash;
		this.width = grid.width;
		this.height = grid.height;
		this.xDegreesPerPixel = grid.xDegreesPerPixel;
		this.yDegreesPerPixel = grid.yDegreesPerPixel;
	}*/
	
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
	

	// poly: polygon bounds of the current plot
	// CALLED DURING LOADING OF SHAPEFILE
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
		if (elevenCharKey < 0) {
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
	
	
	// CONVERTING AN INDEX ID TO CORRESPONDING GEOHASH
	/*public String indexToGroupHash(int idx) throws HashGridException {
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
	}*/
	
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
		if (index < 0) {
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
	/*public int geohashToIndex(String hash) throws BitmapException {
		if (!matchesBase(hash))
			throw new BitmapException("Point does not fall within purview of bitmap");
		String subhash = hash.substring(this.baseHash.length());
		return (int)GeoHash.hashToLong(subhash);
	}*/
	
	/**
	 * GIVEN A GEOHASH CELL FIND ITS INDEX IN THE GRID
	 * THIS INDEX IS CALCULATED AS AN OFFSET FROM THE NORTHWEST BOUNDS
	 * @author sapmitra
	 * @param hash
	 * @return
	 * @throws BitmapException
	 */
	public int geohashToIndex(String hash) throws BitmapException {
		
		int zId = getZone(hash);
		
		if (zId <= 0)
			throw new BitmapException("Point does not fall within basehash(es) of bitmap");
		
		// dir = 3 because it returns the true intercept from a NW reference point
		Point2D posnWrtOrigin = GeoHash.locateCellInGrid(getZoneBase(zId), hash, zId);
		
		// THE OFFSETS OF THIS POINT WRT THE NORTH-WEST BOUNDARY
		// THE FOLLOWING ARE ALWAYS >= 0
		// MAKE SURE THAT WE DON'T TAG THE ADJAVENT CELL DURING CONVERSION
		int offY = yNorth-posnWrtOrigin.getY();
		// ZONE 1 or 2
		if(posnWrtOrigin.getY() > 0) {
			offY = yNorth-posnWrtOrigin.getY()+1;
		}
		
		int offX = xWest+posnWrtOrigin.getX();
		// ZONE 1 or 4
		if(posnWrtOrigin.getX() < 0) {
			offX = xWest+posnWrtOrigin.getX() + 1;
		}
		
		// FOR THE ZONE, VALIDATE IF IT LIES WITHIN BOUNDS
		if(offY < 0 || offX < 0 || offY > height || offX > width)
			return -1;
		
		int index = (offY-1)*width+offX;
		
		return index;
	}
	
	// GIVEN A GRID INDEX AND THE PRECISION IT REPRESENTS, FIND THE CORRESPONDING GEOHASH
	public String indexToGeoHash(int idx, int precision) {
		
		int offY = (idx/width)+1;
		int offX = idx%width;
		
		// GETTING COORDINATES WRT ORIGIN
		int x_origin = offX - xWest;
		
		if(offX - xWest - 1 < 0)
			x_origin = offX - xWest - 1;
		
		int y_origin = yNorth - offY;
		if(yNorth - offY + 1 > 0) {
			y_origin = yNorth - offY + 1;
		}
		
		// USING THE COORDINATES, FIND THE GEOHASH
		int zone = GeoHash.locateZoneFromCell(x_origin, y_origin);
		String zoneBase = getZoneBase(zone);
		
		String gh = locateGeohashInGrid(zoneBase, x_origin, y_origin, zone, precision);
		
		return gh;
	}
	
	
	// GIVEN OFFSETS FROM THE SE CORNER, FIND THE GEOHASH
	public String locateGeohashInGrid(String baseHash, int x, int y, int zone, int precision) {
		
		String ret = baseHash;
		x = Math.abs(x);
		y = Math.abs(y);
		
		// FIND THE GEOHASH CLOSEST TO ORIGIN FOR THIS BASEHASH IN TERMS OF THE PRECISION
		// USE THE MAXWIDTH TO FIND THE ROW & COLUMN IN THE MATRIX
		int maxWidth = 1;
		int maxHeight = 1;
		
		for(int i = baseHash.length(); i < precision; i++) {
			if(i%2 != 0) {
				maxWidth *= GeoHash.oddWidth;
				maxHeight *= GeoHash.oddHeight;
			} else {
				maxWidth *= GeoHash.evenWidth;
				maxHeight *= GeoHash.evenHeight;
			}
		}
		
		
		for(int i = baseHash.length()+1; i <= precision; i++) {
			// FINDING HOW MANY i SIZED GEOHASHES LIE BEFORE THIS OFFSET
			
			if(i%2 == 0) {
				
				// LOOKING FOR NUMBER OF EVEN SIZED GEOHASH BLOCKS
				maxWidth/=GeoHash.oddWidth;
				maxHeight/=GeoHash.oddHeight;
				
				int xblocks = x;
				int yblocks = y;
				
				if(i!=precision) {
					
					xblocks = (int)Math.ceil((double)x / (double)maxWidth);
					//x = x % maxWidth;
					x = x - (maxWidth*(xblocks-1));
					
					
					yblocks = (int)Math.ceil((double)y / (double)maxHeight);
					//y = y % maxHeight;
					y = y - (maxHeight*(yblocks-1));
				}
				
				
				if(zone == 1 || zone == 4) {
					xblocks = GeoHash.oddWidth - xblocks+1;
				}
				if(zone == 1 || zone == 2) {
					yblocks = GeoHash.oddHeight - yblocks+1;
				}
				
				
				// GEOHASH USING THE MATRIX
				char cx = GeoHash.oddMatrix[yblocks-1][xblocks-1];
				
				ret+=cx;
				//System.out.println(x+"---"+cx);
				
				
			} else {
				// LOOKING FOR NUMBER OF ODD SIZED GEOHASH BLOCKS
				maxWidth/=GeoHash.evenWidth;
				maxHeight/=GeoHash.evenHeight;
				
				int xblocks = x;
				int yblocks = y;
				
				if(i!= precision) {
					xblocks = (int)Math.ceil((double)x / (double)maxWidth);
					//x = x % maxWidth;
					x = x - (maxWidth*(xblocks-1));
					
					
					yblocks = (int)Math.ceil((double)y / (double)maxHeight);
					//y = y % maxHeight;
					y = y - (maxHeight*(yblocks-1));
				}
				
				
				if(zone == 1 || zone == 4) {
					xblocks = GeoHash.evenWidth - xblocks+1;
				}
				if(zone == 1 || zone == 2) {
					yblocks = GeoHash.evenHeight - yblocks+1;
				}
				
				
				// GEOHASH USING THE MATRIX
				char cx = GeoHash.evenMatrix[yblocks-1][xblocks-1];
				
				ret+=cx;
				//System.out.println(x+"---"+cx);
			}
		}
		return ret;
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
	
	
	/**
	 * Converts a coordinate pair (defined with latitude, longitude in decimal
	 * degrees) to an x, y location in the grid.
	 *
	 * @param coords
	 *            the Coordinates to convert.
	 *
	 * @return Corresponding x, y location in the grid.
	 */
	/*public Point<Integer> coordinatesToXY(Coordinates coords) {
		String geohash = GeoHash.encode(coords, this.precision);
		int index = (int)(GeoHash.hashToLong(geohash));
		
		return indexToXY(index);
	}*/
	
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
	/*public int[] query(GeoavailabilityQuery query) throws BitmapException {
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
	}*/
	
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

	/*public String getBaseHash() {
		return this.baseHash;
	}*/

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
	
	public static void main(String arg[]) {
		
		String s = "9,,,,";
		String[] tokens = s.split(",");
		System.out.println();
	}
	public static void main1(String arg[]) throws BitmapException {
		
		int precision = 7;
		HashGrid h = new HashGrid("94,96,93,91", precision, "94bpbpb", "96zzzzz", "93pbpbp", "9100000");
		//HashGrid h = new HashGrid("96,", precision, "96bpbpb", "96zzzzz", "96pbpbp", "9600000");
		//HashGrid h = new HashGrid("9", 3, "97b", "9gz", "9bp", "920");
		
		int k = h.geohashToIndex("94bmmm4");
		System.out.println("ID:"+k);
		
		String indexToGeoHash = h.indexToGeoHash(k, precision);
		
		System.out.println("HASH:"+indexToGeoHash);
	}
	
	public String getZonesString() {
		return zone1+","+zone2+","+zone3+","+zone4;
	}
	
	
}
