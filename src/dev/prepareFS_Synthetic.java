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
public class prepareFS_Synthetic {
	
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
		
		// PARTITIONING PRECISION
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
		// FOUND USING FindUniqueGeohashesFromShapeFile.java
		//String[] generateSmallerGeohashes = {"9tbkh4dr","9tbkh4dq","9tbkh4dm","9tbkh4dk","9tbkh4d7","9tbkh4d6","9tbkh4d3","9tbkh4d2","9tbkh46r","9tbkh46q","9tbkh46m","9tbkh4dp","9tbkh4dn","9tbkh4dj","9tbkh4dh","9tbkh4d5","9tbkh4d4","9tbkh4d1","9tbkh4d0","9tbkh46p","9tbkh46n","9tbkh46j","9tbkh49z","9tbkh49y","9tbkh49v","9tbkh49u","9tbkh49g","9tbkh49f","9tbkh49c","9tbkh49b","9tbkh43z","9tbkh43y","9tbkh43v"};
		//String [] generateSmallerGeohashes = {"9tbkh5sf","9tbkh5sg","9tbkh5sh","9tbkh5sb","9tbkh5sc","9tbkh5sd","9tbkh5se","9tbkh5u0","9tbkh5sn","9tbkh5u1","9tbkh5u2","9tbkh5sp","9tbkh5u3","9tbkh5sq","9tbkh5sj","9tbkh5sk","9tbkh5sm","9tbkh5u8","9tbkh5sv","9tbkh5u9","9tbkh5sw","9tbkh5sx","9tbkh5sy","9tbkh5u4","9tbkh5sr","9tbkh5u5","9tbkh5ss","9tbkh5u6","9tbkh5st","9tbkh5u7","9tbkh5su","9tbkh5tb","9tbkh5sz","9tbkh5tg","9tbkh5th","9tbkh5tj","9tbkh5tc","9tbkh5td","9tbkh5te","9tbkh5tf","9tbkh5v1","9tbkh5tp","9tbkh5v2","9tbkh5v3","9tbkh5tq","9tbkh5v4","9tbkh5tr","9tbkh5tk","9tbkh5tm","9tbkh5v0","9tbkh5tn","9tbkh5v9","9tbkh5tw","9tbkh5tx","9tbkh5ty","9tbkh5tz","9tbkh5v5","9tbkh5ts","9tbkh5v6","9tbkh5tt","9tbkh5v7","9tbkh5tu","9tbkh5v8","9tbkh5tv","9tbkh5ub","9tbkh5uc","9tbkh5s0","9tbkh5qn","9tbkh5s1","9tbkh5qh","9tbkh5qj","9tbkh5s6","9tbkh5s7","9tbkh5s8","9tbkh5s9","9tbkh5s2","9tbkh5qp","9tbkh5s3","9tbkh5s4","9tbkh5s5","9tbkh5t0","9tbkh5t1","9tbkh5t2","9tbkh5t7","9tbkh5t8","9tbkh5t9","9tbkh5t3","9tbkh5t4","9tbkh5t5","9tbkh5t6","9tbkh5q4","9tbkh5q5","9tbkh5q0","9tbkh5q1","9tbkh5mb","9tbkh5mc","9tbkh5mh","9tbkh5mj","9tbkh5mk","9tbkh5md","9tbkh5me","9tbkh5mf","9tbkh5mg","9tbkh5mp","9tbkh5mq","9tbkh5mr","9tbkh5ms","9tbkh5mm","9tbkh5mn","9tbkh5mx","9tbkh5my","9tbkh5mz","9tbkh5mt","9tbkh5mu","9tbkh5mv","9tbkh5mw","9tbkh5nj","9tbkh5nh","9tbkh5nn","9tbkh5np","9tbkh57j","9tbkh57k","9tbkh5yn","9tbkh57m","9tbkh5yh","9tbkh57f","9tbkh57g","9tbkh5yj","9tbkh57h","9tbkh57r","9tbkh57s","9tbkh57t","9tbkh57u","9tbkh5yp","9tbkh57n","9tbkh57p","9tbkh57q","9tbkh57z","9tbkh57v","9tbkh57w","9tbkh57x","9tbkh57y","9tbkh5wj","9tbkh55h","9tbkh55j","9tbkh55k","9tbkh55d","9tbkh55e","9tbkh5wh","9tbkh55f","9tbkh55g","9tbkh5y4","9tbkh572","9tbkh55p","9tbkh5y5","9tbkh573","9tbkh55q","9tbkh574","9tbkh55r","9tbkh575","9tbkh55s","9tbkh5y0","9tbkh5wn","9tbkh5y1","9tbkh55m","9tbkh5wp","9tbkh570","9tbkh55n","9tbkh571","9tbkh55x","9tbkh55y","9tbkh55z","9tbkh576","9tbkh55t","9tbkh577","9tbkh55u","9tbkh578","9tbkh55v","9tbkh579","9tbkh55w","9tbkh56b","9tbkh56c","9tbkh56f","9tbkh56g","9tbkh56y","9tbkh56z","9tbkh56u","9tbkh56v","9tbkh57b","9tbkh57c","9tbkh57d","9tbkh57e","9tbkh5uh","9tbkh5uj","9tbkh5uk","9tbkh5ud","9tbkh5ue","9tbkh5uf","9tbkh5ug","9tbkh5up","9tbkh550","9tbkh5uq","9tbkh551","9tbkh5ur","9tbkh5w4","9tbkh552","9tbkh5us","9tbkh5w5","9tbkh553","9tbkh5um","9tbkh5un","9tbkh5w0","9tbkh5w1","9tbkh5ux","9tbkh558","9tbkh5uy","9tbkh559","9tbkh5uz","9tbkh5ut","9tbkh554","9tbkh5uu","9tbkh555","9tbkh5uv","9tbkh556","9tbkh5uw","9tbkh557","9tbkh5vb","9tbkh5vc","9tbkh5vd","9tbkh54b","9tbkh54g","9tbkh5vj","9tbkh5vk","9tbkh5ve","9tbkh54c","9tbkh5vf","9tbkh5vg","9tbkh5vh","9tbkh54f","9tbkh5vq","9tbkh5vr","9tbkh5vs","9tbkh5vt","9tbkh5vm","9tbkh5vn","9tbkh5vp","9tbkh5vy","9tbkh5vz","9tbkh54y","9tbkh54z","9tbkh5vu","9tbkh5vv","9tbkh5vw","9tbkh54u","9tbkh5vx","9tbkh54v","9tbkh55b","9tbkh55c","9tbkh5e0","9tbkh5e1","9tbkh5e2","9tbkh5e3","9tbkh5e8","9tbkh5e9","9tbkh5e4","9tbkh5e5","9tbkh5e6","9tbkh5e7","9tbkh5db","9tbkh5dg","9tbkh5dc","9tbkh5df","9tbkh5jy","9tbkh5jz","9tbkh5kf","9tbkh5kg","9tbkh5kh","9tbkh5kb","9tbkh5kc","9tbkh5kd","9tbkh5ke","9tbkh5kn","9tbkh5m0","9tbkh5m1","9tbkh5kp","9tbkh5m2","9tbkh5kq","9tbkh5m3","9tbkh5kj","9tbkh5kk","9tbkh5km","9tbkh5kv","9tbkh5m8","9tbkh5kw","9tbkh5m9","9tbkh5kx","9tbkh5ky","9tbkh5kr","9tbkh5m4","9tbkh5ks","9tbkh5m5","9tbkh5kt","9tbkh5m6","9tbkh5ku","9tbkh5m7","9tbkh5kz","9tbkh5n1","9tbkh5n4","9tbkh5n0","9tbkh5n5","9tbkh5hw","9tbkh5j9","9tbkh5hx","9tbkh5hy","9tbkh5hz","9tbkh5k0","9tbkh5k1","9tbkh5k6","9tbkh5k7","9tbkh5k8","9tbkh5k9","9tbkh5k2","9tbkh5k3","9tbkh5k4","9tbkh5k5","9tbkh5je","9tbkh5jf","9tbkh5jg","9tbkh5jh","9tbkh5jb","9tbkh5jc","9tbkh5jd","9tbkh5jm","9tbkh5jn","9tbkh5jp","9tbkh5jj","9tbkh5jk","9tbkh5ju","9tbkh5jv","9tbkh5jw","9tbkh5jx","9tbkh5jq","9tbkh5jr","9tbkh5js","9tbkh5jt","9tbkh5fy","9tbkh5fz","9tbkh5fu","9tbkh5h7","9tbkh5fv","9tbkh5h8","9tbkh5h9","9tbkh5gb","9tbkh5gc","9tbkh5gd","9tbkh5ge","9tbkh5gj","9tbkh5gk","9tbkh5gm","9tbkh5gf","9tbkh5gg","9tbkh5gh","9tbkh5gr","9tbkh5gs","9tbkh5gt","9tbkh5gu","9tbkh5gn","9tbkh5gp","9tbkh5gq","9tbkh5gz","9tbkh5gv","9tbkh5gw","9tbkh5gx","9tbkh5gy","9tbkh5hc","9tbkh5hd","9tbkh5he","9tbkh5hf","9tbkh5hb","9tbkh5hk","9tbkh5hm","9tbkh5hn","9tbkh5j0","9tbkh5hg","9tbkh5hh","9tbkh5hj","9tbkh5hs","9tbkh5j5","9tbkh5ht","9tbkh5j6","9tbkh5hu","9tbkh5j7","9tbkh5hv","9tbkh5j8","9tbkh5j1","9tbkh5hp","9tbkh5j2","9tbkh5hq","9tbkh5j3","9tbkh5hr","9tbkh5j4","9tbkh5dy","9tbkh5dz","9tbkh5du","9tbkh5dv","9tbkh5eb","9tbkh5ec","9tbkh5eh","9tbkh5ej","9tbkh5ek","9tbkh5ed","9tbkh5ee","9tbkh5ef","9tbkh5eg","9tbkh5g2","9tbkh5ep","9tbkh5g3","9tbkh5eq","9tbkh5g4","9tbkh5er","9tbkh5g5","9tbkh5es","9tbkh5em","9tbkh5g0","9tbkh5en","9tbkh5g1","9tbkh5ex","9tbkh5ey","9tbkh5ez","9tbkh5g6","9tbkh5et","9tbkh5g7","9tbkh5eu","9tbkh5g8","9tbkh5ev","9tbkh5g9","9tbkh5ew","9tbkh5fb","9tbkh5fc","9tbkh5ff","9tbkh5fg","9tbkh5h3","9tbkh5h4","9tbkh5h5","9tbkh5h6","9tbkh5h0","9tbkh5h1","9tbkh5h2"};
		String [] generateSmallerGeohashes = {"9tbkh5sf","9tbkh5sg","9tbkh5sh","9tbkh5sb","9tbkh5sc","9tbkh5sd","9tbkh5se","9tbkh5sj","9tbkh5sk","9tbkh5sm","9tbkh5sv","9tbkh5ss","9tbkh5st","9tbkh5su","9tbkh5tb","9tbkh5tg","9tbkh5th","9tbkh5tj","9tbkh5tc","9tbkh5td","9tbkh5te","9tbkh5tf","9tbkh5tk","9tbkh5tm","9tbkh5ts","9tbkh5tt","9tbkh5tu","9tbkh5tv","9tbkh5s0","9tbkh5s1","9tbkh5s6","9tbkh5s7","9tbkh5s8","9tbkh5s9","9tbkh5s2","9tbkh5s3","9tbkh5s4","9tbkh5s5","9tbkh5t0","9tbkh5t1","9tbkh5t2","9tbkh5t7","9tbkh5t8","9tbkh5t9","9tbkh5t3","9tbkh5t4","9tbkh5t5","9tbkh5t6","9tbkh5mb","9tbkh5mc","9tbkh5mh","9tbkh5mj","9tbkh5mk","9tbkh5md","9tbkh5me","9tbkh5mf","9tbkh5mg","9tbkh5mp","9tbkh5mq","9tbkh5mr","9tbkh5ms","9tbkh5mm","9tbkh5mn","9tbkh5mx","9tbkh5my","9tbkh5mz","9tbkh5mt","9tbkh5mu","9tbkh5mv","9tbkh5mw","9tbkh5jy","9tbkh5jz","9tbkh5kf","9tbkh5kg","9tbkh5kh","9tbkh5kb","9tbkh5kc","9tbkh5kd","9tbkh5ke","9tbkh5kn","9tbkh5m0","9tbkh5m1","9tbkh5kp","9tbkh5m2","9tbkh5kq","9tbkh5m3","9tbkh5kj","9tbkh5kk","9tbkh5km","9tbkh5kv","9tbkh5m8","9tbkh5kw","9tbkh5m9","9tbkh5kx","9tbkh5ky","9tbkh5kr","9tbkh5m4","9tbkh5ks","9tbkh5m5","9tbkh5kt","9tbkh5m6","9tbkh5ku","9tbkh5m7","9tbkh5kz","9tbkh5hw","9tbkh5j9","9tbkh5hx","9tbkh5hy","9tbkh5hz","9tbkh5k0","9tbkh5k1","9tbkh5k6","9tbkh5k7","9tbkh5k8","9tbkh5k9","9tbkh5k2","9tbkh5k3","9tbkh5k4","9tbkh5k5","9tbkh5je","9tbkh5jf","9tbkh5jg","9tbkh5jh","9tbkh5jb","9tbkh5jc","9tbkh5jd","9tbkh5jm","9tbkh5jn","9tbkh5jp","9tbkh5jj","9tbkh5jk","9tbkh5ju","9tbkh5jv","9tbkh5jw","9tbkh5jx","9tbkh5jq","9tbkh5jr","9tbkh5js","9tbkh5jt","9tbkh4uj","9tbkh4uk","9tbkh5h7","9tbkh5h8","9tbkh5h9","9tbkh4uh","9tbkh4uq","9tbkh4ur","9tbkh4us","9tbkh4ut","9tbkh4um","9tbkh4un","9tbkh4up","9tbkh4uy","9tbkh4uz","9tbkh4uu","9tbkh4uv","9tbkh4uw","9tbkh4ux","9tbkh4vj","9tbkh4vk","9tbkh4vm","9tbkh4vh","9tbkh5hc","9tbkh4vr","9tbkh5hd","9tbkh4vs","9tbkh5he","9tbkh4vt","9tbkh5hf","9tbkh4vu","9tbkh4vn","9tbkh4vp","9tbkh5hb","9tbkh4vq","9tbkh5hk","9tbkh4vz","9tbkh5hm","9tbkh5hn","9tbkh5j0","9tbkh5hg","9tbkh4vv","9tbkh5hh","9tbkh4vw","9tbkh4vx","9tbkh5hj","9tbkh4vy","9tbkh5hs","9tbkh5j5","9tbkh5ht","9tbkh5j6","9tbkh5hu","9tbkh5j7","9tbkh5hv","9tbkh5j8","9tbkh5j1","9tbkh5hp","9tbkh5j2","9tbkh5hq","9tbkh5j3","9tbkh5hr","9tbkh5j4","9tbkh5h3","9tbkh5h4","9tbkh5h5","9tbkh5h6","9tbkh5h0","9tbkh5h1","9tbkh5h2"};
		
		fsc.setAllGeohashes(generateSmallerGeohashes);
		
		// BASE HASH FOR GRID
		fsc.setBaseHashForGrid("9tbkh5,,,");
		// NW GEOHASH
		
		fsc.setNw("9tbkh5bpbpb");
		fsc.setNe("9tbkh5zzzzz");
		// SE GEOHASH
		fsc.setSe("9tbkh5pbpbp");
		fsc.setSw("9tbkh500000");
		
		// ALL ATTRIBUTES FOR VIZUALIZATION
		fsc.setAllAttributes("irt");
		
		// GRID FILE PATH
		fsc.setGridFilePath("/s/chopin/b/grad/sapmitra/Documents/arizona/cleanData/Roots_2019/plots_arizona_2019_synthetic.json");
		
		// HASHGRID PRECISION
		fsc.setHashGridPrecision(11);
		
		// SET INDICES MAP
		//THE INDICES AT WHICH ALL NECESSARY FIELDS MAY BE FOUND IN THE DATASET
		Map<String, int[]> indMap = new HashMap<String, int[]>();
		int[] irts = {2,3,0,5};
		indMap.put("irt", irts);
		
		fsc.setIndicesMap(indMap);
		
		
	}
}
