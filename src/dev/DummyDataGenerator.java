package dev;
import java.awt.geom.Path2D;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;

import org.json.JSONArray;
import org.json.JSONObject;

import galileo.dataset.Coordinates;
import galileo.util.GeoHash;

/**
 * THIS CLASS SHALL BE DELETED UPON RECEIPT OF ACTUAL DATA. 
 * THIS CLASS SHOULD NOT BE INCLUDED IN THE FINAL VERSION OF THIS SOFTWARE*/
public class DummyDataGenerator { 
	public static void main(String [] args) throws IOException{
		File dummyData = new File("/tmp/dummy.csv");
//		File dummyData = new File("/s/bach/j/under/mroseliu/dummy.csv");
		if (dummyData.exists())
			dummyData.delete();
		dummyData.createNewFile();
//		if (!dummyData.exists())
//			dummyData.createNewFile();
		File tmpDir = new File("/tmp");
		long freeSpaceBytes = tmpDir.getFreeSpace();
		//assume 300 bytes per line (worst case)
//		long numLinesPerPlot = freeSpaceBytes/300/5693;
		int numIngestors = Integer.parseInt(args[0]);
		long numLinesPerPlot = 100;//(long)(4294967296.0 * 5) / 2 / 250 / numIngestors / 5693;
		FileWriter writer = new FileWriter(dummyData, true);
		HashMap<Path2D, Integer> plotIDMap = new HashMap<>();
		//Generate some fake data!!
		String plots = new String(Files.readAllBytes(Paths.get("/s/parsons/l/sys/www/radix/columbus-master/static/js/plots.json")));
		JSONObject plotJson = new JSONObject(plots);
		JSONArray geometries = (JSONArray)plotJson.get("features");
//		JSONArray coords = (JSONArray)((JSONObject)((JSONObject)geometries.get(0)).get("geometry")).get("coordinates");
//		System.out.println(((JSONArray)coords.get(0)).get(0));
		SortedSet<Integer> possiblePlotNums = new TreeSet<>();
		for (int i = 1; i < 5693; i++)
			possiblePlotNums.add(i);
		for (Object o : geometries) {
			Object pID = ((JSONObject)(((JSONObject)o).get("properties"))).get("ID_Plot");
			if (!pID.equals(null)) {
				possiblePlotNums.remove((int)pID);
			}
		}
		String hostname = InetAddress.getLocalHost().getHostName();
		Date time = new Date(System.currentTimeMillis()-(3600000*48));//3,600,000 ms = 1 hr
		SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd kk:mm:ss.SSS z yyyy");
//		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		HashSet<String> geos = new HashSet<>();
		long bytesWritten = 0;
		for (Object o : geometries){
			String geno = ((JSONObject)(((JSONObject)o).get("properties"))).get("Genotype").toString();
			int rep = (int)((JSONObject)(((JSONObject)o).get("properties"))).get("Rep");
			if (!geno.equals("Water")) {
				//Object o = a JSONArray
				Path2D poly = new Path2D.Double();
				//coords = all coordinates belonging to current object in iteration, length=1
				JSONArray coords = ((JSONArray)((JSONObject)((JSONObject)o).get("geometry")).get("coordinates"));
				JSONArray firstCoord = (JSONArray)((JSONArray)coords.get(0)).get(0);
				
				poly.moveTo(firstCoord.getDouble(0), firstCoord.getDouble(1));
				for (int i = 1; i < ((JSONArray)coords.get(0)).length(); i++){
					poly.lineTo(((JSONArray)((JSONArray)coords.get(0)).get(i)).getDouble(0), ((JSONArray)((JSONArray)coords.get(0)).get(i)).getDouble(1));
					geos.add(GeoHash.encode(new Coordinates(((JSONArray)((JSONArray)coords.get(0)).get(i)).getDouble(0), ((JSONArray)((JSONArray)coords.get(0)).get(i)).getDouble(1)), 8));
				}
	//			int plotID = ((JSONObject)(((JSONObject)o).get("properties"))).getInt("ID_Plot");
				Object pID = ((JSONObject)(((JSONObject)o).get("properties"))).get("ID_Plot");
				if (!pID.equals(null))
					plotIDMap.put(poly, (int)pID);
				else {
					int nextNumber = possiblePlotNums.first();
					plotIDMap.put(poly, nextNumber);
					possiblePlotNums.remove(nextNumber);
				}
				poly.closePath();
//				plotShapes.add(poly);
				int index = 0;
				
				double randTemp = ThreadLocalRandom.current().nextDouble(80, 85);
				double randHumid = ThreadLocalRandom.current().nextDouble(.55, .75);
				double randCO2 = ThreadLocalRandom.current().nextDouble(380, 420);
				double rand1 = ThreadLocalRandom.current().nextDouble(25, 35);
				double rand2 = ThreadLocalRandom.current().nextDouble(110, 130);
				double rand3 = ThreadLocalRandom.current().nextDouble(25, 35);
				double rand4 = ThreadLocalRandom.current().nextDouble(0, 2);
				double rand5 = ThreadLocalRandom.current().nextDouble(0, 2);
				double rand6 = ThreadLocalRandom.current().nextDouble(140, 200);
//				String randGeno = "Genotype-";
				
//				for (int i = 0; i < 8; i++)
//					randGeno+= alpha.charAt(ThreadLocalRandom.current().nextInt(alpha.length()));
				//Generate x points per plot: 100=small, 1000=medium, 10000=big
				for (long i = 0; i < numLinesPerPlot; i++){//start super small to test database
					double [] randCoord = randomPoint(poly);
					String record = formatter.format(time) + ",";
					record += randCoord[0] + "," + randCoord[1] + ",";
					record += pID + ","; //index=plotID (for testing purposes)
					record += (randTemp + ThreadLocalRandom.current().nextDouble(-2, 2)) + ","; //add a random temperature from 80-85 degrees
					record += (randHumid + ThreadLocalRandom.current().nextDouble(-.02, .02)) + ","; //add a random humidity from 55%-75%
					record += (randCO2 + ThreadLocalRandom.current().nextDouble(-5, 5)) +","; //add a random CO2 from 380ppm-420ppm (based on global avg. CO2)
					
					record += geno.replaceAll(",", "-") + ",";//simulated genotype info
					record += (rand1 + ThreadLocalRandom.current().nextDouble(-3, 3)) + ",";
					record += rand2 + ThreadLocalRandom.current().nextDouble(-5, 5) + ",";
					record += rand3 + ThreadLocalRandom.current().nextDouble(-3, 3) + ",";
					record += rand4 + ThreadLocalRandom.current().nextDouble(-.4, .4) + ",";
					record += rand5 + ThreadLocalRandom.current().nextDouble(-.4, .4) + ",";
					record += rand6 + ThreadLocalRandom.current().nextDouble(-5, 5) + ",";
					record += rep + ",";
					if (ThreadLocalRandom.current().nextInt(101) % 20 == 0)//5% chance of false
						record += "false";
					else
						record += "true";
					time.setTime(time.getTime()+5);//add 5ms to time to simulate 200 reading per second
					writer.append(record+"\n");
					bytesWritten += record.getBytes().length +1;//add 1 to account for extra new line
					
				}
				index ++;
				
			}
			
		}
		writer.close();
		File logFile = new File("/s/bach/j/under/mroseliu/dataGen/populate-data.log");
		FileWriter logWriter = new FileWriter(logFile, true);
		logWriter.append(hostname + ":" + (double)bytesWritten/1024/1024/1024+"\n");
		logWriter.close();
		//Now have full list of all plot polygons, generate random data to fall within each polygon
		//File generated will be a csv file of format:
		// timestamp,lat,lon,plotID,temp,humidity,CO2
//		int index = 0;
//		for (Path2D plot : plotShapes){
//			Date time = new Date();
//			double randTemp = ThreadLocalRandom.current().nextDouble(80, 85);
//			double randHumid = ThreadLocalRandom.current().nextDouble(.55, .75);
//			double randCO2 = ThreadLocalRandom.current().nextDouble(380, 420);
//			double rand1 = ThreadLocalRandom.current().nextDouble(25, 35);
//			double rand2 = ThreadLocalRandom.current().nextDouble(110, 130);
//			double rand3 = ThreadLocalRandom.current().nextDouble(25, 35);
//			double rand4 = ThreadLocalRandom.current().nextDouble(0, 2);
//			double rand5 = ThreadLocalRandom.current().nextDouble(0, 2);
//			double rand6 = ThreadLocalRandom.current().nextDouble(140, 200);
////			String randGeno = "Genotype-";
//			
////			for (int i = 0; i < 8; i++)
////				randGeno+= alpha.charAt(ThreadLocalRandom.current().nextInt(alpha.length()));
//			//Generate x points per plot: 100=small, 1000=medium, 10000=big
//			for (int i = 0; i < 500; i++){
//				double [] randCoord = randomPoint(plot);
//				String record = time + ",";
//				record += randCoord[0] + "," + randCoord[1] + ",";
//				record += plotIDMap.get(plot) + ","; //index=plotID (for testing purposes)
//				record += (randTemp + ThreadLocalRandom.current().nextDouble(-2, 2)) + ","; //add a random temperature from 80-85 degrees
//				record += (randHumid + ThreadLocalRandom.current().nextDouble(-.02, .02)) + ","; //add a random humidity from 55%-75%
//				record += (randCO2 + ThreadLocalRandom.current().nextDouble(-5, 5)) +","; //add a random CO2 from 380ppm-420ppm (based on global avg. CO2)
//				
//				record += randGeno + ",";//simulated genotype info
//				record += (rand1 + ThreadLocalRandom.current().nextDouble(-3, 3)) + ",";
//				record += rand2 + ThreadLocalRandom.current().nextDouble(-5, 5) + ",";
//				record += rand3 + ThreadLocalRandom.current().nextDouble(-3, 3) + ",";
//				record += rand4 + ThreadLocalRandom.current().nextDouble(-.4, .4) + ",";
//				record += rand5 + ThreadLocalRandom.current().nextDouble(-.4, .4) + ",";
//				record += rand6 + ThreadLocalRandom.current().nextDouble(-5, 5) + ",";
//				if (ThreadLocalRandom.current().nextInt(101) % 20 == 0)//5% chance of false
//					record += "false";
//				else
//					record += "true";
//				time.setTime(time.getTime()+500);//add 500ms to time to simulate 2 reading per second
//				writer.append(record+"\n");
//				
//			}
//			index ++;
////			if (index > 1000)
////				break;
//			
////			break;
//		}
//		writer.close();
		
	}
	public static double[] randomPoint(Path2D poly){
//		System.out.println("minX: " + poly.getBounds2D().getMinX() + " maxX: " + poly.getBounds2D().getMaxX() + " minY: "+poly.getBounds2D().getMinY()+ " maxY: " + poly.getBounds2D().getMaxY());
		double minX = poly.getBounds2D().getMinX();
		double maxX = poly.getBounds2D().getMaxX();
		double minY = poly.getBounds2D().getMinY();
		double maxY = poly.getBounds2D().getMaxY();
		
//		minX += (maxX-minX)*.5;
//		maxX -= (maxX-minX)*.5;
//		minY += (maxY-minY)*.5;
//		maxY -= (maxY-minY)*.5;
		double xCoord = ThreadLocalRandom.current().nextDouble(minX, maxX);
		double yCoord = ThreadLocalRandom.current().nextDouble(minY, maxY);
		while (!poly.contains(xCoord, yCoord)){
			xCoord = ThreadLocalRandom.current().nextDouble(poly.getBounds2D().getMinX(), poly.getBounds2D().getMaxX());
			yCoord = ThreadLocalRandom.current().nextDouble(poly.getBounds2D().getMinY(), poly.getBounds2D().getMaxY());
		}
		return new double[]{xCoord, yCoord};
	}
}
