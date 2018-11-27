package galileo.resource;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.restlet.resource.Get;

import galileo.comm.MetadataRequest;
import galileo.comm.MetadataResponse;
import galileo.event.Event;
import galileo.service.ColumbusServerApplication;

/**
 * Handles the incoming service request for the Columbus application. An
 * instance would be created for this class by the GalileoServerApplication for
 * every incoming request. This class is also a Galileo client.
 * 
 * 
 * 
 * @author maxr1876
 *
 */
public class PlotServerResource extends ColumbusServerResource {
	private static final Logger LOGGER = Logger.getLogger(FeaturesetServerResource.class.getName());
	
	@Get
	public String getPlot() {
		try {
			JSONObject reqJSON = new JSONObject();
			reqJSON.put("kind", "galileo#plot");
			reqJSON.put("plotID", getQueryValue("plotID"));
			reqJSON.put("type", getQueryValue("kind"));
			String filesystem = getQueryValue("filesystem");
			try {
				reqJSON.put("features", getQueryValue("features"));
			}catch (Exception e) {
				//Do nothing, no features were requested
			}
			if(filesystem == null)
				throw new IllegalAccessException("filesystem is required to return the features.");
			reqJSON.put("filesystem",filesystem);
			MetadataRequest mr = new MetadataRequest(reqJSON);
			LOGGER.fine(reqJSON.toString());
			Event event = sendMessage(mr);
			MetadataResponse response = (MetadataResponse)event;
			JSONObject jsonResponse = response.getResponse();
			LOGGER.fine(jsonResponse.toString());
			return jsonResponse.toString();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failed to get features", e);
			return ColumbusServerApplication.getFailureResponse("plot", e.getMessage());
		}
	}
	
}
