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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.text.DateFormat;
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
import java.util.zip.Adler32;

import org.irods.jargon.core.exception.JargonException;
import org.locationtech.spatial4j.io.GeohashUtils;
import org.xerial.snappy.Snappy;

import galileo.bmp.BitmapException;
import galileo.bmp.HashGrid;
import galileo.bmp.HashGridException;
import galileo.comm.Connector;
import galileo.comm.IRODSReadyCheckRequest;
import galileo.comm.IRODSReadyCheckResponse;
import galileo.comm.IRODSRequest;
import galileo.comm.IRODSRequest.TYPE;
import galileo.comm.NonBlockStorageRequest;
import galileo.config.SystemConfig;
import galileo.dataset.Block;
import galileo.dataset.Coordinates;
import galileo.dataset.DataIngestor;
import galileo.dataset.Metadata;
import galileo.dataset.TemporalProperties;
import galileo.dataset.feature.Feature;
import galileo.dataset.feature.FeatureSet;
import galileo.event.Event;
import galileo.fs.FileSystemException;
import galileo.fs.GeospatialFileSystem;
import galileo.graph.SummaryStatistics;
import galileo.integrity.RadixIntegrityGraph;
import galileo.net.NetworkDestination;
import galileo.util.GeoHash;
import sun.management.Sensor;

public class DataStoreHandler {
	
	//public static String fsName = DataIngestor.fsName;
	//public static String sensorName = DataIngestor.fileSensorType;
	public static final String IRODS_BASE_PATH_WOSLASH = "/iplant/home/radix_subterra";
	public static final String IRODS_BASE_PATH = "/iplant/home/radix_subterra";
	
	private BlockingQueue<StoreMessage> unProcessedMessages;
	private ConcurrentHashMap<String, TimeStampedBuffer> plotIDToChunks;
	private MessageHandler[] threadPool;
	
	// PLOT-ID -> METADATA STRING
	private Map<String, String> plotsProcessed = new HashMap<>();
	
	private static Logger logger = Logger.getLogger("galileo");
	private StorageNode sn;
	public ArrayList<Long> unprocessedTimes, irodsTimes, metadataTimes;
	private Timer mapClearer = new Timer();
	private Timer IRODSReadyChecker = new Timer();
	
	// LAST TIME A MESSAGE WAS PROCESSED BY THE SYSTEM, EITHER LOCAL STORAGE OR STORAGE TO IRODS
	private long lastMessageTime;
	private long lastToLocalAction;
	
	private IRODSManager subterra;
	private Connector connector;
	private File messageLogger = new File(SystemConfig.getInstallDir()+"/throughput.txt");
	private BufferedWriter bw;
	
	// TIME OF INACTIVITY FOR A PLOT AFTER WHICH ACCUMULATION FOR THAT PLOT'S DATA IS INITIATED
	public static int plotHasBeenInactive = 5;
	
	// TIME OF INACTIVITY AFTER WHICH ALL PLOT DATA IS ACCUMULATED AND THEN SENT TO IRODS
	public static int irodsCheckTimeSecs = 10;
	// FREQUENCY AT WHICH DATA SYNC IS PERFORMED
	public static int irodsCheckFreq = 15;
	
	// TIME OF INCATIVITY AFTER WHICH RIG DUMPING IS CHECKED & INITIATED
	public static int irodsRIGCheckTimeSecs = 60;
	// TIME BETWEEN IRODS ACTIVITY AFTER WHICH RIG DUMP STARTS
	public static int irodsRIGUpdateDelay = 30*1000;
	
	// PATHS THAT HAVE BEEN FINALLY WRITTEN ON DISK, BUT NOT UPDATED ON RIG YET
	public Map<String,List<String>> pathsPending = new HashMap<String,List<String>>();
	
	// IN CASE OF WRITING TO IRODS AS A COMPRESSED TAR
	public List<String> pathsToWrite = new ArrayList<String>();
	
	private boolean irodsInsertionStarted = false;
	private long lastIRODSInsertionTime;
	
	private Timer IRODS_RIG_DUMPER = new Timer();
	private Timer unProcessedMessageChecker = new Timer();
	private int numLoops = 0;
	public long start = 0;
	
	// INITIALIZED FROM MAPCLEARER...NUMBER OF LOCAL PLOTS THAT ARE STILL BEING WRITTEN TO DISK
	private int totalLocalActionsOngoing = 0;
	
	// TOTAL #OF PLOTS THAT HAVE BEEN PROCESSED BUT THEIR DATA HAS NOT BEEN COMBIED OR WRITTEN TO IRODS
	private int totalPlotsProcessedByMapClearer = 0;
	
	private int numIRODSConnections = 0;
	
	// THE TIME AT WHICH ALL STORAGE & ACCUMULATION ENDS
	private long endTime = 0;
	
	// IRODS LIMITS THE AMOUNT OF SIMULTANEOUS CONNECTIONS THAT CAN BE MADE DURING A WRITE
	private static int allowedConnectionsPerMC = 4;
	
	private NetworkDestination coordinator;
	
	// DO WE CREATE TAR FILE FROM ALL COMPILED PLOT DATA....YES IF FALSE
	private boolean irodsOff = true;
	// DO WE WRITE TAR FILE TO IRODS AND UNTAR IT THROUGH OUR CODE
	private boolean i_tar_dumpOff = true;
	
	private int myNum = -1;
	
	private boolean goAhead = false;
	
	public DataStoreHandler(StorageNode sn) {
		
		lastMessageTime = System.currentTimeMillis();
		lastToLocalAction = System.currentTimeMillis();
	
		
		try {
			NetworkInfo network = NetworkConfig.readNetworkDescription(SystemConfig.getNetworkConfDir());
			allowedConnectionsPerMC = 50 / network.getAllNodes().size();
		} catch (Exception e2) {
			logger.severe("ERROR READING NETWORK INFO IN DATASTOREHANDLER");
		}
		
		
		try {
			String nodeFile = SystemConfig.getNetworkConfDir() + File.separator + "hostnames";
			String [] hosts = new String(Files.readAllBytes(Paths.get(nodeFile))).split(System.lineSeparator());
			coordinator = new NetworkDestination(hosts[hosts.length-1].split(":")[0], Integer.parseInt(hosts[hosts.length-1].split(":")[1]));
			
			int cnt = 1;
			for(String host: hosts) {
				if(host.split(":")[0].equals(sn.getHostName()) || host.split(":")[0].equals(sn.getCanonicalHostName()))
					myNum = cnt;
				cnt++;
					
			}
			
			logger.info("RIKI: THIS IS NODE NUMBER: "+myNum);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			logger.severe(e1.toString());
		}
		
		// SCHEDULE PLOT DATA BACKER AT 10 SEC INTERVALS
		// JUST PERFORMS LOCAL BACKUP OF REMAINDER OF DATA IN plotIDToChunks
		// SCRAPES WHATEVER IS LEFT IN PLOTIDTOCHUNKS AND ENSURES THAT THEY ARE SAVED
		mapClearer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				
				// THIS IS FOR LOCAL STORAGE
				synchronized (plotIDToChunks) {
					
					if(plotIDToChunks.isEmpty())
						return;
					
					HashSet<String> toRemove = new HashSet<>();
					for (Map.Entry<String, TimeStampedBuffer> entry : plotIDToChunks.entrySet()) {
						
						// IF MORE THAN 10 SECONDS HAVE PASSED SINCE NEW DATA FOR A PLOT HAS COME IN
						// TAKE THE DATA BUFFERED FOR THAT PLOT ID FROM THE BUFFER TO LOCAL GALILEO
						
						if (System.currentTimeMillis() - entry.getValue().getTimeStamp() > plotHasBeenInactive*1000) {
							String[] tokens = entry.getKey().split("--");
							String sensorType = tokens[1];
							String fsName = tokens[2];
									
							int plotId = Integer.valueOf(tokens[0]);
							
							totalLocalActionsOngoing++;
							totalPlotsProcessedByMapClearer++;
						
							// handleToLocal METHOD FOR THIS
							StoreMessage irodsMsg = new StoreMessage(Type.TO_LOCAL, entry.getValue().getBuffer(), (GeospatialFileSystem) sn.getFS(fsName),
									fsName, plotId);
							irodsMsg.setSensorType(sensorType);
							unProcessedMessages.add(irodsMsg);
							toRemove.add(entry.getKey());
							
						}
					}
					for (String pID : toRemove)
						plotIDToChunks.remove(pID);
					
					synchronized (bw) {
						try {
							bw.flush();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		//}, 60 * 1000, 10 * 1000);
		}, 5 * 1000, 2 * 1000);
		
		// UNLOADS RIG_DUMP
		IRODS_RIG_DUMPER.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				
				if(!irodsInsertionStarted || pathsPending.size() == 0) {
					//logger.info("RIKI: NOTHING TO DUMP INTO RIG YET...");
					return;
				}
				synchronized(unProcessedMessages) {
					if(unProcessedMessages.size() > 0) {
						return;
					}
				}
				
				if(System.currentTimeMillis() - lastIRODSInsertionTime < irodsRIGUpdateDelay) {
					//logger.info("RIKI: DETECTED IRODS ACTIVITY...WILL WAIT FOR IT TO STOP");
					return;
				}
				//logger.info("RIKI: GALILEO-BASED ACTIVITY COMPLETE IN " + (double)endTime/(double)1000 + " Secs");
				
				if(irodsOff)
					return;
				
				// COORDINATOR MUST ALLOW FILES TO BE WRITTEN IN NUMERICAL ORDER
				// ASK THE COORDINATOR FOR PERMISSION
				
				IRODSRequest reply = null;
						
				if(!goAhead) {
					try {
						logger.info("RIKI: REQUESTING ACCESS FOR ME: "+myNum);
						reply = (IRODSRequest)connector.sendMessage(coordinator, new IRODSRequest(TYPE.QUEUE_REQ, myNum));
						logger.info("RIKI: ACCESS CODE: "+reply.getType());
						
						if(reply !=null && reply.getType() == TYPE.QUEUE_GRANT) {
							logger.info("RIKI: GREEN SIGNAL");
							goAhead = true;
						} else {
							logger.info("RIKI: RED SIGNAL ");
							return;
						}
					} catch (Exception e) {
						logger.severe("RIKI: QUEUE REQ ERROR: "+ e.getMessage());
					}
				}
				
				logger.info("RIKI: IRODS ACTIVITY HAS STOPPED..READY TO UNLOAD PATHS");
				synchronized(pathsPending) {
					
					String thisFS = pathsPending.keySet().iterator().next().split("--")[0];
					String tarName = thisFS+"_"+sn.getHostName()+"_"+(System.currentTimeMillis()/1000)+".tar";
					
					// CREATE THE TAR FILE LOCALLY
					IRODSManager.createTarFile(SystemConfig.getRootDir() + File.separator + thisFS, SystemConfig.getRootDir(), tarName);
					//IRODSManager.createTarFile(SystemConfig.getRootDir() + File.separator + thisFS, "/tmp", thisFS);
					logger.info("RIKI: TAR CREATION SUCCESSFUL AT "+SystemConfig.getRootDir());
					
					
					// ACTUAL SENDING OF TAR FILES AND UNTARRING THEM AT THE IRODS SERVER
					if(!i_tar_dumpOff) {
							
						// SEND THE TAR FILE TO IRODS
						File tarToExport = new File(SystemConfig.getRootDir()+File.separator+tarName);
						//File toExport = new File("/tmp"+File.separator+tarName);
						
	
						/*Set<PosixFilePermission> perms = new HashSet<>();
						perms.add(PosixFilePermission.OWNER_READ);
						//perms.add(PosixFilePermission.OWNER_WRITE);
						perms.add(PosixFilePermission.OWNER_EXECUTE);
						perms.add(PosixFilePermission.GROUP_READ);
						//perms.add(PosixFilePermission.GROUP_WRITE);
						perms.add(PosixFilePermission.GROUP_EXECUTE);
						perms.add(PosixFilePermission.OTHERS_READ);
						//perms.add(PosixFilePermission.OTHERS_WRITE);
						perms.add(PosixFilePermission.OTHERS_EXECUTE);
						
						try {
							Files.setPosixFilePermissions(toExport.toPath(), perms);
						} catch (IOException e1) {
							logger.info("RIKI: PERMISSIONERROR: "+e1.getMessage());
						}*/
						
						try {
							String irodsTarDumpLocation = IRODSManager.IRODS_BASE+IRODSManager.IRODS_SEPARATOR+"tmptar"+IRODSManager.IRODS_SEPARATOR;
							//subterra.writeRemoteFileAtSpecificPath(tarToExport, IRODSManager.IRODS_BASE+"/");
							subterra.writeRemoteFileAtSpecificPath(tarToExport, irodsTarDumpLocation);
							// UNLOAD TAR FILE ON IRODS SYSTEM
							//subterra.untarIRODSFile(IRODSManager.IRODS_BASE+IRODSManager.IRODS_SEPARATOR+tarName, IRODSManager.IRODS_BASE, "");
							subterra.untarIRODSFile(irodsTarDumpLocation+tarName, IRODSManager.IRODS_BASE, "");
						} catch (JargonException e) {
							logger.info("RIKI: PROBLEM SENDING TAR TO IRODS.."+ e.getMessage());
						}
						
						logger.info("RIKI: TARRING & UNTARRING FINISHED");
					}
					
					
					Set<String> toRemove = new HashSet<String>();
					
					// FOR EACH FILESYSTEM
					for(String key : pathsPending.keySet()) {
						
						List<String> paths = pathsPending.get(key);
						String[] tokens = key.split("--");
						String fName = tokens[0];
						String sName = tokens[1];
						
						String hostName = sn.getHostName();
						if(paths == null || paths.size() == 0)
							continue;	
						
						StringBuffer sb = new StringBuffer("");
						
						for(String p : paths) {
							sb.append(p+"\n");
						}
						String timeStamp = new SimpleDateFormat("yyyy_MM_dd").format(new Date());
						subterra.writeRemoteData(sb.toString(), fName+"/rig/idump-"+hostName+"-"+timeStamp+"-"+sName);
						logger.info("RIKI: RHIGDUMP SHIPPED OFF FROM "+fName+"/rig/idump-"+hostName+"-"+timeStamp+"-"+sName);
						toRemove.add(key);
					}
					pathsPending.clear();
					pathsToWrite.clear();
					logger.info("RIKI: PENDING PATHS CLEARED: "+pathsPending.size()+" UNPROCESSED:"+unProcessedMessages.size());
					
					// ALLOW OTHER NODES TO OFFLOAD DATA TO IRODS ONE AT A TIME - LETTING CORRDINATOR KNOW THIS NODE IS DONE BLOCKING
					try {
						connector.sendMessage(coordinator, new IRODSRequest(TYPE.QUEUE_REQ, myNum));
						goAhead = false;
					} catch (Exception e) {
						logger.severe("RIKI: QUEUE REL ERROR: "+ e.getMessage());
					}
					
					numIRODSConnections = 0;
					irodsInsertionStarted = false;
				}
			}
		//}, 60 * 1000, 10 * 1000);
		}, (irodsRIGCheckTimeSecs)*1000, (irodsRIGCheckTimeSecs/4)*1000);
		
		// THIS IS FOR IRODS STORAGE & FOR A COMBINED GALILEO STORAGE
		// READS THE TEMPORARY GALILEO FILE WITH BACKED UP RECORDS AND SENDS IT TO IRODS FOR STORAGE
		// ALSO COMBINES DATA FROM ALL PLOTS FROM ALL NODES INTO ONE NODE
		IRODSReadyChecker.scheduleAtFixedRate(new TimerTask() {
			@Override
				public void run() {
					synchronized(plotIDToChunks) {
						
						if(sn.getFSMap() == null)
							return;
						for(String thisFS : sn.getFSMap().keySet()) {
							
							// NOTHING GOING ON....
							if(totalLocalActionsOngoing == 0 && totalPlotsProcessedByMapClearer == 0) {
								//logger.info("RIKI: TOO EARLY>>>"+totalLocalActionsOngoing+" "+totalPlotsProcessedByMapClearer);
								continue;
							}
							
							// PLOTS BEING CURRENTLY WRITTEN....NOT NOW
							if(totalLocalActionsOngoing > 0) {
								logger.info("RIKI: TOO SOON>>>"+totalLocalActionsOngoing);
								continue;
							}
							
							//logger.info("RIKI: IRODS INSERTION ABOUT TO START");
							// MESSAGE PROCESSING HAS STAYED IDLE FOR MORE THAN 5 MINS
							
							// NO NEW MESSAGES HAVE COME IN FOR A WHILE - irodsCheckTimeSecs
							// THERE ARE NEW PLOTS THAT HAVE BEEN PROCESSED
							// MAP_CLEARER HAS NOT DONE ANY LOCAL SAVE FOR A WHILE
							if (System.currentTimeMillis() - lastToLocalAction >= irodsCheckTimeSecs*1000 && plotsProcessed!=null && plotsProcessed.size() > 0) { 
							
								logger.info("RIKI: DATA SYNC CONDITION MET "+totalLocalActionsOngoing+" "+plotsProcessed.size());
											
								try {
									
									boolean allReady = true;
									
									
									// CHECKS IF ALL NODES ARE IDLE. 
									// NODES ARE READY IF IT HAS BEEN N MINUTES SINCE LAST MESSAGE
									// ONLY WHEN ALL NODES ARE IDLE IS WHEN ACTUAL CLUBBING OF DATA IS INITIATED
									for (Event e : broadcastEvent(new IRODSReadyCheckRequest(IRODSReadyCheckRequest.Type.CHECK), connector)) {
										if (!((IRODSReadyCheckResponse)e).isReady())
											allReady = false;
									}
									
									logger.info("RIKI: ALLREADY STATUS AFTER BROADCAST:"+ allReady);
									//IF ALL NODES ARE READY, initiate IRODS transfer phase
									if (allReady) {
										
										HashSet<String> toRemove = new HashSet<>();
										
										// For each plot that was processed by this machine, attempt to get the lock from coordinator
										
										synchronized(plotsProcessed) {
											for (Map.Entry<String, String> entry : plotsProcessed.entrySet()) {
												
												String[] tokens = entry.getKey().split("--");
												int plotId = Integer.valueOf(tokens[0]);
												String sensorName = tokens[1];
												String fsName = tokens[2];
												
												if(!thisFS.equals(fsName))
													continue;
												
												GeospatialFileSystem fs = (GeospatialFileSystem) sn.getFS(thisFS);
												List<NodeInfo> nodes = fs.getGlobalGrid().getRelevantNodes(plotId);
												
												// LOCK REQUEST FOR EACH PLOT SENT TO COORDINATOR ONE AT A TIME
												IRODSRequest reply = null;
												
												// REQUEST FOR LOCK ONLY IF THIS PLOT SPANS MULTIPLE NODES
												if(nodes.size() > 1) {
													NodeInfo legitimateHost = nodes.get(0);
													// IF THIS IS THE 1st NODE IN THE LIST, IT GETS TO ACCUMMULATE THE DATA
													if (legitimateHost.getHostname().equals(sn.getHostName()) || legitimateHost.getHostname().equals(sn.getCanonicalHostName())) {
														reply = (IRODSRequest)connector.sendMessage(coordinator, new IRODSRequest(TYPE.LOCK_REQUEST, plotId));
														
														if(plotId == 9971)
															logger.info("RIKI: LEGITIMATE OWNER OF PLOT "+plotId);
													} else {
														if(plotId == 9971)
															logger.info("RIKI: THIS NODE DOES NOT HAVE A RIGHT OVER PLOT "+plotId);
													}
												} else if(nodes.size() == 0)
													logger.info("RIKI: UNNATURAL:"+plotId);
												
												if(plotId == 9971)
												{
													if(reply != null)
														logger.info("RIKIXXX: "+ reply.getType());
												}
												// IF LOCK FOR THE REQUESTED PLOT IS GRANTED BY THE COORDINATOR NODE
												// MEANS THIS NODE WILL HANDLE SUBMISSION OF ALL DATA FOR THIS PLOT
												if (nodes.size() == 1 || (reply != null && reply.getType() == TYPE.LOCK_ACQUIRED)) {
													//logger.info("RIKI: LOCK ACQUIRED FOR :"+ entry.getKey());
													//logger.info("RIKI: LOCK FOR PLOT & IRODS INSERTION "+entry.getKey()+" ACQUIRED BY NODE: "+sn.getHostName());
													//this machine gets privilege to write this plot file to IRODS.
													
													// handleDataRequest METHOD FOR THIS
													StoreMessage dataRequest = new StoreMessage(Type.DATA_REQUEST, "", (GeospatialFileSystem)sn.getFS(thisFS), thisFS, plotId);
													dataRequest.setSensorType(sensorName);
													
													if(nodes.size() == 1) {
														// THIS PLOT WAS NOT LOCKED SINCE IT WAS NOT NECESSARY
														dataRequest.locked = false;
													}
													
													String entryVal = entry.getValue();
													
													String dateString = entryVal.substring(0,entryVal.lastIndexOf("-"));
													
													/*dataRequest.setFilePath(SystemConfig.getRootDir() + File.separator + thisFS+ File.separator + plotId + "/" +
															entry.getValue().replaceAll("-", "/") + "/" + plotId+"-" + entry.getValue() + ".gblock");*/
													dataRequest.setFilePath(SystemConfig.getRootDir() + File.separator + thisFS+ File.separator + 
															dateString.replaceAll("-", File.separator) + File.separator + plotId + File.separator + sensorName + File.separator
															+ dateString +"-" + plotId + "-" +sensorName+  ".gblock");
													unProcessedMessages.offer(dataRequest);
												}
												toRemove.add(entry.getKey());
											}
											
											for (String pID : toRemove)
												plotsProcessed.remove(pID);
											
											totalLocalActionsOngoing = 0;
											totalPlotsProcessedByMapClearer -= toRemove.size();
										
										}
									}
								} catch (IOException | InterruptedException e) {
									logger.severe("Cluster configuration file missing or corrupt");
								}
							}
						}
					}
				}
		}, (irodsCheckFreq)*1000, irodsCheckFreq*1000); //300 seconds = 5 minutes
		
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
	
	
	private List<Event> multicastEvent(Event event, Connector connector, List<NodeInfo> nodes) throws IOException, InterruptedException{
		
		ArrayList<Event> responses = new ArrayList<>();
		
		//String nodeFile = SystemConfig.getNetworkConfDir() + File.separator + "hostnames";
		//String [] hosts = new String(Files.readAllBytes(Paths.get(nodeFile))).split(System.lineSeparator());
		for (NodeInfo host : nodes) {
			NetworkDestination dest = new NetworkDestination(host);
			
			if (!dest.getHostname().equals(sn.getHostName()) && !dest.getHostname().equals(sn.getCanonicalHostName())) {//don't send event to self
				
				//logger.info("RIKI: ABOUT TO CONTACT "+dest);
				Event response = connector.sendMessage(dest, event);
				responses.add(response);
			}
		}
		return responses;
	}
	
	private List<Event> broadcastEvent(Event event, Connector connector) throws IOException, InterruptedException{
		
		ArrayList<Event> responses = new ArrayList<>();
		
		String nodeFile = SystemConfig.getNetworkConfDir() + File.separator + "hostnames";
		String [] hosts = new String(Files.readAllBytes(Paths.get(nodeFile))).split(System.lineSeparator());
		for (String host : hosts) {
			NetworkDestination dest = new NetworkDestination(host.split(":")[0], Integer.parseInt(host.split(":")[1]));
			logger.info("RIKI: ABOUT TO CONTACT "+dest);
			if (!host.split(":")[0].equals(sn.getHostName()) && !host.split(":")[0].equals(sn.getCanonicalHostName())) {//don't send event to self
				
				logger.info("RIKI: CONTACTING TO CHECK FOR IDLE "+dest);
				Event response = connector.sendMessage(dest, event);
				logger.info("RIKI: GOT BACK FROM "+dest+" "+response);
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
					
					//start = System.currentTimeMillis();
					switch(toProcess.getType()){			
						case UNPROCESSED:
							if(numLoops == 0) {
								start = System.currentTimeMillis();
							}
							numLoops = 7;
							//logger.info("RIKI: UNPROCESSED MESSAGE "+toProcess.getSensorType()+" "+toProcess.getFSName());
							handleUnprocessed(toProcess);
							
							lastMessageTime = System.currentTimeMillis();
							break;
						case TO_LOCAL:
							//logger.info("RIKI: TOLOCAL MESSAGE "+toProcess.getSensorType()+" "+toProcess.getFSName());
							handleToLocal(toProcess);
							/*end = System.currentTimeMillis() - start;
							synchronized(irodsTimes) {
								irodsTimes.add(end);
							}*/
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
		
		// THE DATA HERE IS A SET OF LINES, MOST OF WHICH BELONGS TO THIS NODE
		// THE OTHERS NEED TO BE MAPPED TO THEIR RESPECTIVE NODES AND SHIPPED OFF
		private void handleUnprocessed(StoreMessage msg) throws ParseException {
			
			String sensorType = msg.getSensorType();
			
			String data = msg.getData();
			
			Calendar cal = Calendar.getInstance();
			String lineSep = System.lineSeparator();
			String [] lines = data.split(System.lineSeparator());
			
			//logger.info("RIKI: HANDLING UNPROCESSED DATA OF SIZE: " + lines.length);
			
			GeospatialFileSystem gfs = msg.getFS();
			HashMap<NodeInfo, String> otherDests = new HashMap<>();//to be used for mapping data points to other destinations
			HashGrid grid = gfs.getGlobalGrid();
			
			// THE INDICES OF THE IMPORTANT FIELDS FOR THE FILE BEING INGESTED
			int[] indices = gfs.getConfigs().getIndices(sensorType);
			
			int temporalIndex = gfs.getDataTemporalIndex(sensorType);
			
			SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd kk:mm:ss.SSS z yyyy");//need to change if timestamp format changes
			
			if(!gfs.isTimeTimestamp())
				formatter = new SimpleDateFormat(gfs.getDateFormat());
			
			for (String line : lines) {
				
				if(line.trim().isEmpty())
					continue;
				String lineTokens[] = line.split(",");
				
				if(lineTokens.length <= indices[0] || lineTokens.length <= indices[1])
					continue;
				// LAT-LON INDICES ARE INVERTED FOR HASHGRID
				double lat = Double.parseDouble(lineTokens[indices[1]]);
				double lon = Double.parseDouble(lineTokens[indices[0]]);
				Coordinates coords = new Coordinates(lat, lon);
				
				int plotID;
				try {
					plotID = grid.locatePoint(coords);
				} catch (BitmapException e) {
					logger.severe("Could not identify plot for coordinates: " + coords);
					continue;
				}
				
				if(plotID < 0)
					continue;
				
				//First ensure that this point in fact belongs on this node
				NodeInfo dest = ((SpatialHierarchyPartitioner)gfs.getPartitioner()).locateHashVal(GeoHash.encode(coords, grid.getPrecision()));
				
				if(plotID == 9748) {
					logger.info("RIKI: CONFLICTING PLOT: "+ plotID+" Coor:"+coords+" DEST:"+dest);
				}
				if(dest == null) {
					//logger.info("RIKI: INVALID DESTINATION FOR "+coords);
					continue;
				}
				
				//logger.info("RIKI: DEST IS "+dest);
				
				if (dest.getHostname().equals(sn.getHostName()) || dest.getHostname().equals(sn.getCanonicalHostName())) {
						// INSERT INTO THE PLOTIDTOCHUNKS MAP
						String plotKey = plotID+"--"+sensorType+"--"+msg.getFSName();
						plotIDToChunks.computeIfAbsent(plotKey, k -> new TimeStampedBuffer(new StringBuilder()));
						plotIDToChunks.get(plotKey).update(line+lineSep);
						
						// IF NUMBEROF LINE FOR THIS PLOT CROSSES 500, CREATE AN IRODS MESSAGE OUT OF THEM,
						// OTHERWISE KEEP ADDING THEM TO THE BUFFER
						synchronized(plotIDToChunks) {
							
							// WHEN THERE ARE MORE THAN 500 RECORDS FOR A PLOT, ATTEMPT TO STORE IT IN LOCAL GALILEO
							// OTHERWISE PERSIST IT IN plotIDToChunks
							if (plotIDToChunks.get(plotKey) != null && plotIDToChunks.get(plotKey).getBuffer().split(lineSep).length >= DataIngestor.CHUNK_SIZE) {//this threshold is subject to change!
								//add to existing block for the plot identified
								// THIS IS BEING STORED IN LOCAL, ALTHOUGH IT SAYS IRODS MSG
								//logger.info("RIKI: OP1 ");
								StoreMessage localNIrodsStorageMsg = new StoreMessage(Type.TO_LOCAL, plotIDToChunks.get(plotKey).getBuffer(), gfs, msg.getFSName(), plotID);
								
								localNIrodsStorageMsg.setSensorType(sensorType);
								//logger.info("RIKI: I AM HERE "+localNIrodsStorageMsg.getSensorType());
								
								unProcessedMessages.add(localNIrodsStorageMsg);
								plotIDToChunks.remove(plotKey);
							}
						}
						
						String [] firstLine = lines[0].split(",");
						
						String timestamp = firstLine[temporalIndex];
						
						// IF THE TIME IS A DATE STRING
						
						if(gfs.isTimeTimestamp()) {
							cal = GeoHash.getCalendarFromTimestamp(timestamp, true);
						} else {
						
							Date parsedDate = formatter.parse(timestamp);
							cal.setTime(parsedDate);
						}
						
						
						int month = cal.get(Calendar.MONTH) + 1;//add 1 because Calendar class months are 0 based (i.e Jan=0, Feb=1...) but we need human readable month
						int year = cal.get(Calendar.YEAR);
						int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
						
						// PLOTS PROCESSED MAPPED TO THE DATE AND SENSOR THEY HOLD
						synchronized(plotsProcessed) {
							plotsProcessed.put(plotKey, year+"-"+month+"-"+dayOfMonth+"-"+sensorType);
						}
						
				} else {//the observation is to be stored on a machine other than this one
					//logger.info("RIKI: OP2 ");
					// MAPPING THE OTHER LINES TO THEIR RESPECTIVE NODES
					if (!otherDests.keySet().contains(dest)) 
						otherDests.put(dest, line+lineSep);
					else
						otherDests.put(dest, otherDests.get(dest)+line+lineSep);
				}
			}
			
			//logger.info("RIKI: plotsProcessed SIZE AFTER MSG MAPPING: "+plotsProcessed.size());
			
			for(Map.Entry<NodeInfo,String> entry : otherDests.entrySet()) {
				try {
					//logger.info("RIKI: IS THIS IT?? "+msg.getSensorType());
					byte [] compressed = Snappy.compress(entry.getValue());
					NonBlockStorageRequest req = new NonBlockStorageRequest(compressed, msg.getFSName());
					req.setSensorType(msg.getSensorType());
					req.setCheckAll(false);
					sn.sendEvent(entry.getKey(), req);
				} catch (IOException e) {
					logger.severe("Error sending partially processed data to node: " + entry.getKey());
				}
			}
		}
		
		
		public String getSensorTypeFromPath(String path) {
			
			if(!path.contains(".")) {
				return "invalid";
			}
			String tokens[] = path.split("\\.");
			
			String tempStr = tokens[tokens.length-2];
			
			String tokens1[] = tempStr.split("-");
			
			return tokens1[tokens1.length-1];
			
		}
		
		/**
		 * this combines plot data from all nodes into a single node
		 * THIS TAKES THE TEMP FILES FROM GALILEO AND SENDS THEM TO IRODS SERVER
		 * @author sapmitra
		 * @param msg
		 */
		private void handleDataRequest(StoreMessage msg) {
			try {
				
				String fsName = msg.getFSName();
				//String sensorType = getSensorTypeFromPath(msg.getFilePath());
				String sensorType = msg.getSensorType();
				
				IRODSRequest dataRequest = new IRODSRequest(TYPE.DATA_REQUEST, msg.getPlotID());
				dataRequest.setFs(msg.getFSName());
				dataRequest.setFilePath(msg.getFilePath().replaceAll(SystemConfig.getRootDir(),""));
				
				//logger.info("RIKI: BEFORE HANDLEDATAREQUEST BROADCAST");
				// ASKING OTHER NODES FOR DATA REGARDING THIS PLOT
				// SINCE THIS NODE HAS RECEIVED PERMISSION TO SUBMIT ALL DATA FOR THIS PLOT
				List<Event> responses = new ArrayList<Event>();
				
				GeospatialFileSystem fs = (GeospatialFileSystem) sn.getFS(msg.getFSName());
				List<NodeInfo> nodes = fs.getGlobalGrid().getRelevantNodes(msg.getPlotID());
				
				if(nodes.size() > 1) {
					
					responses = multicastEvent(dataRequest, this.connector, nodes);
				}
				
				if(msg.getPlotID() == 9971) {
					logger.info("RIKI: FETCHING LEGIT DATA FROM: "+nodes+" GOT"+ ((responses == null)?0:"+"+responses.size())+">>"+((IRODSRequest)responses.get(0)).getType());
					
				}
				
				StringBuilder foreignplotData = new StringBuilder("");
				
				// COMBINATION OF ALL FOREIGN SUMMARIES
				SummaryStatistics ss = null;
				int cnt = 0;
				
				boolean receivedAnyForeignData = false;
				
				// RESPONSES FROM OTHER NODES REGARDING THE PLOT
				for (Event e : responses) {
					IRODSRequest reply = (IRODSRequest)e;
					if (reply.getType() == TYPE.DATA_REPLY) {
						receivedAnyForeignData = true;
						
						foreignplotData.append(reply.getData()+System.lineSeparator());
						
						if(fsName.startsWith("roots")) {
							if(cnt == 0) {
								ss = reply.getSummary();
								cnt = 1;
							} else {
								ss = SummaryStatistics.mergeSummary(ss, reply.getSummary());
							}
						}
					}
				}
				
				File localPlotData = new File(msg.getFilePath());
				//logger.info("RIKI: DEALING WITH FILEPATH :"+msg.getFilePath());
				String localContents = new String(Files.readAllBytes(Paths.get(localPlotData.getAbsolutePath())));
				
				// NO QUESTION OF MERGING IF NO FOREIGN DATA RECEIVED
				if(receivedAnyForeignData) {
					logger.info("RIKI: RECIEVED FOREIGN DATA FOR " + msg.getFilePath());
					
					if(msg.getFSName().startsWith("roots")) {
						
						SummaryStatistics merged = msg.getFS().getStatistics(localPlotData.getAbsolutePath());
						
						// MERGING ALL COMBINED FOREIGN SUMMARY WITH LOCAL SUMMARY
						merged = SummaryStatistics.mergeSummary(merged, ss);
						
						msg.getFS().putSummaryData(merged, localPlotData.getAbsolutePath());
					
					}
				}
				
				String sortedPlotData = "";
				
				// NO NEED TO SORT DATA UNLESS SENDING OFF TO IRODS. SORTING IS EXCESS FUNCTIONALITY
				// ALSO NO SORTING IF NO NEW FOREIGN DATA IS RECEIVED
				if(receivedAnyForeignData) {
					foreignplotData.append(localContents);
					
					// NOW LOCALPLOTDATA HAS ALL THE DATA IN IT
					localContents = foreignplotData.toString().replaceAll("(?m)^\\s", "");//remove any extraneous new lines that found their way in
					//logger.info("RIKI: ALL RELEVANT PLOT DATA HAVE BEEN ACCUMULATED "+localPlotData);
					
					// ALL DATA RELATED TO THIS PLOT FROM ALL NODES ON THIS NODE
					
					//GeospatialFileSystem fs = msg.getFS();
					String dateFormat = fs.getDateFormat();
					int temporalIndex = fs.getDataTemporalIndex(sensorType);
					
					sortedPlotData = localContents;
					
					String [] sortedLines = localContents.split(System.lineSeparator());
					Arrays.sort(sortedLines, new Comparator<String>() {
					    @Override
					    public int compare(String o1, String o2) {
					    	
					    	if(fs.isTimeTimestamp()) {
					    		Date d1 = null, d2 = null;
							
								d1 = new Date(Long.valueOf(o1.split(",")[temporalIndex]));
								d2 = new Date(Long.valueOf(o2.split(",")[temporalIndex]));
							
								return d1.compareTo(d2);
					    		
					    	} else {
						    	SimpleDateFormat formatter = new SimpleDateFormat(dateFormat);//need to change if timestamp format changes
								Date d1 = null, d2 = null;
								try {
									d1 = formatter.parse(o1.split(",")[temporalIndex]);
									d2 = formatter.parse(o2.split(",")[temporalIndex]);
								} catch (ParseException e) {
									e.printStackTrace();
								}
								return d1.compareTo(d2);
					    	}
					    }
					});
					
					StringBuffer newData = new StringBuffer();
					for (String line : sortedLines) {
						newData.append(line);
						newData.append(System.lineSeparator());
					}
					
					sortedPlotData = newData.toString().trim();
					
					// OVERWRITING PARTIAL PLOT DATA WITH FULL DATA, COMBINED FROM ALL NODES
					
					logger.info("RIKI: WRITING OUT FULL PLOT DATA TO :"+localPlotData.getAbsolutePath());
					
					FileWriter overWriter = new FileWriter(localPlotData, false);
					overWriter.write(sortedPlotData);
					overWriter.close();
				} else {
					sortedPlotData = localContents;
					
					logger.info("RIKI: PROCESSED FULL PLOT DATA AND WRITTEN AT :"+localPlotData.getAbsolutePath());
				}
				
				
				endTime = System.currentTimeMillis() - start;
				// RIKI ACTUAL WRITING TO IRODS
				
				//Send off to IRODS
				boolean isSuccess = true;
				
				if (msg.getPlotID() > 0) {
					
					lastIRODSInsertionTime = System.currentTimeMillis();
					irodsInsertionStarted = true;
					
					long crcVal = RadixIntegrityGraph.getChecksumFromData(sortedPlotData);
					
					String actPath = localPlotData.getAbsolutePath().replaceAll(SystemConfig.getRootDir(),"");
					pathsToWrite.add(localPlotData.getAbsolutePath());
					
					List<String> pPaths = new ArrayList<String>();
					
					String key = fsName+"--"+sensorType;
					
					if(isSuccess) {
						synchronized(pathsPending) {
							
							if(pathsPending.get(key) == null)
								pathsPending.put(key,pPaths);
							else { 
								pPaths = pathsPending.get(key);
							}
							pPaths.add(IRODS_BASE_PATH_WOSLASH+actPath+"$$"+ crcVal);
							//logger.info("RIKI: IRODS PATH FOR RIG: "+IRODS_BASE_PATH_WOSLASH+actPath+"$$"+ crcVal);
						}
					}
				}
				
				
			} catch (IOException | InterruptedException e) {
				logger.severe("Error on broadcasting a data request for plot " + msg.getPlotID() + " " + e);
			} finally {
				try {
					
					if(!msg.locked)
						return;
					
					IRODSRequest reply = (IRODSRequest)connector.sendMessage(coordinator, new IRODSRequest(TYPE.LOCK_RELEASE_REQUEST, msg.getPlotID()));
					if(reply.getType() == TYPE.LOCK_RELEASED) {
						//logger.info("LOCK RELEASED FOR "+msg.getPlotID());
					} else {
						logger.info("LOCK RELEASE REJECTED FOR "+msg.getPlotID());
						IRODSRequest reply1 = (IRODSRequest)connector.sendMessage(coordinator, new IRODSRequest(TYPE.LOCK_RELEASE_REQUEST, msg.getPlotID()));
						if(reply1.getType() != TYPE.LOCK_RELEASED) {
							logger.info("LOCK RELEASE REJECTED TWICE FOR " + msg.getPlotID());
						}
					}
				} catch (Exception e) {
					logger.severe("LOCK RELEASE FAILED FOR " + msg.getPlotID());
					e.printStackTrace();
				} 
				
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
			GeospatialFileSystem fs = msg.getFS();
			
			//logger.info("RIKI: ABOUT TO TRY TO MAKE A LOCAL SAVE");
			String sensorType = msg.getSensorType();
			
			int[] indices = fs.getConfigs().getIndices(sensorType);
			int temporalIndex = indices[2];
			
			long start = System.currentTimeMillis();
			try {
				// DATA FROM THE BUFFER
				String data = msg.getData();
				String [] sortedLines = data.toString().split(System.lineSeparator());
				
				// SORTING THE DATA IN BUFFER BASED ON TIME
				
				Arrays.sort(sortedLines, new Comparator<String>() {
				    @Override
				    public int compare(String o1, String o2) {
				    	
				    	if(fs.isTimeTimestamp()) {
				    		Date d1 = null, d2 = null;
						
							d1 = new Date(Long.valueOf(o1.split(",")[temporalIndex]));
							d2 = new Date(Long.valueOf(o2.split(",")[temporalIndex]));
						
							return d1.compareTo(d2);
				    		
				    	} else {
					    	SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd kk:mm:ss.SSS z yyyy");//need to change if timestamp format changes
							Date d1 = null, d2 = null;
							try {
								d1 = formatter.parse(o1.split(",")[temporalIndex]);
								d2 = formatter.parse(o2.split(",")[temporalIndex]);
							} catch (ParseException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							return d1.compareTo(d2);
				    	}
				    }
				});
				
				StringBuffer newData = new StringBuffer();
				for (String line : sortedLines) {
					newData.append(line);
					newData.append(System.lineSeparator());
				}
				
				// DATA FROM BUFFER SORTED BASED ON TIMESTAMP
				data = newData.toString().trim();
				
				// THE SUMMARY STATISTICS FOR THIS PLOT SEGMENT
				SummaryStatistics summary = new SummaryStatistics();
				Metadata meta = createMetaArizona(msg.getPlotID(), data, temporalIndex, msg.getFS().isTimeTimestamp(), indices, sensorType, summary);
				
//				String IRODSPath = path to file, without actual file name. File name is used from localfile
				String IRODSPath = meta.getName().replaceAll("-", File.separator);
				
				// THE 'METADATA' OF THE BLOCK HAS THE SUMMARY DATA IN IT INSIDE THE 'ATTRIBUTES' VARIABLE
				// THE ACTUAL DATA GETS WRITTEN TO FILE TOWARDS THE END OF THIS FUNCTION
				
				//THE PATH TO THIS FILE WHEN IT GETS SAVED IN IRODS
				String irodsStoragePath = IRODS_BASE_PATH + IRODSPath+ "/" + meta.getName() + ".gblock";
				Block block;
				
				// DATA SAVING TO GALILEO
				
				// ANY OTHER PLOT DATA
				if(sensorType.equals("vanilla")) {
					// NOT STORING ACTUAL DATA, BUT JUST THE IRODS PATH WHERE THIS GETS STORED
					block = new Block(msg.getFSName(), meta, (IRODS_BASE_PATH + IRODSPath+ "/" + meta.getName() + ".gblock").getBytes());
				} else {
					// CASE OF ARIZONA DATA
					// ADDING SENSORTYPE TO THE NAME, SO A DIFFERENT FILE FOR DIFFERENT SENSOR DATA
					block = new Block(msg.getFSName(), meta, data.getBytes());
				}				
				
				synchronized(metadataTimes) {
					metadataTimes.add(System.currentTimeMillis()-start);
				}
				
				// THE BLOCK DATA IS SAVED INTO A FILE AND CONTAINS THE PATH TO AN IRODS FILE TO WHICH IT WOULD BE SAVED
				// THE BLOCK METADATA, WHICH CONTAINS THE ACTUAL DATA IS SAVED ON THE METADATA GRAPH
				if(sensorType.equals("vanilla")) {
					msg.getFS().storeBlock(block);
				} else {
					msg.getFS().storeBlockArizona(block, sensorType, summary, irodsStoragePath);
					//logger.info("RIKI: FINISHED LOCAL SAVE FOR "+irodsStoragePath);
				}
				
				// A TEMPORARY FILE IS ALSO SAVED IN GALILEO THAT SAVES THE ACTUAL BLOCK DATA
				
				// IRODS SENDING IS DONE THROUGH scheduleAtFixedRate
				// WE ARE NOT DOING DAILYTEMP ANYMORE
				/*
				logger.info("RIKI: ABOUT TO WRITE OUT TO DAILYTEMP :"+ (SystemConfig.getRootDir() + File.separator + "dailyTemp/" + IRODSPath + File.separator + meta.getName() + ".gblock"));
				
				File tempDir = new File(SystemConfig.getRootDir() + File.separator + "dailyTemp/" + IRODSPath);
				if (!tempDir.exists()) 
					tempDir.mkdirs();
				File tempFile = new File(SystemConfig.getRootDir() + File.separator + "dailyTemp/" + IRODSPath + File.separator + meta.getName() + ".gblock");
				if (!tempFile.exists())
					tempFile.createNewFile();
				
				FileWriter writer = new FileWriter(tempFile, true);
				writer.append(msg.getData() + "\n");
				writer.close();
				
				*/
				
			} catch (ParseException | FileSystemException | IOException e) {
				logger.severe("Error extracting metadata and storing." + Arrays.toString(e.getStackTrace()) + e.getMessage());
			} finally {
				synchronized(plotIDToChunks) {
					lastToLocalAction = System.currentTimeMillis();
					totalLocalActionsOngoing--;
				}
			}
		}
		
		
		private void handleToLocal_backup(StoreMessage msg) {
			//Compute metadata for this chunk of data. Write IRODS path into a local file for permanent storage.
			//Then in a temporary file, write the actual data. This temporary file will hold the raw data
			//until all nodes have fully processed incoming messages, at which point this data will be joined
			//with all other data of the same plot.
			GeospatialFileSystem fs = msg.getFS();
			
			String sensorType = msg.getSensorType();
			
			int[] indices = fs.getConfigs().getIndices(sensorType);
			int temporalIndex = indices[2];
			
			long start = System.currentTimeMillis();
			try {
				// DATA FROM THE BUFFER
				String data = msg.getData();
				String [] sortedLines = data.toString().split(System.lineSeparator());
				
				// SORTING THE DATA IN BUFFER BASED ON TIME
				Arrays.sort(sortedLines, new Comparator<String>() {
				    @Override
				    public int compare(String o1, String o2) {
				    	
				    	if(fs.isTimeTimestamp()) {
				    		Date d1 = null, d2 = null;
						
							d1 = new Date(Long.valueOf(o1.split(",")[temporalIndex]));
							d2 = new Date(Long.valueOf(o2.split(",")[temporalIndex]));
						
							return d1.compareTo(d2);
				    		
				    	} else {
					    	SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd kk:mm:ss.SSS z yyyy");//need to change if timestamp format changes
							Date d1 = null, d2 = null;
							try {
								d1 = formatter.parse(o1.split(",")[temporalIndex]);
								d2 = formatter.parse(o2.split(",")[temporalIndex]);
							} catch (ParseException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							return d1.compareTo(d2);
				    	}
				    }
				});
				
				StringBuffer newData = new StringBuffer();
				for (String line : sortedLines) {
					newData.append(line);
					newData.append(System.lineSeparator());
				}
				
				// DATA FROM BUFFER SORTED BASED ON TIMESTAMP
				data = newData.toString().trim();
				
				SummaryStatistics summary = new SummaryStatistics();
				Metadata meta = createMetaArizona(msg.getPlotID(), data, temporalIndex, msg.getFS().isTimeTimestamp(), indices, sensorType, summary);
				
//				String IRODSPath = path to file, without actual file name. File name is used from localfile
				String IRODSPath = meta.getName().replaceAll("-", File.separator);
				
				// THE 'METADATA' OF THE BLOCK HAS THE SUMMARY DATA IN IT INSIDE THE 'ATTRIBUTES' VARIABLE
				// THE ACTUAL DATA GETS WRITTEN TO FILE TOWARDS THE END OF THIS FUNCTION
				
				// Create a block which contains the location of the raw data in IRODS
				
				Block block;
				
				// DATA SAVING TO GALILEO
				
				if(sensorType.equals("vanilla")) {
					block = new Block(msg.getFSName(), meta, (IRODS_BASE_PATH + IRODSPath+ "/" + meta.getName()+ sensorType + ".gblock").getBytes());
				} else {
					// CASE OF ARIZONA DATA
					// ADDING SENSORTYPE TO THE NAME, SO A DIFFERENT FILE FOR DIFFERENT SENSOR DATA
					meta.setName(meta.getName()+sensorType);
					block = new Block(msg.getFSName(), meta, data.getBytes());
				}				
				
				synchronized(metadataTimes) {
					metadataTimes.add(System.currentTimeMillis()-start);
				}
				
				// THE BLOCK DATA IS SAVED INTO A FILE AND CONTAINS THE PATH TO AN IRODS FILE TO WHICH IT WOULD BE SAVED
				// THE BLOCK METADATA, WHICH CONTAINS THE ACTUAL DATA IS SAVED ON THE METADATA GRAPH
				if(sensorType.equals("vanilla")) {
					msg.getFS().storeBlock(block);
				} else {
					//msg.getFS().storeBlockArizona(block, sensorType, summary);
				}
				
				
				// A TEMPORARY FILE IS ALSO SAVED IN GALILEO THAT SAVES THE ACTUAL BLOCK DATA
				
				// IRODS SENDING IS DONE THROUGH scheduleAtFixedRate
				logger.info("RIKI: ABOUT TO WRITE OUT TO DAILYTEMP :"+ (SystemConfig.getRootDir() + File.separator + "dailyTemp/" + IRODSPath + File.separator + meta.getName() + ".gblock"));
				
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
		
		/**
		 * 
		 * @author sapmitra
		 * @param plotID
		 * @param data - THE ACTUAL DATA SORTED BY TIMESTAMP
		 * @param temporalIndex
		 * @param isTimestamp IF THE TIME IS JUST THE DATE AND NOT A TIMESTAMP
		 * @param indices 
		 * @param sensorType 
		 * @param sensorType 
		 * @return
		 * @throws ParseException
		 */
		private Metadata createMetaArizona(int plotID, String data, int temporalIndex, boolean isTimestamp, int[] indices, String sensorType, SummaryStatistics summary) throws ParseException {
			
			// THIS IS CREATING METADATA FOR A BLOCK OF PARTIAL DATA -SENSOR/LIDAR
			
			Metadata meta = new Metadata();
			String[] dataLines = data.split(System.lineSeparator());
			String [] firstLine = dataLines[0].split(",");
			String[] lastLine = dataLines[dataLines.length-1].split(",");
			
			String first_timestamp = firstLine[temporalIndex];
			String last_timestamp = lastLine[temporalIndex];
			
			// IF THE TIME IS TIMESTAMP AND NOT A DATE STRING
			if(isTimestamp) {
				Calendar cal1 = GeoHash.getCalendarFromTimestamp(first_timestamp, true);
				/*Date date = cal1.getTime();
				DateFormat dateFormat = new SimpleDateFormat("EEE MMM dd kk:mm:ss.SSS z yyyy");
				first_timestamp = dateFormat.format(date);
				
				Calendar cal2 = GeoHash.getCalendarFromTimestamp(last_timestamp, true);
				Date dateL = cal2.getTime();
				last_timestamp = dateFormat.format(dateL);*/
				
				int month = cal1.get(Calendar.MONTH) + 1;//add 1 because Calendar class months are 0 based (i.e Jan=0, Feb=1...) but we need human readable month
				int year = cal1.get(Calendar.YEAR);
				int dayOfMonth = cal1.get(Calendar.DAY_OF_MONTH);
				
				meta.setName("" + year + "-" + month + "-" + dayOfMonth+"-"+plotID + "-" + sensorType);
				//String [] sorted = data.split(System.lineSeparator());
				
				long firstTime = Long.parseLong(first_timestamp);
				long lastTime = Long.parseLong(last_timestamp);
				
				if (firstTime == lastTime)
					lastTime ++;// a hack to get around data chunks with only one item (add 1ms to end time)
				meta.setTemporalProperties(new TemporalProperties(firstTime, lastTime));
				
				FeatureSet attributes = createAttributes_Arizona(data, indices, sensorType, summary, plotID, year,month,dayOfMonth);
				
				meta.setAttributes(attributes);
				return meta;
				
			} else {
				// THIS IS THE CASE OF ARIZONA
				
				SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd kk:mm:ss.SSS z yyyy");//need to change if timestamp format changes
				Date parsedDate = formatter.parse(first_timestamp);
				Calendar cal = Calendar.getInstance();
				cal.setTime(parsedDate);
				
				int month = cal.get(Calendar.MONTH) + 1;//add 1 because Calendar class months are 0 based (i.e Jan=0, Feb=1...) but we need human readable month
				int year = cal.get(Calendar.YEAR);
				int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
				
				meta.setName("" + year + "-" + month + "-" + dayOfMonth+"-"+plotID + "-" + sensorType);
				//String [] sorted = data.split(System.lineSeparator());
				
				long firstTime = formatter.parse(first_timestamp).getTime();
				long lastTime = formatter.parse(last_timestamp).getTime();
				
				if (firstTime == lastTime)
					lastTime ++;// a hack to get around data chunks with only one item (add 1ms to end time)
				meta.setTemporalProperties(new TemporalProperties(firstTime, lastTime));
				
				FeatureSet attributes = createAttributes_Arizona(data, indices, sensorType, summary, plotID, year,month,dayOfMonth);
				
				meta.setAttributes(attributes);
				return meta;
			}
			
			/*
			SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd kk:mm:ss.SSS z yyyy");//need to change if timestamp format changes
			Date parsedDate = formatter.parse(first_timestamp);
			Calendar cal = Calendar.getInstance();
			cal.setTime(parsedDate);
			
			int month = cal.get(Calendar.MONTH) + 1;//add 1 because Calendar class months are 0 based (i.e Jan=0, Feb=1...) but we need human readable month
			int year = cal.get(Calendar.YEAR);
			int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
			
			meta.setName("" + plotID + "-" + year + "-" + month + "-" + dayOfMonth+"-"+sensorType);
			
			
			long firstTime = formatter.parse(first_timestamp).getTime();
			long lastTime = formatter.parse(last_timestamp).getTime();
			
			if (firstTime == lastTime)
				lastTime ++;// a hack to get around data chunks with only one item (add 1ms to end time)
			meta.setTemporalProperties(new TemporalProperties(firstTime, lastTime));
			
			FeatureSet attributes = createAttributes_Arizona(data, indices, sensorType, summary, plotID, year+"-"+month+"-"+dayOfMonth);
			
			meta.setAttributes(attributes);
			return meta;*/
			
		}
		
		// THIS IS JUST BACKUP
		private Metadata createMeta(int plotID, String data, int temporalIndex, boolean isEpoch, int[] indices, String sensorType, String fsName) throws ParseException {
			
			Metadata meta = new Metadata();
			String[] dataLines = data.split(System.lineSeparator());
			String [] firstLine = dataLines[0].split(",");
			String[] lastLine = dataLines[dataLines.length-1].split(",");
			
			String first_timestamp = firstLine[temporalIndex];
			String last_timestamp = lastLine[temporalIndex];
			
			// IF THE TIME IS JUST THE DATE AND NOT A TIMESTAMP
			if(isEpoch) {
				Calendar cal1 = Calendar.getInstance();
				cal1 = GeoHash.getCalendarFromTimestamp(first_timestamp, true);
				Date date = cal1.getTime();
				DateFormat dateFormat = new SimpleDateFormat("EEE MMM dd kk:mm:ss.SSS z yyyy");
				first_timestamp = dateFormat.format(date);
				
				Calendar cal2 = Calendar.getInstance();
				cal2 = GeoHash.getCalendarFromTimestamp(last_timestamp, true);
				Date dateL = cal2.getTime();
				last_timestamp = dateFormat.format(dateL);
			}
			
			
			SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd kk:mm:ss.SSS z yyyy");//need to change if timestamp format changes
			Date parsedDate = formatter.parse(first_timestamp);
			Calendar cal = Calendar.getInstance();
			cal.setTime(parsedDate);
			
			int month = cal.get(Calendar.MONTH) + 1;//add 1 because Calendar class months are 0 based (i.e Jan=0, Feb=1...) but we need human readable month
			int year = cal.get(Calendar.YEAR);
			int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
			
			meta.setName("" + plotID + "-" + year + "-" + month + "-" + dayOfMonth);
			//String [] sorted = data.split(System.lineSeparator());
			
			// FIRST AND LAST RECORD
			//String first = sorted[0], last = sorted[sorted.length-1];
			String first = dataLines[0], last = dataLines[dataLines.length-1];
			
			long firstTime = formatter.parse(first_timestamp).getTime();
			long lastTime = formatter.parse(last_timestamp).getTime();
			
			if (firstTime == lastTime)
				lastTime ++;// a hack to get around data chunks with only one item (add 1ms to end time)
			meta.setTemporalProperties(new TemporalProperties(firstTime, lastTime));
			
			FeatureSet attributes = createAttributes_backup(data);
			attributes.put(new Feature("plotID", plotID));
			attributes.put(new Feature("date", year+"-"+month+"-"+dayOfMonth));
			//rep and genotype are in shapefile, so HashGrid maintains in-memory mapping of this data for each plot
			attributes.put(new Feature("rep", sn.getGlobalGrid(fsName).getPlotInfo(plotID).a));
			attributes.put(new Feature("genotype", sn.getGlobalGrid(fsName).getPlotInfo(plotID).b));
			meta.setAttributes(attributes);
			return meta;
			
		}
		
		private FeatureSet createAttributes_Arizona(String data, int[] indices, String sensorType, SummaryStatistics summary, int plotID, 
				int year,int month,int dayOfMonth) {
			
			int dataIndex = indices[3];
			
			//Assuming features are: CO2, Temperature, Humidity
			FeatureSet attributes = new FeatureSet();
			
			attributes.put(new Feature(GeospatialFileSystem.TEMPORAL_YEAR_FEATURE, year));
			attributes.put(new Feature(GeospatialFileSystem.TEMPORAL_MONTH_FEATURE, month));
			attributes.put(new Feature(GeospatialFileSystem.TEMPORAL_DAY_FEATURE, dayOfMonth));
			attributes.put(new Feature("plotID", plotID));
			attributes.put(new Feature("sensorType", sensorType));
			
			// SENSOR READINGS
			ArrayList<Double> sensor_readings = new ArrayList<>();
			
			String [] lines = data.split(System.lineSeparator());
			
			// READING LINE BY LINE
			for (String line : lines) {
				if (line.isEmpty())
					continue;//don't process an empty line which may have found its way into the data chunk
				
				String [] observations = line.split(",");
				
				if(observations.length <= dataIndex) {
					//logger.info("RIKI: INVALID ENTRY");
					continue;
				}
				
				String valueOfAttribute = observations[dataIndex];
				sensor_readings.add(Double.parseDouble(valueOfAttribute));
			}
			
			if(sensor_readings.size() > 0 ) {
				summary.setMin(Collections.min(sensor_readings));
				summary.setMax(Collections.max(sensor_readings));
				summary.setAvg(galileo.util.Math.computeAvg(sensor_readings));
				summary.setStdDev(galileo.util.Math.computeStdDev(sensor_readings));
				summary.setCount(sensor_readings.size());
			} 

			return attributes;
			
		}
	}
	
	
	private FeatureSet createAttributes_backup(String data) {
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


	public void removePlotEntry(String key) {
		synchronized(plotsProcessed) {
			plotsProcessed.remove(key);
		}
		
	}
	
	/*
	 * public String getSensorTypeFromPath(String path) {
	 * 
	 * if(!path.contains(".")) { return "invalid"; } String tokens[] =
	 * path.split("\\.");
	 * 
	 * String tempStr = tokens[tokens.length-2];
	 * 
	 * String tokens1[] = tempStr.split("-");
	 * 
	 * return tokens1[tokens1.length-1];
	 * 
	 * } public DataStoreHandler() {} public static void main(String arg[]) {
	 * 
	 * String m =
	 * "/s/lattice-1/a/nobackup/galileo/sapmitra/galileo-sapmitra/dailyTemp/20404/2018/9/28/irt/20404-2018-9-28-irt.gblock";
	 * DataStoreHandler d = new DataStoreHandler();
	 * System.out.println(d.getSensorTypeFromPath(m));
	 * 
	 * }
	 */
	
}
