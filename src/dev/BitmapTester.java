package dev;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.spatial4j.context.jts.JtsSpatialContext;
import org.locationtech.spatial4j.context.jts.JtsSpatialContextFactory;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.SpatialRelation;

import galileo.bmp.BitmapException;
import galileo.bmp.HashGrid;
import galileo.dataset.Coordinates;
import galileo.dataset.Point;
import galileo.util.GeoHash;
import geo.main.java.com.github.davidmoten.geo.Coverage;
import geo.main.java.com.github.davidmoten.geo.GeoHashUtils;
import java.awt.geom.Area;
public class BitmapTester {
	static int GEO_PRECISION = 11;
	public static void main(String [] args) throws IOException, BitmapException {
		long startTime = System.currentTimeMillis();
		HashGrid grid = new HashGrid("wdw0x9", GEO_PRECISION, "wdw0x9bpbpb", "wdw0x9pbpbp");//eventually need to automate the detection of corners
		ArrayList<Path2D> plotShapes = new ArrayList<>();
		//Read plot file
		String plots = new String(Files.readAllBytes(Paths.get("/s/bach/j/under/mroseliu/NSF_Time_Series/Raptor/galileo/config/grid/plots.json")));
		JSONObject plotJson = new JSONObject(plots);
		ArrayList<Coordinates> testCoords = new ArrayList<>();
		JSONArray geometries = (JSONArray)plotJson.get("features");
		int added = 0;
		//Create list of polygon objects to add to bitmap
		ArrayList<String> allPoints = new ArrayList<>();
		ArrayList<Integer> badPlots = new ArrayList<>();
		int numPolys = 0;

		for (Object o : geometries){
			Path2D poly = new Path2D.Double();
			
			//coords = all coordinates belonging to current object in iteration, length=1
			JSONArray coords = ((JSONArray)((JSONObject)((JSONObject)o).get("geometry")).get("coordinates"));
			JSONArray firstCoord = (JSONArray)((JSONArray)coords.get(0)).get(0);
			poly.moveTo(firstCoord.getDouble(0), firstCoord.getDouble(1));
			Object plotID = ((JSONObject)(((JSONObject)o).get("properties"))).get("ID_Plot");
			String genotype = ((JSONObject)(((JSONObject)o).get("properties"))).get("Genotype").toString();
			if (genotype.equals("Water")) {
				badPlots.add((int)plotID);
				continue;
			}
			
			ArrayList<Coordinates> polyPoints = new ArrayList<>();
			polyPoints.add(new Coordinates((double)firstCoord.get(1), (double)firstCoord.get(0)));
			for (int i = 1; i < ((JSONArray)coords.get(0)).length(); i++){
				double lat = ((JSONArray)((JSONArray)coords.get(0)).get(i)).getDouble(0);
				double lon = ((JSONArray)((JSONArray)coords.get(0)).get(i)).getDouble(1);
				String hash = GeoHash.encode(new Coordinates(lat, lon), GEO_PRECISION);
				polyPoints.add(new Coordinates(lat, lon));
				poly.lineTo(lat, lon);
				if (!allPoints.contains(hash)) {
					allPoints.add(hash);
				}

			}

			poly.closePath();
			numPolys++;

//			String [] coverageHashes = GeoHash.getIntersectingGeohashes(poly, GEO_PRECISION);
////			String [] coverageHashes = GeoHash.getIntersectingGeohashes(polyPoints, GEO_PRECISION);
//			for (String ghash : coverageHashes) {
//				if (!plotID.equals(null))
//					grid.addPoint(ghash, (int)plotID, poly);
//			}
//			
			double[][] coordArr = listToArr(polyPoints);
			Set<String> coverage = GeoHashUtils.geoHashesForPolygon(GEO_PRECISION, coordArr);

			for (String ghash : coverage) {

				double [] hashBox = GeoHashUtils.decode_bbox(ghash);

				Path2D hashRect = new Path2D.Double();
				hashRect.moveTo(hashBox[2], hashBox[0]);
				hashRect.lineTo(hashBox[2], hashBox[1]);
				hashRect.lineTo(hashBox[3], hashBox[1]);
				hashRect.lineTo(hashBox[3], hashBox[0]);
				hashRect.lineTo(hashBox[2], hashBox[0]);
				hashRect.closePath();
				if ((int)plotID > 4700)
					grid.addPoint(ghash, (int)plotID, poly);
				if (poly.intersects(hashRect.getBounds2D())) {
					grid.addPoint(ghash, (int)plotID, poly);
				}
//				if (poly.contains(hashBox[2], hashBox[0]) || poly.contains(hashBox[2], hashBox[1]) || poly.contains(hashBox[3], hashBox[1]) || poly.contains(hashBox[3], hashBox[0]))
//					grid.addPoint(ghash, (int)plotID, poly);

//				else if ((int) plotID == 5000)
//					System.out.println(hashBox[0] + "," + hashBox[2] + "\n" + hashBox[1] + ","+hashBox[2]+"\n"+hashBox[1]+","+hashBox[3]+"\n"+hashBox[0]+","+hashBox[3]);

			}
//			System.out.println("Plot " + plotID + " coverage dropped from " + coverage.size() + " to " + numHashesForPlot);
//			Coordinates [] corners = getCorners(polyPoints);
//			Coverage area = null;
////			Set<String> hashesToAdd = new HashSet<>();
//			try {
//				//This is covering excess area, need to change the algorithm...
//			area = geo.main.java.com.github.davidmoten.geo.GeoHash.coverBoundingBox(corners[0].getLatitude(), corners[0].getLongitude(),
//		            corners[1].getLatitude(), corners[1].getLongitude(),GEO_PRECISION);
//			}catch(IllegalArgumentException e) {
//				System.out.println("Failed for plot " + plotID);
//				if (!plotID.equals(null))
//					badPlots.add((int)plotID);
//				else
//					badPlots.add(0);
//			}
//
//			if (area != null) {
//				Set<String> hashesToAdd = area.getHashes();
//				if (!plotID.equals(null) && (int)plotID == 473) {
//					ArrayList<double[]> hashBoxes = new ArrayList<>();
//					for (String h : hashesToAdd)
//						hashBoxes.add(GeoHashUtils.decode_bbox(h));
//					
//				}
//				for (String hash : hashesToAdd) {
//					if (!plotID.equals(null)) 
//						grid.addPoint(hash, (int)plotID, poly);	
//				}
//			}
//			if(!plotID.equals(null) && (int)plotID==473) {
//				ArrayList<double[]> hashBoxes = new ArrayList<>();
//				for (String h : hashesToAdd)
//					hashBoxes.add(GeoHashUtils.decode_bbox(h));
////				for (String s : hashesToAdd)
////					hashBoxes.add(GeoHashUtils.decode_bbox(s));
//				for (double[] d : hashBoxes) {
//					System.out.println(d[0]+","+d[2]);
//					System.out.println(d[1]+","+d[3]);
//				}
//			}
			
		}
		grid.applyUpdates();
		long endInitialize = System.currentTimeMillis();
		int mb = 1024*1024;
        Runtime runtime = Runtime.getRuntime();
        System.out.println("Used Memory:"
    			+ (runtime.totalMemory() - runtime.freeMemory()) / mb);
        System.out.println("Max. Memory:" + runtime.maxMemory() / mb);
		System.out.println("Time to initialize: " + (endInitialize-startTime));
//		System.out.println("Geohashes with double boundaries: " + grid.intersections);
//		System.out.println(grid.stupidPlots + " out of " + grid.intersections + " were side-by-side plots = " + (grid.stupidPlots/grid.intersections));
		testCoords.add(new Coordinates(14.160260765910222, 121.26893153047192));
		testCoords.add(new Coordinates(14.160260058924646, 121.26893078366905));
		testCoords.add(new Coordinates(14.160260127826573, 121.26893023291682));
		testCoords.add(new Coordinates(14.160259298711816, 121.26894091669799));
		int numZeros = 0, numOnes = 0;

		HashGrid testGrid = new HashGrid("wdw0x9", GEO_PRECISION, "wdw0x9bpbpb", "wdw0x9pbpbp");
//		for (Coordinates c : testCoords)
//			testGrid.addPoint(c);
//		testGrid.applyUpdates();
//		System.out.println(Arrays.toString(grid.query(testGrid.bmp)));
//		System.out.println("allPoints size="+allPoints.size());
//		System.out.println("geometries length="+geometries.length()+", bmp size="+grid.bmp.toArray().length + ", total num duplicates="+numDupes + ", total num points="+numPoints);
		Point<Integer> point =  grid.coordinatesToXY(new Coordinates(14.1611889f, 121.2689369f));
		//Try to get some plot_ids from randomly generated points...
		String csvFile = "/tmp/dummy.csv";
        String line = "";
        String cvsSplitBy = ",";
        int correct_count = 0, wrong = 0, linesRead = 0;
        startTime = System.currentTimeMillis();
        int nulls = 0;
        for (Coordinates c : testCoords) {
        	grid.locatePoint(c);
        }
        System.out.println("Took " + ((double)System.currentTimeMillis()-(double)startTime)/1000.0 + " seconds to identify plots for " + testCoords.size() + " points");
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
        	HashGrid queryGrid = new HashGrid("wdw0x9", GEO_PRECISION, "wdw0x9bpbpb", "wdw0x9pbpbp");
            while ((line = br.readLine()) != null) {

                // use comma as separator
                String[] current_line = line.split(cvsSplitBy);
                if (current_line.length < 5)
                	continue;
                //indices: [0]=time, [1]=long, [2]=lat, [3]=plotID, [4]=temperature, [5]=humidity, [6]=CO2
                double lat = Double.parseDouble(current_line[1]);
                double lon = Double.parseDouble(current_line[2]);
                
                int plotId = Integer.parseInt(current_line[3]);
                queryGrid.addPoint(new Coordinates(lat, lon));
//                if (!badPlots.contains(plotId)) {
	                Coordinates curr_point = new Coordinates(lat, lon);
	                int gridID = grid.locatePoint(curr_point);

	               
	                if (gridID == plotId)
	                	correct_count++;
	                else {
	                	wrong++;

	                }
	                linesRead++;
//	            }
            }
//            System.out.println("queryGrid: " + queryGrid.getBitmap());
//            System.out.println("intersections: " + Arrays.toString(grid.query(queryGrid.getBitmap())));
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("plotMapping size: " + grid.plotMapping.size());
        System.out.println("plotShapeToPlotID size: " + grid.plotShapeToPlotID.size() + ", elevenCharIntersections size: " + grid.elevenCharIntersections.size());
        System.out.println("Time to identify plots: " + (System.currentTimeMillis()-startTime));
		System.out.println("Correctly identified " + correct_count + ", wrong: " + wrong+", read " + linesRead+ " lines"+ ", bad plots: " + badPlots.size());
	}
	public static double distance(double lat1, double lat2, double lon1,
	        double lon2, double el1, double el2) {

	    final int R = 6371; // Radius of the earth

	    double latDistance = Math.toRadians(lat2 - lat1);
	    double lonDistance = Math.toRadians(lon2 - lon1);
	    double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
	            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
	            * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
	    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	    double distance = R * c * 1000; // convert to meters

	    double height = el1 - el2;

	    distance = Math.pow(distance, 2) + Math.pow(height, 2);

	    return Math.sqrt(distance);
	}
	
	//Working for most cases...
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
//		if (NW_most.getLongitude() > SE_most.getLongitude()) {
//			//in this case, its a weird shaped plot so consider different points
//			//a dirty hack to shift points apart just enough to create a difference...
//			double nw = NW_most.getLongitude();
//			double se = SE_most.getLongitude();
//			while(nw > se) {
//				nw -= .00000000000001;
//				se += .00000000000001;
//			}
//			NW_most.setLongitude(nw);
//			SE_most.setLongitude(se);
//		}
		
		return new Coordinates[] {NW_most, SE_most};
	}
	public static double[][] listToArr(List<Coordinates> coords){
		double [][] coordArr = new double[coords.size()][2];
		for (int i = 0; i < coords.size(); i++) {
			coordArr[i] = new double[] {coords.get(i).getLongitude(), coords.get(i).getLatitude()};
		}
		return coordArr;
	}
}
