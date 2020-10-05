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

public class prepareFS_Az {
	
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

		FilesystemRequest fsRequest = new FilesystemRequest("roots-arizona-2018", FilesystemAction.CREATE, featureList, spatialHint);
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
		String [] generateSmallerGeohashes = {"9tbkh4d4","9tbkh4d5","9tbkh4d6","9tbkh4d7","9tbkh49u","9tbkh49v","9tbkh43y","9tbkh43z","9tbkh49y","9tbkh43v","9tbkh46j","9tbkh46m","9tbkh46r","9tbkh46n","9tbkh46p","9tbkh46q","9tbkh4dh","9tbkh4dj","9tbkh4dk","9tbkh4d0","9tbkh49f","9tbkh4d1","9tbkh4dq","9tbkh49g","9tbkh4d2","9tbkh4d3","9tbkh49b","9tbkh4dm","9tbkh49c","9tbkh4dn"};
		fsc.setAllGeohashes(generateSmallerGeohashes);
		
		// BASE HASH FOR GRID
		fsc.setBaseHashForGrid("9tbkh4,,,");
		// NW GEOHASH
		fsc.setNw("9tbkh49ybpb");
		fsc.setNe("9tbkh4dqzzz");
		// SE GEOHASH
		fsc.setSe("9tbkh46mpbp");
		
		fsc.setSw("9tbkh43v000");
		
		// ALL ATTRIBUTES FOR VIZUALIZATION
		fsc.setAllAttributes("ndvi$sonar$irt$lidar");
		
		// GRID FILE PATH
		//fsc.setGridFilePath("/s/chopin/b/grad/sapmitra/Documents/arizona/cleanData/Roots_2018/F2 TRoots Planting/plots_arizona.json");
		fsc.setGridFilePath("/s/chopin/e/proj/sustain/sapmitra/arizona/cleanData/Roots_2018/F2 TRoots Planting/plots_arizona.json");
		
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
