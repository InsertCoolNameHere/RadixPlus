package galileo.integrity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import galileo.dataset.Metadata;
import galileo.dataset.feature.Feature;
import galileo.dataset.feature.FeatureSet;
import galileo.dataset.feature.FeatureType;
import galileo.graph.Path;
import galileo.query.Query;
import galileo.util.Math;
import galileo.util.Pair;

// EACH FS HAS ITS OWN R.I.G.
public class RadixIntegrityGraph {

	public List<String[]> pendingPaths;
	
	// A GRAPH THAT CAN SERVE BOTH AS A METADATA GRAPH AS WELL AS AN INTEGRITY GRAPH
	public HierarchicalRadixGraph<String> hrig;
	
	private static final Logger logger = Logger.getLogger("galileo");
	
	public synchronized void updatePathsIntoRIG() {
		
		if (this.pendingPaths.size() > 0) {
			Iterator<String[]> pathIterator = this.pendingPaths.iterator();
			
			while (pathIterator.hasNext()) {
				
				String[] features = pathIterator.next();
				String pl = features[features.length - 1];
				
				String tokens[] = pl.split("\\$\\$");
				
				// EXTRACTING THE CHECKSUM VALUE
				long hashValue = Long.valueOf(tokens[2]);
				
				String irodsPath = tokens[1];
				
				// PUTTING ONLY THE IRODS PATH, WHICH IS THE ONLY RELEVANT DATA INTO THE FEATURES ARRAY
				features[features.length - 1] = irodsPath;
				
				try {
					Metadata metadata = new Metadata();
					FeatureSet featureset = new FeatureSet();
					
					for (int i = 0; i < features.length; i++) {
						Pair<String, FeatureType> pair = this.featureList.get(i);
						
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
					
					// CREATING A PATH OUT OF THE STRING ARRAY
					// THE ATTRIBUTES ARE ADDED AS VERTICES,
					// THE PATH AND HASHVALUE GET ADDED AS HASH AND PAYLOAD ATTRIBUTE TO THE PATH
					RIGFeaturePath<String> featurePath = createPath(irodsPath, metadata, hashValue);
					
					// APPENDING THE PATH TO THE TREE
					hrig.addPath(featurePath);
				} catch (Exception e) {
					logger.warning(e.getMessage());
				}
				pathIterator.remove();
				
				
				// ALSO ADD THIS PATH TO THE R.I.G.
			}
			
		}
	}
	
	
	
	// LIST OF PRE-DEFINED GRAPH'S NODENAMES AND TYPES
	private List<Pair<String, FeatureType>> featureList;
	
	public void addToPendingPath(String[] pathTokens) {
		if(pendingPaths == null) {
			pendingPaths = new ArrayList<String[]>();
		}
		
		pendingPaths.add(pathTokens);
	}
	
	public RadixIntegrityGraph() {}
	
	
	public RadixIntegrityGraph(String featureList) {
		
		if (featureList != null) {
			this.featureList = new ArrayList<>();
			for (String nameType : featureList.split(",")) {
				String[] pair = nameType.split(":");
				
				this.featureList.add(new Pair<String, FeatureType>(pair[0], FeatureType.fromInt(Integer.parseInt(pair[1]))));
			}
			/* Cannot modify featurelist anymore */
			this.featureList = Collections.unmodifiableList(this.featureList);
		}
	}
	
	public synchronized void clearPendingPaths() {
		pendingPaths.clear();
	}
	
	public static void main(String[] args) {
		

	}
	
	public List<String[]> evaluateQuery(Query query) {
		List<String[]> featurePaths = new ArrayList<String[]>();
		List<Path<Feature, String>> evaluatedPaths = hrig.evaluateQuery(query);
		for (Path<Feature, String> path : evaluatedPaths) {
			String[] featureValues = new String[path.size()];
			int index = 0;
			for (Feature feature : path.getLabels())
				featureValues[index++] = feature.getString();
			featurePaths.add(featureValues);
		}
		
		return featurePaths;
	
	}
	
	
	protected RIGFeaturePath<String> createPath(String physicalPath, Metadata meta, long hashValue) {
		RIGFeaturePath<String> path = new RIGFeaturePath<String>(physicalPath, hashValue, meta.getAttributes().toArray());
		return path;
	}
	

}
