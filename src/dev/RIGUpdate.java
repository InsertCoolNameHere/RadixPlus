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
import galileo.comm.RigUpdateRequest;
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
public class RIGUpdate {
	
	public static void main(String [] args) throws IOException, InterruptedException{
		Connector connector = new Connector();
		
		RigUpdateRequest req = new RigUpdateRequest("roots-arizona");

		//Any Galileo storage node hostname and port number
		NetworkDestination storageNode = new NetworkDestination("lattice-1.cs.colostate.edu", 5635);
		connector.publishEvent(storageNode, req);
		Thread.sleep(2500);
		connector.close();
	}
	
	
	
}
