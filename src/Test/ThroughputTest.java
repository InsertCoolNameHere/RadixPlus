package Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import galileo.comm.Connector;
import galileo.comm.FilesystemAction;
import galileo.comm.FilesystemRequest;
import galileo.comm.TemporalType;
import galileo.config.SystemConfig;
import galileo.dataset.SpatialHint;
import galileo.dataset.feature.FeatureType;
import galileo.net.NetworkDestination;
import galileo.util.Pair;

public class ThroughputTest {

	public static void main(String [] args) throws IOException, InterruptedException {
		File fullLog = new File("/s/bach/j/under/mroseliu/Documents/systemPerf/5MBStampSize");
		if (!fullLog.exists())
			fullLog.createNewFile();
//		Process populate = new ProcessBuilder("/s/bach/j/under/mroseliu/NSF_Time_Series/Raptor/galileo/bin/data-populator.sh", "/s/bach/j/under/mroseliu/NSF_Time_Series/Raptor/galileo/config/network/5ingestors", "5").start();
		BufferedWriter bw = new BufferedWriter(new FileWriter(fullLog, true));
		for (int i = 0; i < 20; i++) {
			
			//Start cluster and print status of all nodes
			System.out.println(startCluster());
			prepareFS();
			Thread.sleep(5000);//Prepare the filesystem and wait a couple seconds to ensure all nodes are ready
			Process startIngest = new ProcessBuilder("/s/bach/j/under/mroseliu/NSF_Time_Series/Radix/galileo/bin/start-ingest.sh", "/s/bach/j/under/mroseliu/NSF_Time_Series/Radix/galileo/config/network/10ingestors").start();
			Thread.sleep(60000 * 5); //measure for 5 minutes
			//Get output of python process to measure throughput
			Process getThroughput = new ProcessBuilder("python", "/s/bach/j/under/mroseliu/Documents/systemPerf/throughput.py").start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(getThroughput.getInputStream()));
			StringBuilder builder = new StringBuilder();
			String line = null;
			while ( (line = reader.readLine()) != null) {
			   builder.append(line);
			   builder.append(System.lineSeparator());
			}
			bw.append(builder.toString());
			System.out.println("Throughput: " + builder.toString());
			bw.flush();
			//Now delete the log file
			File logFile = new File("/s/bach/j/under/mroseliu/Documents/systemPerf/throughput.txt");
			logFile.delete();
			
			//Now kill the cluster, and clear out leftover data
			Process killCluster = new ProcessBuilder("/s/bach/j/under/mroseliu/NSF_Time_Series/Radix/galileo/bin/galileo-cluster", "stop").start();
			Thread.sleep(10000);//Wait a couple seconds to ensure each node properly shut down
			Process clearOldData = new ProcessBuilder("/s/bach/j/under/mroseliu/NSF_Time_Series/Radix/galileo/bin/galileo-clear.sh", "").start();
			//ensure all the stupid things actually stopped
			Process ensureDead = new ProcessBuilder("/s/bach/j/under/mroseliu/NSF_Time_Series/Radix/galileo/bin/kill-rogues.sh", "/s/bach/j/under/mroseliu/NSF_Time_Series/Radix/galileo/config/network/hostsNoPorts").start();
			Thread.sleep(10000);
		}
		bw.close();
	
	}
	
	
	
	public static String startCluster() throws IOException, InterruptedException {
		Process p = new ProcessBuilder("/s/bach/j/under/mroseliu/NSF_Time_Series/Radix/galileo/bin/galileo-cluster", "start").start();
		//Wait a few seconds for hashgrid to initialize fully on each node
		Thread.sleep(15000);
		//Get status of each node and ensure they are all ready
		Process status = new ProcessBuilder("/s/bach/j/under/mroseliu/NSF_Time_Series/Radix/galileo/bin/galileo-cluster", "status").start();
		BufferedReader reader = new BufferedReader(new InputStreamReader(status.getInputStream()));
		StringBuilder builder = new StringBuilder();
		String line = null;
		while ( (line = reader.readLine()) != null) {
		   builder.append(line);
		   builder.append(System.lineSeparator());
		}
		return builder.toString();
	}
	
	
	public static void prepareFS() throws IOException, InterruptedException {
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

