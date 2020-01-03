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
import galileo.dataset.feature.Feature;
import galileo.event.BasicEventWrapper;
import galileo.event.Event;
import galileo.event.EventWrapper;
import galileo.fs.GeospatialFileSystem;
import galileo.net.ClientMessageRouter;
import galileo.net.GalileoMessage;
import galileo.net.MessageListener;
import galileo.net.NetworkDestination;
import galileo.query.Expression;
import galileo.query.Operation;
import galileo.query.Operator;
import galileo.query.Query;
import galileo.util.GeoHash;

public class RadixArizonaQueryWithRspTest implements MessageListener {
	
	private static boolean isQuery = true;
	
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
		rJSON.put("type", "series"); rJSON.put("features", "min_osavi"); 
		rJSON.put("plotID", 19);
		rJSON.put("filesystem", "roots");
		
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
			
			RadixArizonaQueryWithRspTest vqt = new RadixArizonaQueryWithRspTest();
			
			messageRouter.addListener(vqt);
			
			//for(int i=0 ;i < 10; i++) {
				
				gc.sendMessage(targetNode, vr);
				
			//}
			
		} finally {
			
			Thread.sleep(100*1000);
			gc.close();
		}
	}
	
	private static Event createPolygonRequest() {
		//33.061982, -111.970253
		//(40.65319061279297, -104.9959945678711), (40.653018951416016, -104.99565124511719)
		float lat1 = 33.061851f;
		float lat2 = 33.061588f;
		//33.060944, -111.970672
		float lon1 = -111.969627f;
		float lon2 = -111.969301f;
		
		
		Coordinates c1 = new Coordinates(lat2, lon1);
		Coordinates c2 = new Coordinates(lat2, lon2);
		Coordinates c3 = new Coordinates(lat1, lon2);
		Coordinates c4 = new Coordinates(lat1, lon1);
		//Coordinates c5 = new Coordinates(36.78f, -107.64f);
		
		List<Coordinates> cl = new ArrayList<Coordinates>();
		cl.add(c1); cl.add(c2); cl.add(c3); cl.add(c4);
		
		//GeoHash.decodeHash("9xjr6b8m");
		
		//System.out.println(GeoHash.getIntersectingGeohashes(cl, 8));
		
		QueryRequest qr = new QueryRequest("roots-arizona", cl);
		qr.setSensorName("irt");
		qr.setTime("2018-9-28");
		
		// THE QUERY FOR
		
		//Query q = new Query(new Operation(new Expression(Operator.EQUAL, new Feature("date", "2018-9-28"))));
		Query q1 = new Query(new Operation(new Expression(Operator.EQUAL, new Feature("sensorType", "irt"))));
		
		//Query allQ = queryIntersection(q,q1);
		
		qr.setMetdataQuery(q1);
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
		args[0] = "lattice-1.cs.colostate.edu";
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
	
	
	public static Query queryIntersection(Query q1, Query q2) {
		if (q1 != null && q2 != null) {
			Query query = new Query();
			for (Operation q1Op : q1.getOperations()) {
				for (Operation q2Op : q2.getOperations()) {
					Operation op = new Operation(q1Op.getExpressions());
					op.addExpressions(q2Op.getExpressions());
					query.addOperation(op);
				}
			}
			return query;
		} else if (q1 != null) {
			return q1;
		} else if (q2 != null) {
			return q2;
		} else {
			return null;
		}
	}
	// [END Main]
}
