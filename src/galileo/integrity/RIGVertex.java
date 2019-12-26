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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Provides a lightweight generic implementation of a graph vertex backed by a
 * TreeMap for extensibility.  This provides the basis of the hybrid
 * trees/graphs used in the system.
 *
 * @author malensek
 */
public class RIGVertex<L extends Comparable<L>, V> {

    protected L label;
    public String path = null;
    protected Set<V> values = new HashSet<V>();
    protected TreeMap<L, RIGVertex<L, V>> edges = new TreeMap<>();
    
    public long hashValue; 
    
    public MerkleTree mt = null;

    public RIGVertex() { }

    public RIGVertex(L label) {
        this.label = label;
    }
    
    public boolean hasHash() {
    	if(mt == null)
    		return false;
    	
    	return true;
    }
    
    

    public RIGVertex(L label, V value) {
        this.label = label;
        this.addValue(value);
    }

    public RIGVertex(L label, Collection<V> values) {
        this.label = label;
        this.addValues(values);
    }

    public RIGVertex(RIGVertex<L, V> v) {
        this.label = v.label;
    }

    /**
     * Determines if two vertices are connected.
     *
     * @return true if the Vertex label is found on a connecting edge.
     */
    public boolean connectedTo(L label) {
        return edges.containsKey(label);
    }

    /**
     * Retrieve a neighboring Vertex.
     *
     * @param label Neighbor's label.
     *
     * @return Neighbor Vertex.
     */
    public RIGVertex<L, V> getNeighbor(L label) {
        return edges.get(label);
    }

    public NavigableMap<L, RIGVertex<L, V>> getNeighborsLessThan(
            L label, boolean inclusive) {
        return edges.headMap(label, inclusive);
    }

    public NavigableMap<L, RIGVertex<L, V>> getNeighborsGreaterThan(
            L label, boolean inclusive) {
        return edges.tailMap(label, inclusive);
    }

    /**
     * Retrieve the labels of all neighboring vertices.
     *
     * @return Neighbor Vertex labels.
     */
    public Set<L> getNeighborLabels() {
        return edges.keySet();
    }

    /**
     * Traverse all edges to return all neighboring vertices.
     *
     * @return collection of all neighboring vertices.
     */
    public Collection<RIGVertex<L, V>> getAllNeighbors() {
        return edges.values();
    }

    /**
     * Connnects two vertices.  If this vertex is already connected to the
     * provided vertex label, then the already-connected vertex is returned, and
     * its value is updated.
     *
     * @param vertex The vertex to connect to.
     *
     * @return Connected vertex.
     */
    public RIGVertex<L, V> connect(RIGVertex<L, V> vertex) {
        L label = vertex.getLabel();
        RIGVertex<L, V> edge = getNeighbor(label);
        if (edge == null) {
            edges.put(label, vertex);
            return vertex;
        } else {
            edge.addValues(vertex.getValues());
            return edge;
        }
    }

    /**
     * Add and connect a collection of vertices in the form of a traversal path.
     */
    public void addPath(Iterator<RIGVertex<L, V>> path) {
        if (path.hasNext()) {
            RIGVertex<L, V> vertex = path.next();
            RIGVertex<L, V> edge = connect(vertex);
            edge.addPath(path);
        }
    }

    public L getLabel() {
        return label;
    }

    public void setLabel(L label) {
        this.label = label;
    }

    public Set<V> getValues() {
        return values;
    }

    public void addValue(V value) {
        this.values.add(value);
    }

    public void addValues(Collection<V> values) {
        this.values.addAll(values);
    }
    
    public void setHashValue(long val) {
        this.hashValue = val;
    }
    
    /**
	 * Big-endian conversion
	 */
	public static byte[] longToByteArray(long value) {
		return new byte[] { (byte) (value >> 56), (byte) (value >> 48), (byte) (value >> 40), (byte) (value >> 32),
				(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value };
	}
    

    /**
     * Retrieves all {@link RIGPath} instances represented by the children of this
     * Vertex.
     *
     * @return List of Paths that are descendants of this Vertex
     */
    public List<RIGPath<L, V>> descendantPaths() {
        RIGPath<L, V> p = new RIGPath<L, V>();
        List<RIGPath<L, V>> paths = new ArrayList<>();
        for (RIGVertex<L, V> child : this.getAllNeighbors()) {
            traverseDescendants(child, paths, p);
        }

        return paths;
    }

    /**
     * Traverses through descendant Vertices, finding Path instances.  A Path
     * leads to one or more payloads stored as Vertex values.  This method is
     * designed to be used recursively.
     *
     * @param vertex Vertex to query descendants
     * @param paths List of Paths discovered thus far during traversal.  This is
     * updated as new Path instances are found.
     * @param currentPath The current Path being inspected by the traversal
     */
    protected void traverseDescendants(RIGVertex<L, V> vertex,
            List<RIGPath<L, V>> paths, RIGPath<L, V> currentPath) {

        RIGPath<L, V> p = new RIGPath<>(currentPath);
        p.add(new RIGVertex<>(vertex));

        if (vertex.getValues().size() > 0) {
            /* If the vertex has values, we've found a path endpoint. */
            p.setPayload(vertex.getValues());
            paths.add(p);
        }

        for (RIGVertex<L, V> child : vertex.getAllNeighbors()) {
            traverseDescendants(child, paths, p);
        }
    }

    /**
     * Retrieves the number of descendant vertices for this {@link RIGVertex}.
     *
     * @return number of descendants (children)
     */
    public long numDescendants() {
        long total = this.getAllNeighbors().size();
        for (RIGVertex<L, V> child : this.getAllNeighbors()) {
            total += child.numDescendants();
        }

        return total;
    }

    /**
     * Retrieves the number of descendant edges for this {@link RIGVertex}.  This
     * count includes the links between descendants for scan operations.
     *
     * @return number of descendant edges.
     */
    public long numDescendantEdges() {
        long total = 0;
        int numNeighbors = this.getAllNeighbors().size();

        if (numNeighbors > 0) {
            total = numNeighbors + numNeighbors - 1;
        }

        for (RIGVertex<L, V> child : this.getAllNeighbors()) {
            total += child.numDescendantEdges();
        }

        return total;
    }

    /**
     * Removes all the edges from this Vertex, severing any connections with
     * neighboring vertices.
     */
    public void clearEdges() {
        edges.clear();
    }

    /**
     * Clears all values associated with this Vertex.
     */
    public void clearValues() {
        values.clear();
    }

    /**
     * Pretty-print this vertex (and its children) with a given indent level.
     */
    protected String toString(int indent) {
        String ls = System.lineSeparator();
        String str = "(" + getLabel() + " " + values + ")" + ls;

        String space = " ";
        for (int i = 0; i < indent; ++i) {
            space += "|  ";
        }
        space += "|-";
        ++indent;

        for (RIGVertex<L, V> vertex : edges.values()) {
            str += space + vertex.toString(indent);
        }

        return str;
    }

    @Override
    public String toString() {
        return toString(0);
    }

	public void setMerkleTree(MerkleTree mt2) {
		// TODO Auto-generated method stub
		mt = mt2;
	}
}
