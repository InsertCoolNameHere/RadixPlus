/*
Copyright (c) 2018, Computer Science Department, Colorado State University
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

This software is provided by the copyright holders and contributors "as is" and
any express or implied warranties, including, but not limited to, the implied
warranties of merchantability and fitness for a particular purpose are
disclaimed. In no event shall the copyright holder or contributors be liable for
any direct, indirect, incidental, special, exemplary, or consequential damages
(including, but not limited to, procurement of substitute goods or services;
loss of use, data, or profits; or business interruption) however caused and on
any theory of liability, whether in contract, strict liability, or tort
(including negligence or otherwise) arising in any way out of the use of this
software, even if advised of the possibility of such damage.
*/
package galileo.dht;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import galileo.comm.GalileoEventMap;
import galileo.comm.MetadataResponse;
import galileo.comm.QueryResponse;
import galileo.event.BasicEventWrapper;
import galileo.event.Event;
import galileo.event.EventContext;
import galileo.net.ClientMessageRouter;
import galileo.net.GalileoMessage;
import galileo.net.MessageListener;
import galileo.net.NetworkDestination;
import galileo.net.RequestListener;
import galileo.serialization.SerializationException;

/**
 * This class will collect the responses from all the nodes of galileo and then
 * transfers the result to the listener. Used by the {@link StorageNode} class.
 * 
 * @author kachikaran
 */
public class ClientRequestHandler implements MessageListener {

	private static final Logger logger = Logger.getLogger("galileo");
	private GalileoEventMap eventMap;
	private BasicEventWrapper eventWrapper;
	private ClientMessageRouter router;
	private AtomicInteger expectedResponses;
	private Collection<NetworkDestination> nodes;
	private EventContext clientContext;
	private List<GalileoMessage> responses;
	private RequestListener requestListener;
	private Event response;
	private long elapsedTime;

	public ClientRequestHandler(Collection<NetworkDestination> nodes, EventContext clientContext,
			RequestListener listener) throws IOException {
		this.nodes = nodes;
		this.clientContext = clientContext;
		this.requestListener = listener;

		this.router = new ClientMessageRouter(true);
		this.router.addListener(this);
		this.responses = new ArrayList<GalileoMessage>();
		this.eventMap = new GalileoEventMap();
		this.eventWrapper = new BasicEventWrapper(this.eventMap);
		this.expectedResponses = new AtomicInteger(this.nodes.size());
	}

	public void closeRequest() {
		silentClose(); // closing the router to make sure that no new responses
						// are added.
		class LocalFeature implements Comparable<LocalFeature> {
			String name;
			String type;
			int order;

			LocalFeature(String name, String type, int order) {
				this.name = name;
				this.type = type;
				this.order = order;
			}

			@Override
			public boolean equals(Object obj) {
				if (obj == null || !(obj instanceof LocalFeature))
					return false;
				LocalFeature other = (LocalFeature) obj;
				if (this.name.equalsIgnoreCase(other.name) && this.type.equalsIgnoreCase(other.type)
						&& this.order == other.order)
					return true;
				return false;
			}

			@Override
			public int hashCode() {
				return name.hashCode() + type.hashCode() + String.valueOf(this.order).hashCode();
			}

			@Override
			public int compareTo(LocalFeature o) {
				return this.order - o.order;
			}
		}
		Map<String, Set<LocalFeature>> resultMap = new HashMap<>();
		int responseCount = 0;
		Map<String, List<String>> plotResultMap = new HashMap<>();
		for (GalileoMessage gresponse : this.responses) {
			responseCount++;
			Event event;
			try {
				event = this.eventWrapper.unwrap(gresponse);
				if (event instanceof QueryResponse && this.response instanceof QueryResponse) {
					QueryResponse actualResponse = (QueryResponse) this.response;
					actualResponse.setElapsedTime(elapsedTime);
					QueryResponse eventResponse = (QueryResponse) event;
					JSONObject responseJSON = actualResponse.getJSONResults();
					JSONObject eventJSON = eventResponse.getJSONResults();
					if (responseJSON.length() == 0) {
						for (String name : JSONObject.getNames(eventJSON))
							responseJSON.put(name, eventJSON.get(name));
					} else {
						if (responseJSON.has("queryId") && eventJSON.has("queryId")
								&& responseJSON.getString("queryId").equalsIgnoreCase(eventJSON.getString("queryId"))) {
							if (actualResponse.isDryRun()) {
								JSONObject actualResults = responseJSON.getJSONObject("result");
								
								JSONObject eventResults = eventJSON.getJSONObject("result");
								if (null != JSONObject.getNames(eventResults)) {
									for (String name : JSONObject.getNames(eventResults)) {
										if (actualResults.has(name)) {
											JSONArray ar = actualResults.getJSONArray(name);
											JSONArray er = eventResults.getJSONArray(name);
											for (int i = 0; i < er.length(); i++) {
												ar.put(er.get(i));
											}
										} else {
											actualResults.put(name, eventResults.getJSONArray(name));
										}
									}
								}
							} else {
								JSONArray actualResults = responseJSON.getJSONArray("result");
								JSONArray eventResults = eventJSON.getJSONArray("result");
	
								for (int i = 0; i < eventResults.length(); i++)
									actualResults.put(eventResults.getJSONObject(i));
							}
							if (responseJSON.has("hostProcessingTime")) {
								JSONObject aHostProcessingTime = responseJSON.getJSONObject("hostProcessingTime");
								JSONObject eHostProcessingTime = eventJSON.getJSONObject("hostProcessingTime");

								JSONObject aHostFileSize = responseJSON.getJSONObject("hostFileSize");
								JSONObject eHostFileSize = eventJSON.getJSONObject("hostFileSize");

								for (String key : eHostProcessingTime.keySet())
									aHostProcessingTime.put(key, eHostProcessingTime.getLong(key));
								for (String key : eHostFileSize.keySet())
									aHostFileSize.put(key, eHostFileSize.getLong(key));

								responseJSON.put("totalFileSize",
										responseJSON.getLong("totalFileSize") + eventJSON.getLong("totalFileSize"));
								responseJSON.put("totalNumPaths",
										responseJSON.getLong("totalNumPaths") + eventJSON.getLong("totalNumPaths"));
								responseJSON.put("totalProcessingTime",
										java.lang.Math.max(responseJSON.getLong("totalProcessingTime"),
												eventJSON.getLong("totalProcessingTime")));
								responseJSON.put("totalBlocksProcessed", responseJSON.getLong("totalBlocksProcessed")
										+ eventJSON.getLong("totalBlocksProcessed"));
							}
						}
					}
				} else if (event instanceof MetadataResponse && this.response instanceof MetadataResponse) {
					MetadataResponse emr = (MetadataResponse) event;
					JSONArray emrResults = emr.getResponse().getJSONArray("result");
					JSONObject emrJSON = emr.getResponse();
					if ("galileo#plot".equalsIgnoreCase(emrJSON.getString("kind")) && "summary".equalsIgnoreCase(emrJSON.getString("type"))) {
						//Add each item for each day
						//One feature may have many days of data
						for (int i = 0; i < emrResults.length(); i++) {
							String plotJSON = emrResults.get(i).toString();
							String feature = plotJSON.toString().split("=")[0];
							String value = plotJSON.toString().split("=")[1];
							if (plotResultMap.get(feature) == null) 
								plotResultMap.put(feature, new ArrayList<>());
							plotResultMap.get(feature).add(value);
						}
						if (this.responses.size() == responseCount) {
							JSONObject jsonResponse = new JSONObject();
							jsonResponse.put("kind",  "galileo#plot");
							jsonResponse.put("type", "summary");
							JSONArray featureArray = new JSONArray();
							for (Map.Entry<String, List<String>> entry : plotResultMap.entrySet()) {
								//plot is always the same genotype
								if (entry.getKey().equals("genotype")) {
									featureArray.put("genotype:" +entry.getValue().get(0));
								}
								else if (entry.getKey().equals("plotID")) {
									featureArray.put("ID:" + Integer.parseInt(entry.getValue().get(0)));
								}
								else if (entry.getKey().contains("avg") || entry.getKey().contains("count")){
									double sum = 0;
									for (String val : entry.getValue()) {
										sum += Double.parseDouble(val);
									}
									double avg = sum / entry.getValue().size();
									if (entry.getKey().equals("count"))
										featureArray.put(entry.getKey() + ":" + sum);
									else
										featureArray.put(entry.getKey() + ":" + avg);
								}
								else if (entry.getKey().contains("max")) {
									double max = Double.parseDouble(entry.getValue().get(0));
									for (String val : entry.getValue())
										if (Double.parseDouble(val) > max)
											max = Double.parseDouble(val);
									featureArray.put(entry.getKey() + ":" + max);
								}
								else if (entry.getKey().contains("min")) {
									double min = Double.parseDouble(entry.getValue().get(0));
									for (String val : entry.getValue())
										if (Double.parseDouble(val) < min)
											min = Double.parseDouble(val);
									featureArray.put(entry.getKey() + ":" + min);
								}
								else if (entry.getKey().contains("std")) {
									double sum = 0;
									for (String val : entry.getValue())
										sum += Math.pow(Double.parseDouble(val), 2);//sum up the variance
									double avgVariance = sum/entry.getValue().size();
									featureArray.put(entry.getKey() + ":" + Math.sqrt(avgVariance));
								}
							}
							jsonResponse.put("result", featureArray);
							this.response = new MetadataResponse(jsonResponse);
						}
					}
					else if ("galileo#plot".equalsIgnoreCase(emrJSON.getString("kind")) && "series".equalsIgnoreCase(emrJSON.getString("type"))) {
						for (int i = 0; i < emrResults.length(); i++) {
							String plotJSON = emrResults.get(i).toString();
							String date = plotJSON.split("->")[0];
							String[] featuresAndVals = plotJSON.split("->")[1].split(",");
							if (plotResultMap.get(date) == null)
								plotResultMap.put(date, new ArrayList<>());
							for (String featVal : featuresAndVals)
								plotResultMap.get(date).add(featVal);
						}
						if (this.responses.size() == responseCount) {
							JSONObject jsonResponse = new JSONObject();
							jsonResponse.put("kind",  "galileo#plot");
							jsonResponse.put("type", "series");
							JSONArray featureArray = new JSONArray();
							for (Map.Entry<String, List<String>> entry : plotResultMap.entrySet()) {
								String toPut = entry.getKey()+"->";
								for (String feat : entry.getValue()) {
									toPut += feat+",";
								}
								featureArray.put(toPut.substring(0, toPut.length()-1));
							}
							
							jsonResponse.put("result", featureArray);
							this.response = new MetadataResponse(jsonResponse);
						}
					}
					else if ("galileo#features".equalsIgnoreCase(emrJSON.getString("kind"))) {
						for (int i = 0; i < emrResults.length(); i++) {
							JSONObject fsJSON = emrResults.getJSONObject(i);
							for (String fsName : fsJSON.keySet()) {
								Set<LocalFeature> featureSet = resultMap.get(fsName);
								if (featureSet == null) {
									featureSet = new HashSet<LocalFeature>();
									resultMap.put(fsName, featureSet);
								}
								JSONArray features = fsJSON.getJSONArray(fsName);
								for (int j = 0; j < features.length(); j++) {
									JSONObject jsonFeature = features.getJSONObject(j);
									featureSet.add(new LocalFeature(jsonFeature.getString("name"),
											jsonFeature.getString("type"), jsonFeature.getInt("order")));
								}
							}
						}
						if (this.responses.size() == responseCount) {
							JSONObject jsonResponse = new JSONObject();
							jsonResponse.put("kind", "galileo#features");
							JSONArray fsArray = new JSONArray();
							for (String fsName : resultMap.keySet()) {
								JSONObject fsJSON = new JSONObject();
								JSONArray features = new JSONArray();
								for (LocalFeature feature : new TreeSet<>(resultMap.get(fsName)))
									features.put(new JSONObject().put("name", feature.name).put("type", feature.type)
											.put("order", feature.order));
								fsJSON.put(fsName, features);
								fsArray.put(fsJSON);
							}
							jsonResponse.put("result", fsArray);
							this.response = new MetadataResponse(jsonResponse);
						}
					} else if ("galileo#filesystem".equalsIgnoreCase(emrJSON.getString("kind"))) {
						MetadataResponse amr = (MetadataResponse) this.response;
						JSONObject amrJSON = amr.getResponse();
						if (amrJSON.getJSONArray("result").length() == 0)
							amrJSON.put("result", emrResults);
						else {
							JSONArray amrResults = amrJSON.getJSONArray("result");
							for (int i = 0; i < emrResults.length(); i++) {
								JSONObject emrFilesystem = emrResults.getJSONObject(i);
								for (int j = 0; j < amrResults.length(); j++) {
									JSONObject amrFilesystem = amrResults.getJSONObject(j);
									if (amrFilesystem.getString("name")
											.equalsIgnoreCase(emrFilesystem.getString("name"))) {
										long latestTime = amrFilesystem.getLong("latestTime");
										long earliestTime = amrFilesystem.getLong("earliestTime");
										if (latestTime == 0 || latestTime < emrFilesystem.getLong("latestTime")) {
											amrFilesystem.put("latestTime", emrFilesystem.getLong("latestTime"));
											amrFilesystem.put("latestSpace", emrFilesystem.getString("latestSpace"));
										}
										if (earliestTime == 0 || (earliestTime > emrFilesystem.getLong("earliestTime")
												&& emrFilesystem.getLong("earliestTime") != 0)) {
											amrFilesystem.put("earliestTime", emrFilesystem.getLong("earliestTime"));
											amrFilesystem.put("earliestSpace",
													emrFilesystem.getString("earliestSpace"));
										}
										break;
									}
								}
							}
						}
					} else if ("galileo#overview".equalsIgnoreCase(emrJSON.getString("kind"))) {
						logger.info(emrJSON.getString("kind") + ": emr results length = " + emrResults.length());
						MetadataResponse amr = (MetadataResponse) this.response;
						JSONObject amrJSON = amr.getResponse();
						if (amrJSON.getJSONArray("result").length() == 0)
							amrJSON.put("result", emrResults);
						else {
							JSONArray amrResults = amrJSON.getJSONArray("result");
							for (int i = 0; i < emrResults.length(); i++) {
								JSONObject efsJSON = emrResults.getJSONObject(i);
								String efsName = efsJSON.keys().next();
								JSONObject afsJSON = null;
								for (int j = 0; j < amrResults.length(); j++) {
									if (amrResults.getJSONObject(j).has(efsName)) {
										afsJSON = amrResults.getJSONObject(j);
										break;
									}
								}
								if (afsJSON == null)
									amrResults.put(efsJSON);
								else {
									JSONArray eGeohashes = efsJSON.getJSONArray(efsName);
									JSONArray aGeohashes = afsJSON.getJSONArray(efsName);
									for (int j = 0; j < eGeohashes.length(); j++) {
										JSONObject eGeohash = eGeohashes.getJSONObject(j);
										JSONObject aGeohash = null;
										for (int k = 0; k < aGeohashes.length(); k++) {
											if (aGeohashes.getJSONObject(k).getString("region")
													.equalsIgnoreCase(eGeohash.getString("region"))) {
												aGeohash = aGeohashes.getJSONObject(k);
												break;
											}
										}
										if (aGeohash == null)
											aGeohashes.put(eGeohash);
										else {
											long eTimestamp = eGeohash.getLong("latestTimestamp");
											int blockCount = aGeohash.getInt("blockCount")
													+ eGeohash.getInt("blockCount");
											long fileSize = aGeohash.getInt("fileSize") + eGeohash.getInt("fileSize");
											aGeohash.put("blockCount", blockCount);
											aGeohash.put("fileSize", fileSize);
											if (eTimestamp > aGeohash.getLong("latestTimestamp"))
												aGeohash.put("latestTimestamp", eTimestamp);
										}
									}
								}
							}
						}
					}
				}
			} catch (IOException | SerializationException e) {
				logger.log(Level.SEVERE, "An exception occurred while processing the response message. Details follow:"
						+ e.getMessage(), e);
			} catch (Exception e) {
				logger.log(Level.SEVERE,
						"An unknown exception occurred while processing the response message. Details follow:"
								+ e.getMessage(), e);
			}
		}
		if (this.response instanceof QueryResponse) {
			QueryResponse actualResponse = (QueryResponse) this.response;
			JSONArray json = actualResponse.getJSONResults().getJSONArray("result");
			Set<String> noDupes = new HashSet<>();
			for(int i = 0; i < json.length(); i++) {
				JSONArray paths = ((JSONObject)json.get(i)).getJSONArray("filePath");
				for (int j = 0; j < paths.length(); j++) 
					noDupes.add((String)paths.get(j));
			}
			JSONArray newArray = new JSONArray();
			for (String s : noDupes) {
				newArray.put(s);
			}
			JSONObject result = new JSONObject();
			result.put("result", newArray);
			this.response = new QueryResponse(((QueryResponse) this.response).getId(), ((QueryResponse) this.response).getHeader(), result);
		}
		this.requestListener.onRequestCompleted(this.response, clientContext, this);
	}

	@Override
	public void onMessage(GalileoMessage message) {
		if (null != message)
			this.responses.add(message);
		int awaitedResponses = this.expectedResponses.decrementAndGet();
		logger.log(Level.INFO, "Awaiting " + awaitedResponses + " more message(s)");
		if (awaitedResponses <= 0) {
			this.elapsedTime = System.currentTimeMillis() - this.elapsedTime;
			logger.log(Level.INFO, "Closing the request and sending back the response.");
			new Thread() {
				public void run() {
					ClientRequestHandler.this.closeRequest();
				}
			}.start();
		}
	}

	/**
	 * Handles the client request on behalf of the node that received the
	 * request
	 * 
	 * @param request
	 *            - This must be a server side event: Generic Event or
	 *            QueryEvent
	 * @param response
	 */
	public void handleRequest(Event request, Event response) {
		try {
			this.response = response;
			GalileoMessage mrequest = this.eventWrapper.wrap(request);
			for (NetworkDestination node : nodes) {
				this.router.sendMessage(node, mrequest);
			}
			this.elapsedTime = System.currentTimeMillis();
		} catch (IOException e) {
			logger.log(Level.INFO,
					"Failed to send request to other nodes in the network. Details follow: " + e.getMessage());
		}
	}

	public void silentClose() {
		try {
			this.router.forceShutdown();
		} catch (Exception e) {
			logger.log(Level.INFO, "Failed to shutdown the completed client request handler: ", e);
		}
	}

	@Override
	public void onConnect(NetworkDestination endpoint) {

	}

	@Override
	public void onDisconnect(NetworkDestination endpoint) {

	}
}
