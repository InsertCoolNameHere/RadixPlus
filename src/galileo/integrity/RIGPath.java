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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Represents a simple graph path.  A path contains a number of vertices and
 * edges that are connected to form a chain that traverses through a graph.
 *
 * @author malensek
 */
public class RIGPath<L extends Comparable<L>, V>
implements Iterable<RIGVertex<L, V>> {
    protected List<RIGVertex<L, V>> vertices = new ArrayList<>();
    protected Set<V> payload = new HashSet<V>();
    /**
     * Create a Path with a number of vertices pre-populated.
     */
    @SafeVarargs
    public RIGPath(RIGVertex<L, V>... vertices) {
        for (RIGVertex<L, V> vertex : vertices) {
            this.vertices.add(vertex);
        }
    }

    /**
     * Create a Path with a single payload and number of vertices pre-populated.
     */
    @SafeVarargs
    public RIGPath(V payload, RIGVertex<L, V>... vertices) {
        this(vertices);
        addPayload(payload);
    }

    /**
     * Create a Path with payload(s) and number of vertices pre-populated.
     */
    @SafeVarargs
    public RIGPath(Set<V> payload, RIGVertex<L, V>... vertices) {
        this(vertices);
        setPayload(payload);
    }

    /**
     * Creates a path by copying the vertices from an existing path.
     */
    public RIGPath(RIGPath<L, V> p) {
        /* New Vertices must be created if this path will be used anywhere but
         * its source graph; the type hierarchy is embedded in the vertices. */
        for (RIGVertex<L, V> v : p.getVertices()) {
            this.vertices.add(new RIGVertex<L, V>(v));
        }
    }

    /**
     * Retrieves the number of {@link Vertex} instances in this Path.
     */
    public int size() {
        return vertices.size();
    }

    public void add(RIGVertex<L, V> vertex) {
        vertices.add(vertex);
    }

    public void add(L label) {
        add(new RIGVertex<L, V>(label));
    }

    public void add(L label, V value) {
        add(new RIGVertex<>(label, value));
    }

    public void remove(int index) {
        vertices.remove(index);
    }

    public boolean remove(RIGVertex<L, V> vertex) {
        return vertices.remove(vertex);
    }

    public RIGVertex<L, V> get(int index) {
        return vertices.get(index);
    }

    public RIGVertex<L, V> getTail() {
        return vertices.get(vertices.size() - 1);
    }

    public List<RIGVertex<L, V>> getVertices() {
        return vertices;
    }

    public Set<V> getPayload() {
        return payload;
    }
    
    public void setPayload(Set<V> payload) {
    	
        this.payload = payload;
    }

    public void addPayload(V payload) {
        this.payload.add(payload);
    }

    public boolean hasPayload() {
        return payload.size() > 0;
    }

    public boolean hasCorrectPayload(String start, String end, String startOp, String endOp){
    	long argStart = Long.parseLong(start);
		long argEnd = Long.parseLong(end);

    	if (payload.size() > 0){
    		for (V v : payload){
    			String [] splitLoad = ((String)v).split(File.separator);
    			String [] time = splitLoad[splitLoad.length-1].split(Pattern.quote("."));
    			long loadStart = Long.parseLong(time[0].split("-")[0]);
    			long loadEnd = Long.parseLong(time[0].split("-")[1]);
    			return evaluateExpression(loadStart, loadEnd, argStart, argEnd, startOp, endOp);
    		}
    	}
    	return false;
    }
    
    private boolean evaluateExpression(long timeStart, long timeEnd, long queryStart, long queryEnd, String startOp, String endOp)throws IllegalArgumentException{
    	boolean startPasses = false;
    	boolean endPasses = false;
    	switch(startOp){
    		case ">=":
    			if (timeStart >= queryStart)
    				startPasses = true;
    			break;
    		case ">":
    			if (timeStart > queryStart)
    				startPasses = true;
    			break;
    		case "<=":
    			if (timeStart <= queryStart)
    				startPasses = true;
    			break;
    		case "<":
    			if (timeStart < queryStart)
    				startPasses = true;
    			break;
    		case "==":
    			if (timeStart == queryStart)
    				startPasses = true;
    			break;
    		case "!=":
    			if (timeStart != queryStart)
    				startPasses = true;
    			break;
    		default:
    			throw new IllegalArgumentException("Unknown operators " + startOp +", " + endOp +". Cannot evaluate.");
    	}
    	switch(endOp){
    		case ">=":
    			endPasses = (timeEnd >= queryEnd);
    			break;
    		case ">":
    			endPasses = (timeEnd > queryEnd);
    			break;
    		case "<=":
    			endPasses = (timeEnd <= queryEnd);
    			break;
    		case "<":
    			endPasses = (timeEnd < queryEnd);
    			break;
    		case "==":
    			endPasses = (timeEnd == queryEnd);
    			break;
    		case "!=":
    			endPasses = (timeEnd != queryEnd);
    			break;
    			
    	}
    	return (startPasses && endPasses);
    }
    /**
     * Retrieve a list of the {@link Vertex} labels in this Path.
     */
    public List<L> getLabels() {
        List<L> labels = new ArrayList<>();
        for (RIGVertex<L, V> vertex : vertices) {
            labels.add(vertex.getLabel());
        }

        return labels;
    }

    public void sort(Comparator<? super RIGVertex<L, V>> c) {
        Collections.sort(vertices, c);
    }

    @Override
    public Iterator<RIGVertex<L, V>> iterator() {
        return vertices.iterator();
    }

    @Override
    public String toString() {
        String str = "";
        for (int i = 0; i < vertices.size(); ++i) {
            str += vertices.get(i).getLabel();

            if (i < vertices.size() - 1) {
                str += " -> ";
            } else {
                /* Include the path 'payload' */
                str += " -> payload=" + payload.toString();
            }
        }

        return str;
    }
}
