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
package galileo.integrity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import galileo.dataset.feature.Feature;
import galileo.dataset.feature.FeatureType;
import galileo.query.Expression;
import galileo.query.Operation;
import galileo.query.PayloadFilter;
import galileo.query.Query;
import galileo.util.Pair;
import galileo.graph.FeatureHierarchy;
import galileo.graph.FeatureTypeMismatchException;
import galileo.graph.GraphException;
import galileo.graph.Path;
import galileo.graph.Vertex;

/**
 * A type-aware hierarchical graph implementation with each type occupying a
 * level in the hierarchy.
 *
 * @author malensek
 */
public class HierarchicalRadixGraph<T> {

    private static final Logger logger = Logger.getLogger("galileo");

    /** The root vertex. */
    private RIGVertex<Feature, T> root = new RIGVertex<>();

    /** Describes each level in the hierarchy. */
    private Map<String, Level> levels = new HashMap<>();

    /**
     * We maintain a separate Queue with Feature names inserted in
     * hierarchical order.  While levels.keySet() contains the same information,
     * there is no contractual obligation for HashMap to return the keyset in
     * the original insertion order (although in practice, it probably does).
     */
//    private Queue<String> features = new LinkedList<>();
    private Queue<String> features = new ConcurrentLinkedQueue<>();

    /**
     * Tracks information about each level in the graph hierarchy.
     */
    private class Level {

        public Level(int order, FeatureType type) {
            this.order = order;
            this.type = type;
        }

        public int order;
        public FeatureType type;
    }

    public HierarchicalRadixGraph() { }

    /**
     * Creates a HierarchicalGraph with a set Feature hierarchy.  Features are
     * entered into the hierarchy in the order they are received.
     *
     * @param hierarchy Graph hierarchy represented as a
     * {@link FeatureHierarchy}.
     */
    public HierarchicalRadixGraph(FeatureHierarchy hierarchy) {
        for (Pair<String, FeatureType> feature : hierarchy) {
            getOrder(feature.a, feature.b);
        }
    }
    
    public HierarchicalRadixGraph(List<Pair<String, FeatureType>> hierarchy) {
    	
    	if(hierarchy == null)
    		return;
        for (Pair<String, FeatureType> feature : hierarchy) {
            getOrder(feature.a, feature.b);
        }
    }

    public List<RIGPath<Feature, T>> evaluateQuery(Query query) {
    	
        List<RIGPath<Feature, T>> paths = null;
        
        // OPERATIONS ARE OR IN A QUERY
        // EXECUTING OPERATIONS INDIVIDUALLY
        for (Operation operation : query.getOperations()) {
        	
        	String lastFeature = getLastFeature(operation);
        	
            RIGQueryTracker<T> tracker = new RIGQueryTracker<>(root, features.size());
            
            // ACTUAL QUERY EVALUATION
            evaluateOperation(operation, tracker, lastFeature);
            
            List<RIGPath<Feature, T>> opResult = null;

            opResult = tracker.getQueryResults();
            
            if (paths == null) {
                paths = opResult;
            } else {
                paths.addAll(opResult);
            }
        }

        /*for (RIGPath<Feature, T> path : paths) {
            removeNullFeatures(path);
        }*/
        return paths;
    }
    
    /**
	 * @param operation
	 * @return
	 * RETURNS THE LAST FEATURE IN THE HIERARCHY IN THIS OPERATION
	 */
	private String getLastFeature(Operation operation) {
		String lastOne = null;
		
		for (String feature : features) {
			if(operation.getOperand(feature) != null) {
				lastOne = feature;
			}
		}
				
		return lastOne;
	}

	public JSONArray getFeaturesJSON(){
    	Set<Entry<String, Level>> entries = levels.entrySet();
    	JSONArray features = new JSONArray();
    	for(Entry<String, Level> e : entries){
    		JSONObject feature = new JSONObject();
    		feature.put("name", e.getKey());
    		feature.put("type", e.getValue().type.name());
    		feature.put("order", e.getValue().order);
    		features.put(feature);
    	}
    	return features;
    }

    // OPERATIONS ARE OR
    public void evaluateOperation(Operation operation, RIGQueryTracker<T> tracker, String lastFeature) {
    	synchronized(features) {
    		
    		/*tracker.nextLevel();
    		for (RIGPath<Feature, T> path : tracker.getCurrentResults()) {
            	// THE LEAFT VERTEX OF THE CURRENT PATH
                RIGVertex<Feature, T> vertex = path.getTail();
                
                tracker.addResults(path, vertex.getAllNeighbors());
            }*/
    		
	        for (String feature : features) {
	        	
	        	// INCREASING THE LEVEL
	            tracker.nextLevel();
	            
	            boolean lastTime = false;
	            
	            if(feature.equals(lastFeature) /*&& tracker.getCurrentLevel() < tracker.results.size()*/) {
	            	// TIME TO RETURN WHOLE DIRECTORY INSTEAD OF BLOCKS
	            	lastTime = true;
	            }
	            
	            // EACH LEVEL HOLDS THE PATHS COVERED AT THAT ITERATION FOR THAT FEATURE LEVEL. THE PATHS FROM THE 
	            // LAST LEVEL ARE EXPANDED ON AND THE NEXT SET OF RESULTS ARE SET ON THE NEXT LEVEL
	            /* Find all expressions related to the current Feature (operand) */
	            List<Expression> expressions = operation.getOperand(feature);
	            //System.out.println(tracker.getCurrentResults().size() +"  "+feature);
	            
	            if (expressions == null) {
	                /* No expressions deal with the current feature.  Traverse all
	                 * neighbors. */
	                for (RIGPath<Feature, T> path : tracker.getCurrentResults()) {
	                	// THE LEAFT VERTEX OF THE CURRENT PATH
	                    RIGVertex<Feature, T> vertex = path.getTail();
	                    
	                    tracker.addResults(path, vertex.getAllNeighbors());
	                }
	            } else {
	                /* Note that we are evaluating an Expression at this level */
	                tracker.markEvaluated();
	
	                
	                for (RIGPath<Feature, T> path : tracker.getCurrentResults()) {
	                    RIGVertex<Feature, T> vertex = path.getTail();
	                    
	                    Collection<RIGVertex<Feature, T>> resultCollection = evaluateExpressions(expressions, vertex);
	                    
	                    if(!lastTime)
	                    	tracker.addResults(path, resultCollection);
	                    else {
	                    	tracker.addLastResults(path, resultCollection);
	                    	//return;
	                    }
	                }
	            }
	            
	        }
    	}
    }

    /**
     * Evaluate query {@link Expression}s at a particular vertex.  Neighboring
     * vertices that match the Expression will be traversed further.
     *
     * @param expressions List of {@link Expression}s that should be evaluated
     * against neighboring nodes
     * @param vertex {@link Vertex} to apply the Expression to.
     *
     * @return a collection of matching vertices.
     */
    private Collection<RIGVertex<Feature, T>> evaluateExpressions(
            List<Expression> expressions, RIGVertex<Feature, T> vertex) {

        Set<RIGVertex<Feature, T>> resultSet = null;

        for (Expression expression : expressions) {
            Set<RIGVertex<Feature, T>> evalSet = new HashSet<>();
            Feature value = expression.getValue();

            switch (expression.getOperator()) {
                case EQUAL: {
                    /* Select a particular neighboring vertex */
                    RIGVertex<Feature, T> equalTo = vertex.getNeighbor(value);

                    if (equalTo == null) {
                        /* There was no Vertex that matched the value given. */
                        break;
                    }

                    evalSet.add(equalTo);
                    break;
                }

                case NOTEQUAL: {
                    /* Add all the neighboring vertices, and then remove the
                     * particular value specified. */
                    evalSet.addAll(vertex.getAllNeighbors());
                    evalSet.remove(vertex.getNeighbor(value));

                    break;
                }

                case LESS: {
                    NavigableMap<Feature, RIGVertex<Feature, T>> neighbors
                        = vertex.getNeighborsLessThan(value, false);
                    removeWildcard(neighbors);
                    evalSet.addAll(neighbors.values());

                    break;
                }

                case LESSEQUAL: {
                    NavigableMap<Feature, RIGVertex<Feature, T>> neighbors
                        = vertex.getNeighborsLessThan(value, true);
                    removeWildcard(neighbors);
                    evalSet.addAll(neighbors.values());

                    break;
                }

                case GREATER: {
                    evalSet.addAll(
                            vertex.getNeighborsGreaterThan(value, false)
                            .values());

                    break;
                }

                case GREATEREQUAL: {
                    evalSet.addAll(
                            vertex.getNeighborsGreaterThan(value, true)
                            .values());

                    break;
                }

                case UNKNOWN:
                default:
                    logger.log(java.util.logging.Level.WARNING,
                            "Invalid operator ({0}) in expression: {1}",
                            new Object[] {
                                expression.getOperator(),
                                expression.toString()} );
            }

            if (resultSet == null) {
                /* If this is the first Expression we've evaluated, then the
                 * evaluation set becomes our result set that will be further
                 * reduced as more Expressions are evaluated. */
                resultSet = evalSet;
            } else {
                /* Remove all items from the result set that are not present in
                 * this current evaluation set.  This effectively drills down
                 * through the results until we have our final query answer. */
                resultSet.retainAll(evalSet);
            }
        }

        return resultSet;
    }

    /**
     * When a path does not contain a particular Feature, we use a null feature
     * (FeatureType.NULL) to act as a "wildcard" in the graph so that the path
     * stays linked together. The side effect of this is that 'less than'
     * comparisons may return wildcards, which are removed with this method.
     *
     * @param map The map to remove the first NULL element from. If the map has
     * no elements or the first element is not a NULL FeatureType, then no
     * modifications are made to the map.
     */
    private void removeWildcard(NavigableMap<Feature, RIGVertex<Feature, T>> map) {
        if (map.size() <= 0) {
            return;
        }

        Feature first = map.firstKey();
        if (first.getType() == FeatureType.NULL) {
            map.remove(first);
        }
    }

    /**
     * Adds a new {@link Path} to the Hierarchical Graph.
     * Adds the hash value to the leaf of the path.
     * @param hashValue the checksum of the leaf for this path
     */
    public void addPathToRIG(RIGPath<Feature, T> path) throws FeatureTypeMismatchException, GraphException {
    	
        if (path.size() == 0) {
            throw new GraphException("Attempted to add empty path!");
        }


        /* Place the path payload (traversal result) at the end of this path. */
        // ALREADY HANDLED IN THE PATH CREATION
        //path.get(path.size() - 1).addValues(path.getPayload());

        root.addPath(path.iterator());
    }

    /**
     * Removes all null Features from a path.  This includes any Features that
     * are the standard Java null, or Features with a NULL FeatureType.
     *
     * @param path Path to remove null Features from.
     */
    private void removeNullFeatures(RIGPath<Feature, T> path) {
        Iterator<RIGVertex<Feature, T>> it = path.iterator();
        while (it.hasNext()) {
            Feature f = it.next().getLabel();
            if (f == null || f.getType() == FeatureType.NULL) {
                it.remove();
            }
        }
    }

    private boolean applyPayloadFilter(Path<Feature, T> path,
            PayloadFilter<T> filter) {

        Set<T> payload = path.getPayload();
        if (filter.excludesItems() == false) {
            /* We only include the items in the filter */
            payload.retainAll(filter.getItems());
        } else {
            /* Excludes anything in the filter */
            payload.removeAll(filter.getItems());
        }

        return payload.isEmpty();
    }

    /**
     * Determines the numeric order of a Feature based on the current
     * orientation of the graph.  For example, humidity features may come first,
     * followed by temperature, etc.  If the feature in question has not yet
     * been added to the graph, then it is connected to the current leaf nodes,
     * effectively placing it at the bottom of the hierarchy, and its order
     * number is set to the current number of feature types in the graph.
     *
     * @return int representing the list ordering of the Feature
     */
    private int getOrder(String name, FeatureType type) {
        int order;
        Level level = levels.get(name);
        if (level != null) {
            order = level.order;
        } else {
            order = addNewFeature(name, type);
        }

        return order;
    }

    private int getOrder(Feature feature) {
        return getOrder(feature.getName(), feature.getType());
    }

    /**
     * Update the hierarchy levels and known Feature list with a new Feature.
     */
    private int addNewFeature(String name, FeatureType type) {
        Integer order = levels.keySet().size();
        levels.put(name, new Level(order, type));
        features.offer(name);

        return order;
    }

    /**
     * Retrieves the ordering of Feature names in this graph hierarchy.
     */
    public FeatureHierarchy getFeatureHierarchy() {
        FeatureHierarchy hierarchy = new FeatureHierarchy();
	        for (String feature : features) {
	            try {
	                hierarchy.addFeature(feature, levels.get(feature).type);
	            } catch (GraphException e) {
	                /* If a GraphException is thrown here, something is seriously
	                 * wrong. */
	                logger.severe("NULL FeatureType found in graph hierarchy!");
	            }
	        }
        
        return hierarchy;
    }

    public List<RIGPath<Feature, T>> getAllPaths() {
        List<RIGPath<Feature, T>> paths = root.descendantPaths();
        for (RIGPath<Feature, T> path : paths) {
            removeNullFeatures(path);
        }
        return paths;
    }

    public RIGVertex<Feature, T> getRoot() {
        return root;
    }
    
    @Override
    public String toString() {
        return root.toString();
    }
	
	public void updateHashes(int height, List<Set<String>> levelToLabelMap) {
		
		// LEVEL TRAVERSAL, STARTING FROM BOTTOM TO TOP
		for(int i = height; i > 0; i--) {
			int l = i;
			if(l == 1)
				System.out.println("Hi");
			levelTraverser(root, l, levelToLabelMap, l);
		}
		
	}
	
	public void levelTraverser(RIGVertex<Feature, T> node, int lvl, List<Set<String>> levelToLabelMap, int actualLevel) {
        
        if(node == null)
            return;
        
        
        
        if(lvl == 1) {
            // THIS IS THE LEVEL WE ARE LOOKING FOR
            if(node != null) {
            	
            	// MAKE SURE THIS RIG VERTEX ACTUALLY NEEDS RECOMPUTATION
                if(levelToLabelMap.get(actualLevel-1) != null && levelToLabelMap.get(actualLevel-1).contains(node.label.dataToString())) {
                	
                } else {
                	return;
                }
            	
                
                // GET ALL CHILDREN AND THEIR SIGNATURES
                // COMBINE THE SIGNATURES AND CREATE A MERKLE TREE
                
            	List<Tuple> combinations = new ArrayList<Tuple>();
                List<Long>  childrenSignatures = new ArrayList<Long>();
                List<String>  childrenPaths = new ArrayList<String>();
                
                for(RIGVertex<Feature, T> n : node.edges.values()) {
                	
                	combinations.add(new Tuple(n.hashValue,n.path));
                	//childrenSignatures.add(n.hashValue);
                	
                	//childrenPaths.add(n.path);
                }
                
                sortChildren(combinations, childrenPaths, childrenSignatures);
                
                MerkleTree mt = new MerkleTree(childrenSignatures, childrenPaths);
                
                node.mt = mt;
                node.hashValue = mt.getRoot().getHash();
                
            }
            
            return;
        } else {
        	for(RIGVertex<Feature, T> m : node.edges.values()) {
        		levelTraverser(m, lvl-1, levelToLabelMap, actualLevel);
            
        	}
        }
        
    }
	
	
	/**
	 * BRINGING ORDER TO HOW CHILDREN ARE COMBINED TO FORM A PARENT HASHVALUE
	 * @author sapmitra
	 * @param combinations
	 * @param childPaths
	 * @param childSignatures
	 */
	public static void sortChildren(List<Tuple> combinations, List<String> childPaths, List<Long> childSignatures) {
		
		Collections.sort(combinations, new Comparator<Tuple>()
        {
            public int compare( Tuple o1, Tuple o2 )
            {
                return (o1.stringValue).compareTo( o2.stringValue);
            }
        } );
		
		for(Tuple t : combinations) {
			childSignatures.add(t.lonValue);
        	
			childPaths.add(t.stringValue);
		}
		
		
	}
	
	public static void main(String arg[]) {
		
		List<Long>  childrenSignatures = new ArrayList<Long>();
        List<String>  childrenPaths = new ArrayList<String>();
		
		List<Tuple> com = new ArrayList<>();
		com.add(new Tuple(2,"b"));
		com.add(new Tuple(5,"e"));
		com.add(new Tuple(1,"a"));
		com.add(new Tuple(3,"c"));
		com.add(new Tuple(4,"d"));
		
		sortChildren(com, childrenPaths, childrenSignatures);
		
		System.out.println("Hi");
		
	}
	
	
	
}
