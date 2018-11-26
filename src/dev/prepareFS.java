package dev;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import galileo.comm.FilesystemAction;
import galileo.comm.FilesystemRequest;
import galileo.comm.TemporalType;
import galileo.dataset.SpatialHint;
import galileo.dataset.feature.FeatureType;
import galileo.net.NetworkDestination;
import galileo.util.Pair;
import galileo.comm.Connector;

/**
 * THIS CLASS IS FOR DEVELOPMENT ONLY, AND SHOULD BE REMOVED IN THE FINAL DISTRIBUTION*/
public class prepareFS {
	public static void main(String [] args) throws IOException, InterruptedException{
		Connector connector = new Connector();
		List<Pair<String, FeatureType>> featureList = new ArrayList<>();
		//features must be in order which they appear in raw data
		featureList.add(new Pair<>("time", FeatureType.STRING));
		
		featureList.add(new Pair<>("lat", FeatureType.DOUBLE));
		featureList.add(new Pair<>("long", FeatureType.DOUBLE));
		featureList.add(new Pair<>("plotID", FeatureType.INT));
		featureList.add(new Pair<>("temperature", FeatureType.DOUBLE));
		featureList.add(new Pair<>("humidity", FeatureType.DOUBLE));
		featureList.add(new Pair<>("CO2", FeatureType.DOUBLE));
		featureList.add(new Pair<>("genotype", FeatureType.STRING));
		featureList.add(new Pair<>("rep", FeatureType.INT));
		for (int i = 1; i < 7; i++)
			featureList.add(new Pair<>("Random"+i, FeatureType.DOUBLE));
		featureList.add(new Pair<>("Random7", FeatureType.STRING));
		SpatialHint spatialHint = new SpatialHint("lat", "long");

		FilesystemRequest fsRequest = new FilesystemRequest(
		"roots", FilesystemAction.CREATE, featureList, spatialHint);
		fsRequest.setNodesPerGroup(5);
		fsRequest.setPrecision(11);
		fsRequest.setTemporalType(TemporalType.HOUR_OF_DAY);

		//Any Galileo storage node hostname and port number
		NetworkDestination storageNode = new NetworkDestination("lattice-100.cs.colostate.edu", 5635);
		connector.publishEvent(storageNode, fsRequest);
		Thread.sleep(2500);
		connector.close();
	}
}
