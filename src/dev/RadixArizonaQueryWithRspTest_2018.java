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

import galileo.comm.BlockQueryRequest;
import galileo.comm.BlockQueryResponse;
import galileo.comm.Connector;
import galileo.comm.GalileoEventMap;
import galileo.comm.MetadataRequest;
import galileo.comm.MetadataResponse;
import galileo.comm.QueryRequest;
import galileo.comm.QueryResponse;
import galileo.dataset.Coordinates;
import galileo.dataset.feature.Feature;
import galileo.dht.Partitioner;
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

public class RadixArizonaQueryWithRspTest_2018 implements MessageListener {
	
	private static int isQuery = 0;
	
	public static long start = 0;
	
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
			
			System.out.println("RESPONSE RECEIVED..." + (System.currentTimeMillis() - start));
			
			if (isQuery == 0) {
				QueryResponse response = (QueryResponse) wrapper.unwrap(message);
				
				System.out.println("RESPONSE\n=============\n");
				System.out.println(response.getJSONResults().get("result").toString());
				System.out.println(response.getJSONResults().get("totalNumPaths"));
			} else if (isQuery == 1){
				MetadataResponse response = (MetadataResponse) wrapper.unwrap(message);

				System.out.println(response.getResponse().toString());
			} else if (isQuery == 2){
				BlockQueryResponse response = (BlockQueryResponse) wrapper.unwrap(message);

				System.out.println(response.pathResults);
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
		rJSON.put("type", "summary");
		rJSON.put("plotID", 9971);
		rJSON.put("filesystem", "roots-arizona-2018");
		
		reqJson = rJSON.toString();
		
		System.out.println(reqJson);
		
		Event vr = null;
		
		if(isQuery == 0) {
			
			vr = createPolygonRequest();
			
		} else if(isQuery == 1){
		
			vr = new MetadataRequest(reqJson) ;
		
		} else if(isQuery == 2){
		
			vr = createRIGRequest() ;
		
		}
		
		try {
			
			ClientMessageRouter messageRouter = gc.getMessageRouter();
			
			RadixArizonaQueryWithRspTest_2018 vqt = new RadixArizonaQueryWithRspTest_2018();
			
			messageRouter.addListener(vqt);
			
			//for(int i=0 ;i < 10; i++) {
				start = System.currentTimeMillis();
				gc.sendMessage(targetNode, vr);
				
			//}
			
		} finally {
			
			Thread.sleep(1000*1000);
			gc.close();
		}
	}
	
	private static Event createPolygonRequest() {
		//-111.96569161279297 -111.96481223242188 33.06369681494141 33.065075920654294

		/*
		 * Coordinates c11 = new Coordinates(33.064443019694764f, -111.964975382f);
		 * Coordinates c21 = new Coordinates(33.0644430196937f, -111.96499170149933f);
		 * Coordinates c31 = new Coordinates(33.06442054969379f, -111.96499146649519f);
		 * Coordinates c41 = new Coordinates(33.06442054969485f, -111.964975147f);
		 */
		//33.061982, -111.970253
		//(40.65319061279297, -104.9959945678711), (40.653018951416016, -104.99565124511719)
		//(33.06200408935547, -111.96910095214844), (33.06132125854492, -111.96910095214844), (33.06132125854492, -111.96965026855469), (33.06200408935547, -111.96965026855469)
		float lat2 = 33.06172f;
		float lat1 = 33.0610f;
		//33.060944, -111.970672
		float lon2 = -111.9694f;
		float lon1 = -111.970f;
		
		
		Coordinates c1 = new Coordinates(lat2, lon2);
		Coordinates c2 = new Coordinates(lat2, lon1);
		Coordinates c3 = new Coordinates(lat1, lon1);
		Coordinates c4 = new Coordinates(lat1, lon2);
		
		List<Coordinates> cl = new ArrayList<Coordinates>();
		cl.add(c1); cl.add(c2); cl.add(c3); cl.add(c4); 
		
		//System.out.println("WE GOT:"+GeoHash.getIntersectingGeohashes(cl, 8).length);
		
		//GeoHash.decodeHash("9xjr6b8m");
		
		//System.out.println(GeoHash.getIntersectingGeohashes(cl, 8));
		
		QueryRequest qr = new QueryRequest("roots-arizona-2018", cl);
		qr.setSensorName("irt");
		qr.setTime("2018-xx-xx-xx");
		
		// THE QUERY FOR
		
		//Query q = new Query(new Operation(new Expression(Operator.EQUAL, new Feature("date", "2018-9-28"))));
		//Query q1 = new Query(new Operation(new Expression(Operator.EQUAL, new Feature("sensorType", "irt"))));
		
		//Query allQ = queryIntersection(q,q1);
		
		//qr.setMetdataQuery(q1);
		return qr;
	}
	
	
	private static Event createRIGRequest() {
		//33.061982, -111.970253
		//(40.65319061279297, -104.9959945678711), (40.653018951416016, -104.99565124511719)
		//(33.06200408935547, -111.96910095214844), (33.06132125854492, -111.96910095214844), (33.06132125854492, -111.96965026855469), (33.06200408935547, -111.96965026855469)
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
		
		BlockQueryRequest qr = new BlockQueryRequest("roots-arizona-2018", cl,null, "2018-xx-xx-xx");
		//qr.setSensorName("irt");
		//qr.setTime("2018-09-28-xx");
		
		// THE QUERY FOR
		
		//Query q = new Query(new Operation(new Expression(Operator.EQUAL, new Feature("date", "2018-9-28"))));
		//Query q1 = new Query(new Operation(new Expression(Operator.EQUAL, new Feature("sensorType", "irt"))));
		
		//Query allQ = queryIntersection(q,q1);
		
		//qr.setMetdataQuery(q1);
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
		args[0] = "lattice-6.cs.colostate.edu";
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
