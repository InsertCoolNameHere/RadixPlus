package Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import galileo.comm.Connector;
import galileo.comm.QueryRequest;
import galileo.comm.QueryResponse;
import galileo.comm.TemporalType;
import galileo.dataset.Block;
import galileo.dataset.Coordinates;
import galileo.dataset.Metadata;
import galileo.dataset.Point;
import galileo.dataset.feature.Feature;
import galileo.event.Event;
import galileo.net.NetworkDestination;
import galileo.query.Expression;
import galileo.query.Operation;
import galileo.query.Operator;
import galileo.query.Query;

public class TestGet {
	public static void main(String [] args) throws IOException, InterruptedException{
		Connector connector = new Connector();
//		QueryRequest queryRequest = new QueryRequest("mimic2", new Query(new Operation(new Expression("==", new Feature("age", 76)))), null);
//		NetworkDestination storageNode = new NetworkDestination("seoul.cs.colostate.edu", 5634);
//		QueryResponse response = (QueryResponse)connector.sendMessage(storageNode, queryRequest);
//		NetworkInfo network = NetworkConfig.readNetworkDescription(SystemConfig.getNetworkConfDir());
//		JSONObject results = response.getJSONResults();
		//Obtaining data from blocks where age > 50 && gender = 'F'
			//In order to evaluate the query as "age>50 && gender = "F", all expressions must be part of the same operation
			//If handing multiple operations to the query constructor, they will be evaluated with || instead
//		Query metaQuery = new Query(new Operation(new Expression(Operator.EQUAL, new Feature("age", 76)),
//				new Expression(Operator.EQUAL, new Feature("gender", "F"))));
//		QueryRequest qr = new QueryRequest("mimic2", metaQuery, null);
//		//Any Galileo storage node hostname and port number
//		NetworkDestination storageNode = new NetworkDestination("albany.cs.colostate.edu", 5634);
//		Event event = connector.sendMessage(storageNode, qr);
//		QueryResponse response = (QueryResponse)event;
//		JSONObject jsonResponse = response.getJSONResults();
//		System.out.println(jsonResponse.toString());
//
//		while(response.hasBlocks()) {
//		    Block block = response.getNextBlock();
//		    byte[] blockData = block.getData();
////		    Metadata meta = block.getMetadata();
////		    System.out.println(meta);
//		    //do the processing
//		    //if you wish to obtain multiple blocks in a single go
//		    //List<Block> blocks = response.getNextBlocks(5);
//		}
//
//		connector.close();
		
		List<Coordinates> queryPoly = new ArrayList<>();
		// 2800 plots polygon
//		queryPoly.add(new Coordinates(121.26802271381166, 14.159681865829254));
//		queryPoly.add(new Coordinates(121.2678309358671, 14.158895147449144));
//		queryPoly.add(new Coordinates(121.26846527829912, 14.158753407814912));
//		queryPoly.add(new Coordinates(121.26865839734819, 14.159531024169201));
//		queryPoly.add(new Coordinates(121.26802271381166, 14.159681865829254));
		
		//10 plots polygon
		queryPoly.add(new Coordinates(121.26809578876805, 14.159850863216333));
		queryPoly.add(new Coordinates(121.26806695502114, 14.159858015183787));
		queryPoly.add(new Coordinates(121.26808304827523, 14.15990450296672));
		queryPoly.add(new Coordinates(121.26810852926087, 14.159897676090099));
		queryPoly.add(new Coordinates(121.26809578876805, 14.159850863216333));
		
		//30 plots polygon
//		queryPoly.add(new Coordinates(14.159987949584533, 121.2687049600313));
//		queryPoly.add(new Coordinates(14.159910120799422, 121.26868628390923));
//		queryPoly.add(new Coordinates(14.159900103231095, 121.26873833863237));
//		queryPoly.add(new Coordinates(14.159966758581314, 121.26875582266143));
//		queryPoly.add(new Coordinates(14.15997215265505, 121.26872721243194));
//		queryPoly.add(new Coordinates(14.159983711384049, 121.26873078871074));
//		queryPoly.add(new Coordinates(14.159987949584533, 121.2687049600313));
		QueryRequest query = new QueryRequest("roots", queryPoly);
		ArrayList<Long> times = new ArrayList<>();
//		QueryRequest query = new QueryRequest("roots",null, new Query(new Operation(new Expression(Operator.LESS,new Feature("plotID", 2801)))));
//		for (int i = 0; i < 20; i++) {
			long start = System.currentTimeMillis();
			QueryResponse response = (QueryResponse)connector.sendMessage(new NetworkDestination("lattice-115", 5635), query);
			System.out.println(System.currentTimeMillis()-start);
//		}
//		long sum = 0;
//		for (Long l : times)
//			sum += l;
//		System.out.println(sum/times.size());
//		System.out.println(response.getJSONResults());
		
		connector.close();
		
		
		
		
		
	}
}
