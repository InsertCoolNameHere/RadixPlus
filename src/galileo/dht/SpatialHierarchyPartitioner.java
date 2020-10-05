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
package galileo.dht;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import galileo.dataset.Coordinates;
import galileo.dataset.Metadata;
import galileo.dataset.SpatialProperties;
import galileo.dht.hash.BalancedHashRing;
import galileo.dht.hash.ConstrainedGeohash;
import galileo.dht.hash.HashException;
import galileo.dht.hash.HashRing;
import galileo.dht.hash.HashTopologyException;
import galileo.dht.hash.SpatialHash;
import galileo.util.GeoHash;

/**
 * Implements a spatial partitioner that creates a two-tiered hierarchical DHT.
 *
 * @author malensek
 */
public class SpatialHierarchyPartitioner extends Partitioner<Metadata> {

	private static final Logger logger = Logger.getLogger("galileo");

	private ConstrainedGeohash groupHash;
	private BalancedHashRing<Metadata> groupHashRing;
	private Map<BigInteger, GroupInfo> groupPositions = new HashMap<>();

//	private SHA1 nodeHash = new SHA1();
	private SpatialHash nodeHash = new SpatialHash();
	private Map<BigInteger, BalancedHashRing<SpatialProperties>> nodeHashRings = new HashMap<>();//was previously BalancedHashRing<byte[]>
	private Map<BigInteger, Map<BigInteger, NodeInfo>> nodePositions = new HashMap<>();

	public SpatialHierarchyPartitioner(StorageNode storageNode, NetworkInfo network, String[] geohashes)
			throws PartitionException, HashException, HashTopologyException {

		super(storageNode, network);
		SPATIAL_PRECISION = geohashes[0].length();
		List<GroupInfo> groups = network.getGroups();

		if (groups.size() == 0) {
			throw new PartitionException("At least one group must exist in "
					+ "the current network configuration to use this " + "partitioner.");
		}

		groupHash = new ConstrainedGeohash(geohashes);
		groupHashRing = new BalancedHashRing<>(groupHash);

		for (GroupInfo group : groups) {
			placeGroup(group);
		}
	}

	private void placeGroup(GroupInfo group) throws HashException, HashTopologyException {
		BigInteger position = groupHashRing.addNode(null);
		groupPositions.put(position, group);
		logger.info(String.format("Group '%s' placed at %d", group.getName(), position));

		nodeHashRings.put(position, new BalancedHashRing<>(nodeHash));
		for (NodeInfo node : group.getNodes()) {
			placeNode(position, node);
		}
	}

	private void placeNode(BigInteger groupPosition, NodeInfo node) throws HashException, HashTopologyException {
		BalancedHashRing<SpatialProperties> hashRing = nodeHashRings.get(groupPosition);
		BigInteger nodePosition = hashRing.addNode(null);

		GroupInfo group = groupPositions.get(groupPosition);
		
		logger.info(String.format("Node [%s] placed in Group '%s' at %d", node, group.getName(), nodePosition));

		if (nodePositions.get(groupPosition) == null) {
			nodePositions.put(groupPosition, new HashMap<BigInteger, NodeInfo>());
		}
		nodePositions.get(groupPosition).put(nodePosition, node);
	}
	
	//receives full length 11-char geohash
	public NodeInfo locateHashVal(String geohash) {
		//Hash the geohash to determine the group
		try {
			BigInteger groupHashVal = groupHash.hash(geohash.substring(0, groupHash.getPrecision())); //Grab first 8 characters
			BigInteger group = groupHashRing.locateHashVal(groupHashVal);//locate on the hash ring
			BalancedHashRing<SpatialProperties> nodeHash = nodeHashRings.get(group);
			BigInteger node = nodeHash.locateHashVal(BigInteger.valueOf(GeoHash.hashToLong(geohash.substring(groupHash.getPrecision()))));
			return nodePositions.get(group).get(node);
//			return nodePositions.get(group).get(BigInteger.valueOf(GeoHash.hashToLong(geohash.substring(groupHash.getPrecision()))));
		} catch (HashException e) {
			return null;
		}
	}
	@Override
	public NodeInfo locateData(Metadata metadata) throws HashException, PartitionException {
		/* First, determine the group that should hold this file */
		BigInteger group = groupHashRing.locate(metadata);
		/* Next, the StorageNode */
//		String combinedAttrs = metadata.getName();
//		for (Feature feature : metadata.getAttributes()) {
//			combinedAttrs += feature.getString();
//		}
		SpatialProperties coords = metadata.getSpatialProperties();
		HashRing<SpatialProperties> nodeHash = nodeHashRings.get(group);
		BigInteger node = nodeHash.locate(coords);//previously combinedAttrs.getBytes()
		NodeInfo info = nodePositions.get(group).get(node);
		
		if (info == null) {
			throw new PartitionException("Could not locate specified data");
		}
		return info;
	}
	
	// RIKI: USED DURING SYNC OF PLOT DATA
	public List<NodeInfo> getNodesForGeohashSet(Set<String> hashes) throws HashException {
		
		Set<NodeInfo> nodes = new HashSet<NodeInfo>();
		for (String hash1 : hashes) {
			
			String hash = hash1.substring(0,8);
			
			NodeInfo n = locateHashVal(hash);
			
			if(n != null)
				nodes.add(n);
			
		}
		return new ArrayList<NodeInfo>(nodes);
	}

	@Override
	public List<NodeInfo> findDestinations(Metadata data) throws HashException, PartitionException {
		if (data == null || data.getSpatialProperties() == null)
			return network.getAllNodes();

		SpatialProperties sp = data.getSpatialProperties();
		Set<NodeInfo> destinations = new HashSet<NodeInfo>();
		if (sp.hasRange() && sp.getSpatialRange().hasPolygon()) {
			List<Coordinates> polygon = sp.getSpatialRange().getPolygon();
			// Geohash precision for spatial ring is 2.
			String[] hashes = GeoHash.getIntersectingGeohashes(polygon, Partitioner.SPATIAL_PRECISION);
			
			
			String sh = "";
			for(String h : hashes) {
				sh+=h+",";
			}
			logger.info("HASHES FOUND: "+sh);
			
			for (String hash : hashes) {
				Metadata metadata = new Metadata();
				metadata.setSpatialProperties(new SpatialProperties(GeoHash.decodeHash(hash)));
				BigInteger group = groupHashRing.locate(metadata);
				HashRing<SpatialProperties> nodeRing = nodeHashRings.get(group);
				Set<BigInteger> npositions = nodeRing.getPositions();
				for (BigInteger nposition : npositions)
					destinations.add(nodePositions.get(group).get(nposition));
			}
		} else {
			BigInteger group = groupHashRing.locate(data);
			HashRing<SpatialProperties> nodeRing = nodeHashRings.get(group);
			Set<BigInteger> npositions = nodeRing.getPositions();
			for (BigInteger nposition : npositions)
				destinations.add(nodePositions.get(group).get(nposition));
		}
		return new ArrayList<NodeInfo>(destinations);
	}
}