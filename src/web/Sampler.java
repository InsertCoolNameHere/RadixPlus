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
package web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import galileo.bmp.HashGrid;
import galileo.bmp.HashGridException;
import galileo.dataset.Coordinates;
import galileo.dataset.Metadata;
import galileo.dataset.SpatialProperties;
import galileo.dht.NodeInfo;
import galileo.dht.PartitionException;
import galileo.dht.Partitioner;
import galileo.dht.SpatialHierarchyPartitioner;
import galileo.dht.StorageNode;
import galileo.dht.hash.HashException;
public class Sampler {
	private Partitioner<Metadata> partitioner;
	private Random rand = new Random();
	static Logger logger = Logger.getLogger("galileo");
	
	public int latIndex = 11;
	public int lonIndex = 12;
	
	public Sampler(SpatialHierarchyPartitioner partitioner){
		this.partitioner = partitioner;
		rand.setSeed(System.currentTimeMillis());
	}
	
	public Sampler(SpatialHierarchyPartitioner partitioner, int latIndex, int lonIndex){
		this.partitioner = partitioner;
		this.latIndex = latIndex;
		this.lonIndex = lonIndex;
		rand.setSeed(System.currentTimeMillis());
	}
	
	public static void main(String arg[]) throws IOException, HashGridException {
		Sampler sam = new Sampler(null);
		sam.latIndex = 2;
		sam.lonIndex = 3;
		HashGrid queryGrid = new HashGrid("9tbkh4,,,",11,"9tbkh49ybpb","9tbkh4dqzzz","9tbkh46mpbp","9tbkh43v000");
		
		String ip = new String(Files.readAllBytes(Paths.get("/s/chopin/e/proj/sustain/sapmitra/arizona/cleanData/Roots_2018/irt_small.csv")));
        
		sam.sample(queryGrid, ip);
	}
	
	/**
	 * This method applies the "stamping" method of sampling. That is,
	 * create a query HashGrid from the sampled file info, and logical AND
	 * it with the system's global HashGrid. The intersection of bits will
	 * determine the storage node data should be sent to.
	 * @throws HashGridException */
	public SamplerResponse sample(HashGrid grid, String toSample) throws HashGridException {
		String [] lines = toSample.split("\\r?\\n");
		
		int sampSize = 100;
		if(lines.length/4 < sampSize)
			sampSize = lines.length/4 ;
		List<Integer> lineNums = getLineNums(sampSize, lines.length);
		
		HashMap<NodeInfo, Integer> dests = new HashMap<>();
		/*There surely must be a better way to initialize a query grid?*/
		//HashGrid queryGrid = new HashGrid("wdw0x9", grid.getPrecision(), "wdw0x9bpbpb", "wdw0x9pbpbp");
		//HashGrid queryGrid = new HashGrid(grid.getBaseHash(), grid.getPrecision(), StorageNode.a1, StorageNode.a2);
		HashGrid queryGrid = new HashGrid(grid.getZonesString(), grid.getPrecision(), grid.upperLeftHash, grid.upperRightHash,
				grid.bottomRightHash, grid.bottomLeftHash);
		
		/*logger.info("RIKI: SAMPLER PARAMETERS: "+grid.getZonesString()+" "+ grid.getPrecision()+" "+ grid.upperLeftHash+" "+ grid.upperRightHash+" "+
				grid.bottomRightHash+" "+ grid.bottomLeftHash);*/
		
		int ln = 0;
		for (String line : lines){
			String[] tokens = line.split(",");
			if(tokens.length > lonIndex && lineNums.contains(ln)) {
				double lat = Double.parseDouble(line.split(",")[latIndex]);
				double lon = Double.parseDouble(line.split(",")[lonIndex]); //hard code index #1 and #2 to be lat and long. WILL CHANGE
				//logger.info("RIKI:PT:"+lat+","+lon);
				queryGrid.addPoint(new Coordinates(lat, lon));
			}
			ln++;
		}
	
		/*Ensure that all points were added*/
		queryGrid.applyUpdates();
		/*Once all points are added, query the master grid to get the intersection*/
		int [] intersections = grid.query(queryGrid.getBitmap());
		
		if (intersections.length == 0) {
			logger.info("RIKI: 0 intersections detected between hashgrid and querygrid");
		} else {
			float p = (float)intersections.length/(float)lines.length;
			
			//logger.info("RIKI: SOME INTERSECTIONS FOUND "+(p*100)+"%");
			
		}

		/*For each index, use the partitioner to determine where it goes*/
		
		for (int hash : intersections) {
			NodeInfo dest = ((SpatialHierarchyPartitioner)partitioner).locateHashVal(grid.indexToGeoHash(hash, grid.getPrecision())); //The full geohash of the point intersecting hash
			if (!dests.containsKey(dest))
				dests.put(dest, 1);
			else
				dests.put(dest, dests.get(dest)+1);
		}
//		NodeInfo finalDest = null;
//		int count = 0;
//		for (NodeInfo node : dests.keySet()){
//			if (dests.get(node) > count){
//				finalDest = node;
//				count = dests.get(node);
//			}
//		}
//		return dests;
		return new SamplerResponse(dests, true);
	}
	
	
	/**
	 * This method relies on hard-coded positions for various values.
	 * Upon receipt of real-life data, this method will need to change.
	 * @throws PartitionException 
	 * @throws HashException */
	public NodeInfo sample(String toSample) throws HashException, PartitionException{
		String [] lines = toSample.split("\\r?\\n");
		HashMap<NodeInfo, Integer> dests = new HashMap<>();
		for (String line : lines){
			
			float lat = Float.parseFloat(line.split(",")[latIndex]);
			float lon = Float.parseFloat(line.split(",")[lonIndex]); //hard code index #1 and #2 to be lat and long. WILL CHANGE
			Metadata meta = new Metadata();
			meta.setSpatialProperties(new SpatialProperties(lat,lon));
			NodeInfo x = this.partitioner.locateData(meta);

			if (!dests.containsKey(x))
				dests.put(x, 1);
			else
				dests.put(x, dests.get(x)+1);
		}
		NodeInfo finalDest = null;
		int count = 0;
		for (NodeInfo node : dests.keySet()){
			if (dests.get(node) > count){
				finalDest = node;
				count = dests.get(node);
			}
		}
		return finalDest;
	}

	private List<Integer> getLineNums(int numSamples, int maxLine){
		List<Integer> lineNums = new ArrayList<>(numSamples);
		while (lineNums.size() < numSamples) {
			int toAdd = rand.nextInt(maxLine);
			if (!lineNums.contains(toAdd))
				lineNums.add(toAdd);
		}
		return lineNums;
	}
}
