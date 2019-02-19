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

/**
 * @author Max Roselius: mroseliu@rams.colostate.edu
 * 
 * This class is instantiated on each node. It implements a thread pool for handling data ingestion messages in Radix.
 * The logic here is convoluted and hard to follow. Messages are georeferenced as they come in, and stored in a hashmap
 * of <dataPoint, plotID>. After a plot is probably fully ingested, metadata extraction is performed on that plot and it is 
 * prepared to be archived in IRODS.*/
package galileo.dht;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Logger;

import org.irods.jargon.core.exception.JargonException;
import org.xerial.snappy.Snappy;

import galileo.bmp.BitmapException;
import galileo.bmp.HashGrid;
import galileo.comm.Connector;
import galileo.comm.IRODSReadyCheck;
import galileo.comm.IRODSRequest;
import galileo.comm.IRODSRequest.TYPE;
import galileo.comm.NonBlockStorageRequest;
import galileo.config.SystemConfig;
import galileo.dataset.Block;
import galileo.dataset.Coordinates;
import galileo.dataset.Metadata;
import galileo.dataset.TemporalProperties;
import galileo.dataset.feature.Feature;
import galileo.dataset.feature.FeatureSet;
import galileo.event.Event;
import galileo.fs.FileSystemException;
import galileo.fs.GeospatialFileSystem;
import galileo.net.NetworkDestination;
import galileo.util.GeoHash;

public class DataStoreHandler {
	
	private BlockingQueue<StoreMessage> unProcessedMessages;
	private ConcurrentHashMap<Integer, TimeStampedBuffer> plotIDToChunks;
	private MessageHandler[] threadPool;
	private Map<Integer, String> plotsProcessed = new HashMap<>();
	private static Logger logger = Logger.getLogger("galileo");
	private StorageNode sn;
	public ArrayList<Long> unprocessedTimes, irodsTimes, metadataTimes;
	private Timer mapClearer = new Timer();
	private Timer IRODSReadyChecker = new Timer();
	
	// LAST TIME A MESSAGE WAS PROCESSED BY THE SYSTEM, EITHER LOCAL STORAGE OR STORAGE TO IRODS
	private long lastMessageTime;
	private IRODSManager subterra;
	private Connector connector;
	private File messageLogger = new File("/s/bach/j/under/mroseliu/Documents/systemPerf/throughput.txt");
	private BufferedWriter bw;
	
	public DataStoreHandler(StorageNode sn) {
		
		lastMessageTime = System.currentTimeMillis();
		
		// SCHEDULE PLOT DATA BACKER AT 10 SEC INTERVALS
		// JUST PERFORMS LOCAL BACKUP OF DATA COLLECTED
		mapClearer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				
				HashSet<Integer> toRemove = new HashSet<>();
				
				// THIS IS FOR LOCAL STORAGE
				synchronized (plotIDToChunks) {
					for (Map.Entry<Integer, TimeStampedBuffer> entry : plotIDToChunks.entrySet()) {
						
						// IF MORE THAN 10 SECONDS HAVE PASSED SINCE NEW DATA FOR A PLOT HAS COME IN
						// TAKE THE DATA BUFFERED FOR THAT PLOT ID FROM THE BUFFER TO LOCAL GALILEO
						if (System.currentTimeMillis() - entry.getValue().getTimeStamp() > 10000) {
							
							// handleToLocal METHOD FOR THIS
							StoreMessage irodsMsg = new StoreMessage(Type.TO_LOCAL, entry.getValue().getBuffer(), (GeospatialFileSystem) sn.getFS("roots"),
									"roots", entry.getKey());
							unProcessedMessages.add(irodsMsg);
							toRemove.add(entry.getKey());
							
						}
					}
					for (Integer pID : toRemove)
						plotIDToChunks.remove(pID);
					synchronized (bw) {
						try {
							bw.flush();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
		}, 10 * 1000, 10 * 1000);
		
		// THIS IS FOR IRODS STORAGE
		// READS THE TEMPORARY GALILEO FILE WITH BACKED UP RECORDS AND SENDS IT TO IRIDS FOR STORAGE
		IRODSReadyChecker.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				if (System.currentTimeMillis() - lastMessageTime >= 300*1000) { 
					
					//If 5 minutes has passed since last message processed, data is ready to be sent to IRODS
					//First check if all other machines are ready				
					try {
						
						boolean allReady = true;
						
						String nodeFile = SystemConfig.getNetworkConfDir() + File.separator + "hostnames";
						String [] hosts = new String(Files.readAllBytes(Paths.get(nodeFile))).split(System.lineSeparator());
						
						// CHECKS IF ALL NODES ARE READY TO RECEIVE DATA FIRST
						// NODES ARE READY IF IT HAS BEEN 10 MINUTES SINCE LAST MESSAGE
						for (Event e : broadcastEvent(new IRODSReadyCheck(IRODSReadyCheck.Type.CHECK), connector)) {
							if (!((IRODSReadyCheck)e).isReady())
								allReady = false;
						}
						
						//IF ALL NODES ARE READY, initiate IRODS transfer phase
						if (allReady) {
							//default coordinator is last machine on list
							// COORDINATOR IS THE NODE THAT MAINTAINS A LOCK FOR ALL PLOT IDS
							// ONLY ONE PLOT ID CAN BE WORKED WITH AT A TIME
							NetworkDestination coordinator = new NetworkDestination(hosts[hosts.length-1].split(":")[0], Integer.parseInt(hosts[hosts.length-1].split(":")[1]));
							
							// For each plot that was processed by this machine, attempt to get the lock from coordinator
							for (Map.Entry<Integer, String> entry : plotsProcessed.entrySet()) {
								
								// LOCK REQUEST FOR EACH PLOT SENT TO COORDINATOR ONE AT A TIME
								IRODSRequest reply = (IRODSRequest)connector.sendMessage(coordinator, new IRODSRequest(TYPE.LOCK_REQUEST, entry.getKey()));
								
								// IF LOCK FOR THE REQUESTED PLOTS IS GRANTED BY THE COORDINATOR NODE
								if (reply.getType() == TYPE.LOCK_ACQUIRED) {
									
									//this machine gets privilege to write this plot file to IRODS.
									//create a StoreMessage so a thread can deal with this task
									
									// handleDataRequest METHOD FOR THIS
									StoreMessage dataRequest = new StoreMessage(Type.DATA_REQUEST, "", (GeospatialFileSystem)sn.getFS("roots"), "roots", entry.getKey());
									
									dataRequest.setFilePath(SystemConfig.getRootDir() + File.separator + "dailyTemp/" + entry.getKey() + "/" +
											entry.getValue().replaceAll("-", "/") + "/" + entry.getKey()+"-" + entry.getValue() + ".gblock");
									unProcessedMessages.offer(dataRequest);
								}
							}
						}
					} catch (IOException | InterruptedException e) {
						logger.severe("Cluster configuration file missing or corrupt");
					}
				}
			}
		}, 300*1000, 300*1000); //300 seconds = 5 minutes
		
		unProcessedMessages = new PriorityBlockingQueue<>();
		plotIDToChunks = new ConcurrentHashMap<>(100, .9f, 10);
		threadPool = new MessageHandler[10];
		unprocessedTimes = new ArrayList<>();
		irodsTimes = new ArrayList<>();
		metadataTimes = new ArrayList<>();
		this.sn = sn;
		subterra = new IRODSManager();
		try {
			connector = new Connector();
			if (!messageLogger.exists())
				messageLogger.createNewFile();
			bw = new BufferedWriter(new FileWriter(messageLogger, true));
		} catch (IOException e) {
			logger.severe("Unable to initiate connector for DataStoreHandler " + e);
		}
		for (int i = 0; i < threadPool.length; i++) {
			threadPool[i] = new MessageHandler();
			threadPool[i].start();
		}
	}
	
	public void killThreads() {
		for (MessageHandler mh : threadPool)
			mh.kill();
	}
	
	public void addMessage(StoreMessage message) {
		unProcessedMessages.offer(message);
	}
	
	public long getLastProcessedTime() {
		return this.lastMessageTime;
	}
	
	private List<Event> broadcastEvent(Event event, Connector connector) throws IOException, InterruptedException{
		
		ArrayList<Event> responses = new ArrayList<>();
		
		String nodeFile = SystemConfig.getNetworkConfDir() + File.separator + "hostnames";
		String [] hosts = new String(Files.readAllBytes(Paths.get(nodeFile))).split(System.lineSeparator());
		for (String host : hosts) {
			NetworkDestination dest = new NetworkDestination(host.split(":")[0], Integer.parseInt(host.split(":")[1]));
			
			if (!host.split(":")[0].equals(sn.getHostName())) {//don't send event to self
				Event response = connector.sendMessage(dest, event);
				responses.add(response);
			}
		}
		return responses;
	}
	
	public class MessageHandler extends Thread{
		
		private volatile boolean isAlive = true;
		private Connector connector;
		public void run() {
			try {
				this.connector = new Connector();
			}catch(IOException e) {
				logger.severe("Error creating connector for thread " + this.getName() + "!");
			}
			while (isAlive) {
				try {
					StoreMessage toProcess = unProcessedMessages.take();
					long start = System.currentTimeMillis();
					switch(toProcess.getType()){			
						case UNPROCESSED:
							handleUnprocessed(toProcess);
							long end = System.currentTimeMillis() - start;
							synchronized(unprocessedTimes) {
								unprocessedTimes.add(end);
							}
							lastMessageTime = System.currentTimeMillis();
							break;
						case TO_LOCAL:
							handleToLocal(toProcess);
							end = System.currentTimeMillis() - start;
							synchronized(irodsTimes) {
								irodsTimes.add(end);
							}
							lastMessageTime = System.currentTimeMillis();
							break;
						case TO_IRODS:
							handleToIRODS(toProcess);
							break;
						case DATA_REQUEST:
							handleDataRequest(toProcess);
							break;
						default:
							logger.severe("Unrecognized message type: " + toProcess.getType());
					}
					synchronized(bw) {
						bw.append(System.currentTimeMillis() + "\n");
					}
				} catch (InterruptedException | ParseException | IOException e) {
					logger.info("Error processing message on StorageNode. " + Arrays.toString(e.getStackTrace()));
				}
				
			}
		}
		public void kill() {
			this.isAlive = false;
		}
		
		
		private void handleUnprocessed(StoreMessage msg) throws ParseException {
			String data = msg.getData();
			SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd kk:mm:ss.SSS z yyyy");//need to change if timestamp format changes
			Calendar cal = Calendar.getInstance();
			String lineSep = System.lineSeparator();
			String [] lines = data.split(System.lineSeparator());
			GeospatialFileSystem gfs = msg.getFS();
			HashMap<NodeInfo, String> otherDests = new HashMap<>();//to be used for mapping data points to other destinations
			HashGrid grid = sn.getGlobalGrid();
			for (String line : lines) {
				double lat = Double.parseDouble(line.split(",")[2]);
				double lon = Double.parseDouble(line.split(",")[1]);
				Coordinates coords = new Coordinates(lat, lon);
				int plotID;
				try {
					plotID = grid.locatePoint(coords);
				} catch (BitmapException e) {
					logger.severe("Could not identify plot for coordinates: " + coords);
					continue;
				}
				//First ensure that this point in fact belongs on this node
				NodeInfo dest = ((SpatialHierarchyPartitioner)gfs.getPartitioner()).locateHashVal(GeoHash.encode(coords, grid.getPrecision()));
				if (dest.getHostname().equals(sn.getHostName())) {
						plotIDToChunks.computeIfAbsent(plotID, k -> new TimeStampedBuffer(new StringBuilder()));
						plotIDToChunks.get(plotID).update(line+lineSep);
						synchronized(plotIDToChunks.get(plotID)) {
							
							// WHEN THERE ARE MORE THAN 500 RECORDS FOR A PLOT, ATTEMPT TO STORE IT IN LOCAL GALILEO
							// OTHERWISE PERSIST IT IN plotIDToChunks
							if (plotIDToChunks.get(plotID) != null && plotIDToChunks.get(plotID).getBuffer().split(lineSep).length >= 500) {//this threshold is subject to change!
								//add to existing block for the plot identified
								StoreMessage irodsMsg = new StoreMessage(Type.TO_LOCAL, plotIDToChunks.get(plotID).getBuffer(), gfs, msg.getFSName(), plotID);
								unProcessedMessages.add(irodsMsg);
								plotIDToChunks.remove(plotID);
							}
						}
						String [] firstLine = lines[0].split(",");
						String timestamp = firstLine[0];
						Date parsedDate = formatter.parse(timestamp);
						cal.setTime(parsedDate);
						int month = cal.get(Calendar.MONTH) + 1;//add 1 because Calendar class months are 0 based (i.e Jan=0, Feb=1...) but we need human readable month
						int year = cal.get(Calendar.YEAR);
						int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
						plotsProcessed.put(plotID, year+"-"+month+"-"+dayOfMonth);

				}else {//the observation is to be stored on a machine other than this one
					if (!otherDests.keySet().contains(dest)) 
						otherDests.put(dest, line+lineSep);
					else
						otherDests.put(dest, otherDests.get(dest)+line+lineSep);
				}
			}
			for(Map.Entry<NodeInfo,String> entry : otherDests.entrySet()) {
				try {
					byte [] compressed = Snappy.compress(entry.getValue());
					NonBlockStorageRequest req = new NonBlockStorageRequest(compressed, msg.getFSName());
					req.setCheckAll(false);
					sn.sendEvent(entry.getKey(), req);
				} catch (IOException e) {
					logger.severe("Error sending partially processed data to node: " + entry.getKey());
				}
			}
		}
		
		private void handleDataRequest(StoreMessage msg) {
			try {
				IRODSRequest dataRequest = new IRODSRequest(TYPE.DATA_REQUEST, msg.getPlotID());
				dataRequest.setFilePath(msg.getFilePath());
				List<Event> responses = broadcastEvent(dataRequest, this.connector);
				StringBuilder plotData = new StringBuilder();
				for (Event e : responses) {
					IRODSRequest reply = (IRODSRequest)e;
					if (reply.getType() == TYPE.DATA_REPLY)
						plotData.append(reply.getData()+System.lineSeparator());
				}
				File localPlotData = new File(msg.getFilePath());
				String localContents = new String(Files.readAllBytes(Paths.get(localPlotData.getAbsolutePath())));
				plotData.append(localContents);
				localContents = plotData.toString().replaceAll("(?m)^\\s", "");//remove any extraneous new lines that found their way in
				String [] sortedLines = localContents.split(System.lineSeparator());
				Arrays.sort(sortedLines, new Comparator<String>() {
				    @Override
				    public int compare(String o1, String o2) {
				    	SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd kk:mm:ss.SSS z yyyy");//need to change if timestamp format changes
						Date d1 = null, d2 = null;
						try {
							d1 = formatter.parse(o1.split(",")[0]);
							d2 = formatter.parse(o2.split(",")[0]);
						} catch (ParseException e) {
							e.printStackTrace();
						}
						return d1.compareTo(d2);
				    }
				});
				StringBuffer newData = new StringBuffer();
				for (String line : sortedLines) {
					newData.append(line);
					newData.append(System.lineSeparator());
				}
				String sortedPlotData = newData.toString().trim();
				FileWriter overWriter = new FileWriter(localPlotData, false);
				overWriter.write(sortedPlotData);
				overWriter.close();
				//Send off to IRODS
//				if (msg.getPlotID() < 100)
//					subterra.writeRemoteFile(localPlotData, this);
				
				
			} catch (IOException | InterruptedException e) {
				logger.severe("Error on broadcasting a data request for plot " + msg.getPlotID() + " " + e);
			}
		}
		
		/**
		 * TEMPORARILY STORING DATA IN GALILEO UNTIL ALL PROCESSING IS DONE
		 * AFTER WHICH IT GETS STORED IN IRODS THROUGH A SEPARATE SERVICE
		 * 
		 * @author sapmitra
		 * @param msg
		 */
		private void handleToLocal(StoreMessage msg) {
			//Compute metadata for this chunk of data. Write IRODS path into a local file for permanent storage.
			//Then in a temporary file, write the actual data. This temporary file will hold the raw data
			//until all nodes have fully processed incoming messages, at which point this data will be joined
			//with all other data of the same plot.
			long start = System.currentTimeMillis();
			try {
				// DATA FROM THE BUFFER
				String data = msg.getData();
				String [] sortedLines = data.toString().split(System.lineSeparator());
				
				// SORTING THE DATA IN BUFFER BASED ON TIME
				Arrays.sort(sortedLines, new Comparator<String>() {
				    @Override
				    public int compare(String o1, String o2) {
				    	SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd kk:mm:ss.SSS z yyyy");//need to change if timestamp format changes
						Date d1 = null, d2 = null;
						try {
							d1 = formatter.parse(o1.split(",")[0]);
							d2 = formatter.parse(o2.split(",")[0]);
						} catch (ParseException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						return d1.compareTo(d2);
				    }
				});
				
				StringBuffer newData = new StringBuffer();
				for (String line : sortedLines) {
					newData.append(line);
					newData.append(System.lineSeparator());
				}
				
				// DATA FROM BUFFER SORTED BASED ON TIMESTAMP
				data = newData.toString().trim();
				Metadata meta = createMeta(msg.getPlotID(), data);
				
//				String IRODSPath = path to file, without actual file name. File name is used from localfile
				String IRODSPath = meta.getName().replaceAll("-", File.separator);
				
				// THE 'METADATA' OF THE BLOCK HAS THE ACTUAL DATA IN IT INSIDE THE 'ATTRIBUTES' VARIABLE
				//Create a block which contains the location of the raw data in IRODS
				Block block = new Block(msg.getFSName(), meta, ("/iplant/radix_subterra/plots/" + IRODSPath+ "/" + meta.getName() + ".gblock").getBytes());
//				Block block = new Block(msg.getFSName(), meta, data.getBytes());
				
				synchronized(metadataTimes) {
					metadataTimes.add(System.currentTimeMillis()-start);
				}
				
				// THE BLOCK DATA IS SAVED INTO A FILE AND CONTAINS THE PATH TO AN IRODS FILE TO WHICH IT WOULD BE SAVED
				// THE BLOCK METADATA, WHICH CONTAINS THE ACTUAL DATA IS SAVED ON THE METADATA GRAPH
				msg.getFS().storeBlock(block);
				
				
				// A TEMPORARY FILE IS ALSO SAVED IN GALILEO THAT SAVES THE ACTUAL BLOCK DATA
				File tempDir = new File(SystemConfig.getRootDir() + File.separator + "dailyTemp/" + IRODSPath);
				if (!tempDir.exists()) 
					tempDir.mkdirs();
				File tempFile = new File(SystemConfig.getRootDir() + File.separator + "dailyTemp/" + IRODSPath + File.separator + meta.getName() + ".gblock");
				if (!tempFile.exists())
					tempFile.createNewFile();
				
				FileWriter writer = new FileWriter(tempFile, true);
				writer.append(msg.getData() + "\n");
				writer.close();
				
			} catch (ParseException | FileSystemException | IOException e) {
				logger.severe("Error extracting metadata and storing." + Arrays.toString(e.getStackTrace()));
			}
		}
		
		private void handleToIRODS(StoreMessage msg) {
			
		}
		
		private Metadata createMeta(int plotID, String data) throws ParseException {
			Metadata meta = new Metadata();
			String[] dataLines = data.split(System.lineSeparator());
			String [] firstLine = dataLines[0].split(",");
			String timestamp = firstLine[0];
			SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd kk:mm:ss.SSS z yyyy");//need to change if timestamp format changes
			Date parsedDate = formatter.parse(timestamp);
			Calendar cal = Calendar.getInstance();
			cal.setTime(parsedDate);
			int month = cal.get(Calendar.MONTH) + 1;//add 1 because Calendar class months are 0 based (i.e Jan=0, Feb=1...) but we need human readable month
			int year = cal.get(Calendar.YEAR);
			int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
			meta.setName("" + plotID + "-" + year + "-" + month + "-" + dayOfMonth);
			String [] sorted = data.split(System.lineSeparator());
			String first = sorted[0], last = sorted[sorted.length-1];
			long firstTime = formatter.parse(first.split(",")[0]).getTime();
			long lastTime = formatter.parse(last.split(",")[0]).getTime();
			if (firstTime == lastTime)
				lastTime ++;// a hack to get around data chunks with only one item (add 1ms to end time)
			meta.setTemporalProperties(new TemporalProperties(firstTime, lastTime));
			FeatureSet attributes = createAttributes(data);
			attributes.put(new Feature("plotID", plotID));
			attributes.put(new Feature("date", year+"-"+month+"-"+dayOfMonth));
			//rep and genotype are in shapefile, so HashGrid maintains in-memory mapping of this data for each plot
			attributes.put(new Feature("rep", sn.getGlobalGrid().getPlotInfo(plotID).a));
			attributes.put(new Feature("genotype", sn.getGlobalGrid().getPlotInfo(plotID).b));
			meta.setAttributes(attributes);
			return meta;
			
		}
		
		private FeatureSet createAttributes(String data) {
			//Assuming features are: CO2, Temperature, Humidity
			FeatureSet attributes = new FeatureSet();
			ArrayList<Double> CO2 = new ArrayList<>();
			ArrayList<Double> temperatures = new ArrayList<>();
			ArrayList<Double> humidities = new ArrayList<>();
			String [] lines = data.split(System.lineSeparator());
			ArrayList <Double> rand1 = new ArrayList<>();
			ArrayList<Double> rand2 = new ArrayList<>();
			ArrayList<Double> rand3 = new ArrayList<>();
			ArrayList<Double> rand4 = new ArrayList<>();
			for (String line : lines) {
				if (line.isEmpty())
					continue;//don't process an empty line which may have found its way into the data chunk
				String [] obs = line.split(",");
				temperatures.add(Double.parseDouble(obs[4]));
				humidities.add(Double.parseDouble(obs[5]));
				CO2.add(Double.parseDouble(obs[6]));
				rand1.add(Double.parseDouble(obs[8]));
				rand2.add(Double.parseDouble(obs[9]));
				rand3.add(Double.parseDouble(obs[10]));
				rand4.add(Double.parseDouble(obs[11]));
			}
			
			//For each of CO2, temp, humidity, compute avg, max, min, std. dev as attributes of this data
			attributes.put(new Feature("min_temperature", Collections.min(temperatures)));
			attributes.put(new Feature("max_temperature", Collections.max(temperatures)));
			attributes.put(new Feature("avg_temperature", galileo.util.Math.computeAvg(temperatures)));
			attributes.put(new Feature("std_temperature", galileo.util.Math.computeStdDev(temperatures)));
			
			attributes.put(new Feature("min_humidity", Collections.min(humidities)));
			attributes.put(new Feature("max_humidity", Collections.max(humidities)));
			attributes.put(new Feature("avg_humidity", galileo.util.Math.computeAvg(humidities)));
			attributes.put(new Feature("std_humidity", galileo.util.Math.computeStdDev(humidities)));
			
			
			attributes.put(new Feature("min_CO2", Collections.min(CO2)));
			attributes.put(new Feature("max_CO2", Collections.max(CO2)));
			attributes.put(new Feature("avg_CO2", galileo.util.Math.computeAvg(CO2)));
			attributes.put(new Feature("std_CO2", galileo.util.Math.computeStdDev(CO2)));
			
			attributes.put(new Feature("min_r1", Collections.min(rand1)));
			attributes.put(new Feature("max_r1", Collections.max(rand1)));
			attributes.put(new Feature("avg_r1", galileo.util.Math.computeAvg(rand1)));
			attributes.put(new Feature("std_r1", galileo.util.Math.computeStdDev(rand1)));
			
			attributes.put(new Feature("min_r2", Collections.min(rand2)));
			attributes.put(new Feature("max_r2", Collections.max(rand2)));
			attributes.put(new Feature("avg_r2", galileo.util.Math.computeAvg(rand2)));
			attributes.put(new Feature("std_r2", galileo.util.Math.computeStdDev(rand2)));
			
			attributes.put(new Feature("min_r3", Collections.min(rand3)));
			attributes.put(new Feature("max_r3", Collections.max(rand3)));
			attributes.put(new Feature("avg_r3", galileo.util.Math.computeAvg(rand3)));
			attributes.put(new Feature("std_r3", galileo.util.Math.computeStdDev(rand3)));
			
			attributes.put(new Feature("min_r4", Collections.min(rand4)));
			attributes.put(new Feature("max_r4", Collections.max(rand4)));
			attributes.put(new Feature("avg_r4", galileo.util.Math.computeAvg(rand4)));
			attributes.put(new Feature("std_r4", galileo.util.Math.computeStdDev(rand4)));
			
			attributes.put(new Feature("count", lines.length));

			return attributes;
			
		}
	}
	
	private class TimeStampedBuffer{
		private StringBuilder buffer;
		private long timestamp;
		public TimeStampedBuffer(StringBuilder buffer) {
			this.buffer = buffer;
			this.timestamp = System.currentTimeMillis();
		}
		
		public void update(String toAppend) {
			synchronized(this.buffer) {
				this.buffer.append(toAppend);
				this.timestamp = System.currentTimeMillis();
			}
			
		}
		
		public String getBuffer() {
			return this.buffer.toString();
		}
		
		public long getTimeStamp() {
			return this.timestamp;
		}
	}
	
}
