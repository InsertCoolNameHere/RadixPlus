package galileo.resource;

import java.io.File;
import java.io.PrintWriter;
import org.restlet.representation.Representation;
import org.restlet.representation.FileRepresentation;
import org.restlet.resource.Post;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.restlet.resource.Get;
import org.restlet.data.MediaType;

import galileo.comm.FilesystemAction;
import galileo.comm.FilesystemRequest;
import galileo.comm.MetadataRequest;
import galileo.comm.MetadataResponse;
import galileo.event.Event;
import galileo.service.ColumbusServerApplication;

public class FilesystemServerResource extends ColumbusServerResource {

	private static final Logger LOGGER = Logger.getLogger(FilesystemServerResource.class.getName());

	@Get("?names")
	public String getFilesystems() {
		try {
			String timezone = getQueryValue("timezone");
			if(timezone == null)
				timezone = "GMT";
			JSONObject requestJSON = new JSONObject();
			requestJSON.put("kind", "galileo#filesystem");
			MetadataRequest mr = new MetadataRequest(requestJSON);
			LOGGER.fine(requestJSON.toString());
			Event event = sendMessage(mr);
			MetadataResponse response = (MetadataResponse)event;
			JSONObject jsonResponse = response.getResponse();
			Calendar c = Calendar.getInstance();
			c.setTimeZone(TimeZone.getTimeZone(timezone));
			JSONArray result = jsonResponse.getJSONArray("result");
			for(int i=0; i< result.length(); i++){
				JSONObject filesystem = result.getJSONObject(i);
				c.setTimeInMillis(filesystem.getLong("earliestTime"));
				filesystem.put("earliestTime", c.get(Calendar.YEAR) + "-" + String.format("%02d", c.get(Calendar.MONTH) + 1) + "-" + String.format("%02d", c.get(Calendar.DAY_OF_MONTH)));
				c.setTimeInMillis(filesystem.getLong("latestTime"));
				filesystem.put("latestTime", c.get(Calendar.YEAR) + "-" + String.format("%02d", c.get(Calendar.MONTH) + 1) + "-" + String.format("%02d", c.get(Calendar.DAY_OF_MONTH)));
			}
			return jsonResponse.toString();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failed to get filesystems", e);
			return ColumbusServerApplication.getFailureResponse("filesystems", e.getMessage());
		}
	}
	
	@Get("?overview")
	public String getOverview() {
		try {
			String timezone = getQueryValue("timezone");
			if(timezone == null)
				timezone = "GMT";
			String filesystems = getQueryValue("name");
			if(filesystems == null)
				throw new IllegalAccessException("filesystem is required to return the overview");
			JSONObject requestJSON = new JSONObject();
			requestJSON.put("kind", "galileo#overview");
			requestJSON.put("filesystem", new JSONArray(Arrays.asList(filesystems.split(","))));
			MetadataRequest mr = new MetadataRequest(requestJSON);
			LOGGER.fine(requestJSON.toString());
			Event event = sendMessage(mr);
			MetadataResponse response = (MetadataResponse)event;
			JSONObject jsonResponse = response.getResponse();
			Calendar c = Calendar.getInstance();
			c.setTimeZone(TimeZone.getTimeZone(timezone));
			JSONArray result = jsonResponse.getJSONArray("result");
			JSONArray overview = new JSONArray();
			for(int i=0; i< result.length(); i++){
				JSONObject featureCollection = new JSONObject();
				JSONArray features = new JSONArray();
				featureCollection.put("type", "FeatureCollection");
				featureCollection.put("features", features);
				JSONObject filesystem = result.getJSONObject(i);
				String fsName = filesystem.keys().next();
				JSONArray regions = filesystem.getJSONArray(fsName);
				int maxBlockCount = 0;
				for(int j=0; j<regions.length(); j++){
					JSONObject region = regions.getJSONObject(j);
					c.setTimeInMillis(region.getLong("latestTimestamp"));
					JSONObject feature = new JSONObject();
					feature.put("type", "Feature");
					JSONObject properties = new JSONObject();
					properties.put("region", region.getString("region"));
					properties.put("blocks", region.getInt("blockCount"));
					properties.put("filesize", String.format("%.2f", region.getLong("fileSize")/(1024*1024.0)) +"MB");
					properties.put("last-visited", c.get(Calendar.YEAR) + "-" + String.format("%02d", c.get(Calendar.MONTH) + 1) + "-" + String.format("%02d", c.get(Calendar.DAY_OF_MONTH)));
					feature.put("properties", properties);
					JSONObject geometry = new JSONObject();
					geometry.put("type", "Polygon");
					JSONArray coordinates = new JSONArray();
					JSONArray outer = new JSONArray();
					JSONArray spatialCoordinates = region.getJSONArray("spatialCoordinates");
					for(int k =0; k<spatialCoordinates.length(); k++){
						JSONObject vertex = spatialCoordinates.getJSONObject(k);
						JSONArray vertexPair = new JSONArray();
						vertexPair.put(vertex.getDouble("lng"));
						vertexPair.put(vertex.getDouble("lat"));
						outer.put(vertexPair);
						if(k==spatialCoordinates.length() -1){
							outer.put(outer.getJSONArray(0));
						}
					}
					coordinates.put(outer);
					geometry.put("coordinates", coordinates);
					feature.put("geometry", geometry);
					features.put(feature);
					maxBlockCount = Math.max(maxBlockCount, region.getInt("blockCount"));
				}
				for(int j =0; j<features.length(); j++){
					JSONObject feature = features.getJSONObject(j);
					JSONObject properties = feature.getJSONObject("properties");
					properties.put("max-blocks", maxBlockCount);
				}
				overview.put(new JSONObject().put(fsName, featureCollection));
			}
			jsonResponse.put("result", overview);
			return jsonResponse.toString();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failed to get the overview", e);
			return ColumbusServerApplication.getFailureResponse("overview", e.getMessage());
		}
	}
	
	@Get("?delete")
	public String deleteFilesystem() {
		try {
			String fsName = getQueryValue("name");
			FilesystemRequest fsr = new FilesystemRequest(fsName, FilesystemAction.DELETE, null, null);
			publishEvent(fsr);
			JSONObject responseJSON = new JSONObject();
			responseJSON.put("kind", "galileo#filesystem");
			responseJSON.put("action", "delete");
			responseJSON.put("status", "submitted");
			responseJSON.put("message", "Request the list of filesystems to ensure successful deletion.");
			return responseJSON.toString();
		} catch(Exception e){
			LOGGER.log(Level.SEVERE, "Failed to delete the filesystem", e);
			return ColumbusServerApplication.getFailureResponse("delete", e.getMessage());
		}
	}
	
	@Get("?hosts")
	public String getHosts(){
		try{
			ServletContext servlet = (ServletContext) getContext().getAttributes()
					.get("org.restlet.ext.servlet.ServletContext");
			// Get the path of the config file relative to the WAR
			String rootPath = servlet.getRealPath("/WEB-INF/hostnames");
			Path path = Paths.get(rootPath);
			JSONObject responseJSON = new JSONObject();
			File configFile = new File(path.toString());
			FileRepresentation hostsfile = new FileRepresentation(configFile, MediaType.TEXT_PLAIN);
			String hosts = hostsfile.getText();
			responseJSON.put("hosts", hosts);
			return responseJSON.toString();
			
		} catch (Exception e){
			LOGGER.log(Level.SEVERE, "Failed to retrieve host names ", e);
			return ColumbusServerApplication.getFailureResponse("getHosts", e.getMessage());
		}
	}
	
	@Post("?upload")
	public String uploadFiles(Representation rep) {
		try {
			File f = new File("/s/bach/j/under/mroseliu/abab.txt");
			//PrintWriter w = new PrintWriter(f);
			FileWriter fw = new FileWriter(f, true);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter w = new PrintWriter(bw);
			w.println("New chunk:");
			w.println(rep.getText());//getText() method returns text from file passed, should be sufficient
			w.close();
		} catch(Exception e){
			LOGGER.log(Level.SEVERE, "Failed to upload file", e);
			return ColumbusServerApplication.getFailureResponse("upload", e.getMessage());
		}
		JSONObject responseJSON = new JSONObject();
		responseJSON.put("result", "Upload successful!");
		return responseJSON.toString();
	}
	
}
