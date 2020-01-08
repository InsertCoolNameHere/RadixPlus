package galileo.integrity;

import java.io.IOException;
import java.util.List;

import org.irods.jargon.core.exception.JargonException;

import galileo.dataset.feature.Feature;
import galileo.dht.IRODSManager;
import galileo.fs.GeospatialFileSystem;
import galileo.query.Expression;
import galileo.query.Operation;
import galileo.query.Query;

public class RIGFetchQueryTest {

	
	public static void main(String arg[]) throws JargonException, IOException {
		
		String whereToPut = "/tmp/sampleDownload";
		
		RadixIntegrityGraph rig = new RadixIntegrityGraph("plotID:1,"+GeospatialFileSystem.TEMPORAL_YEAR_FEATURE+":1,"+GeospatialFileSystem.TEMPORAL_MONTH_FEATURE+":1,"+GeospatialFileSystem.TEMPORAL_DAY_FEATURE+":1,sensorType:9", "/iplant/home/radix_subterra", "roots-arizona");
		rig.addPath("/iplant/home/radix_subterra/roots-arizona/20403/2018/9/28/irt/20403-2018-9-28-irt.gblock$$3373363902");
		rig.addPath("/iplant/home/radix_subterra/roots-arizona/20420/2018/9/28/irt/20420-2018-9-28-irt.gblock$$2550387965");
		rig.addPath("/iplant/home/radix_subterra/roots-arizona/20419/2018/9/28/irt/20419-2018-9-28-irt.gblock$$3522680225");
		rig.addPath("/iplant/home/radix_subterra/roots-arizona/20404/2018/9/28/irt/20404-2018-9-28-irt.gblock$$1043362283");
		
		
		rig.updatePathsIntoRIG();
		
		System.out.println("Hi "+rig.hrig.getRoot().hashValue); 
		
		
		Query query = buildQuery();
		
		List<String> evaluatedPaths = rig.evaluateQuery(query);
		
		
		for(String p : evaluatedPaths) {
			
			System.out.println(p);
		}
		
		IRODSManager im = new IRODSManager();
		
		
		// ACTUAL DOWNLOADING
		for(String p : evaluatedPaths) {
			
			String[] split = p.split("\\$\\$");
			
			String actPath = split[0];
			long hashValue = Long.valueOf(split[1]);
			if(p.contains(".gblock")) {
				
			} else {
				
				im.readRemoteDirectory(whereToPut, actPath);
			}
		}
		
		
		
	}

	private static Query buildQuery() {
		
		Query q = new Query();
		//q.addOperation(new Operation(new Expression(">", new Feature("plotID", 20405)),new Expression("==", new Feature(GeospatialFileSystem.TEMPORAL_YEAR_FEATURE, 2018)),new Expression("==", new Feature("sensorType", "irt"))));
		q.addOperation(new Operation(new Expression("==", new Feature(GeospatialFileSystem.TEMPORAL_YEAR_FEATURE, 2018)),new Expression("==", new Feature(GeospatialFileSystem.TEMPORAL_MONTH_FEATURE, 9))));
		return q;
	}
	
}
