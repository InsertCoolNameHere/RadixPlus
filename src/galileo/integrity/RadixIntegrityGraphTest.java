package galileo.integrity;

import java.util.List;

import galileo.dataset.feature.Feature;
import galileo.query.Expression;
import galileo.query.Operation;
import galileo.query.Query;

public class RadixIntegrityGraphTest {

	
	public static void main(String arg[]) {
		
		RadixIntegrityGraph rig = new RadixIntegrityGraph("year:1,month:1,day:1,sensor:9", "/iplant/home/radix_subterra", "arizona");
		rig.addPath("/iplant/home/radix_subterra/arizona/2014/5/2/irt/fname.gblock$$1756");
		rig.addPath("/iplant/home/radix_subterra/arizona/2014/5/2/sonar/fname1.gblock$$1234");
		rig.addPath("/iplant/home/radix_subterra/arizona/2014/5/4/lidar/fname2.gblock$$1214");
		rig.addPath("/iplant/home/radix_subterra/arizona/2014/6/4/lidar/fname2.gblock$$1274");
		
		rig.updatePathsIntoRIG();
		
		System.out.println("Hi "+rig.hrig.getRoot().hashValue); 
		
		
		Query query = buildQuery();
		
		List<String> evaluateQuery = rig.evaluateQuery(query);
		
		
		for(String p : evaluateQuery) {
			
			System.out.println(p);
		}
		
	}

	private static Query buildQuery() {
		Query q = new Query();
		q.addOperation(new Operation(new Expression("==", new Feature("month", 5)),new Expression("==", new Feature("sensor", "irt"))));
		q.addOperation(new Operation(new Expression("==", new Feature("year", 2014)),new Expression("==", new Feature("month", 6))));
		return q;
	}
	
}
