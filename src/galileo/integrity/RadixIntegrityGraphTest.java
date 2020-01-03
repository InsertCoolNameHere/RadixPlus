package galileo.integrity;

import java.util.List;

import galileo.dataset.feature.Feature;
import galileo.fs.GeospatialFileSystem;
import galileo.query.Expression;
import galileo.query.Operation;
import galileo.query.Query;

public class RadixIntegrityGraphTest {

	
	public static void main(String arg[]) {
		
		RadixIntegrityGraph rig = new RadixIntegrityGraph("plotID:1,"+GeospatialFileSystem.TEMPORAL_YEAR_FEATURE+":1,"+GeospatialFileSystem.TEMPORAL_MONTH_FEATURE+":1,"+GeospatialFileSystem.TEMPORAL_DAY_FEATURE+":1,sensorType:9", "/iplant/home/radix_subterra", "roots-arizona");
		rig.addPath("/iplant/home/radix_subterra/roots-arizona/20403/2018/9/28/irt/20403-2018-9-28-irt.gblock$$3373363902");
		rig.addPath("/iplant/home/radix_subterra/roots-arizona/20420/2018/9/28/irt/20420-2018-9-28-irt.gblock$$2550387965");
		rig.addPath("/iplant/home/radix_subterra/roots-arizona/20419/2018/9/28/irt/20419-2018-9-28-irt.gblock$$3522680225");
		rig.addPath("/iplant/home/radix_subterra/roots-arizona/20404/2018/9/28/irt/20404-2018-9-28-irt.gblock$$1043362283");
		
		
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
		q.addOperation(new Operation(new Expression(">", new Feature("plotID", 20405)),new Expression("==", new Feature(GeospatialFileSystem.TEMPORAL_YEAR_FEATURE, 2018)),new Expression("==", new Feature("sensorType", "irt"))));
		//q.addOperation(new Operation(new Expression("==", new Feature("year", 2014)),new Expression("==", new Feature("month", 6))));
		return q;
	}
	
}
