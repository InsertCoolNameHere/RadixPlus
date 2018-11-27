package galileo.resource;

import java.net.Socket;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import galileo.net.NetworkDestination;
import galileo.service.ColumbusServerApplication;

public class StatusServerResource extends ServerResource {
	@Get
	public String sayHello() {
		JSONObject response = new JSONObject();
		response.put("greeting", "Hi There!");
		List<NetworkDestination> destinations = ((ColumbusServerApplication) getApplication()).getAllDestinations();
		Map<String, String> hostAddresses = ((ColumbusServerApplication) getApplication()).getAllHostAddresses();
		JSONArray hostStatus = new JSONArray();
		response.put("status", hostStatus);
		for (NetworkDestination destination : destinations) {
			try(Socket s = new Socket(hostAddresses.get(destination.getHostname()), destination.getPort())) {
				hostStatus.put(new JSONObject().put("hostname", destination.getHostname()).put("port", destination.getPort())
						.put("availability", "Online").put("address", hostAddresses.get(destination.getHostname())));
			} catch (Exception e) {
				hostStatus.put(new JSONObject().put("hostname", destination.getHostname()).put("port", destination.getPort())
						.put("availability", "Offline").put("address", hostAddresses.get(destination.getHostname())));
			}
		}
		return response.toString();
	}
}