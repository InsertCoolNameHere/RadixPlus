package dev;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.spatial4j.io.GeohashUtils;

import galileo.comm.FilesystemAction;
import galileo.comm.FilesystemRequest;
import galileo.comm.TemporalType;
import galileo.dataset.SpatialHint;
import galileo.dataset.feature.Feature;
import galileo.dataset.feature.FeatureType;
import galileo.fs.FilesystemConfig;
import galileo.fs.GeospatialFileSystem;
import galileo.net.NetworkDestination;
import galileo.util.GeoHash;
import galileo.util.Pair;
import galileo.bmp.HashGrid;
import galileo.comm.Connector;

/**
 * THIS CLASS IS FOR DEVELOPMENT ONLY, AND SHOULD BE REMOVED IN THE FINAL DISTRIBUTION*/
public class prepareFS_Az {
	
	public static void main(String [] args) throws IOException, InterruptedException{
		Connector connector = new Connector();
		
		List<Pair<String, FeatureType>> featureList = new ArrayList<>();
				
		// FEATURELIST IS PROBABLY NOT USED AT ALL
		
		featureList.add(new Pair<>("plotID", FeatureType.INT));
		
		featureList.add(new Pair<>(GeospatialFileSystem.TEMPORAL_YEAR_FEATURE, FeatureType.INT));
		featureList.add(new Pair<>(GeospatialFileSystem.TEMPORAL_MONTH_FEATURE, FeatureType.INT));
		featureList.add(new Pair<>(GeospatialFileSystem.TEMPORAL_DAY_FEATURE, FeatureType.INT));
		
		featureList.add(new Pair<>("sensorType", FeatureType.STRING));
		
		
		SpatialHint spatialHint = new SpatialHint("lat", "long");

		FilesystemRequest fsRequest = new FilesystemRequest("roots-arizona", FilesystemAction.CREATE, featureList, spatialHint);
		fsRequest.setNodesPerGroup(5);
		fsRequest.setPrecision(8);
		fsRequest.setTemporalType(TemporalType.HOUR_OF_DAY);
		
		
		FilesystemConfig fsc = new FilesystemConfig();
		populateConfig(fsc);
		fsRequest.setConfigs(fsc);

		//Any Galileo storage node hostname and port number
		NetworkDestination storageNode = new NetworkDestination("lattice-1.cs.colostate.edu", 5635);
		connector.publishEvent(storageNode, fsRequest);
		Thread.sleep(2500);
		connector.close();
	}
	
	
	public static void populateConfig(FilesystemConfig fsc) {
		
		// ALL GEOHASHES FOR PARTITIONER
		// FindUniqueGeohashesForPartitioning
		//String[] generateSmallerGeohashes = {"9tbkh4dr","9tbkh4dq","9tbkh4dm","9tbkh4dk","9tbkh4d7","9tbkh4d6","9tbkh4d3","9tbkh4d2","9tbkh46r","9tbkh46q","9tbkh46m","9tbkh4dp","9tbkh4dn","9tbkh4dj","9tbkh4dh","9tbkh4d5","9tbkh4d4","9tbkh4d1","9tbkh4d0","9tbkh46p","9tbkh46n","9tbkh46j","9tbkh49z","9tbkh49y","9tbkh49v","9tbkh49u","9tbkh49g","9tbkh49f","9tbkh49c","9tbkh49b","9tbkh43z","9tbkh43y","9tbkh43v"};
		String [] generateSmallerGeohashes = {"9tbkh4c0","9tbkh4c1","9tbkh4c2","9tbkh4c3","9tbkh4c4","9tbkh4c5","9tbkh4c6","9tbkh4c7","9tbkh4c8","9tbkh4c9","9tbkh4cb","9tbkh4cc","9tbkh4cd","9tbkh4ce","9tbkh4cf","9tbkh4cg","9tbkh4ch","9tbkh4cj","9tbkh4ck","9tbkh4cm","9tbkh4cn","9tbkh4cp","9tbkh4cq","9tbkh4cr","9tbkh4cs","9tbkh4ct","9tbkh4cu","9tbkh4cv","9tbkh4cw","9tbkh4cx","9tbkh4cy","9tbkh4cz","9tbkh4f0","9tbkh4f1","9tbkh4f2","9tbkh4f3","9tbkh4f4","9tbkh4f5","9tbkh4f6","9tbkh4f7","9tbkh4f8","9tbkh4f9","9tbkh4fb","9tbkh4fc","9tbkh4fd","9tbkh4fe","9tbkh4ff","9tbkh4fg","9tbkh4fh","9tbkh4fj","9tbkh4fk","9tbkh4fm","9tbkh4fn","9tbkh4fp","9tbkh4fq","9tbkh4fr","9tbkh4fs","9tbkh4ft","9tbkh4fu","9tbkh4fv","9tbkh4fw","9tbkh4fx","9tbkh4fy","9tbkh4fz","9tbkh4g0","9tbkh4g1","9tbkh4g2","9tbkh4g3","9tbkh4g4","9tbkh4g5","9tbkh4g6","9tbkh4g7","9tbkh4g8","9tbkh4g9","9tbkh4gb","9tbkh4gc","9tbkh4gd","9tbkh4ge","9tbkh4gf","9tbkh4gg","9tbkh4gh","9tbkh4gj","9tbkh4gk","9tbkh4gm","9tbkh4gn","9tbkh4gp","9tbkh4gq","9tbkh4gr","9tbkh4gs","9tbkh4gt","9tbkh4gu","9tbkh4gv","9tbkh4gw","9tbkh4gx","9tbkh4gy","9tbkh4gz","9tbkh490","9tbkh491","9tbkh492","9tbkh493","9tbkh494","9tbkh495","9tbkh496","9tbkh497","9tbkh498","9tbkh499","9tbkh49b","9tbkh49c","9tbkh49d","9tbkh49e","9tbkh49f","9tbkh49g","9tbkh49h","9tbkh49j","9tbkh49k","9tbkh49m","9tbkh49n","9tbkh49p","9tbkh49q","9tbkh49r","9tbkh49s","9tbkh49t","9tbkh49u","9tbkh49v","9tbkh49w","9tbkh49x","9tbkh49y","9tbkh49z","9tbkh4d0","9tbkh4d1","9tbkh4d2","9tbkh4d3","9tbkh4d4","9tbkh4d5","9tbkh4d6","9tbkh4d7","9tbkh4d8","9tbkh4d9","9tbkh4db","9tbkh4dc","9tbkh4dd","9tbkh4de","9tbkh4df","9tbkh4dg","9tbkh4dh","9tbkh4dj","9tbkh4dk","9tbkh4dm","9tbkh4dn","9tbkh4dp","9tbkh4dq","9tbkh4dr","9tbkh4ds","9tbkh4dt","9tbkh4du","9tbkh4dv","9tbkh4dw","9tbkh4dx","9tbkh4dy","9tbkh4dz","9tbkh4e0","9tbkh4e1","9tbkh4e2","9tbkh4e3","9tbkh4e4","9tbkh4e5","9tbkh4e6","9tbkh4e7","9tbkh4e8","9tbkh4e9","9tbkh4eb","9tbkh4ec","9tbkh4ed","9tbkh4ee","9tbkh4ef","9tbkh4eg","9tbkh4eh","9tbkh4ej","9tbkh4ek","9tbkh4em","9tbkh4en","9tbkh4ep","9tbkh4eq","9tbkh4er","9tbkh4es","9tbkh4et","9tbkh4eu","9tbkh4ev","9tbkh4ew","9tbkh4ex","9tbkh4ey","9tbkh4ez","9tbkh430","9tbkh431","9tbkh432","9tbkh433","9tbkh434","9tbkh435","9tbkh436","9tbkh437","9tbkh438","9tbkh439","9tbkh43b","9tbkh43c","9tbkh43d","9tbkh43e","9tbkh43f","9tbkh43g","9tbkh43h","9tbkh43j","9tbkh43k","9tbkh43m","9tbkh43n","9tbkh43p","9tbkh43q","9tbkh43r","9tbkh43s","9tbkh43t","9tbkh43u","9tbkh43v","9tbkh43w","9tbkh43x","9tbkh43y","9tbkh43z","9tbkh460","9tbkh461","9tbkh462","9tbkh463","9tbkh464","9tbkh465","9tbkh466","9tbkh467","9tbkh468","9tbkh469","9tbkh46b","9tbkh46c","9tbkh46d","9tbkh46e","9tbkh46f","9tbkh46g","9tbkh46h","9tbkh46j","9tbkh46k","9tbkh46m","9tbkh46n","9tbkh46p","9tbkh46q","9tbkh46r","9tbkh46s","9tbkh46t","9tbkh46u","9tbkh46v","9tbkh46w","9tbkh46x","9tbkh46y","9tbkh46z","9tbkh470","9tbkh471","9tbkh472","9tbkh473","9tbkh474","9tbkh475","9tbkh476","9tbkh477","9tbkh478","9tbkh479","9tbkh47b","9tbkh47c","9tbkh47d","9tbkh47e","9tbkh47f","9tbkh47g","9tbkh47h","9tbkh47j","9tbkh47k","9tbkh47m","9tbkh47n","9tbkh47p","9tbkh47q","9tbkh47r","9tbkh47s","9tbkh47t","9tbkh47u","9tbkh47v","9tbkh47w","9tbkh47x","9tbkh47y","9tbkh47z"};
		fsc.setAllGeohashes(generateSmallerGeohashes);
		
		// BASE HASH FOR GRID
		fsc.setBaseHashForGrid("9tbkh4,,,");
		// NW GEOHASH
		fsc.setNw("9tbkh4bpbpb");
		fsc.setNe("9tbkh4zzzzz");
		// SE GEOHASH
		fsc.setSe("9tbkh4pbpbp");
		fsc.setSw("9tbkh400000");
		
		// ALL ATTRIBUTES FOR VIZUALIZATION
		fsc.setAllAttributes("ndvi$sonar$thermal$lidar");
		
		// GRID FILE PATH
		fsc.setGridFilePath("/s/chopin/b/grad/sapmitra/Documents/arizona/cleanData/Roots_2018/F2 TRoots Planting/plots_arizona.json");
		
		// HASHGRID PRECISION
		fsc.setHashGridPrecision(11);
		
		// SET INDICES MAP
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
