package galileo.bmp;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONObject;

import galileo.dataset.Coordinates;
import galileo.util.GeoHash;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

public class HashGridTest {
	
	public static double northOffset = 2.74;
	public static double eastOffset = 1.524;
	// 60 INCHES
	public static double offset = 0.762*2d;
	
	// PLANTER STARTED FROM E to W in 2018 (opposite of 2019).
	public static int startBorderNum = 49;
	
	// Border Numbers increase from E to W.	
	public static int borderIncrement = 1;
	
	public static int startLatIndx = 35;
	public static int startLonIndx = 36;
	public static int endLatIndx = 40;
	public static int endLonIndx = 41;
	
	
	private static String baseHash="START";
	
	public static List<Integer> rangesIgnore = new ArrayList<>(Arrays.asList(1,2,51,52)) ;
	public static List<Integer> rowIgnore = new ArrayList<>(Arrays.asList(1,8)) ;
	
	public static Map<Integer, Integer> borderNumMap = new HashMap<Integer, Integer>();
	
	public static Map<Integer, Boolean> borderNumPrintMap = new HashMap<Integer, Boolean>();
	
	public static int precision = 11;
	public static HashGrid h = new HashGrid("9tbkh4,,,", precision, "9tbkh4bpbpb", "9tbkh4zzzzz", "9tbkh4pbpbp", "9tbkh400000");
	
	public static void main(String arg[]) throws BitmapException, IOException {
		//HashGrid h = new HashGrid("96,", precision, "96bpbpb", "96zzzzz", "96pbpbp", "9600000");
		//HashGrid h = new HashGrid("9", 3, "97b", "9gz", "9bp", "920");
		
		interpretPlanterData("/s/chopin/b/grad/sapmitra/Documents/arizona/cleanData/Roots_2018/F2 TRoots Planting");
	}
	
	
	public static List<Coordinates> getBounds(double startLat, double startLon, double endLat, double endLon, boolean isOdd) {
		
		Coordinates sCoord = new Coordinates(startLat, startLon);
		Coordinates eCoord = new Coordinates(endLat, endLon);
		
		if(northOffset > 0) {
			GeodesicData newStart = Geodesic.WGS84.Direct(sCoord.getLatitude(), sCoord.getLongitude(), Utility.getAziAngleFromDirection("north"), northOffset);
			GeodesicData newEnd = Geodesic.WGS84.Direct(eCoord.getLatitude(), eCoord.getLongitude(), Utility.getAziAngleFromDirection("north"), northOffset);
			
			
			sCoord = new Coordinates(newStart.lat2, newStart.lon2);
			eCoord = new Coordinates(newEnd.lat2, newEnd.lon2);
		}
		
		if(eastOffset > 0) {
			
			GeodesicData newStart = Geodesic.WGS84.Direct(sCoord.getLatitude(), sCoord.getLongitude(), Utility.getAziAngleFromDirection("east"), eastOffset);
			GeodesicData newEnd = Geodesic.WGS84.Direct(eCoord.getLatitude(), eCoord.getLongitude(), Utility.getAziAngleFromDirection("east"), eastOffset);
			
			
			sCoord = new Coordinates(newStart.lat2, newStart.lon2);
			eCoord = new Coordinates(newEnd.lat2, newEnd.lon2);
			
		}
		
		startLat = sCoord.getLatitude();
		startLon = sCoord.getLongitude();
		endLat = eCoord.getLatitude();
		endLon = eCoord.getLongitude();
		
		List<Coordinates> bounds = new ArrayList<Coordinates>();
		
		// row 1,2 have the same start/end lon.
		// TO get bounds go east for odd and west for even
		if(isOdd) {
			// GO EAST
			
			// NORTH-EAST
			double latne = startLat;
			double lonne = startLon;
			double azine = 90;
			GeodesicData g_ne = Geodesic.WGS84.Direct(latne, lonne, azine, offset);

			Coordinates ne = new Coordinates(g_ne.lat2, g_ne.lon2);
			
			// SOUTH-EAST
			double latse = endLat;
			double lonse = endLon;
			double azise = 90;
			GeodesicData g_se = Geodesic.WGS84.Direct(latse, lonse, azise, offset);

			Coordinates se = new Coordinates(g_se.lat2, g_se.lon2);
			
			
			bounds.add(sCoord);bounds.add(ne);bounds.add(se);bounds.add(eCoord);bounds.add(sCoord);
		} else {
			// GO WEST
			
			// NORTH-WEST
			double latnw = startLat; 
			double lonnw = startLon;
	        double azinw = 270;
	        GeodesicData g_nw = Geodesic.WGS84.Direct(latnw, lonnw, azinw, offset);
			
	        Coordinates nw = new Coordinates(g_nw.lat2 , g_nw.lon2);
	        
	        
	        
	        // SOUTH-WEST
			double latsw = endLat;
			double lonsw = endLon;
			double azisw = 270;
			GeodesicData g_sw = Geodesic.WGS84.Direct(latsw, lonsw, azisw, offset);

			Coordinates sw = new Coordinates(g_sw.lat2, g_sw.lon2);
			
			bounds.add(sCoord);bounds.add(nw);bounds.add(sw);bounds.add(eCoord);bounds.add(sCoord);
		}
		
        return bounds;
		
	}
	
	
	public static void interpretPlanterData(String dirName) throws IOException, BitmapException {
		
		JSONObject plotJson = new JSONObject();
		
		JSONArray plot_Infos = new JSONArray();
		
		plotJson.put("type", "FeatureCollection");
		plotJson.put("features", plot_Infos);
		
		Map<String, List<Coordinates>> plotToBoundsMap = new HashMap<String, List<Coordinates>>();
		
		// ALL LINES IN THE PLANTER FILE
		List<String> allLines = readCsv(dirName);
		
		System.out.println(allLines.size());
		
		int borderNum = startBorderNum;
		
		for(String line: allLines) {
			
			String[] tokens = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
			
			String rangeNum = tokens[2];
			String rowNum = tokens[3];
			
			int row = Integer.valueOf(rowNum);
			
			boolean isOdd = true;
			
			if(row%2 == 0)
				isOdd = false;
			
			//34,35
			String startLat = tokens[startLatIndx];
			String startLon = tokens[startLonIndx];
			
			//39,40
			String endLat = tokens[endLatIndx];
			String endLon = tokens[endLonIndx];
			
			List<Coordinates> bounds = getBounds(Double.valueOf(startLat), Double.valueOf(startLon), Double.valueOf(endLat), Double.valueOf(endLon), isOdd);
			String plotIdString = borderNum + "-" + rangeNum + "-" + rowNum;
			
			for(Coordinates b : bounds) {
				String g = GeoHash.encode(b, 11);
				int x = h.geohashToIndex(g);
				String g1 = h.indexToGeoHash(x, 11);
				
				if(g1.equals(g)) {
					System.out.println("PASS");
				} else {
					System.out.println("FOR "+plotIdString+" GS: "+g+" "+g1);
				}
			}
			
		}
		
		//System.out.println(groupedBorders.keySet().size()+">>"+groupedBorders.keySet());
		System.out.println("TOTAL BORDERS: "+baseHash);
	}
	
	
	
	public static List<String> readCsv(String dirName) throws IOException {
		
		File file = new File(dirName);
		
		FileFilter filter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
            	
            	return pathname.getAbsolutePath().endsWith("p.csv");
            	
            }
         };
		
		List<String> allLines = new ArrayList<String>();
		
		if (file.isDirectory()) {
			File[] files = file.listFiles(filter);
			
			FileInputStream inputStream = null;
			Scanner sc = null;
			
			for(File f : files) {
				
				String filepath = f.getAbsolutePath();
				
				if(!filepath.endsWith("p.csv")) {
					continue;
				}
				
				System.out.println("READING: "+filepath);
				
				inputStream = new FileInputStream(filepath);
				sc = new Scanner(inputStream);
				
				while (sc.hasNextLine()) {
					
					String line = sc.nextLine();
					
					/*
					 * if(count >= 2) continue;
					 */
					
					if(line.contains("Date") || line.trim().isEmpty()) {
						continue;
					}
					allLines.add(line);
					
					
				}
				inputStream.close();
				
				
			}
			
			
		}
		
		return allLines;
	}

}
