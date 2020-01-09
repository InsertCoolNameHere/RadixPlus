package galileo.integrity;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.zip.Adler32;

import galileo.dataset.Metadata;
import galileo.dataset.feature.Feature;
import galileo.dataset.feature.FeatureSet;
import galileo.dataset.feature.FeatureType;
import galileo.fs.GeospatialFileSystem;
import galileo.graph.MetadataGraph;
import galileo.graph.Path;
import galileo.query.Query;
import galileo.util.Math;
import galileo.util.Pair;

// EACH FS HAS ITS OWN R.I.G.
public class RadixIntegrityGraph {
	
	public RadixIntegrityGraph() {}
	
	public static String ROOT_LABEL = "X_root_X";
	public String rootPath = "";
	
	// LIST OF PRE-DEFINED GRAPH'S NODENAMES AND TYPES
	private List<Pair<String, FeatureType>> featureList;
	
	// THE SEPARATOR FOR IRODS PATHS
	public static String rigPathSeparator = "/";
	
	public List<String[]> pendingPaths;
	
	// A GRAPH THAT CAN SERVE BOTH AS A METADATA GRAPH AS WELL AS AN INTEGRITY GRAPH
	public HierarchicalRadixGraph<String> hrig;
	
	private static final Logger logger = Logger.getLogger("galileo");
	
	public void addPath(String irodsPath) {
		
		//logger.info("RIKI: HHH "+irodsPath+" "+ rootPath);
		if(irodsPath.startsWith(rootPath)) {
			String tmp = irodsPath.substring(rootPath.length());
			addToPendingPath(tmp.split(rigPathSeparator));
		} else {
			logger.severe("RIKI: PATH FORMAT MISMATCH");
		}
	}
	
	// INITIALIZING THE HIERARCHICAL GRAPH WITH PRESET FEATURES
	public RadixIntegrityGraph(String featureList, String rootPath, String fsName) {
		
		pendingPaths = new ArrayList<String[]>();
		
		this.rootPath = rootPath+rigPathSeparator+fsName+rigPathSeparator;
		
		List<String> featureNames = null;
		if (featureList != null) {
			
			this.featureList = new ArrayList<>();
			featureNames = new ArrayList<String>();
			
			for (String nameType : featureList.split(",")) {
				String[] pair = nameType.split(":");
				featureNames.add(pair[0]);
				this.featureList.add(new Pair<String, FeatureType>(pair[0], FeatureType.fromInt(Integer.parseInt(pair[1]))));
			}
			/* Cannot modify featurelist anymore */
			this.featureList = Collections.unmodifiableList(this.featureList);
			
		}
		
		hrig = new HierarchicalRadixGraph<>(this.featureList);
		hrig.getRoot().path = this.rootPath;
		hrig.getRoot().setLabel(new Feature(ROOT_LABEL,ROOT_LABEL));
		
		
	}
	
	
	public RadixIntegrityGraph(List<Pair<String, FeatureType>> featureList, String rootPath, String fsName) {
		
		pendingPaths = new ArrayList<String[]>();
		
		//this.rootPath = rootPath+rigPathSeparator+fsName+rigPathSeparator;
		this.rootPath = rootPath+rigPathSeparator;
		
		if (featureList != null) {
			this.featureList = Collections.unmodifiableList(featureList);
		}
		
		hrig = new HierarchicalRadixGraph<>(this.featureList);
		hrig.getRoot().path = this.rootPath;
		hrig.getRoot().setLabel(new Feature(ROOT_LABEL,ROOT_LABEL));
		
		
	}
	
	
	// ADD RECORDS OF INSERTED IRODS FILES INTO THE R.I.G.
	public synchronized void updatePathsIntoRIG() {
		
		// A MAP TO WHICH NODES AT EACH LEVEL NEEDS UPDATE
		List<Set<String>> levelToLabelMap = new ArrayList<Set<String>>();
		
		
		for(int i=0; i <= this.featureList.size(); i++) {
			levelToLabelMap.add(i, new TreeSet<String>());
		}
		levelToLabelMap.get(0).add(ROOT_LABEL);
		
		int height = 0;
		
		if (this.pendingPaths.size() > 0) {
			Iterator<String[]> pathIterator = this.pendingPaths.iterator();
			
			int cnt = 0;
			
			while (pathIterator.hasNext()) {
				
				String[] features = pathIterator.next();
				String pl = features[features.length - 1];
				
				String tokens[] = pl.split("\\$\\$");
				
				if(cnt == 0) {
					height = features.length;
					cnt++;
				}
				
				// EXTRACTING THE CHECKSUM VALUE
				long hashValue = Long.valueOf(tokens[1]);
				
				// PUTTING ONLY THE IRODS PATH, WHICH IS THE ONLY RELEVANT DATA INTO THE FEATURES ARRAY
				features[features.length - 1] = tokens[0];
				
				String irodsPath = "";
				for(String s: features)
					irodsPath += s+rigPathSeparator;
				irodsPath = irodsPath.substring(0, irodsPath.length()-1);
				irodsPath = rootPath+irodsPath;
				
				try {
					Metadata metadata = new Metadata();
					FeatureSet featureset = new FeatureSet();
					
					for (int i = 0; i < features.length-1; i++) {
						Pair<String, FeatureType> pair = this.featureList.get(i);
						
						if (pair.b == FeatureType.FLOAT)
							featureset.put(new Feature(pair.a, Math.getFloat(features[i])));
						else if (pair.b == FeatureType.INT)
							featureset.put(new Feature(pair.a, Math.getInteger(features[i])));
						else if (pair.b == FeatureType.LONG)
							featureset.put(new Feature(pair.a, Math.getLong(features[i])));
						else if (pair.b == FeatureType.DOUBLE)
							featureset.put(new Feature(pair.a, Math.getDouble(features[i])));
						else if (pair.b == FeatureType.STRING)
							featureset.put(new Feature(pair.a, features[i]));
					}
					metadata.setAttributes(featureset);
					
					// CREATING A PATH OUT OF THE STRING ARRAY
					// THE ATTRIBUTES ARE ADDED AS VERTICES,
					// THE PATH AND HASHVALUE GET ADDED AS HASH AND PAYLOAD ATTRIBUTE TO THE PATH
					RIGFeaturePath<String> featurePath = createRIGPath(irodsPath, metadata, hashValue, levelToLabelMap);
					
					// APPENDING THE PATH TO THE TREE
					hrig.addPathToRIG(featurePath);
					
				} catch (Exception e) {
					logger.warning(e.getMessage());
				}
				pathIterator.remove();
				
			}
			
			// ALSO UPDATE THIS PATH ON THE R.I.G.
			hrig.updateHashes(height, levelToLabelMap);
			
		}
	}
	
	
	public void addToPendingPath(String[] pathTokens) {
		if(pendingPaths == null) {
			pendingPaths = new ArrayList<String[]>();
		}
		
		pendingPaths.add(pathTokens);
	}
	
	
	public synchronized void clearPendingPaths() {
		pendingPaths.clear();
	}
	
	public static void main(String[] args) {
		

	}
	
	/**
	 * QUERY BLOCKS/DIRECTORIES IN IRODS THAT MATCH
	 * @author sapmitra
	 * @param query
	 * @return
	 */
	public List<String> evaluateQuery(Query query) {
		List<String[]> featurePaths = new ArrayList<String[]>();
		
		// RETURN DIRECTORY/FILEPATHS....IF IT IS A DIRECTORY PATH, CALCULATE THE HASH OF THE DIRECTORY AFTER DOWNLOAD
		List<RIGPath<Feature, String>> evaluatedPaths = hrig.evaluateQuery(query);
		
		for (RIGPath<Feature, String> path : evaluatedPaths) {
			String[] featureValues = new String[path.size()+1];
			int index = 0;
			for (Feature feature : path.getLabels())
				featureValues[index++] = feature.getString();
			featureValues[index] = getHashFromPayload(path.payload.iterator().next());
			featurePaths.add(featureValues);
		}
		
		List<String> rigpaths = new ArrayList<String>();
		
		for(String[] ss : featurePaths) {
			String p = "";
			int k = 0;
			for(String s: ss) {
				
				if("X_root_X".equals(s)) {
					p+=rootPath;
				} else if(k == 0|| k == ss.length-1) {
					p+=s;
				} else if(k == ss.length-2) {
					p+=s+"$$";
				} else
					p+=s+"/";
				k++;
			}
			rigpaths.add(p);
		}
		
		
		return rigpaths;
	
	}
	
	public static String getHashFromPayload(String ph) {
		String tokens[] = ph.split("\\$\\$");
		return tokens[1];
	}
	
	
	/**
	 * GIVEN A FILE, GET ITS CHECKSUM
	 * @param filePath
	 * @return
	 * @throws IOException
	 */
	public static long getChecksumFromFilepath(String filePath) throws IOException {
		
		Adler32 crc = new Adler32();
		
		byte[] bytesF = Files.readAllBytes(Paths.get(filePath));
		
		crc.update(bytesF);
		
		return crc.getValue();
	}
	
	/**
	 * GIVEN A STRING, GET ITS CHECKSUM
	 * @param fileData
	 * @return
	 * @throws IOException
	 */
	
	public static long getChecksumFromData(String fileData) throws IOException {
		
		Adler32 crc = new Adler32();
		
		byte[] bytesF = fileData.getBytes();
		
		crc.update(bytesF);
		
		return crc.getValue();
	}
	
	/**
	 * POPULATE THE FEATURE PATH WITH FEATURE LABELS
	 * SET PAYLOAD AT EACH NODE IS INSIDE "VALUES", WHICH REPRESENTS THE DIRECTORY PATH IT REPRESENTS
	 * SET HASH VALUES AT THE LEAVES ONLY
	 * MARK THE LEVEL_TO_LABEL MAP FOR FUTURE UPDATES
	 * @author sapmitra
	 * @param physicalPath
	 * @param meta
	 * @param hashValue
	 * @param levelToLabelMap
	 * @return
	 */
	protected RIGFeaturePath<String> createRIGPath(String physicalPath, Metadata meta, long hashValue, List<Set<String>> levelToLabelMap) {
		
		RIGFeaturePath<String> path = new RIGFeaturePath<String>(physicalPath, hashValue, meta.getAttributes().toArray());
		
		String pathDup = physicalPath;
		
		// FINDING AND SETTING THE PAYLOAD FOR EACH NODE
		for(int i = path.getVertices().size()-1; i >=0; i--) {
			RIGVertex<Feature, String> rv = path.getVertices().get(i);
			
			// ADDING THE DIRECTORY/FILE PATH TO THIS VERTEX
			rv.path = pathDup;
			
			// Marking the nodes that have been changes in the tree
			/*if(levelToLabelMap.get(i) == null) {
				levelToLabelMap.add(i, new TreeSet<String>());
			}*/
			
			// MARKING THE LABELS AT A GIVEN LABELS THAT HAVE BEEN ALTERED AND HENCE NEED RECOMPUTATION
			// THE LEVEL AT THE LEAF DOES NOT NEED ANY UPDATING, ITS HASHVALUE IS ALREADY SET AND UPDATED
			if(i != path.getVertices().size()-1)
			levelToLabelMap.get(i+1).add(rv.label.dataToString());
			
			// ADDING THE HASHVALUE TO THE LEAF VERTEX ONLY
			if(i == path.getVertices().size()-1) {
				
				List<Long> sign = new ArrayList<Long>();
				sign.add(hashValue);
				List<String> ps = new ArrayList<String>();
				ps.add(pathDup);
				
				MerkleTree mt = new MerkleTree(sign, ps);
				rv.setMerkleTree(mt);
				
				//rv.path = pathDup;
				rv.hashValue = hashValue;
				
				int indx = pathDup.lastIndexOf(rigPathSeparator);
				pathDup = pathDup.substring(0,indx);
			}
			
			int indx = pathDup.lastIndexOf(rigPathSeparator);
			pathDup = pathDup.substring(0,indx);
		}
		//path.get(index)
		return path;
	}
	

}
