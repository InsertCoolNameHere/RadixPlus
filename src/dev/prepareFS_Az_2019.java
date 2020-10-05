package dev;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import galileo.comm.Connector;
import galileo.comm.FilesystemAction;
import galileo.comm.FilesystemRequest;
import galileo.comm.TemporalType;
import galileo.dataset.SpatialHint;
import galileo.dataset.feature.FeatureType;
import galileo.fs.FilesystemConfig;
import galileo.fs.GeospatialFileSystem;
import galileo.net.NetworkDestination;
import galileo.util.Pair;

/**
 * SAMPLE CODE FOR CREATING A NEW DISTRIBUTED FILESYSTEM*/

public class prepareFS_Az_2019 {
	
	public static void main(String [] args) throws IOException, InterruptedException{
		Connector connector = new Connector();
		
		List<Pair<String, FeatureType>> featureList = new ArrayList<>();
				
		// FEATURELIST IS NOT USED .... KEEP AS IS
		
		featureList.add(new Pair<>(GeospatialFileSystem.TEMPORAL_YEAR_FEATURE, FeatureType.INT));
		featureList.add(new Pair<>(GeospatialFileSystem.TEMPORAL_MONTH_FEATURE, FeatureType.INT));
		featureList.add(new Pair<>(GeospatialFileSystem.TEMPORAL_DAY_FEATURE, FeatureType.INT));
		featureList.add(new Pair<>("plotID", FeatureType.INT));
		
		featureList.add(new Pair<>("sensorType", FeatureType.STRING));
		
		
		SpatialHint spatialHint = new SpatialHint("lat", "long");

		FilesystemRequest fsRequest = new FilesystemRequest("roots-arizona-2019", FilesystemAction.CREATE, featureList, spatialHint);
		//fsRequest.setNodesPerGroup(5);
		fsRequest.setPrecision(8);
		fsRequest.setTemporalType(TemporalType.HOUR_OF_DAY);
		
		
		FilesystemConfig fsc = new FilesystemConfig();
		populateConfig(fsc);
		fsRequest.setConfigs(fsc);

		//Any Galileo storage node hostname and port number
		NetworkDestination storageNode = new NetworkDestination("lattice-6.cs.colostate.edu", 5635);
		connector.publishEvent(storageNode, fsRequest);
		Thread.sleep(2500);
		connector.close();
	}
	
	
	public static void populateConfig(FilesystemConfig fsc) {
		
		// ALL GEOHASHES FOR PARTITIONER
		// LIST OF ALL POSSIBLE GEOHASHES OF LENGTH 8 THAT ARE ALLOWED FOR THIS FILESYSTEM....USED FOR PARTITIONING
		String [] generateSmallerGeohashes = {"9tbkh5j1","9tbkh5j3","9tbkh5j4","9tbkh5j5","9tbkh5j6","9tbkh5j7","9tbkh5j9","9tbkh5jd","9tbkh5je","9tbkh5jh","9tbkh5jj","9tbkh5jk","9tbkh5jm","9tbkh5jn","9tbkh5jp","9tbkh5jq","9tbkh5jr","9tbkh5js","9tbkh5jt","9tbkh5jw","9tbkh5jx","9tbkh5m0","9tbkh5m1","9tbkh5m2","9tbkh5m3","9tbkh5m4","9tbkh5m6","9tbkh5m8","9tbkh5m9","9tbkh5md"};
		fsc.setAllGeohashes(generateSmallerGeohashes);
		
		// BASE HASH FOR GRID
		fsc.setBaseHashForGrid("9tbkh5,,,");
		// NW GEOHASH
		fsc.setNw("9tbkh5m4bpb");
		fsc.setNe("9tbkh5mdzzz");
		// SE GEOHASH
		fsc.setSe("9tbkh5j9pbp");
		
		fsc.setSw("9tbkh5j1000");
		
		// ALL ATTRIBUTES FOR VIZUALIZATION
		fsc.setAllAttributes("ndvi$sonar$irt$lidar");
		
		// GRID FILE PATH
		fsc.setGridFilePath("/s/chopin/e/proj/sustain/sapmitra/arizona/cleanData/Roots_2019/plots_arizona_2019.json");
		
		// HASHGRID PRECISION
		fsc.setHashGridPrecision(11);
		
		// SET INDICES MAP
		//THE INDICES AT WHICH ALL NECESSARY FIELDS MAY BE FOUND IN THE DATASET
		Map<String, int[]> indMap = new HashMap<String, int[]>();
		int[] irts = {2,3,0,5};
		indMap.put("irt", irts);
		int[] sonars = {2,3,0,5};
		indMap.put("sonar", sonars);
		int[] ndvis = {2,3,0,5};
		indMap.put("ndvi", ndvis);
		int[] lidars = {2,3,0,7};
		indMap.put("lidar", lidars);
		
		fsc.setIndicesMap(indMap);
		
		
	}
}
