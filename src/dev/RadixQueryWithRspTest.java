package dev;
/* 
 * 
 * All rights reserved.
 * 
 * CSU EDF Project
 * 
 * This program read a csv-formatted file and send each line to the galileo server
 */

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import galileo.comm.Connector;
import galileo.comm.GalileoEventMap;
import galileo.comm.MetadataRequest;
import galileo.comm.MetadataResponse;
import galileo.comm.QueryRequest;
import galileo.comm.QueryResponse;
import galileo.dataset.Coordinates;
import galileo.event.BasicEventWrapper;
import galileo.event.Event;
import galileo.event.EventWrapper;
import galileo.net.ClientMessageRouter;
import galileo.net.GalileoMessage;
import galileo.net.MessageListener;
import galileo.net.NetworkDestination;
import galileo.util.GeoHash;

public class RadixQueryWithRspTest implements MessageListener {
	
	private static boolean isQuery = false;
	
	private static GalileoEventMap eventMap = new GalileoEventMap();
	private static EventWrapper wrapper = new BasicEventWrapper(eventMap);
	
	@Override
	public void onConnect(NetworkDestination endpoint) {
	}

	@Override
	public void onDisconnect(NetworkDestination endpoint) {
	}

	@Override
	public void onMessage(GalileoMessage message) {
		try {
			
			System.out.println("RESPONSE RECEIVED...");
			
			if (isQuery) {
				QueryResponse response = (QueryResponse) wrapper.unwrap(message);
				
				System.out.println("RESPONSE\n=============\n");
				System.out.println(response.getJSONResults().toString());
			} else {
				MetadataResponse response = (MetadataResponse) wrapper.unwrap(message);

				System.out.println(response.getResponse().toString());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	
	

	// [START processFile]
	/**
	 * read each line from the csv file and send it to galileo server
	 * @param targetNode 
	 * 
	 * @param pathtothefile
	 *            path to the csv file
	 * @param galileoconnector
	 *            GalileoConnector instance
	 * @throws Exception
	 */
	
	private static void processRequest(Connector gc, NetworkDestination targetNode) throws Exception {
		
		// CREATING FS1
		String reqJson = "";
		
		JSONObject rJSON = new JSONObject();
		rJSON.put("kind", "galileo#plot");
		rJSON.put("type", "series"); 
		rJSON.put("features", "ndvi,irt"); 
		rJSON.put("plotID", 20419);
		rJSON.put("filesystem", "roots-arizona");
		
		reqJson = rJSON.toString();
		
		System.out.println(reqJson);
		
		Event vr = null;
		
		if(isQuery) {
			
			vr = createPolygonRequest();
			
		} else {
		
			vr = new MetadataRequest(reqJson) ;
		
		}
		
		try {
			
			ClientMessageRouter messageRouter = gc.getMessageRouter();
			
			RadixQueryWithRspTest vqt = new RadixQueryWithRspTest();
			
			messageRouter.addListener(vqt);
			
			//for(int i=0 ;i < 10; i++) {
				
				gc.sendMessage(targetNode, vr);
				
			//}
			
		} finally {
			
			Thread.sleep(10*1000);
			gc.close();
		}
	}
	
	private static Event createPolygonRequest() {
		
		//(40.65319061279297, -104.9959945678711), (40.653018951416016, -104.99565124511719)
		float lat1 = 40.65315f;
		float lat2 = 40.65314f;
		
		float lon1 = -104.9957f;
		float lon2 = -104.9956f;
		
		
		Coordinates c1 = new Coordinates(lat2, lon1);
		Coordinates c2 = new Coordinates(lat2, lon2);
		Coordinates c3 = new Coordinates(lat1, lon2);
		Coordinates c4 = new Coordinates(lat1, lon1);
		//Coordinates c5 = new Coordinates(36.78f, -107.64f);
		
		List<Coordinates> cl = new ArrayList<Coordinates>();
		cl.add(c1); cl.add(c2); cl.add(c3); cl.add(c4);
		
		//GeoHash.decodeHash("9xjr6b8m");
		
		//System.out.println(GeoHash.getIntersectingGeohashes(cl, 8));
		
		QueryRequest qr = new QueryRequest("roots", cl);
		return qr;
	}

	// [START Main]
	/**
	 * Based on command line argument, call processFile method to store the data
	 * at galileo server
	 * 
	 * @param args
	 */
	public static void main(String[] args1) {
		String args[] = new String[2];
		args[0] = "lattice-2.cs.colostate.edu";
		args[1] = "5635";
		
		if (args.length != 2) {
			System.out.println("Usage: RadixQueryTest [galileo-hostname] [galileo-port-number]");
			System.exit(0);
		} else {
			try {
				Connector gc = new Connector();
				
				NetworkDestination targetNode = new NetworkDestination(args[0], Integer.parseInt(args[1]));
				
				System.out.println(args[0] + "," + Integer.parseInt(args[1]));
				
				processRequest(gc, targetNode);
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		System.out.println("Query Sent Out");
		System.exit(0);
	}
	// [END Main]
}
