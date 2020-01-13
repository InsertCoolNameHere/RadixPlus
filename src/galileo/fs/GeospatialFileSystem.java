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
package galileo.fs;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.irods.jargon.core.exception.JargonException;
import org.json.JSONArray;
import org.json.JSONObject;

import galileo.bmp.Bitmap;
import galileo.bmp.BitmapException;
import galileo.bmp.GeoavailabilityGrid;
import galileo.bmp.GeoavailabilityMap;
import galileo.bmp.GeoavailabilityQuery;
import galileo.bmp.HashGrid;
import galileo.bmp.HashGridException;
import galileo.comm.TemporalType;
import galileo.config.SystemConfig;
import galileo.dataset.Block;
import galileo.dataset.Coordinates;
import galileo.dataset.Metadata;
import galileo.dataset.Point;
import galileo.dataset.SpatialHint;
import galileo.dataset.SpatialProperties;
import galileo.dataset.SpatialRange;
import galileo.dataset.TemporalProperties;
import galileo.dataset.feature.Feature;
import galileo.dataset.feature.FeatureSet;
import galileo.dataset.feature.FeatureType;
import galileo.dht.DataStoreHandler;
import galileo.dht.GroupInfo;
import galileo.dht.IRODSManager;
import galileo.dht.NetworkInfo;
import galileo.dht.NodeInfo;
import galileo.dht.OriginalTemporalHierarchyPartitioner;
import galileo.dht.PartitionException;
import galileo.dht.Partitioner;
import galileo.dht.SpatialHierarchyPartitioner;
import galileo.dht.StorageNode;
import galileo.dht.hash.HashException;
import galileo.dht.hash.HashTopologyException;
import galileo.dht.hash.TemporalHash;
import galileo.graph.FeaturePath;
import galileo.graph.MetadataGraph;
import galileo.graph.Path;
import galileo.graph.SummaryStatistics;
import galileo.graph.SummaryWrapper;
import galileo.integrity.RadixIntegrityGraph;
import galileo.query.Expression;
import galileo.query.Operation;
import galileo.query.Operator;
import galileo.query.Query;
import galileo.serialization.SerializationException;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.Serializer;
import galileo.util.GeoHash;
import galileo.util.Math;
import galileo.util.Pair;
import geo.main.java.com.github.davidmoten.geo.GeoHashUtils;

/**
 * Implements a {@link FileSystem} for Geospatial data. This file system manager
 * assumes that the information being stored has both space and time properties.
 * <p>
 * Relevant system properties include galileo.fs.GeospatialFileSystem.timeFormat
 * and galileo.fs.GeospatialFileSystem.geohashPrecision to modify how the
 * hierarchy is created.
 */
public class GeospatialFileSystem extends FileSystem {

	private static final Logger logger = Logger.getLogger("galileo");

	private static final String DEFAULT_TIME_FORMAT = "yyyy" + File.separator + "M" + File.separator + "d";
	private static final int MIN_GRID_POINTS = 5000;
	private int numCores;
	//private static GeoavailabilityGrid globalGrid;
	private static final String pathStore = "metadata.paths";
	private final StorageNode master;
	private NetworkInfo network;
	private Partitioner<Metadata> partitioner;
	private TemporalType temporalType;
	private int nodesPerGroup;
	/*
	 * Must be comma-separated name:type string where type is an int returned by
	 * FeatureType
	 */
	private List<Pair<String, FeatureType>> featureList;
	private SpatialHint spatialHint;
	private String storageRoot;

	private MetadataGraph metadataGraph;

	private PathJournal pathJournal;

	private SimpleDateFormat timeFormatter;
	private String timeFormat;
	private int geohashPrecision;
	private TemporalProperties latestTime;
	private TemporalProperties earliestTime;
	private String latestSpace;
	private String earliestSpace;
	private Set<String> geohashIndex;
	
	
	public static final String TEMPORAL_YEAR_FEATURE = "x__year__x";
	public static final String TEMPORAL_MONTH_FEATURE = "x__month__x";
	public static final String TEMPORAL_DAY_FEATURE = "x__day__x";
	private static final String TEMPORAL_HOUR_FEATURE = "x__hour__x";
	private static final String SPATIAL_FEATURE = "x__spatial__x";
	
	
	
	// A MAP BETWEEN THE FILEPATH AND THE CORRESPONDING SUMMARY OF THAT PLOT FILE
	// JUST FILENAME IS ENOUGH FOR KEY
	private Map<String, SummaryStatistics> filePathToSummaryMap = new HashMap<String, SummaryStatistics>();
	private HashGrid globalGrid;
	private FilesystemConfig configs;
	
	private RadixIntegrityGraph rig;

	public GeospatialFileSystem(StorageNode sn, String storageDirectory, String name, int precision, int nodesPerGroup,
			int temporalType, NetworkInfo networkInfo, String featureList, SpatialHint sHint, boolean ignoreIfPresent, FilesystemConfig config)
			throws FileSystemException, IOException, SerializationException, PartitionException, HashException,
			HashTopologyException {
		super(storageDirectory, name, ignoreIfPresent, "geospatial");
		
		//rig = new RadixIntegrityGraph(TEMPORAL_YEAR_FEATURE+":1,"+TEMPORAL_MONTH_FEATURE+":1,"+TEMPORAL_DAY_FEATURE+":1,sensor:9", "/iplant/home/radix_subterra", name);
		rig = new RadixIntegrityGraph(featureList, "/iplant/home/radix_subterra", name);
		
		//logger.info("RIKI: REACHED HERE2");
		this.master = sn;
		this.nodesPerGroup = nodesPerGroup;
		this.geohashIndex = new HashSet<>();
		if (featureList != null) {
			this.featureList = new ArrayList<>();
			for (String nameType : featureList.split(",")) {
				String[] pair = nameType.split(":");
				this.featureList
						.add(new Pair<String, FeatureType>(pair[0], FeatureType.fromInt(Integer.parseInt(pair[1]))));
			}
			this.featureList = Collections.unmodifiableList(this.featureList);
		}
		
		this.configs = config;
		
		
		this.spatialHint = sHint;
		if (this.featureList != null && this.spatialHint == null)
			throw new IllegalArgumentException("Spatial hint is needed when feature list is provided");
		this.storageRoot = storageDirectory;
		this.temporalType = TemporalType.fromType(temporalType);
		this.numCores = Runtime.getRuntime().availableProcessors();

		if (nodesPerGroup <= 0) 
			nodesPerGroup = networkInfo.getGroups().get(0).getSize();
		
		this.network = new NetworkInfo();
		GroupInfo groupInfo = null;
		List<NodeInfo> allNodes = networkInfo.getAllNodes();
		Collections.sort(allNodes);
		TemporalHash th = new TemporalHash(this.temporalType);
		int maxGroups = th.maxValue().intValue();
		for (int i = 0; i < allNodes.size(); i++) {
			if (this.network.getGroups().size() < maxGroups) {
				if (i % nodesPerGroup == 0) {
					groupInfo = new GroupInfo(String.valueOf(i / nodesPerGroup));
					groupInfo.addNode(allNodes.get(i));
					this.network.addGroup(groupInfo);
				} else {
					groupInfo.addNode(allNodes.get(i));
				}
			}
		}
		
		/*String[] geohashes = {"9xjr6b86","9xjr6b87","9xjr6b88","9xjr6b89","9xjr6b07","9xjr6b82","9xjr6b83","9xjr6b96","9xjr6b97","9xjr6b98","9xjr6b99",
				"9xjr6b15","9xjr6b17","9xjr6b8b","9xjr6b8c","9xjr6b8d","9xjr6b90","9xjr6b91","9xjr6b92","9xjr6b93","9xjr6b94","9xjr6b95","9xjr6b2q",
				"9xjr6b2r","9xjr6b2s","9xjr6b2t","9xjr6b2u","9xjr6b2v","9xjr6b2w","9xjr6b2x","9xjr6b2y","9xjr6b2z","9xjr6b2b","9xjr6b2c","9xjr6b2d",
				"9xjr6b2e","9xjr6b2f","9xjr6b2g","9xjr6b2k","9xjr6b2m","9xjr6b3p","9xjr6b3q","9xjr6b3r","9xjr6b3s","9xjr6b3t","9xjr6b3u","9xjr6b3v",
				"9xjr6b3w","9xjr6b3x","9xjr6b3y","9xjr6b3z","9xjr6b3b","9xjr6b3c","9xjr6b3d","9xjr6b3e","9xjr6b3f","9xjr6b3g","9xjr6b3h","9xjr6b3j",
				"9xjr6b3k","9xjr6b3m","9xjr6b3n","9xjr6b0m","9xjr6b8u","9xjr6b8v","9xjr6b8w","9xjr6b22","9xjr6b23","9xjr6b0q","9xjr6b8y","9xjr6b0r",
				"9xjr6b0s","9xjr6b26","9xjr6b0t","9xjr6b27","9xjr6b0u","9xjr6b28","9xjr6b0v","9xjr6b29","9xjr6b0w","9xjr6b0x","9xjr6b9b","9xjr6b0y",
				"9xjr6b9c","9xjr6b0z","9xjr6b9d","9xjr6b9e","9xjr6b8e","9xjr6b8f","9xjr6b8g","9xjr6b8k","9xjr6b8m","9xjr6b0e","9xjr6b0g","9xjr6b8q",
				"9xjr6b8s","9xjr6b0k","9xjr6b8t","9xjr6b30","9xjr6b1n","9xjr6b9v","9xjr6b31","9xjr6b9w","9xjr6b32","9xjr6b1p","9xjr6b33","9xjr6b1q",
				"9xjr6b9y","9xjr6b34","9xjr6b1r","9xjr6b35","9xjr6b1s","9xjr6b36","9xjr6b1t","9xjr6b37","9xjr6b1u","9xjr6b38","9xjr6b1v","9xjr6b39",
				"9xjr6b1w","9xjr6b1x","9xjr6b1y","9xjr6b1z","9xjr6b9f","9xjr6b9g","9xjr6b9h","9xjr6b9j","9xjr6b9k","9xjr6b9m","9xjr6b1e","9xjr6b9n",
				"9xjr6b1g","9xjr6b1h","9xjr6b9q","9xjr6b1j","9xjr6b9s","9xjr6b1k","9xjr6b9t","9xjr6b1m","9xjr6b9u"};*/
		
		String[] geohashes = configs.getAllGeohashes();
		
		
		// HANDLING FOR COLORADO PLOTS
		
		//String[] baseGeohashes = {StorageNode.baseHash};
		
		//geohashes = generateGeohashes(geohashes, geohashes[0].length()+1);
		//logger.info("RIKI: REACHED HERE4");
		this.partitioner = new SpatialHierarchyPartitioner(sn, this.network, geohashes);
		this.timeFormat = System.getProperty("galileo.fs.GeospatialFileSystem.timeFormat", DEFAULT_TIME_FORMAT);
//		int maxPrecision = GeoHash.MAX_PRECISION / 5;
		int maxPrecision = 12;
//		this.geohashPrecision = (precision < 0) ? DEFAULT_GEOHASH_PRECISION
//				: (precision > maxPrecision) ? maxPrecision : precision;
		this.geohashPrecision = 8; //hardcode for now to test
		this.timeFormatter = new SimpleDateFormat();
		this.timeFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
		this.timeFormatter.applyPattern(timeFormat);
		this.pathJournal = new PathJournal(this.storageDirectory + File.separator + pathStore);
		
		// EXAMPLE OF pathToGridFile: /s/chopin/b/grad/sapmitra/workspace/radix/galileo/config/grid/roots_az/plots.json
		String pathToGridFile = SystemConfig.getConfDir()+File.separator+"grid"+File.separator+name;
		
		if(config.getGridFilePath() != null && !config.getGridFilePath().trim().isEmpty()) {
			pathToGridFile = config.getGridFilePath();
		}
		
		try {
			globalGrid = new HashGrid(configs.getBaseHashForGrid(), configs.getHashGridPrecision(), configs.getNw(), configs.getNe(), 
					configs.getSe(), configs.getSw());
			// THIS READS THE PLOTS.JSON FILE AND MARKS THE PLOTS ON THE HASHGRID
			//globalGrid.initGrid(pathToGridFile+File.separator+gridFiles[0].getName());
			globalGrid.initGrid(pathToGridFile);
		} catch (IOException | HashGridException | BitmapException e) {
			logger.log(Level.SEVERE, "could not open grid initialization file. Error: " + e);
		}
		
		updateFS_RIG();
		
		
		setType("geospatial");
		createMetadataGraph();
	}
	
	
	/**
	 * HANDLES UPDATE OF THE RIG GRAPH FOR A GIVEN FILESYSTEM
	 * @throws IOException
	 */
	public void updateFS_RIG() throws IOException {
	
		IRODSManager subterra = new IRODSManager();
		
		String[] paths = null;
		try {
			paths = subterra.readAllRemoteFiles(name);
		} catch (JargonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(paths!=null && paths.length > 0) {
			String pp = "";
			for(String path: paths) {
				addIRODSPendingPath(path);
				pp+=path+"\n";
			}
			logger.info("RIKI: RIG PATHS DOWNLOADED: "+pp);
			
			updateRIG();
			
			logger.info("RIKI: RIG UPDATE COMPLETE.");
		}
		
	}
	
	private static String[] generateGeohashes(String[] baseGeohashes, int desiredPrecision) {
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

	public JSONArray getFeaturesRepresentation() {
		JSONArray features = new JSONArray();
		if (this.featureList != null) {
			for (Pair<String, FeatureType> pair : this.featureList)
				features.put(pair.a + ":" + pair.b.name());
		}
		return features;
	}

	public List<String> getFeaturesList() {
		List<String> features = new ArrayList<String>();
		if (this.featureList != null) {
			for (Pair<String, FeatureType> pair : this.featureList)
				features.add(pair.a + ":" + pair.b.name());
		}
		return features;
	}

	public NetworkInfo getNetwork() {
		return this.network;
	}

	public Partitioner<Metadata> getPartitioner() {
		return this.partitioner;
	}

	public TemporalType getTemporalType() {
		return this.temporalType;
	}

	/**
	 * Initializes the Metadata Graph, either from a successful recovery from
	 * the PathJournal, or by scanning all the {@link Block}s on disk.
	 */
	private void createMetadataGraph() throws IOException {
		metadataGraph = new MetadataGraph();

		/* Recover the path index from the PathJournal */
		List<FeaturePath<String>> graphPaths = new ArrayList<>();
		boolean recoveryOk = pathJournal.recover(graphPaths);
		pathJournal.start();

		if (recoveryOk == true) {
			for (FeaturePath<String> path : graphPaths) {
				try {
					metadataGraph.addPath(path);
				} catch (Exception e) {
					logger.log(Level.WARNING, "Failed to add path", e);
					recoveryOk = false;
					break;
				}
			}
		}

		if (recoveryOk == false) {
			logger.log(Level.SEVERE, "Failed to recover path journal!");
			pathJournal.erase();
			pathJournal.start();
			fullRecovery();
		}
	}

	public synchronized JSONObject obtainState() {
		JSONObject state = new JSONObject();
		state.put("name", this.name);
		state.put("storageRoot", this.storageRoot);
		state.put("precision", this.geohashPrecision);
		state.put("nodesPerGroup", this.nodesPerGroup);
		state.put("geohashIndex", this.geohashIndex);
		StringBuffer features = new StringBuffer();
		if (this.featureList != null) {
			for (Pair<String, FeatureType> pair : this.featureList)
				features.append(pair.a + ":" + pair.b.toInt() + ",");
			features.setLength(features.length() - 1);
		}
		state.put("featureList", this.featureList != null ? features.toString() : JSONObject.NULL);
		JSONObject spHint = null;
		if (this.spatialHint != null) {
			spHint = new JSONObject();
			spHint.put("lat", this.spatialHint.getLatitudeHint());
			spHint.put("long", this.spatialHint.getLongitudeHint());
		}
		state.put("spatialHint", spHint == null ? JSONObject.NULL : spHint);
		state.put("temporalType", this.temporalType.getType());
//		state.put("temporalString", this.temporalType.name());
		state.put("earliestTime", this.earliestTime != null ? this.earliestTime.getStart() : JSONObject.NULL);
//		state.put("earliestSpace", this.earliestSpace != null ? this.earliestSpace : JSONObject.NULL);
		state.put("latestTime", this.latestTime != null ? this.latestTime.getEnd() : JSONObject.NULL);
//		state.put("latestSpace", this.latestSpace != null ? this.latestSpace : JSONObject.NULL);
		state.put("readOnly", this.isReadOnly());
		
		// PERSISTING CONFIG
		JSONObject fsConfig = configs.getJsonRepresentation();
		state.put("fsconfig", fsConfig);
		
		// PERSISTING SUMMARIES
		
		//filePathToSummaryMap
		JSONArray summaryMaps = getSummariesToJSON(filePathToSummaryMap);
		state.put("summaryMaps", summaryMaps);
		
		
		return state;
	}
	
	/*public static JSONObject obtainStateTest(Map<String, SummaryStatistics> filePathToSummaryMap1) {
		JSONObject state = new JSONObject();
		JSONArray summaryMaps = getSummariesToJSON(filePathToSummaryMap1);
		state.put("summaryMaps", summaryMaps);
		
		return state;
		
	}*/


	private JSONArray getSummariesToJSON(Map<String, SummaryStatistics> filePathToSummaryMap1) {
		
		JSONArray summaryMaps = new JSONArray();
		
		for(String summary_path : filePathToSummaryMap1.keySet()) {
			
			JSONObject mainObject = new JSONObject();
			
			SummaryStatistics summary = filePathToSummaryMap1.get(summary_path);
			
			JSONObject json_summary = summary.getJsonRepresentation();
			
			mainObject.put("key", summary_path);
			mainObject.put("val", json_summary);
			
			summaryMaps.put(mainObject);
		}
		return summaryMaps;
	}
	

	public static Map<String, SummaryStatistics> extractSummaryMapFromJson(JSONArray summaryMapJson) {
		
		Map<String, SummaryStatistics> pathToSummaryMap = new HashMap<String, SummaryStatistics>();
		for(int i=0; i<summaryMapJson.length(); i++) {
			JSONObject mainObject = summaryMapJson.getJSONObject(i);
			
			String key = mainObject.getString("key");
			JSONObject json_summary = mainObject.getJSONObject("val");
			
			SummaryStatistics ss = new SummaryStatistics();
			ss.populateObject(json_summary);
			
			pathToSummaryMap.put(key, ss);
		}
		return pathToSummaryMap;
	}
	/*
	public static void restoreStateTest(JSONObject state) {
		// READING IN SUMMARY DATA
		JSONArray summaryMapJson = state.getJSONArray("summaryMaps");
		Map<String, SummaryStatistics> summaryMaps = extractSummaryMapFromJson(summaryMapJson);
		System.out.println(summaryMaps.size());
	}
	
	
	public static void main(String arg[]) {
		SummaryStatistics ss = new SummaryStatistics();
		ss.setAvg(100);ss.setMin(101);ss.setMax(102);ss.setCount(103);ss.setStdDev(104);
		
		SummaryStatistics ss1 = new SummaryStatistics();
		ss.setAvg(100);ss.setMin(101);ss.setMax(102);ss.setCount(103);ss.setStdDev(104);
		
		Map<String, SummaryStatistics> maps = new HashMap<String, SummaryStatistics>();
		maps.put("path1", ss);
		maps.put("path2", ss1);
		
		JSONObject obtainStateTest = obtainStateTest(maps);
		
		restoreStateTest(obtainStateTest);
	}
	*/

	public static GeospatialFileSystem restoreState(StorageNode storageNode, NetworkInfo networkInfo, JSONObject state)
			throws FileSystemException, IOException, SerializationException, PartitionException, HashException,
			HashTopologyException {
		String name = state.getString("name");
		String storageRoot = state.getString("storageRoot");
		int geohashPrecision = state.getInt("precision");
		int nodesPerGroup = state.getInt("nodesPerGroup");
		JSONArray geohashIndices = state.getJSONArray("geohashIndex");
		String featureList = null;
		if (state.get("featureList") != JSONObject.NULL)
			featureList = state.getString("featureList");
		int temporalType = state.getInt("temporalType");
		SpatialHint spHint = new SpatialHint("lat", "long");
//		if (state.get("spatialHint") != JSONObject.NULL) {
//			JSONObject spHintJSON = state.getJSONObject("spatialHint");
//			spHint = new SpatialHint(spHintJSON.getString("latHint"), spHintJSON.getString("lngHint"));
//		}
		
		FilesystemConfig fsc = new FilesystemConfig();
		fsc.populateObject(state.getJSONObject("fsconfig"));
		
		// FS CONFIGS ARE READ IN THE CONSTRUCTOR
		GeospatialFileSystem gfs = new GeospatialFileSystem(storageNode, storageRoot, name, geohashPrecision,
				nodesPerGroup, temporalType, networkInfo, featureList, spHint, true, fsc);
		
		gfs.earliestTime = (state.get("earliestTime") != JSONObject.NULL)
				? new TemporalProperties(state.getLong("earliestTime")) : null;
//		gfs.earliestSpace = (state.get("earliestSpace") != JSONObject.NULL) ? state.getString("earliestSpace") : null;
		gfs.latestTime = (state.get("latestTime") != JSONObject.NULL)
				? new TemporalProperties(state.getLong("latestTime")) : null;
//		gfs.latestSpace = (state.get("latestSpace") != JSONObject.NULL) ? state.getString("latestSpace") : null;
		Set<String> geohashIndex = new HashSet<String>();
		for (int i = 0; i < geohashIndices.length(); i++)
			geohashIndex.add(geohashIndices.getString(i));
		gfs.geohashIndex = geohashIndex;
		
		
		
		// READING IN SUMMARY DATA
		JSONArray summaryMapJson = state.getJSONArray("summaryMaps");
		Map<String, SummaryStatistics> summaryMaps = extractSummaryMapFromJson(summaryMapJson);
		gfs.filePathToSummaryMap = summaryMaps;
		
		return gfs;
	}
	

	public long getLatestTime() {
		if (this.latestTime != null)
			return this.latestTime.getEnd();
		return 0;
	}

	public long getEarliestTime() {
		if (this.earliestTime != null)
			return this.earliestTime.getStart();
		return 0;
	}

	public String getLatestSpace() {
		if (this.latestSpace != null)
			return this.latestSpace;
		return "";
	}

	public String getEarliestSpace() {
		if (this.earliestSpace != null)
			return this.earliestSpace;
		return "";
	}

	public int getGeohashPrecision() {
		return this.geohashPrecision;
	}

	private String getTemporalString(TemporalProperties tp) {
		if (tp == null)
			return "xxxx-xx-xx-xx";
		Calendar c = Calendar.getInstance();
		c.setTimeZone(TemporalHash.TIMEZONE);
		c.setTimeInMillis(tp.getStart());
		int hour = c.get(Calendar.HOUR_OF_DAY);
		int day = c.get(Calendar.DAY_OF_MONTH);
		int month = c.get(Calendar.MONTH) + 1;
		int year = c.get(Calendar.YEAR);
		switch (this.temporalType) {
		case HOUR_OF_DAY:
			return String.format("%d-%d-%d-%d", year, month, day, hour);
		case DAY_OF_MONTH:
			return String.format("%d-%d-%d-xx", year, month, day);
		case MONTH:
			return String.format("%d-%d-xx-xx", year, month);
		case YEAR:
			return String.format("%d-xx-xx-xx", year);
		}
		return String.format("%d-%d-%d-xx", year, month, day);
	}

	private String getSpatialString(SpatialProperties sp) {
		char[] hash = new char[this.geohashPrecision];
		Arrays.fill(hash, 'o');
		String geohash = new String(hash);
		if (sp == null)
			return geohash;

		if (sp.hasRange()) {
			geohash = GeoHash.encode(sp.getSpatialRange(), this.geohashPrecision);
		} else {
			geohash = GeoHash.encode(sp.getCoordinates(), this.geohashPrecision);
		}
		return geohash;
	}
	
	// FOR ARIZONA PLOTS, WE STORE ACTUAL DATA, NOT JUST FILEPATH
	public String storeBlockArizona(Block block, String sensorType, SummaryStatistics summary, String irodsStoragePath) throws FileSystemException, IOException {
		
		Metadata meta = block.getMetadata();
		String name = "";
		if (meta.getName() != null && meta.getName().trim() != "")
			name = meta.getName();
		String blockDirPath = this.storageDirectory + File.separator + getStorageDirectory(block);
		String blockPath = blockDirPath + File.separator + name + FileSystem.BLOCK_EXTENSION;
		String metadataPath = blockDirPath + File.separator + name + FileSystem.METADATA_EXTENSION;
		
		/* Ensure the storage directory is there. */
 		File blockDirectory = new File(blockDirPath);
 		if (!blockDirectory.exists()) {
 			if (!blockDirectory.mkdirs()) {
 				throw new IOException("Failed to create directory (" + blockDirPath + ") for block.");
 			}
 		}

 		Serializer.persist(block.getMetadata(), metadataPath);
 		File gblock = new File(blockPath);
 		boolean newLine = gblock.exists();
 		
 		// ADDING OF METADATA TO METADATA GRAPH
 		//if (!newLine)
 		
 		// IRRESPECTIVE OF WHETHER THIS PATH HAS ALREADY BEEN STORED OR NOT, WE NEED TO UPDATE THE METADATA
 		storeMetadata(meta, blockPath+"$$"+irodsStoragePath);

 		try (FileOutputStream blockData = new FileOutputStream(blockPath, false)) {//overwrite block data, since only storing paths
 			if (newLine)
 				blockData.write("\n".getBytes("UTF-8"));
 			blockData.write(block.getData());
 			blockData.close();
 		} catch (Exception e) {
 			throw new FileSystemException("Error storing block: " + e.getClass().getCanonicalName(), e);
 		}
 		if (latestTime == null || latestTime.getEnd() < meta.getTemporalProperties().getEnd()) {
			this.latestTime = meta.getTemporalProperties();
		}

		if (earliestTime == null || earliestTime.getStart() > meta.getTemporalProperties().getStart()) {
			this.earliestTime = meta.getTemporalProperties();
		}
		// ADDING THE SUMMARY STATISTICS FOR THIS PLOT
		// DURING NORMAL BLOCK STORAGE, SUMMARIES ARE NOT ACCUMULATED
		// THE SUMMARIES ARE ACCUMULATED LATER DURING SYNCHRONIZATION
		
		synchronized (filePathToSummaryMap) {
			
			SummaryStatistics old_summary = filePathToSummaryMap.get(blockPath);
			
			if(old_summary == null) {
				
				filePathToSummaryMap.put(blockPath, summary);
			} else {
				// Merging summaries
				SummaryStatistics newSummary = SummaryStatistics.mergeSummary(old_summary, summary);
				filePathToSummaryMap.put(blockPath, newSummary);
				
			}
		}
			
		return blockPath;
	}

	/**
	 * Creates a new block if one does not exist based on the name of the
	 * metadata or appends the bytes to an existing block in which case the
	 * metadata in the graph will not be updated.
	 */
	@Override
	public String storeBlock(Block block) throws FileSystemException, IOException {
		Metadata meta = block.getMetadata();
		String name = "";
		if (meta.getName() != null && meta.getName().trim() != "")
			name = meta.getName();
		String blockDirPath = this.storageDirectory + File.separator + getStorageDirectory(block);
		String blockPath = blockDirPath + File.separator + name + FileSystem.BLOCK_EXTENSION;
		String metadataPath = blockDirPath + File.separator + name + FileSystem.METADATA_EXTENSION;
		
		/* Ensure the storage directory is there. */
 		File blockDirectory = new File(blockDirPath);
 		if (!blockDirectory.exists()) {
 			if (!blockDirectory.mkdirs()) {
 				throw new IOException("Failed to create directory (" + blockDirPath + ") for block.");
 			}
 		}

 		Serializer.persist(block.getMetadata(), metadataPath);
 		File gblock = new File(blockPath);
 		boolean newLine = gblock.exists();
// 		if (!newLine)
 			storeMetadata(meta, blockPath);

 		try (FileOutputStream blockData = new FileOutputStream(blockPath, false)) {//overwrite block data, since only storing paths
// 			if (newLine)
// 				blockData.write("\n".getBytes("UTF-8"));
 			blockData.write(block.getData());
 			blockData.close();
 		} catch (Exception e) {
 			throw new FileSystemException("Error storing block: " + e.getClass().getCanonicalName(), e);
 		}
 		if (latestTime == null || latestTime.getEnd() < meta.getTemporalProperties().getEnd()) {
			this.latestTime = meta.getTemporalProperties();
		}

		if (earliestTime == null || earliestTime.getStart() > meta.getTemporalProperties().getStart()) {
			this.earliestTime = meta.getTemporalProperties();
		}
//		this.geohashIndex.add(geohash.substring(0, Partitioner.SPATIAL_PRECISION));
		return blockPath;
	}

	public Block retrieveBlock(String blockPath) throws IOException, SerializationException {
		Metadata metadata = null;
		byte[] blockBytes = Files.readAllBytes(Paths.get(blockPath));
		String metadataPath = blockPath.replace(BLOCK_EXTENSION, METADATA_EXTENSION);
		File metadataFile = new File(metadataPath);
		if (metadataFile.exists())
			metadata = Serializer.deserialize(Metadata.class, Files.readAllBytes(Paths.get(metadataPath)));
		return new Block(this.name, metadata, blockBytes);
	}

	/**
	 * Given a {@link Block}, determine its storage directory on disk.
	 *
	 * @param block
	 *            The Block to inspect
	 *
	 * @return String representation of the directory on disk this Block should
	 *         be stored in.
	 */
	private String getStorageDirectory(Block block) {
		Metadata meta = block.getMetadata();
		String directory = meta.getName().replaceAll("-", File.separator);
		return directory;
	}

	private List<Expression> buildTemporalExpression(String temporalProperties) {
		List<Expression> temporalExpressions = new ArrayList<Expression>();
		String[] temporalFeatures = temporalProperties.split("-");
		int length = (temporalFeatures.length <= 4) ? temporalFeatures.length : 4;
		for (int i = 0; i < length; i++) {
			if (temporalFeatures[i].charAt(0) != 'x') {
				String temporalFeature = temporalFeatures[i];
				if (temporalFeature.charAt(0) == '0')
					temporalFeature = temporalFeature.substring(1);
				Feature feature = null;
				switch (i) {
				case 0:
					feature = new Feature(TEMPORAL_YEAR_FEATURE, Integer.valueOf(temporalFeature));
					break;
				case 1:
					feature = new Feature(TEMPORAL_MONTH_FEATURE, Integer.valueOf(temporalFeature));
					break;
				case 2:
					feature = new Feature(TEMPORAL_DAY_FEATURE, Integer.valueOf(temporalFeature));
					break;
				case 3:
					feature = new Feature(TEMPORAL_HOUR_FEATURE, Integer.valueOf(temporalFeature));
					break;
				}
				temporalExpressions.add(new Expression(Operator.EQUAL, feature));
			}
		}
		return temporalExpressions;
	}

	private String getGroupKey(Path<Feature, String> path, String space) {
		if (null != path && path.hasPayload()) {
			List<Feature> labels = path.getLabels();
			String year = "xxxx", month = "xx", day = "xx", hour = "xx";
			int allset = (space == null) ? 0 : 1;
			for (Feature label : labels) {
				switch (label.getName().toLowerCase()) {
				case TEMPORAL_YEAR_FEATURE:
					year = label.getString();
					allset++;
					break;
				case TEMPORAL_MONTH_FEATURE:
					month = label.getString();
					allset++;
					break;
				case TEMPORAL_DAY_FEATURE:
					day = label.getString();
					allset++;
					break;
				case TEMPORAL_HOUR_FEATURE:
					hour = label.getString();
					allset++;
					break;
				case SPATIAL_FEATURE:
					if (space == null) {
						space = label.getString();
						allset++;
					}
					break;
				}
				if (allset == 5)
					break;
			}
			return String.format("%s-%s-%s-%s-%s", year, month, day, hour, space);
		}
		return String.format("%s-%s", getTemporalString(null), (space == null) ? getSpatialString(null) : space);
	}

	private String getSpaceKey(Path<Feature, String> path) {
		if (null != path && path.hasPayload()) {
			List<Feature> labels = path.getLabels();
			for (Feature label : labels)
				if (label.getName().toLowerCase().equals("plotid"))
					return label.getString();
		}
		return getSpatialString(null);
	}

	private Query queryIntersection(Query q1, Query q2) {
		if (q1 != null && q2 != null) {
			Query query = new Query();
			for (Operation q1Op : q1.getOperations()) {
				for (Operation q2Op : q2.getOperations()) {
					Operation op = new Operation(q1Op.getExpressions());
					op.addExpressions(q2Op.getExpressions());
					query.addOperation(op);
				}
			}
			//logger.info(query.toString());
			return query;
		} else if (q1 != null) {
			return q1;
		} else if (q2 != null) {
			return q2;
		} else {
			return null;
		}
	}

	private class ParallelQueryEvaluator implements Runnable {
		private List<Operation> operations;
		private List<Path<Feature, String>> resultPaths;

		public ParallelQueryEvaluator(List<Operation> operations) {
			this.operations = operations;
		}

		public List<Path<Feature, String>> getResults() {
			return this.resultPaths;
		}

		@Override
		public void run() {
			Query query = new Query();
			query.addAllOperations(operations);
			this.resultPaths = metadataGraph.evaluateQuery(query);
		}
	}

	private List<Path<Feature, String>> executeParallelQuery(Query finalQuery) throws InterruptedException {
		//logger.info("Query: " + finalQuery.toString());
		List<Path<Feature, String>> paths = new ArrayList<>();
		List<Operation> operations = finalQuery.getOperations();
		if (operations.size() > 0) {
			if (operations.size() > 5000) {
				int subsetSize = operations.size() / numCores;
				for (int i = 0; i < numCores; i++) {
					operations.subList(i, (i + 1) * subsetSize);
				}
				int size = operations.size();
				ExecutorService executor = Executors.newFixedThreadPool(numCores);
				List<ParallelQueryEvaluator> queryEvaluators = new ArrayList<>();
				for (int i = 0; i < numCores; i++) {
					int from = i * subsetSize;
					int to = (i + 1 != numCores) ? (i + 1) * subsetSize : size;
					List<Operation> subset = new ArrayList<>(operations.subList(from, to));
					ParallelQueryEvaluator pqe = new ParallelQueryEvaluator(subset);
					queryEvaluators.add(pqe);
					executor.execute(pqe);
				}
				operations.clear();
				executor.shutdown();
				boolean termination = executor.awaitTermination(10, TimeUnit.MINUTES);
				if (!termination)
					logger.severe("Query failed to process in 10 minutes");
				paths = new ArrayList<>();
				for (ParallelQueryEvaluator pqe : queryEvaluators)
					if (pqe.getResults() != null)
						paths.addAll(pqe.getResults());
			} else {
				paths = metadataGraph.evaluateQuery(finalQuery);
			}
		}
		return paths;
	}
	
	/**
	 * QUERYING THE R.I.G. INSTEAD OF THE METADATA GRAPH
	 * @author sapmitra
	 * @param temporalProperties
	 * @param spatialProperties
	 * @param metadataQuery
	 * @return
	 */
	public List<String> listRIGPaths(String temporalProperties, List<Coordinates> spatialProperties, Query metadataQuery) {
		
		List<String> blocks = new ArrayList<String>();
		
		List<Expression> temporalExpression = null;
		if(temporalProperties != null) {
			temporalExpression = buildTemporalExpression(temporalProperties);
		}
		
		SpatialProperties sp = new SpatialProperties(new SpatialRange(spatialProperties));
		
		List<Coordinates> geometry = sp.getSpatialRange().hasPolygon() ? sp.getSpatialRange().getPolygon()
				: sp.getSpatialRange().getBounds();
		
		List<String> hashLocations = new ArrayList<>(Arrays.asList(GeoHash.getIntersectingGeohashes(geometry, Partitioner.SPATIAL_PRECISION+2)));
		
		
		if(Partitioner.SPATIAL_PRECISION < getGlobalGrid().getPrecision()) {
			List<String> finerGeohashes = new ArrayList<String>();
			
			for(String h: hashLocations) {
				List<String> tmp = GeoHash.getInternalGeohashes(h, getGlobalGrid().getPrecision());
				if(tmp != null && tmp.size() > 0)
					finerGeohashes.addAll(tmp);
			}
			
			hashLocations = finerGeohashes;
		}
		
		Query query = new Query();
		
		List<Operation> pending_operations = null;
		
		if(metadataQuery != null)
			pending_operations = metadataQuery.getOperations();
		
		if(temporalExpression != null) {
			if(pending_operations != null) {
				for(Operation op : pending_operations) {
					op.addExpressions(temporalExpression);
				}
			} else {
				
				pending_operations = new ArrayList<Operation>();
				Operation op = new Operation(temporalExpression);
				pending_operations.add(op);
			}
		}

		HashGrid queryGrid = new HashGrid(configs.getBaseHashForGrid(), getGlobalGrid().getPrecision(), configs.getNw(), configs.getNe(), 
				configs.getSe(), configs.getSw());
		
		//logger.info("RIKI: HERE X1");
		for (String ghash : hashLocations)
			try {
				queryGrid.addPoint(ghash);
			} catch (BitmapException e1) {
			}
		queryGrid.applyUpdates();
		
		int [] intersections = getGlobalGrid().query(queryGrid.getBitmap());
		
		hashLocations.clear();
		
		
		for (Integer i : intersections)
			hashLocations.add(getGlobalGrid().indexToGeoHash(i, configs.getHashGridPrecision()));
			
		
		//logger.info("RIKI: INTERSECTING GEOHASHES FOR GRID: "+hashLocations);
		//Once coverage is computed, query hashgrid to determine which plots are contained in polygon
		Set<Integer> overlappingPlots = new HashSet<>();
		for (String ghash : hashLocations) {
			Set<Integer> plots;
			try {
				plots = getGlobalGrid().locatePoint(ghash);
				overlappingPlots.addAll(plots);
			} catch (BitmapException e) {
				//If this error is caught, it means there is no data for given index
			}
		}
		
		logger.info("RIKI: TOTAL OVERLAPPING RIG PLOTS FOUND: "+ overlappingPlots.size());
		
		/* Operations: OR; Expressions: AND. So create your operations accordingly */
		
		for (int intersection : overlappingPlots) {//intersections are the 5-char intersections...
			if(pending_operations == null) {
				Operation op = new Operation();
				
				op.addExpressions(new Expression(Operator.EQUAL, new Feature("plotID", intersection)));
				query.addOperation(op);
				
			} else {
				for(Operation o : pending_operations) {
					Operation op = new Operation(o.getExpressions());
					op.addExpressions(new Expression(Operator.EQUAL, new Feature("plotID", intersection)));
					query.addOperation(op);
				}
			}
			
			
		}
		
		blocks = rig.evaluateQuery(query);
		
		return blocks;
	}
		
		

	public Map<String, List<String>> listBlocks(String temporalProperties, List<Coordinates> spatialProperties,
			Query metaQuery, boolean group, String sensorName) throws InterruptedException {
		
		Map<String, List<String>> blockMap = new HashMap<String, List<String>>();
		String space = null;
		List<Path<Feature, String>> paths = null;
		List<String> blocks = new ArrayList<String>();
		/*if (temporalProperties != null && spatialProperties != null) {
			SpatialProperties sp = new SpatialProperties(new SpatialRange(spatialProperties));
			List<Coordinates> geometry = sp.getSpatialRange().hasPolygon() ? sp.getSpatialRange().getPolygon()
					: sp.getSpatialRange().getBounds();
			space = getSpatialString(sp);
			List<String> hashLocations = new ArrayList<>(Arrays.asList(GeoHash.getIntersectingGeohashes(geometry, Partitioner.SPATIAL_PRECISION)));
			hashLocations.retainAll(this.geohashIndex);
			logger.info("baseLocations: " + hashLocations);
			Query query = new Query();
			List<Expression> temporalExpressions = buildTemporalExpression(temporalProperties);
			Polygon polygon = GeoHash.buildAwtPolygon(geometry);
			for (String geohash : hashLocations) {
				Set<GeoHash> intersections = new HashSet<>();
				String pattern = "%" + (geohash.length() * GeoHash.BITS_PER_CHAR) + "s";
				String binaryHash = String.format(pattern, Long.toBinaryString(GeoHash.hashToLong(geohash)));
				GeoHash.getGeohashPrefixes(polygon, new GeoHash(binaryHash.replace(" ", "0")),
						this.geohashPrecision * GeoHash.BITS_PER_CHAR, intersections);
				logger.info("baseHash: " + geohash + ", intersections: " + intersections.size());
				for (GeoHash gh : intersections) {
					String[] hashRange = gh.getValues(this.geohashPrecision);
					if (hashRange != null) {
						Operation op = new Operation(temporalExpressions);
						if (hashRange.length == 1)
							op.addExpressions(
									new Expression(Operator.EQUAL, new Feature(SPATIAL_FEATURE, hashRange[0])));
						else {
							op.addExpressions(
									new Expression(Operator.GREATEREQUAL, new Feature(SPATIAL_FEATURE, hashRange[0])));
							op.addExpressions(
									new Expression(Operator.LESSEQUAL, new Feature(SPATIAL_FEATURE, hashRange[1])));
						}
						query.addOperation(op);
					}
				}
			}
			paths = executeParallelQuery(queryIntersection(query, metaQuery));
		} else if (temporalProperties != null) {
			List<Expression> temporalExpressions = buildTemporalExpression(temporalProperties);
			Query query = new Query(
					new Operation(temporalExpressions.toArray(new Expression[temporalExpressions.size()])));
			paths = metadataGraph.evaluateQuery(queryIntersection(query, metaQuery));
		} else */if (spatialProperties != null) {
			// THIS IS WHERE POLYGON QUERY ENTERS
			//logger.info("RIKI: PERFORMING SPATIAL QUERY");
			
			List<Expression> temporalExpression = null;
			if(temporalProperties != null) {
				temporalExpression = buildTemporalExpression(temporalProperties);
			}
			
			SpatialProperties sp = new SpatialProperties(new SpatialRange(spatialProperties));
			List<Coordinates> geometry = sp.getSpatialRange().hasPolygon() ? sp.getSpatialRange().getPolygon()
					: sp.getSpatialRange().getBounds();
			space = getSpatialString(sp);
			
			logger.info("RIKI: SPATIAL QUERY POLYGON: "+ geometry);
			
			List<String> hashLocations = new ArrayList<>(Arrays.asList(GeoHash.getIntersectingGeohashes(geometry, Partitioner.SPATIAL_PRECISION+2)));
			
			//List<String> hashLocations = new ArrayList<>(Arrays.asList(GeoHash.getIntersectingGeohashes(geometry, getGlobalGrid().getPrecision())));
			
			//logger.info("RIKI: HASHLOCATIONS: "+getGlobalGrid().getPrecision()+" "+hashLocations.size()+" "+hashLocations);
			
			
			// 9 geohash precision to find what geohash bozes intersect with the polygon
			if(Partitioner.SPATIAL_PRECISION < getGlobalGrid().getPrecision()) {
				List<String> finerGeohashes = new ArrayList<String>();
				
				for(String h: hashLocations) {
					List<String> tmp = GeoHash.getInternalGeohashes(h, getGlobalGrid().getPrecision());
					if(tmp != null && tmp.size() > 0)
						finerGeohashes.addAll(tmp);
				}
				
				hashLocations = finerGeohashes;
			}
			
			Query query = new Query();
//			Polygon polygon = GeoHash.buildAwtPolygon(geometry);
			//Eliminate all geohashes that don't intersect with hashGrid
			
			//HashGrid queryGrid = new HashGrid("wdw0x9", master.getGlobalGrid().getPrecision(), "wdw0x9bpbpb", "wdw0x9pbpbp");
			
			//HashGrid queryGrid = new HashGrid(StorageNode.baseHash, master.getGlobalGrid().getPrecision(), StorageNode.a1, StorageNode.a2);
			HashGrid queryGrid = new HashGrid(configs.getBaseHashForGrid(), getGlobalGrid().getPrecision(), configs.getNw(), configs.getNe(), 
					configs.getSe(), configs.getSw());
			
			//logger.info("RIKI: HERE X1");
			for (String ghash : hashLocations)
				try {
					queryGrid.addPoint(ghash);
				} catch (BitmapException e1) {
				}
			queryGrid.applyUpdates();
			
			//logger.info("RIKI: HERE X2");
			
			//logger.info("RIKI: MASTER PRECISION: "+ master.getGlobalGrid().getPrecision());
			//logger.info("RIKI: QUERY GRID BITMAP: "+ queryGrid.getBitmap());
			
			int [] intersections = getGlobalGrid().query(queryGrid.getBitmap());
			
			//logger.info("RIKI: HERE X3");
			
			//logger.info("RIKI: INTERSECTING GEOHASHES: "+intersections);
			hashLocations.clear();
			
			
			for (Integer i : intersections)
				hashLocations.add(getGlobalGrid().indexToGeoHash(i, configs.getHashGridPrecision()));
				
			
			//logger.info("RIKI: INTERSECTING GEOHASHES FOR GRID: "+hashLocations);
			//Once coverage is computed, query hashgrid to determine which plots are contained in polygon
			Set<Integer> overlappingPlots = new HashSet<>();
			for (String ghash : hashLocations) {
				Set<Integer> plots;
				try {
					plots = getGlobalGrid().locatePoint(ghash);
					overlappingPlots.addAll(plots);
				} catch (BitmapException e) {
					//If this error is caught, it means there is no data for given index
				}
			}
			
			logger.info("RIKI: OVERLAPPING PLOTS FOUND: "+ overlappingPlots);
			
			/* Operations: OR; Expressions: AND. So create your operations accordingly */
			
			for (int intersection : overlappingPlots) {//intersections are the 5-char intersections...
//				overlappingPlots.add(master.getGlobalGrid().locatePoint(intersection));
				Operation op = null;
				if(temporalExpression != null)
					op = new Operation(temporalExpression);
				else
					op = new Operation();
				
				op.addExpressions(new Expression(Operator.EQUAL, new Feature("plotID", intersection)));
				// EXTRA SENSORNAME QUERY ADDED FOR ARIZONA ONLY
				if(sensorName!=null && !sensorName.trim().isEmpty()) {
					op.addExpressions(new Expression(Operator.EQUAL, new Feature("sensorType", sensorName.trim())));
				}
				query.addOperation(op);
				
				
			}
			paths = executeParallelQuery(queryIntersection(query, metaQuery));
		} else {
			// non-chronal non-spatial
			paths = (metaQuery == null) ? metadataGraph.getAllPaths() : executeParallelQuery(metaQuery);
		}
		for (Path<Feature, String> path : paths) {
			String groupKey = group ? getGroupKey(path, space) : getSpaceKey(path);
			blocks = blockMap.get(groupKey);
			if (blocks == null) {
				blocks = new ArrayList<String>();
				blockMap.put(groupKey, blocks);
			}
			blocks.addAll(path.getPayload());
		}
		return blockMap;
	}

	private class Tracker {
		private int occurrence;
		private long fileSize;
		private long timestamp;

		public Tracker(long filesize, long millis) {
			this.occurrence = 1;
			this.fileSize = filesize;
			this.timestamp = millis;
		}

		public void incrementOccurrence() {
			this.occurrence++;
		}

		public int getOccurrence() {
			return this.occurrence;
		}

		public void incrementFilesize(long value) {
			this.fileSize += value;
		}

		public long getFilesize() {
			return this.fileSize;
		}

		public void updateTimestamp(long millis) {
			if (this.timestamp < millis)
				this.timestamp = millis;
		}

		public long getTimestamp() {
			return this.timestamp;
		}
	}

	public JSONArray getOverview() {
		JSONArray overviewJSON = new JSONArray();
		Map<String, Tracker> geohashMap = new HashMap<String, Tracker>();
		Calendar timestamp = Calendar.getInstance();
		timestamp.setTimeZone(TemporalHash.TIMEZONE);
		List<Path<Feature, String>> allPaths = metadataGraph.getAllPaths();
		logger.info("all paths size: " + allPaths.size());
		try {
			for (Path<Feature, String> path : allPaths) {
				long payloadSize = 0;
				if (path.hasPayload()) {
					for (String payload : path.getPayload()) {
						try {
							payloadSize += Files.size(java.nio.file.Paths.get(payload));
						} catch (IOException e) { /* e.printStackTrace(); */
							System.err.println("Exception occurred reading the block size. " + e.getMessage());
						}
					}
				}
				String geohash = path.get(4).getLabel().getString();
				String yearFeature = path.get(0).getLabel().getString();
				String monthFeature = path.get(1).getLabel().getString();
				String dayFeature = path.get(2).getLabel().getString();
				String hourFeature = path.get(3).getLabel().getString();
				if (yearFeature.charAt(0) == 'x') {
					System.err.println("Cannot build timestamp without year. Ignoring path");
					continue;
				}
				if (monthFeature.charAt(0) == 'x')
					monthFeature = "12";
				if (hourFeature.charAt(0) == 'x')
					hourFeature = "23";
				int year = Integer.parseInt(yearFeature);
				int month = Integer.parseInt(monthFeature) - 1;
				if (dayFeature.charAt(0) == 'x') {
					Calendar cal = Calendar.getInstance();
					cal.setTimeZone(TemporalHash.TIMEZONE);
					cal.set(Calendar.YEAR, year);
					cal.set(Calendar.MONTH, month);
					dayFeature = String.valueOf(cal.getActualMaximum(Calendar.DAY_OF_MONTH));
				}
				int day = Integer.parseInt(dayFeature);
				int hour = Integer.parseInt(hourFeature);
				timestamp.set(year, month, day, hour, 59, 59);

				Tracker geohashTracker = geohashMap.get(geohash);
				if (geohashTracker == null) {
					geohashMap.put(geohash, new Tracker(payloadSize, timestamp.getTimeInMillis()));
				} else {
					geohashTracker.incrementOccurrence();
					geohashTracker.incrementFilesize(payloadSize);
					geohashTracker.updateTimestamp(timestamp.getTimeInMillis());
				}
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "failed to process a path", e);
		}

		logger.info("geohash map size: " + geohashMap.size());
		for (String geohash : geohashMap.keySet()) {
			Tracker geohashTracker = geohashMap.get(geohash);
			JSONObject geohashJSON = new JSONObject();
			geohashJSON.put("region", geohash);
			List<Coordinates> boundingBox = GeoHash.decodeHash(geohash).getBounds();
			JSONArray bbJSON = new JSONArray();
			for (Coordinates coordinates : boundingBox) {
				JSONObject vertex = new JSONObject();
				vertex.put("lat", coordinates.getLatitude());
				vertex.put("lng", coordinates.getLongitude());
				bbJSON.put(vertex);
			}
			geohashJSON.put("spatialCoordinates", bbJSON);
			geohashJSON.put("blockCount", geohashTracker.getOccurrence());
			geohashJSON.put("fileSize", geohashTracker.getFilesize());
			geohashJSON.put("latestTimestamp", geohashTracker.getTimestamp());
			overviewJSON.put(geohashJSON);
		}
		return overviewJSON;
	}

//	public void updateMetadata(Metadata metadata, String blockPath, String metadataPath) throws IOException, SerializationException {
//		SerializationInputStream in = new SerializationInputStream(new FileInputStream(metadataPath));
//		Metadata existingMetadata = new Metadata(in);
//		logger.info("Received existing metadata: " + existingMetadata);
//	}
	/**
	 * Using the Feature attributes found in the provided Metadata, a path is
	 * created for insertion into the Metadata Graph.
	 */
	protected FeaturePath<String> createPath(String physicalPath, Metadata meta) {
		FeaturePath<String> path = new FeaturePath<String>(physicalPath, meta.getAttributes().toArray());
		return path;
	}

	@Override
	public void storeMetadata(Metadata metadata, String blockPath) throws FileSystemException, IOException {
		
		/* FeaturePath has a list of Vertices, each label in a vertex representing a feature */
		/* BlockPath is the actual path to the block */
		FeaturePath<String> path = createPath(blockPath, metadata);
		pathJournal.persistPath(path);
		storePath(path);
	}

	private void storePath(FeaturePath<String> path) throws FileSystemException {
		try {
			synchronized(metadataGraph) {
				metadataGraph.addPath(path);
			}
		} catch (Exception e) {
			throw new FileSystemException("Error storing metadata: " + e.getClass().getCanonicalName(), e);
		}
	}

	public MetadataGraph getMetadataGraph() {
		return metadataGraph;
	}

	public List<Path<Feature, String>> query(Query query) {
		return metadataGraph.evaluateQuery(query);
	}

	private List<String[]> getFeaturePaths(String blockPath) throws IOException {
		byte[] blockBytes = Files.readAllBytes(Paths.get(blockPath));
		String blockData = new String(blockBytes, "UTF-8");
		List<String[]> paths = new ArrayList<String[]>();
		String[] lines = blockData.split("\\r?\\n");
		int splitLimit = this.featureList.size();
		for (String line : lines)
			paths.add(line.split(",", splitLimit));
		return paths;
	}
	
	private boolean isGridInsidePolygon(GeoavailabilityGrid grid, GeoavailabilityQuery geoQuery) {
		Polygon polygon = new Polygon();
		for (Coordinates coords : geoQuery.getPolygon()) {
			Point<Integer> point = GeoHash.coordinatesToXY(coords);
			polygon.addPoint(point.X(), point.Y());
		}
		logger.info("checking geohash " + grid.getBaseHash() + " intersection with the polygon");
		SpatialRange hashRange = grid.getBaseRange();
		Pair<Coordinates, Coordinates> pair = hashRange.get2DCoordinates();
		Point<Integer> upperLeft = GeoHash.coordinatesToXY(pair.a);
		Point<Integer> lowerRight = GeoHash.coordinatesToXY(pair.b);
		if (polygon.contains(new Rectangle(upperLeft.X(), upperLeft.Y(), lowerRight.X() - upperLeft.X(),
				lowerRight.Y() - upperLeft.Y())))
			return true;
		return false;
	}

	private class ParallelQueryProcessor implements Runnable {
		private List<String[]> featurePaths;
		private Query query;
		private GeoavailabilityGrid grid;
		private Bitmap queryBitmap;
		private String storagePath;

		public ParallelQueryProcessor(List<String[]> featurePaths, Query query, GeoavailabilityGrid grid,
				Bitmap queryBitmap, String storagePath) {
			this.featurePaths = featurePaths;
			this.query = query;
			this.grid = grid;
			this.queryBitmap = queryBitmap;
			this.storagePath = storagePath + BLOCK_EXTENSION;
		}

		@Override
		public void run() {
			try {
				if (queryBitmap != null) {
					int latOrder = -1, lngOrder = -1, index = 0;
					for (Pair<String, FeatureType> columnPair : GeospatialFileSystem.this.featureList) {
						if (columnPair.a.equalsIgnoreCase(GeospatialFileSystem.this.spatialHint.getLatitudeHint()))
							latOrder = index++;
						else if (columnPair.a
								.equalsIgnoreCase(GeospatialFileSystem.this.spatialHint.getLongitudeHint()))
							lngOrder = index++;
						else
							index++;
					}

					GeoavailabilityMap<String[]> geoMap = new GeoavailabilityMap<String[]>(grid);
					Iterator<String[]> pathIterator = this.featurePaths.iterator();
					while (pathIterator.hasNext()) {
						String[] features = pathIterator.next();
						float lat = Math.getFloat(features[latOrder]);
						float lon = Math.getFloat(features[lngOrder]);
						if (!Float.isNaN(lat) && !Float.isNaN(lon))
							geoMap.addPoint(new Coordinates(lat, lon), features);
						pathIterator.remove();
					}
					for (List<String[]> paths : geoMap.query(queryBitmap).values())
						this.featurePaths.addAll(paths);
				}
				if (query != null && this.featurePaths.size() > 0) {
					MetadataGraph temporaryGraph = new MetadataGraph();
					Iterator<String[]> pathIterator = this.featurePaths.iterator();
					while (pathIterator.hasNext()) {
						String[] features = pathIterator.next();
						try {
							Metadata metadata = new Metadata();
							FeatureSet featureset = new FeatureSet();
							for (int i = 0; i < features.length; i++) {
								Pair<String, FeatureType> pair = GeospatialFileSystem.this.featureList.get(i);
								if (pair.b == FeatureType.FLOAT)
									featureset.put(new Feature(pair.a, Math.getFloat(features[i])));
								if (pair.b == FeatureType.INT)
									featureset.put(new Feature(pair.a, Math.getInteger(features[i])));
								if (pair.b == FeatureType.LONG)
									featureset.put(new Feature(pair.a, Math.getLong(features[i])));
								if (pair.b == FeatureType.DOUBLE)
									featureset.put(new Feature(pair.a, Math.getDouble(features[i])));
								if (pair.b == FeatureType.STRING)
									featureset.put(new Feature(pair.a, features[i]));
							}
							metadata.setAttributes(featureset);
							Path<Feature, String> featurePath = createPath("/nopath", metadata);
							temporaryGraph.addPath(featurePath);
						} catch (Exception e) {
							logger.warning(e.getMessage());
						}
						pathIterator.remove();
					}
					List<Path<Feature, String>> evaluatedPaths = temporaryGraph.evaluateQuery(query);
					for (Path<Feature, String> path : evaluatedPaths) {
						String[] featureValues = new String[path.size()];
						int index = 0;
						for (Feature feature : path.getLabels())
							featureValues[index++] = feature.getString();
						this.featurePaths.add(featureValues);
					}
				}

				if (featurePaths.size() > 0) {
					try (FileOutputStream fos = new FileOutputStream(this.storagePath)) {
						Iterator<String[]> pathIterator = featurePaths.iterator();
						while (pathIterator.hasNext()) {
							String[] path = pathIterator.next();
							StringBuffer pathSB = new StringBuffer();
							for (int j = 0; j < path.length; j++) {
								pathSB.append(path[j]);
								if (j + 1 != path.length)
									pathSB.append(",");
							}
							fos.write(pathSB.toString().getBytes("UTF-8"));
							pathIterator.remove();
							if (pathIterator.hasNext())
								fos.write("\n".getBytes("UTF-8"));
						}
					}
				} else {
					this.storagePath = null;
				}
			} catch (IOException | BitmapException e) {
				logger.log(Level.SEVERE, "Something went wrong while querying the filesystem.", e);
				this.storagePath = null;
			}
		}

		public String getStoragePath() {
			return this.storagePath;
		}

	}

	public List<String> query(String blockPath, GeoavailabilityQuery geoQuery, GeoavailabilityGrid grid,
			Bitmap queryBitmap, String pathPrefix) throws IOException, InterruptedException {
		List<String> resultFiles = new ArrayList<>();
		List<String[]> featurePaths = null;
		boolean skipGridProcessing = false;
		if (geoQuery.getPolygon() != null && geoQuery.getQuery() != null) {
			skipGridProcessing = isGridInsidePolygon(grid, geoQuery);
			featurePaths = getFeaturePaths(blockPath);
		} else if (geoQuery.getPolygon() != null) {
			skipGridProcessing = isGridInsidePolygon(grid, geoQuery);
			if (!skipGridProcessing)
				featurePaths = getFeaturePaths(blockPath);
		} else if (geoQuery.getQuery() != null) {
			featurePaths = getFeaturePaths(blockPath);
		} else {
			resultFiles.add(blockPath);
			return resultFiles;
		}

		if (featurePaths == null) {
			resultFiles.add(blockPath);
			return resultFiles;
		}

		queryBitmap = skipGridProcessing ? null : queryBitmap;
		int size = featurePaths.size();
		int partition = java.lang.Math.max(size / numCores, MIN_GRID_POINTS);
		int parallelism = java.lang.Math.min(size / partition, numCores);
		if (parallelism > 1) {
			ExecutorService executor = Executors.newFixedThreadPool(parallelism);
			List<ParallelQueryProcessor> queryProcessors = new ArrayList<>();
			for (int i = 0; i < parallelism; i++) {
				int from = i * partition;
				int to = (i + 1 != parallelism) ? (i + 1) * partition : size;
				List<String[]> subset = new ArrayList<>(featurePaths.subList(from, to));
				ParallelQueryProcessor pqp = new ParallelQueryProcessor(subset, geoQuery.getQuery(), grid, queryBitmap,
						pathPrefix + "-" + i);
				queryProcessors.add(pqp);
				executor.execute(pqp);
			}
			featurePaths.clear();
			executor.shutdown();
			executor.awaitTermination(10, TimeUnit.MINUTES);
			for (ParallelQueryProcessor pqp : queryProcessors)
				if (pqp.getStoragePath() != null)
					resultFiles.add(pqp.getStoragePath());
		} else {
			ParallelQueryProcessor pqp = new ParallelQueryProcessor(featurePaths, geoQuery.getQuery(), grid,
					queryBitmap, pathPrefix);
			pqp.run(); // to avoid another thread creation
			if (pqp.getStoragePath() != null)
				resultFiles.add(pqp.getStoragePath());
		}

		return resultFiles;
	}

	public JSONArray getFeaturesJSON() {
		return metadataGraph.getFeaturesJSON();
	}

	@Override
	public void shutdown() {
		logger.info("FileSystem shutting down");
		try {
			pathJournal.shutdown();
		} catch (Exception e) {
			/* Everything is going down here, just print out the error */
			e.printStackTrace();
		}
	}

	public int getDataLatIndex(String fileSensorType) {
		return configs.getLatIndex(fileSensorType);
	}
	
	public int getDataTemporalIndex(String fileSensorType) {
		return configs.getTemporalIndex(fileSensorType);
	}
	
	public int getDataReadingIndex(String fileSensorType) {
		return configs.getDataIndex(fileSensorType);
	}

	public boolean isTimeTimestamp() {
		return configs.isTimeTimestamp();
	}
	
	public String getDateFormat() {
		return configs.getDateFormat();
	}

	
	public SummaryStatistics getStatistics(String key) {
		
		return filePathToSummaryMap.get(key);
		
		
	}
	
	public Map<String, SummaryStatistics> getfilePathToSummaryMap() {
		return filePathToSummaryMap;
	}


	public void putSummaryData(SummaryStatistics merged, String absolutePath) {
		filePathToSummaryMap.put(absolutePath, merged);
		
	}
	
	public String getSummaryDataString(String absolutePath) {
		SummaryStatistics summaryStatistics = filePathToSummaryMap.get(absolutePath);
		if(summaryStatistics!= null)
			return summaryStatistics.toString();
		else 
			return "";
	}

	public SummaryStatistics getSummaryData(String absolutePath) {
		SummaryStatistics summaryStatistics = filePathToSummaryMap.get(absolutePath);
		if(summaryStatistics!= null)
			return summaryStatistics;
		else 
			return null;
	}

	/**
	 * GIVEN A GALILEO PATH, FIND THE PATH OF THE SAME FILE IN THE IRODS SYSTEM
	 * @author sapmitra
	 * @param block
	 * @return
	 */
	public String getIrodsPath(String block) {
		
		String storageDir = storageDirectory + "";
		int ln = storageDir.length();
		
		String newPath = DataStoreHandler.IRODS_BASE_PATH + block.substring(ln);
		
		return newPath;
	}


	public FilesystemConfig getConfigs() {
		return configs;
	}


	public void setConfigs(FilesystemConfig configs) {
		this.configs = configs;
	}


	public HashGrid getGlobalGrid() {
		return globalGrid;
	}


	public void setGlobalGrid(HashGrid globalGrid) {
		this.globalGrid = globalGrid;
	}

	public int getDataLonIndex(String fileSensorType) {
		return configs.getLonIndex(fileSensorType);
	}

	public RadixIntegrityGraph getRig() {
		return rig;
	}

	public void setRig(RadixIntegrityGraph rig) {
		this.rig = rig;
	}

	public void addIRODSPendingPath(String filePath) {
		rig.addPath(filePath);
		
	}

	public void updateRIG() {
		rig.updatePathsIntoRIG();
		
	}

	
}