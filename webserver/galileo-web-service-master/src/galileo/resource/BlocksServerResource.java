 package galileo.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;

import galileo.comm.QueryRequest;
import galileo.comm.QueryResponse;
import galileo.dataset.Coordinates;
import galileo.event.Event;
import galileo.model.Condition;
import galileo.query.Query;
import galileo.service.ColumbusServerApplication;

public class BlocksServerResource extends ColumbusServerResource {

	private static final Logger LOGGER = Logger.getLogger(BlocksServerResource.class.getName());

	@Get
	public String getBlocks() {
		try {
			String fsName = getQueryValue("fs");
			String date = getQueryValue("date");
			QueryRequest qr = new QueryRequest(fsName, date);
			qr.enableDryRun();
			Event event = sendMessage(qr);
			QueryResponse response = ((QueryResponse) event);
			JSONObject jsonResponse = response.getJSONResults();
			jsonResponse.put("elapsedTime", response.getElapsedTime() + "ms");
			return jsonResponse.toString();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failed to get the blocks", e);
			return ColumbusServerApplication.getFailureResponse("blocks", e.getMessage());
		}
	}

	@Post
	public String getBlocks(Representation rep) {
		try {
			String json = rep.getText();
			JSONObject jsonQuery = new JSONObject(json);
			System.out.println(jsonQuery);
			QueryRequest qr = null;
			Query query = null;
			if (jsonQuery.get("constraint") != JSONObject.NULL) {
				Condition c = Condition.getCondition(jsonQuery.getJSONObject("constraint"));
				query = new Query();
				c.buildQuery(query);
			}
			List<Coordinates> spatial = null;
			if (jsonQuery.get("spatial") != JSONObject.NULL) {
				JSONArray polygon = jsonQuery.getJSONArray("spatial");
				spatial = new ArrayList<>();
				for (int i = 0; i < polygon.length(); i++) {
					JSONObject vertex = polygon.getJSONObject(i);
					double lat = Float.parseFloat(String.valueOf(vertex.get("lat")));
					double lon = Float.parseFloat(String.valueOf(vertex.get("lon")));
					spatial.add(new Coordinates(lat, lon));
				}
			}
			boolean temporal = jsonQuery.get("temporal") != JSONObject.NULL
					&& jsonQuery.getString("temporal").matches(".*\\d+.*");
			String timeString = null;
			if (temporal) {
				String[] temporalQuery = jsonQuery.getString("temporal").split("-");
				timeString = temporalQuery[0] + "-"
						+ (temporalQuery[1].length() < 2 ? "0" + temporalQuery[1] : temporalQuery[1]) + "-"
						+ (temporalQuery[2].length() < 2 ? "0" + temporalQuery[2] : temporalQuery[2]) + "-"
						+ (temporalQuery[3].length() < 2 ? "0" + temporalQuery[3] : temporalQuery[3]);
			}

			if (query == null && spatial == null && !temporal)
				throw new Exception(
						"All spatial, temporal and attribute filter/constraint are missing. Query cannot be made.");

			qr = (query != null) ? new QueryRequest(jsonQuery.getString("identifier"), null, query)
					: (spatial != null) ? new QueryRequest(jsonQuery.getString("identifier"), spatial)
							: new QueryRequest(jsonQuery.getString("identifier"), timeString);
			if (spatial != null)
				qr.setPolygon(spatial);
			if (temporal)
				qr.setTime(timeString);
			LOGGER.info("spatial=" + spatial);
			LOGGER.info("temporal=" + timeString);
			if (query != null)
				LOGGER.info("query=" + query.toString());
//			qr.enableDryRun(); 

			Event event = sendMessage(qr);
			QueryResponse response = ((QueryResponse) event);
			LOGGER.info("jsonResults=" + response.getJSONResults());
			JSONObject jsonResponse = response.getJSONResults();
			jsonResponse.put("elapsedTime", response.getElapsedTime() + "ms");
			
			return jsonResponse.toString();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failed to get the blocks", e);
			return ColumbusServerApplication.getFailureResponse("blocks", e.getMessage());
		}
	}
}
