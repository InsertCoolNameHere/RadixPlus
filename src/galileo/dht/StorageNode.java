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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.irods.jargon.core.exception.JargonException;
import org.json.JSONArray;
import org.json.JSONObject;

import galileo.bmp.Bitmap;
import galileo.bmp.BitmapException;
import galileo.bmp.GeoavailabilityGrid;
import galileo.bmp.GeoavailabilityQuery;
import galileo.bmp.HashGrid;
import galileo.bmp.HashGridException;
import galileo.bmp.QueryTransform;
import galileo.comm.BlockQueryRequest;
import galileo.comm.BlockQueryResponse;
import galileo.comm.BlockRequest;
import galileo.comm.BlockResponse;
import galileo.comm.FilesystemAction;
import galileo.comm.FilesystemEvent;
import galileo.comm.FilesystemRequest;
import galileo.comm.TemporalFilesystemEvent;
import galileo.comm.TemporalFilesystemRequest;
import galileo.comm.GalileoEventMap;
import galileo.comm.IRODSReadyCheckRequest;
import galileo.comm.IRODSReadyCheckResponse;
import galileo.comm.IRODSRequest;
import galileo.comm.IRODSRequest.TYPE;
import galileo.comm.MetadataEvent;
import galileo.comm.MetadataRequest;
import galileo.comm.MetadataResponse;
import galileo.comm.NonBlockStorageRequest;
import galileo.comm.QueryEvent;
import galileo.comm.QueryRequest;
import galileo.comm.QueryResponse;
import galileo.comm.QueueRequest;
import galileo.comm.QueueResponse;
import galileo.comm.RigUpdateRequest;
import galileo.comm.StorageEvent;
import galileo.comm.StorageRequest;
import galileo.comm.TemporalType;
import galileo.config.SystemConfig;
import galileo.dataset.Block;
import galileo.dataset.DataIngestor;
import galileo.dataset.Metadata;
import galileo.dataset.SpatialProperties;
import galileo.dataset.SpatialRange;
import galileo.dataset.TemporalProperties;
import galileo.dataset.feature.Feature;
import galileo.dht.hash.HashException;
import galileo.dht.hash.HashTopologyException;
import galileo.dht.hash.TemporalHash;
import galileo.event.ConcurrentEventReactor;
import galileo.event.Event;
import galileo.event.EventContext;
import galileo.event.EventHandler;
import galileo.fs.FileSystem;
import galileo.fs.FileSystemException;
import galileo.fs.GeospatialFileSystem;
import galileo.graph.Path;
import galileo.graph.SummaryStatistics;
import galileo.net.ClientConnectionPool;
import galileo.net.MessageListener;
import galileo.net.NetworkDestination;
import galileo.net.PortTester;
import galileo.net.RequestListener;
import galileo.net.ServerMessageRouter;
import galileo.query.Expression;
import galileo.query.Operation;
import galileo.query.Operator;
import galileo.query.Query;
import galileo.serialization.SerializationException;
import galileo.util.GeoHash;
import galileo.util.Version;
import web.Listener;

/**
 * Primary communication component in the Galileo DHT. StorageNodes service
 * client requests and communication from other StorageNodes to disseminate
 * state information throughout the DHT.
 *
 * @author malensek
 */
public class StorageNode implements RequestListener{

	private static final Logger logger = Logger.getLogger("galileo");
	private StatusLine nodeStatus;
	private String hostname; // The name of this host
	private String canonicalHostname; // The fqdn of this host
	private int port;
	private String rootDir;
	private String resultsDir;
	private int numCores;

	private File pidFile;
	private File fsFile;
	private DataIngestor ingestor;
	private NetworkInfo network;

	private ServerMessageRouter messageRouter;
	private ClientConnectionPool connectionPool;
//	private Map<String, GeospatialFileSystem> fsMap;
	private Map<String, FileSystem> fsMap;
	//private HashGrid globalGrid;
	private GalileoEventMap eventMap = new GalileoEventMap();
	private ConcurrentEventReactor eventReactor = new ConcurrentEventReactor(this, eventMap,4);
	private List<ClientRequestHandler> requestHandlers;
	private DataStoreHandler dataStoreHandler;
	private ConcurrentHashMap<String, QueryTracker> queryTrackers = new ConcurrentHashMap<>();
	private Listener listener;
	
	private NetworkDestination rig_monitor;
	private boolean isMonitor = false;
	
	
	public StorageNode() throws IOException, HashGridException {
		try {
			this.hostname = InetAddress.getLocalHost().getHostName();
			this.canonicalHostname = InetAddress.getLocalHost().getCanonicalHostName();
		} catch (UnknownHostException e) {
			this.hostname = System.getenv("HOSTNAME");
			if (hostname == null || hostname.length() == 0)
				throw new UnknownHostException(
						"Failed to identify host name of the storage node. Details follow: " + e.getMessage());
		}
		
		this.dataStoreHandler = new DataStoreHandler(this);
		
		this.hostname = this.hostname.toLowerCase();
		this.canonicalHostname = this.canonicalHostname.toLowerCase();
		this.port = NetworkConfig.getPort();
		SystemConfig.reload();
		this.rootDir = SystemConfig.getRootDir();
		this.resultsDir = this.rootDir + "/.results";
		this.nodeStatus = new StatusLine(SystemConfig.getRootDir() + "/status.txt");
		this.fsFile = new File(SystemConfig.getRootDir() + "/storage-node.fs");
		if (!this.fsFile.exists())
			this.fsFile.createNewFile();
		String pid = System.getProperty("pidFile");
		if (pid != null) {
			this.pidFile = new File(pid);
		}
		this.numCores = Runtime.getRuntime().availableProcessors();
		this.requestHandlers = new CopyOnWriteArrayList<ClientRequestHandler>();
		
		
		// THIS PART HANDLES LOADING OF THE SHAPEFILE
		/*Configure the global HashGrid based on a required file. This file
		 * must be located in the config/grid directory. Only one such file may exist.*/
		
		// /radix/galileo/config/grid/
		/*
		logger.severe("RIKI: CONF DIR NOT USED: "+SystemConfig.getConfDir()+File.separator+"grid");
		String pathToGridFile = SystemConfig.getConfDir()+File.separator+"grid";
		File [] gridFiles = new File(pathToGridFile).listFiles();
		if (gridFiles.length != 1)
			throw new HashGridException("Could not locate required grid initialization file. Ensure that only one "
					+ "shape file exists in " + pathToGridFile);
					*/
		/**@TODO Modify HashGrid constructor to receive the file and have it detect the baseHash*/
		
		/*
		try {
			// BASEHASH, UPPER-LEFT, BOTTOM-RIGHT
			//globalGrid = new HashGrid("wdw0x9", 11, "wdw0x9bpbpb", "wdw0x9pbpbp");
			globalGrid = new HashGrid(baseHash, 11, a1, a2);
			//pathToGridFile + name of grid file
			
			// THIS READS THE PLOTS.JSON FILE AND MARKS THE PLOTS ON THE HASHGRID
			globalGrid.initGrid(pathToGridFile+File.separator+gridFiles[0].getName());
			logger.info("HashGrid initialized");
			
		} catch (IOException | HashGridException | BitmapException e) {
			logger.log(Level.SEVERE, "could not open grid initialization file. Error: " + e);
		}*/
	}

	/**
	 * Begins Server execution. This method attempts to fail fast to provide
	 * immediate feedback to wrapper scripts or other user interface tools. Only
	 * once all the prerequisite components are initialized and in a sane state
	 * will the StorageNode begin accepting connections.
	 */
	public void start() throws Exception {
		Version.printSplash();
		
		/* First, make sure the port we're binding to is available. */
		nodeStatus.set("Attempting to bind to port");
		if (PortTester.portAvailable(port) == false) {
			nodeStatus.set("Could not bind to port " + port + ".");
			throw new IOException("Could not bind to port " + port);
		}

		/*
		 * Read the network configuration; if this is invalid, there is no need
		 * to execute the rest of this method.
		 */
		nodeStatus.set("Reading network configuration");
		network = NetworkConfig.readNetworkDescription(SystemConfig.getNetworkConfDir());
		
		
		rig_monitor = network.getAllDestinations().get(0);
		
		if(rig_monitor.getHostname().equals(canonicalHostname)||rig_monitor.getHostname().equals(hostname))
			isMonitor = true;
		// identifying the group of this storage node
		boolean nodeFound = false;
		for (NodeInfo node : network.getAllNodes()) {
			String nodeName = node.getHostname();
			
			//logger.info("RIKI: HOST AND CANONICAL: "+ this.hostname+" "+this.canonicalHostname);
			//logger.info("RIKI: NODENAME: "+nodeName);
			if (nodeName.equals(this.hostname) || nodeName.equals(this.canonicalHostname)) {
				nodeFound = true;
				break;
			}
		}
		if (!nodeFound)
			throw new Exception("Failed to identify the group of the storage node. "
					+ "Type 'hostname' in the terminal and make sure that it matches the "
					+ "hostnames specified in the network configuration files.");

		nodeStatus.set("Restoring filesystems");
		File resultsDir = new File(this.resultsDir);
		if (!resultsDir.exists())
			resultsDir.mkdirs();

		this.fsMap = new HashMap<>();
		try (BufferedReader br = new BufferedReader(new FileReader(fsFile))) {
			String jsonSource = br.readLine();
			//logger.info("jsonSource: " + jsonSource);
			if (jsonSource != null && jsonSource.length() > 0) {
				JSONObject fsJSON = new JSONObject(jsonSource);
				if (JSONObject.getNames(fsJSON) != null)
					for (String fsName : JSONObject.getNames(fsJSON)) {
						try {
							GeospatialFileSystem gfs = GeospatialFileSystem.restoreState(this, network,
								fsJSON.getJSONObject(fsName));
							this.fsMap.put(fsName, gfs);
//							TemporalFileSystem tfs = TemporalFileSystem.restoreState(this, network, fsJSON.getJSONObject(fsName));
//							this.fsMap.put(fsName, tfs);
						} catch (Exception e) {
							logger.log(Level.SEVERE, "could not restore filesystem - " + fsName, e);
						}
					}
			}
		} catch (IOException ioe) {
			logger.log(Level.SEVERE, "Failed to restore filesystems", ioe);
		}
		/* Set up our Shutdown hook */
		Runtime.getRuntime().addShutdownHook(new ShutdownHandler());

		
		/* Pre-scheduler setup tasks */
		connectionPool = new ClientConnectionPool();
		connectionPool.addListener(eventReactor);

		/* Start listening for incoming messages. */
		messageRouter = new ServerMessageRouter();
		messageRouter.addListener(eventReactor);
		messageRouter.listen(port);
		nodeStatus.set("Online");
		
		/* Start processing the message loop */
		while (true) {
			try {
				eventReactor.processNextEvent();
			} catch (Exception e) {
				logger.log(Level.SEVERE, "An exception occurred while processing next event. "
						+ "Storage node is still up and running. Exception details follow:", e);
			}
		}
	}

	protected void sendEvent(NodeInfo node, Event event) throws IOException {
		connectionPool.sendMessage(node, eventReactor.wrapEvent(event));
	}
	
	public FileSystem getFS(String fsName){
//		logger.info("getFS called, there are " + fsMap.size() + "filesystems existing");
		return this.fsMap.get(fsName);
	}
	
	public Map<String, FileSystem> getFSMap() {
		return this.fsMap;
	}
	@EventHandler
	public void handleFileSystemRequest(TemporalFilesystemRequest request, EventContext context)
			throws HashException, IOException, PartitionException {
		String name = request.getName();
		FilesystemAction action = request.getAction();
		List<NodeInfo> nodes = network.getAllNodes();
		double bucketDur = request.getBucketDuration();
		TemporalType type = request.getTemporalType();
//		FilesystemEvent event = new FilesystemEvent(name, action, request.getFeatureList(), request.getSpatialHint());
		TemporalFilesystemEvent event = new TemporalFilesystemEvent(name, action, request.getFeatureList(), bucketDur, type);
		event.setNodesPerGroup(request.getNodesPerGroup());
		for (NodeInfo node : nodes) {
			logger.info("Requesting " + node + " to perform a file system action");
			sendEvent(node, event);
		}
	}
	
	//For handling geospatial and non-purely-temporal requests
	@EventHandler
	public void handleFileSystemRequest(FilesystemRequest request, EventContext context)
			throws HashException, IOException, PartitionException {
		String name = request.getName();
		FilesystemAction action = request.getAction();
		List<NodeInfo> nodes = network.getAllNodes();
		FilesystemEvent event = new FilesystemEvent(name, action, request.getFeatureList(), request.getSpatialHint());
		event.setPrecision(request.getPrecision());
		event.setNodesPerGroup(request.getNodesPerGroup());
		event.setTemporalType(request.getTemporalType());
		event.setConfigs(request.getConfigs());
		for (NodeInfo node : nodes) {
			//logger.info("Requesting " + node + " to perform a file system action");
			sendEvent(node, event);
		}
	}
	
	//For handling geospatial or non-purely-temporal filesystem events
	@EventHandler
	public void handleFileSystem(FilesystemEvent event, EventContext context) {
		logger.log(Level.INFO,
				"Performing action " + event.getAction().getAction() + " for file system " + event.getName());
		if (event.getAction() == FilesystemAction.CREATE) {
			GeospatialFileSystem fs = (GeospatialFileSystem)fsMap.get(event.getName());
			if (fs == null) {
				try {
					logger.info("RIKI: Begin FS Creation");
					fs = new GeospatialFileSystem(this, this.rootDir, event.getName(), event.getPrecision(),
							event.getNodesPerGroup(), event.getTemporalValue(), this.network, event.getFeatures(),
							event.getSpatialHint(), false, event.getConfigs());
					
					fsMap.put(event.getName(), fs);
					//logger.info("RIKI: REACHED HERE5");
					//logger.info("RIKI: FSMAP: "+fsMap);
					//logger.info("RIKI: REACHED HERE6");
				} catch (FileSystemException | SerializationException | IOException | PartitionException | HashException
						| HashTopologyException e) {
					logger.log(Level.SEVERE, "Could not initialize the Galileo File System!", e);
				}
			}
		} else if (event.getAction() == FilesystemAction.DELETE) {
			GeospatialFileSystem fs = (GeospatialFileSystem)fsMap.get(event.getName());
			if (fs != null) {
				fs.shutdown();
				fsMap.remove(event.getName());
				java.nio.file.Path directory = Paths.get(rootDir + File.separator + event.getName());
				try {
					Files.walkFileTree(directory, new SimpleFileVisitor<java.nio.file.Path>() {
						@Override
						public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attrs)
								throws IOException {
							Files.delete(file);
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult postVisitDirectory(java.nio.file.Path dir, IOException exc)
								throws IOException {
							Files.delete(dir);
							return FileVisitResult.CONTINUE;
						}
					});
				} catch (IOException e) {
					logger.log(Level.SEVERE, "Failed to delete the requested file System!", e);
				}
			}
		}
		persistFilesystems();
	}
//	@EventHandler
//	public void handleFileSystem(TemporalFilesystemEvent event, EventContext context) {
//		logger.log(Level.INFO,
//				"Performing action " + event.getAction().getAction() + " for file system " + event.getName());
//		if (event.getAction() == FilesystemAction.CREATE) {
////			GeospatialFileSystem fs = fsMap.get(event.getName());
//			TemporalFileSystem fs = (TemporalFileSystem)fsMap.get(event.getName());
//			if (fs == null) {
//				try {
////					fs = new GeospatialFileSystem(this, this.rootDir, event.getName(), event.getPrecision(),
////							event.getNodesPerGroup(), event.getTemporalValue(), this.network, event.getFeatures(),
////							event.getSpatialHint(), false);
//					fs = new TemporalFileSystem(this, this.rootDir, event.getName(), event.getNodesPerGroup(), this.network, event.getFeatures(), event.getBucketDuration(), event.getTemporalType(), false);
//					fsMap.put(event.getName(), fs);
//				} catch (FileSystemException | SerializationException | IOException | PartitionException | HashException
//						| HashTopologyException e) {
//					logger.log(Level.SEVERE, "Could not initialize the Galileo File System!", e);
//				}
//			}
//		} else if (event.getAction() == FilesystemAction.DELETE) {
////			GeospatialFileSystem fs = fsMap.get(event.getName());
//			TemporalFileSystem fs = (TemporalFileSystem)fsMap.get(event.getName());
//			if (fs != null) {
//				fs.shutdown();
//				fsMap.remove(event.getName());
//				java.nio.file.Path directory = Paths.get(rootDir + File.separator + event.getName());
//				try {
//					Files.walkFileTree(directory, new SimpleFileVisitor<java.nio.file.Path>() {
//						@Override
//						public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attrs)
//								throws IOException {
//							Files.delete(file);
//							return FileVisitResult.CONTINUE;
//						}
//
//						@Override
//						public FileVisitResult postVisitDirectory(java.nio.file.Path dir, IOException exc)
//								throws IOException {
//							Files.delete(dir);
//							return FileVisitResult.CONTINUE;
//						}
//					});
//				} catch (IOException e) {
//					logger.log(Level.SEVERE, "Failed to delete the requested file System!", e);
//				}
//			}
//		}
//		persistFilesystems();
//	}

	/**
	 * Handles a non-block storage request from a client. Note that this method will receive raw
	 * data in the from of a large string (separated by new lines) and will need to be modified
	 * if the format of raw data is changed.*/
	private Timer throughputTimer = new Timer();

	public String getHostName() {
		return this.hostname;
	}
	
	public String getCanonicalHostName() {
		return this.canonicalHostname;
	}
	
	private Set<Integer> plotLocks = new HashSet<>();
	private int currentNode = -1;
	
	/*@EventHandler
	public void handleQueueRequest(QueueRequest request, EventContext context) throws IOException {
		int nodeNum = request.getNodeNum();
		
		if(currentNode == -1) {
			currentNode = nodeNum;
			context.sendReply(new QueueResponse(nodeNum,true));
			logger.info("RIKI: NODE "+nodeNum+" GRANTED QUEUE LOCK");
		} else if(nodeNum == currentNode) {
			// NODE NOTIFYING THAT IT IS DONE WRITING
			currentNode = -1;
			logger.info("RIKI: NODE "+nodeNum+" RELEASED QUEUE LOCK");
		} else {
			context.sendReply(new QueueResponse(nodeNum,false));
		}
	}*/
	
	@EventHandler
	public void handleIRODSRequest(IRODSRequest request, EventContext context) throws IOException {
		switch (request.getType()) {
			case QUEUE_REQ:
				int nodeNum = request.getPlotNum();
				
				if(currentNode == -1) {
					currentNode = nodeNum;
					context.sendReply(new IRODSRequest(TYPE.QUEUE_GRANT, 0));
					logger.info("RIKI: NODE "+nodeNum+" GRANTED QUEUE LOCK");
				} else if(nodeNum == currentNode) {
					// NODE NOTIFYING THAT IT IS DONE WRITING
					currentNode = -1;
					logger.info("RIKI: NODE "+nodeNum+" RELEASED QUEUE LOCK");
				} else {
					context.sendReply(new IRODSRequest(TYPE.IGNORE, 0));
				}
				break;
			case LOCK_REQUEST:
				if (!plotLocks.contains(request.getPlotNum())) {
					plotLocks.add(request.getPlotNum());
					context.sendReply(new IRODSRequest(TYPE.LOCK_ACQUIRED, request.getPlotNum()));
					if(request.getPlotNum() == 9971)
						logger.info("RIKI: PLOT LOCK YES FOR "+request.getPlotNum());
				}
				else {
					context.sendReply(new IRODSRequest(TYPE.IGNORE, request.getPlotNum()));
					if(request.getPlotNum() == 9971)
						logger.info("RIKI: PLOT LOCK NO FOR "+request.getPlotNum());
				}
				break;
			case LOCK_RELEASE_REQUEST:
				if (plotLocks.contains(request.getPlotNum())) {
					plotLocks.remove(request.getPlotNum());
					context.sendReply(new IRODSRequest(TYPE.LOCK_RELEASED, request.getPlotNum()));
				} else {
					context.sendReply(new IRODSRequest(TYPE.IGNORE, request.getPlotNum()));
				}
				break;
			case DATA_REQUEST:
				// Need to search fs for plot data and return it
				File toGet = new File(SystemConfig.getRootDir()+request.getFilePath());
				
				if(request.getPlotNum() == 9971)
					logger.info("RIKI: GOT REQUESTED 9971 AT "+toGet.getAbsolutePath());
				GeospatialFileSystem gfs = (GeospatialFileSystem)fsMap.get(request.getFs());
				
				if (toGet.exists()) {
					String contents = new String(Files.readAllBytes(Paths.get(toGet.getAbsolutePath())));
					
					SummaryStatistics stats = gfs.getStatistics(toGet.getAbsolutePath());
					// NEED TO GET METADATA OF THE THING AS WELL
					
					IRODSRequest reply = new IRODSRequest(TYPE.DATA_REPLY, request.getPlotNum());
					reply.setFilePath(request.getFilePath());
					reply.setData(contents);
					reply.setSummary(stats);
					
					context.sendReply(reply);

					// REMOVE THE LOCAL CONTENTS ON THIS PLOT
					
			    	String toSearch = request.getPlotNum()+File.separator;
			    	String relPath = request.getFilePath();
			    	int end = relPath.lastIndexOf(toSearch) + toSearch.length();
					relPath = relPath.substring(0,end);
					
					String folderLoc = SystemConfig.getRootDir()+relPath;
					
					File toGetFolder = new File(folderLoc);
					//toGetFolder.delete();
					deleteDirectoryRecursion(toGetFolder.toPath());
					if(request.getPlotNum() == 9971)
						logger.info("RIKI: REMOVED ILLEGITIMATE: "+folderLoc);
				}
				else
					context.sendReply(new IRODSRequest(TYPE.IGNORE, request.getPlotNum()));
				break;
			default:
				context.sendReply(new IRODSRequest(TYPE.IGNORE, request.getPlotNum()));
		}
	}
	
	public void deleteDirectoryRecursion(java.nio.file.Path path) throws IOException {
		if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
			try (DirectoryStream<java.nio.file.Path> entries = Files.newDirectoryStream(path)) {
				for (java.nio.file.Path entry : entries) {
					deleteDirectoryRecursion(entry);
				}
			}
		}
		Files.delete(path);
	}
	
	@EventHandler
	public void handleIRODSReadyCheck(IRODSReadyCheckRequest check, EventContext context) throws IOException {
		IRODSReadyCheckResponse response = new IRODSReadyCheckResponse(IRODSReadyCheckResponse.Type.REPLY);
		long timeSinceLastMessage = System.currentTimeMillis() - this.dataStoreHandler.getLastProcessedTime();
		if (timeSinceLastMessage >= DataStoreHandler.irodsCheckTimeSecs*1000) //600000 ms = 10 minutes
			response.setReady(true);
		context.sendReply(response);
	}
	
	@EventHandler
	public void handleNonBlockStorageRequest(NonBlockStorageRequest request, EventContext context) throws IOException {
		String fsName = request.getFS();
		if (fsName != null) {
			GeospatialFileSystem gfs = (GeospatialFileSystem) fsMap.get(fsName);
			if (gfs != null) {
				//logger.info("RIKI: NB STORE MSG1: "+fsName+" "+request.getSensorType());
				// THIS IS A SINGLE CHUNK'S DATA COMING IN FOR PROCESSING
				StoreMessage msg = new StoreMessage(Type.UNPROCESSED, request.getData(), gfs, fsName, request.getSensorType());
				msg.setCheckAll(request.checkAll());
				dataStoreHandler.addMessage(msg);
			}
		}		
	}
	
	
	
	@EventHandler
	public void XhandleRIGUpdateRequest(RigUpdateRequest request, EventContext context) throws IOException {
		
		if(!isMonitor) {
			logger.info("RIKI: NON-MONITOR");
			return;
		}
		String fsName = request.getFilesystem();
		if (fsName != null) {
			GeospatialFileSystem gfs = (GeospatialFileSystem) fsMap.get(fsName);
			if(gfs!=null)
				gfs.XupdateFS_RIG();
		}		
	}

	
	
	
		
	// IDENTICAL TO handleNonBlockStorageRequest
	public void handleLocalNonBlockStorageRequest(NonBlockStorageRequest request) throws IOException {
		String fsName = request.getFS();
		String sensorType = request.getSensorType();
		if (fsName != null) {
			GeospatialFileSystem gfs = (GeospatialFileSystem) fsMap.get(fsName);
			if (gfs != null) {
				logger.info("RIKI: STORE MSG: "+fsName+" "+sensorType);
				StoreMessage msg = new StoreMessage(Type.UNPROCESSED, request.getData(), gfs, fsName, sensorType);
				msg.setCheckAll(request.checkAll());
				msg.setSensorType(sensorType);
				dataStoreHandler.addMessage(msg);
			}
		}
	}
	/**
	 * Handles a storage request from a client. This involves determining where
	 * the data belongs via a {@link Partitioner} implementation and then
	 * forwarding the data on to its destination.
	 */
//	@EventHandler
//	public void handleStorageRequest(StorageRequest request, EventContext context)
//			throws HashException, IOException, PartitionException {
//		/* Determine where this block goes. */
//		Block file = request.getBlock();
//		String fsName = file.getFilesystem();
//		if (fsName != null) {
////			GeospatialFileSystem gfs = this.fsMap.get(fsName);
//			TemporalFileSystem tfs = (TemporalFileSystem)this.fsMap.get(fsName);
//			if (tfs != null) {
//				Metadata metadata = file.getMetadata();
//				Partitioner<Metadata> partitioner = tfs.getPartitioner();
//				NodeInfo node = partitioner.locateData(metadata);
//				logger.log(Level.INFO, "Storage destination: {0}", node);
//				StorageEvent store = new StorageEvent(file);
//				sendEvent(node, store);
//			} else {
//				logger.log(Level.WARNING, "No filesystem found for the specified name " + fsName + ". Request ignored");
//			}
//		} else {
//			logger.log(Level.WARNING, "No filesystem name specified to store the block. Request ignored");
//		}
//	}

	@EventHandler
	public void handleStorage(StorageEvent store, EventContext context) {
		String fsName = store.getBlock().getFilesystem();
		GeospatialFileSystem fs = (GeospatialFileSystem)fsMap.get(fsName);
//		TemporalFileSystem fs = (TemporalFileSystem)fsMap.get(fsName);
		if (fs != null) {
			logger.log(Level.INFO, "Storing block " + store.getBlock() + " to filesystem " + fsName);
			try {
				fs.storeBlock(store.getBlock());
			} catch (FileSystemException | IOException e) {
				logger.log(Level.SEVERE, "Something went wrong while storing the block.", e);
			}
		} else {
			logger.log(Level.SEVERE, "Requested file system(" + fsName + ") not found. Ignoring the block.");
		}
	}
	
	

	/**
	 * Handles a meta request that seeks information regarding the galileo
	 * system.
	 */
	@EventHandler
	public void handleMetadataRequest(MetadataRequest request, EventContext context) {
		try {
			logger.info("Meta Request: " + request.getRequest().getString("kind"));
			if ("galileo#filesystem".equalsIgnoreCase(request.getRequest().getString("kind"))) {
				
				logger.info("RIKI: ENTERED LOOP 1");
				JSONObject response = new JSONObject();
				response.put("kind", "galileo#filesystem");
				response.put("result", new JSONArray());
				ClientRequestHandler reqHandler = new ClientRequestHandler(network.getAllDestinations(), context, this);
				reqHandler.handleRequest(new MetadataEvent(request.getRequest()), new MetadataResponse(response));
				this.requestHandlers.add(reqHandler);
			} else if ("galileo#features".equalsIgnoreCase(request.getRequest().getString("kind"))) {
				
				logger.info("RIKI: ENTERED LOOP 2");
				JSONObject response = new JSONObject();
				response.put("kind", "galileo#features");
				response.put("result", new JSONArray());
				ClientRequestHandler reqHandler = new ClientRequestHandler(network.getAllDestinations(), context, this);
				reqHandler.handleRequest(new MetadataEvent(request.getRequest()), new MetadataResponse(response));
				this.requestHandlers.add(reqHandler);
			} else if ("galileo#overview".equalsIgnoreCase(request.getRequest().getString("kind"))) {
				
				logger.info("RIKI: ENTERED LOOP 3");
				JSONObject response = new JSONObject();
				response.put("kind", "galileo#overview");
				response.put("result", new JSONArray());
				ClientRequestHandler reqHandler = new ClientRequestHandler(network.getAllDestinations(), context, this);
				reqHandler.handleRequest(new MetadataEvent(request.getRequest()), new MetadataResponse(response));
				this.requestHandlers.add(reqHandler);
				
			} else if ("galileo#plot".equalsIgnoreCase(request.getRequest().getString("kind"))) {
				
				// INDIVIDUAL PLOT QUERY...BE IT SUMMARY(CLICK) OR SERIES(PLOTWISE) IS OF THIS TYPE
				logger.info("RIKI: ABOUT TO PERFORM A SINGLE PLOT CALCULATION...");
				/*-------------------------------------------------------------*/
				// THIS HANDLES METADATA REQUEST
				logger.info("RIKI: RECEIVED A METADATA REQUEST..." + request.getRequest().getString("type"));
				
				JSONObject response = new JSONObject();
				response.put("kind", "galileo#plot");
				response.put("result",  new JSONArray());
				response.put("type", request.getRequest().getString("type"));
				logger.info("Putting type: " + request.getRequest().getString("type"));
				ClientRequestHandler reqHandler = new ClientRequestHandler(network.getAllDestinations(), context, this);
				reqHandler.handleRequest(new MetadataEvent(request.getRequest()), new MetadataResponse(response));
				this.requestHandlers.add(reqHandler);
			
			} else if ("galileo#upload".equalsIgnoreCase(request.getRequest().getString("kind"))) {
				
				// <=400 LINES OF CHUNK MUST COME IN EACH DATA AT A TIME
				logger.info("RIKI: ENTERED LOOP FOR FILE UPLOAD");
				/*-------------------------------------------------------------*/
				// THIS HANDLES METADATA REQUEST
				logger.info("RIKI: RECEIVED A STREAMING REQUEST..." + request.getRequest().getString("type")
						+ "XXX" + request.getRequest().getInt("data"));
				
				JSONObject response = new JSONObject();
				response.put("kind", "galileo#success");
				
				logger.info("RIKI: RETURNING RESPONSE ");
				
				/*
				 * ClientRequestHandler reqHandler = new
				 * ClientRequestHandler(network.getAllDestinations(), context, this);
				 * reqHandler.handleRequest(new MetadataEvent(request.getRequest()), new
				 * MetadataResponse(response)); this.requestHandlers.add(reqHandler);
				 */
				
				context.sendReply(new MetadataResponse(response));
				
			
			} else {
				
				logger.info("RIKI: ENTERED LOOP 5");
				JSONObject response = new JSONObject();
				response.put("kind", request.getRequest().getString("kind"));
				response.put("error", "invalid request");
				context.sendReply(new MetadataResponse(response));
			}
		} catch (Exception e) {
			JSONObject response = new JSONObject();
			String kind = "unknown";
			if (request.getRequest().has("kind"))
				kind = request.getRequest().getString("kind");
			response.put("kind", kind);
			response.put("error", e.getMessage());
			try {
				context.sendReply(new MetadataResponse(response));
			} catch (IOException e1) {
				logger.log(Level.SEVERE, "Failed to send response to the original client", e);
			}
		}
	}
	
	@EventHandler
	public void handleMetadata(MetadataEvent event, EventContext context) throws IOException {
		if ("galileo#plot".equalsIgnoreCase(event.getRequest().getString("kind")) && "summary".equalsIgnoreCase(event.getRequest().getString("type"))) {
			// GETS CALLED ON PLOT CLICKED...RETURNS SUMMARY FOR THE PLOT
			// ==================SINGLE PLOT SUMMARY================================
			logger.info("RIKI: SUMMARY META");
			
			JSONObject response = new JSONObject();
			response.put("kind", "galileo#plot");
			response.put("type", "summary");
			JSONArray result = new JSONArray();
			GeospatialFileSystem gfs = (GeospatialFileSystem)this.fsMap.get(event.getRequest().getString("filesystem"));
			Query q = new Query(new Operation(new Expression(Operator.EQUAL, new Feature("plotID", event.getRequest().getInt("plotID")))));
			List<Path<Feature, String>> paths = gfs.query(q);
			for (Path<Feature, String> path : paths) {
				String pathStr = path.toString();
				String [] split = pathStr.split("->");
				for (String s : split)
					if (s.contains("=") && !s.contains("payload") && !s.contains("date"))
						result.put(s.trim());
			}
			response.put("result", result);
			
			logger.info("RIKI: Paths Found: " + paths);
			logger.info("RIKI: Results Calculated: " + result);
			context.sendReply(new MetadataResponse(response));

			
		} else if ("galileo#plot".equalsIgnoreCase(event.getRequest().getString("kind")) && "series".equalsIgnoreCase(event.getRequest().getString("type"))) {
			// GETS CALLED FOR SELECTED PLOTWISE VISUALIZATION
			// =============================SINGLE PLOT TIME SERIES==========================
			logger.info("RIKI: SERIES META");
			JSONObject response = new JSONObject();
			response.put("kind", "galileo#plot");
			response.put("type", "series");
			JSONArray result = new JSONArray();
			GeospatialFileSystem gfs = (GeospatialFileSystem)this.fsMap.get(event.getRequest().getString("filesystem"));
			
			List<Expression> expressions = new ArrayList<Expression>();
			expressions.add(new Expression(Operator.EQUAL, new Feature("plotID", event.getRequest().getInt("plotID"))));
			
			String features = event.getRequest().getString("features");
			
			Query q = new Query();
			for(String f : features.split(",")) {
				List<Expression> myExp = new ArrayList<Expression>(expressions);
				myExp.add(new Expression(Operator.EQUAL, new Feature("sensorType", f)));
				
				Operation op = new Operation(myExp);
				q.addOperation(op);
			}
			
			List<Path<Feature, String>> paths = gfs.query(q);
			
			HashMap<String, List<String>> dateToBlockMap = new HashMap<String, List<String>>();
			HashMap<String, String> dateToFeatureMap = new HashMap<String, String>();
			
			for (Path<Feature, String> path : paths) {
				//ArrayList<String> feats = new ArrayList<>();
				String pathStr = path.toString();
				
				// NODES IN A PATH SEPARATED BY ->
				String [] split = pathStr.split("->");
				
				int yr = 0, month = 0, day = 0; String sType = "";
				String blockPath = "";
				for (String s : split) {
					if (s.contains("=")) {
						String tokens[] = s.split("=");
						String label = tokens[0].trim();
						
						if(label.equals(GeospatialFileSystem.TEMPORAL_YEAR_FEATURE)) {
							yr = Integer.valueOf(tokens[1].trim());
						} else if(label.equals(GeospatialFileSystem.TEMPORAL_MONTH_FEATURE)) {
							month = Integer.valueOf(tokens[1].trim());
						} else if(label.equals(GeospatialFileSystem.TEMPORAL_DAY_FEATURE)) {
							day = Integer.valueOf(tokens[1].trim());
						} else if(label.equals("sensorType")) {
							sType = tokens[1].trim();
						} else if(label.equals("payload")) {
							String pl = tokens[1].trim();
							String t[] = pl.split("\\$\\$");
							blockPath = t[0];
							if(blockPath.startsWith("[")) {
								blockPath = blockPath.substring(1);
							}
							
						}
						
						/*
						if (features.contains(s.split("=")[0].trim()))
							feats.add(s.trim());
						else if (s.split("=")[0].trim().equals("date"))
							dateToFeatureMap.put(s.split("=")[1].trim(), feats);*/
					}
				}
				String key = yr+"-"+month+"-"+day+"-"+sType;
				if(dateToBlockMap.get(key) == null) {
					List<String> blocks = new ArrayList<String>();
					dateToBlockMap.put(key, blocks);
					blocks.add(blockPath);
				} else {
				
					dateToBlockMap.get(key).add(blockPath);
				}
			}
			
			for(String key : dateToBlockMap.keySet()) {
				
				List<String> bpaths = dateToBlockMap.get(key);
				
				SummaryStatistics plotSummary = null;
				for(String path : bpaths) {
				
					SummaryStatistics ss = gfs.getSummaryData(path);
					
					if(plotSummary == null)
						plotSummary = ss;
					else {
						plotSummary = SummaryStatistics.mergeSummary(plotSummary, ss);
					}
					
				}
				dateToFeatureMap.put(key, plotSummary.toString());
				
			}
			
			logger.info("RIKI: dateToFeatureMap:"+dateToFeatureMap);
			
			for (Map.Entry<String, String> entry : dateToFeatureMap.entrySet()) {
				String dateToFeat = entry.getKey() + "->"+entry.getValue();
				
				result.put(dateToFeat);//remove last comma
			}
			response.put("result", result);
			response.put("features", features);
			
			logger.info("RIKI: Paths Found: " + paths);
			logger.info("RIKI: Results Calculated: " + result);
			
			context.sendReply(new MetadataResponse(response));
		}
		else if ("galileo#filesystem".equalsIgnoreCase(event.getRequest().getString("kind"))) {
			
			logger.info("RIKI: TYPE3");
			JSONObject response = new JSONObject();
			response.put("kind", "galileo#filesystem");
			JSONArray result = new JSONArray();
			for (String fsName : fsMap.keySet()) {
				GeospatialFileSystem fs = (GeospatialFileSystem)fsMap.get(fsName);
//				TemporalFileSystem fs = (TemporalFileSystem)fsMap.get(fsName);
				result.put(fs.obtainState());
			}
			response.put("result", result);
			context.sendReply(new MetadataResponse(response));
		} else if ("galileo#overview".equalsIgnoreCase(event.getRequest().getString("kind"))) {
			
			logger.info("RIKI: TYPE4");
			JSONObject request = event.getRequest();
			JSONObject response = new JSONObject();
			response.put("kind", "galileo#overview");
			JSONArray result = new JSONArray();
			if (request.has("filesystem") && request.get("filesystem") instanceof JSONArray) {
				JSONArray fsNames = request.getJSONArray("filesystem");
				for (int i = 0; i < fsNames.length(); i++) {
					GeospatialFileSystem fs = (GeospatialFileSystem)fsMap.get(fsNames.getString(i));
//					TemporalFileSystem fs = (TemporalFileSystem)fsMap.get(fsNames.getString(i));
					if (fs != null) {
						JSONArray overview = fs.getOverview();
						JSONObject fsOverview = new JSONObject();
						fsOverview.put(fsNames.getString(i), overview);
						result.put(fsOverview);
					} else {
						JSONObject fsOverview = new JSONObject();
						fsOverview.put(fsNames.getString(i), new JSONArray());
						result.put(fsOverview);
					}
				}
			}
			response.put("result", result);
			logger.info(response.toString());
			context.sendReply(new MetadataResponse(response));
		} else if ("galileo#features".equalsIgnoreCase(event.getRequest().getString("kind"))) {
			
			// RIKI: THE FIRST REQUEST THAT GETS FIRED DURING LOADING OF THE FRONT-END
			// getFeatures() from the js
			logger.info("RIKI: TYPE5");
			JSONObject request = event.getRequest();
			JSONObject response = new JSONObject();
			response.put("kind", "galileo#features");
			JSONArray result = new JSONArray();
			
			if (request.has("filesystem") && request.get("filesystem") instanceof JSONArray) {
				JSONArray fsNames = request.getJSONArray("filesystem");
				for (int i = 0; i < fsNames.length(); i++) {
					GeospatialFileSystem fs = (GeospatialFileSystem)fsMap.get(fsNames.getString(i));
					if (fs != null) {
						JSONArray features = fs.getFeaturesJSON();
						
						logger.info("RIKI: FOUND FEATURES:"+features);
						JSONObject fsFeatures = new JSONObject();
						fsFeatures.put(fsNames.getString(i), features);
						
						JSONArray attrString = fs.getConfigs().getAllAttributesJson();
						JSONObject fsAttributes = new JSONObject();
						fsAttributes.put(fsNames.getString(i)+"_atr", attrString);
						
						result.put(fsFeatures);
						result.put(fsAttributes);
					} else {
						JSONObject fsFeatures = new JSONObject();
						fsFeatures.put(fsNames.getString(i), new JSONArray());
						result.put(fsFeatures);
					}
				}
			} else {
				for (String fsName : fsMap.keySet()) {
					GeospatialFileSystem fs = (GeospatialFileSystem)fsMap.get(fsName);
					if (fs != null) {
						JSONArray features = fs.getFeaturesJSON();
						JSONObject fsFeatures = new JSONObject();
						fsFeatures.put(fsName, features);
						result.put(fsFeatures);
					} else {
						JSONObject fsFeatures = new JSONObject();
						fsFeatures.put(fsName, new JSONArray());
						result.put(fsFeatures);
					}
				}
			}
			response.put("result", result);
			context.sendReply(new MetadataResponse(response));
		} else {
			JSONObject response = new JSONObject();
			response.put("kind", event.getRequest().getString("kind"));
			response.put("result", new JSONArray());
			context.sendReply(new MetadataResponse(response));
		}
	}
	
	
	// QUERYING THE RIG GRAPH
	/*@EventHandler
	public void XhandleBlockQueryRequest(BlockQueryRequest event, EventContext context) {
		
		long queryId = System.currentTimeMillis();
		
		logger.info("RIKI: RECEIVED A INTEGRITY BLOCK REQUEST "+event.getMetadataQueryString());
		
		String fsName = event.getFilesystemName();
		
		GeospatialFileSystem fs = (GeospatialFileSystem) fsMap.get(fsName);//always roots
		
		if (fs != null) {
			
			List<String> rig_paths = fs.listRIGPaths(event.getTime(), event.getPolygon(), event.getMetadataQuery());
				
			BlockQueryResponse response = new BlockQueryResponse(queryId, rig_paths);
			try {
				context.sendReply(response);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				logger.log(Level.SEVERE, "RIKI: FAILURE WHILE SENDING BACK RESPONSE", e);
			}
			return;
		
				
		
		}
	}*/
	
	
	
	
	

	/**
	 * Handles a query request from a client. Query requests result in a number
	 * of subqueries being performed across the Galileo network.
	 * 
	 * @throws PartitionException
	 * @throws HashException
	 */
	@EventHandler
	public void handleQueryRequest(QueryRequest request, EventContext context) {
		
		String queryId = String.valueOf(System.currentTimeMillis());
		GeospatialFileSystem gfs = (GeospatialFileSystem) this.fsMap.get(request.getFilesystemName());
		
		logger.info("RIKI: RECEIVED A QUERY REQUEST "+gfs);
		if (gfs != null) {
			QueryResponse response = new QueryResponse(queryId, gfs.getFeaturesRepresentation(), new JSONObject());
			Metadata data = new Metadata();
			
			/*if (request.isTemporal()) {
				String[] timeSplit = request.getTime().split("-");
				int timeIndex = Arrays.asList(TemporalType.values()).indexOf(gfs.getTemporalType());
				if (!timeSplit[timeIndex].contains("x")) {
					logger.log(Level.INFO, "Temporal query: {0}", request.getTime());
					Calendar c = Calendar.getInstance();
					c.setTimeZone(TemporalHash.TIMEZONE);
					int year = timeSplit[0].charAt(0) == 'x' ? c.get(Calendar.YEAR) : Integer.parseInt(timeSplit[0]);
					int month = timeSplit[1].charAt(0) == 'x' ? c.get(Calendar.MONTH)
							: Integer.parseInt(timeSplit[1]) - 1;
					int day = timeSplit[2].charAt(0) == 'x' ? c.get(Calendar.DAY_OF_MONTH)
							: Integer.parseInt(timeSplit[2]);
					int hour = timeSplit[3].charAt(0) == 'x' ? c.get(Calendar.HOUR_OF_DAY)
							: Integer.parseInt(timeSplit[3]);
					c.set(year, month, day, hour, 0);
					data.setTemporalProperties(new TemporalProperties(c.getTimeInMillis()));
				}
			}*/
			
			// THE TEMPORAL PROPERTY IS NOT EMBEDDED IN METADATA BECAUSE THE NODES ARE PARTITIONED SPATIALLY
			if (request.isSpatial()) {
				//logger.log(Level.INFO, "Spatial query: {0}", request.getPolygon());
				data.setSpatialProperties(new SpatialProperties(new SpatialRange(request.getPolygon())));
			}

			Partitioner<Metadata> partitioner = gfs.getPartitioner();
			List<NodeInfo> nodes;
			try {
				nodes = partitioner.findDestinations(data);
				logger.info("destinations: " + nodes);
				
				/*QueryEvent qEvent = (request.hasFeatureQuery() || request.hasMetadataQuery())
						? new QueryEvent(queryId, request.getFilesystemName(), request.getFeatureQuery(),
								request.getMetadataQuery())
						: (request.isSpatial())
								? new QueryEvent(queryId, request.getFilesystemName(), request.getPolygon())
								: new QueryEvent(queryId, request.getFilesystemName(), request.getTime());*/
								
				QueryEvent qEvent = new QueryEvent(queryId, request.getFilesystemName(), request.getPolygon());
							
				qEvent.setSensorName(request.getSensorName());
				
				/*if (request.isDryRun()) {
					qEvent.enableDryRun();
					response.setDryRun(true);
				}
				if (request.isSpatial()) {
					qEvent.setPolygon(request.getPolygon());
				}*/
				if (request.isTemporal())
					qEvent.setTime(request.getTime());

				try {
					ClientRequestHandler reqHandler = new ClientRequestHandler(new ArrayList<NetworkDestination>(nodes),
							context, this);
					reqHandler.handleRequest(qEvent, response);
					this.requestHandlers.add(reqHandler);
				} catch (IOException ioe) {
					logger.log(Level.SEVERE,
							"Failed to initialize a ClientRequestHandler. Sending unfinished response back to client",
							ioe);
					try {
						context.sendReply(response);
					} catch (IOException e) {
						logger.log(Level.SEVERE, "Failed to send response back to original client", e);
					}
				}
			} catch (HashException | PartitionException hepe) {
				logger.log(Level.SEVERE,
						"Failed to identify the destination nodes. Sending unfinished response back to client", hepe);
				try {
					context.sendReply(response);
				} catch (IOException e) {
					logger.log(Level.SEVERE, "Failed to send response back to original client", e);
				}
			}
		} else {
			try {
				QueryResponse response = new QueryResponse(queryId, new JSONArray(), new JSONObject());
				context.sendReply(response);
			} catch (IOException ioe) {
				logger.log(Level.SEVERE, "Failed to send response back to original client", ioe);
			}
		}
	}
	
	private String getResultFilePrefix(String queryId, String fsName, String blockIdentifier) {
		return this.resultsDir + "/" + String.format("%s-%s-%s", fsName, queryId, blockIdentifier);
	}

	private class QueryProcessor implements Runnable {
		private String blockPath;
		private String pathPrefix;
		private GeoavailabilityQuery geoQuery;
		private GeoavailabilityGrid grid;
		private GeospatialFileSystem gfs;
		private Bitmap queryBitmap;
		private List<String> resultPaths;
		private long fileSize;

		public QueryProcessor(GeospatialFileSystem gfs, String blockPath, GeoavailabilityQuery gQuery,
				GeoavailabilityGrid grid, Bitmap queryBitmap, String pathPrefix) {
			this.gfs = gfs;
			this.blockPath = blockPath;
			this.geoQuery = gQuery;
			this.grid = grid;
			this.queryBitmap = queryBitmap;
			this.pathPrefix = pathPrefix;
		}

		@Override
		public void run() {
			try {
				this.resultPaths = this.gfs.query(this.blockPath, this.geoQuery, this.grid, this.queryBitmap,
						this.pathPrefix);
				for (String resultPath : this.resultPaths)
					this.fileSize += new File(resultPath).length();
			} catch (IOException | InterruptedException e) {
				logger.log(Level.SEVERE, "Something went wrong while querying the filesystem. No results obtained.");
			}
		}

		public long getFileSize() {
			return this.fileSize;
		}

		public List<String> getResultPaths() {
			return this.resultPaths;
		}
	}

	// .query(
	/**
	 * Handles an internal Query request (from another StorageNode)
	 * Actual query evaluation happens here
	 */
	@EventHandler
	public void handleQuery(QueryEvent event, EventContext context) {
		
		long hostFileSize = 0;
		long totalProcessingTime = 0;
		long blocksProcessed = 0;
		int totalNumPaths = 0;
		JSONArray header = new JSONArray();
		JSONObject blocksJSON = new JSONObject();
		JSONObject summaryResultsJSON = new JSONObject();
		
		JSONArray resultsJSON = new JSONArray();
		long processingTime = System.currentTimeMillis();
		try {
			//logger.info(event.getFeatureQueryString());
			//logger.info(event.getMetadataQueryString());
			logger.info("RIKI: QUERY SENSOR:"+event.getSensorName());
			
			String fsName = event.getFilesystemName();
			GeospatialFileSystem fs = (GeospatialFileSystem) fsMap.get(fsName);//always roots
			if (fs != null) {
				header = fs.getFeaturesRepresentation();
				Map<String, List<String>> blockMap = fs.listBlocks(event.getTime(), event.getPolygon(),
						event.getMetadataQuery(), event.isDryRun(), event.getSensorName());
				
				// THE FRONT-END IS NOT REQUESTING A DRY RUN
				
				// THIS IS THE CASE OF ONLY METADATA AND SUMMARY BEING REQUESTED AND NOT ACTUAL DATA
				JSONArray plotSummaries = new JSONArray();
				
				// POLGON FRONT-END QUERY IS NOT DRY RUN
				// ==========================DRY START====================================
				if (event.isDryRun()) {
					
					JSONObject responseJSON = new JSONObject();
					responseJSON.put("filesystem", event.getFilesystemName());
					responseJSON.put("queryId", event.getQueryId());
					for (String blockKey : blockMap.keySet()) {
						
						JSONArray filePaths = new JSONArray();
						
						SummaryStatistics plotSummary = null;
						
						for(String block : blockMap.get(blockKey)) {
							String irodsBlockPath = fs.getIrodsPath(block);
							
							SummaryStatistics ss = fs.getSummaryData(block);
							
							if(plotSummary == null)
								plotSummary = ss;
							else {
								plotSummary = SummaryStatistics.mergeSummary(plotSummary, ss);
							}
							
							filePaths.put(block+"$$"+irodsBlockPath);
							
						}
						
						// blockKey is just plotID in our case
						blocksJSON.put(blockKey, filePaths);
						summaryResultsJSON.put(blockKey, plotSummary.toJson());
					}
					responseJSON.put("result", blocksJSON);
					responseJSON.put("summaries", summaryResultsJSON);
					QueryResponse response = new QueryResponse(event.getQueryId(), header, responseJSON);
					response.setDryRun(true);
					context.sendReply(response);
					return;
				}
				// ==========================DRY END====================================
				
				
				JSONArray filePaths = new JSONArray();
				
				int totalBlocks = 0;
				
				logger.info("RIKI: BLOCKPATHS: "+ blockMap.size());
				
				for (String blockKey : blockMap.keySet()) {
					List<String> blocks = blockMap.get(blockKey);
					totalBlocks += blocks.size();
					for(String block : blocks){
						
						if(fsName!= null && fsName.startsWith("roots-arizona")) {
							
							//logger.info("RIKI: DEALING WITH "+block);
							// ARIZONA PLOT DATA IS ACTUALLY STORED IN THE BLOCKS
							// WE JUST RETURN THE BLOCK PATH ALONG WITH THE METADATA ASSOSSIATED WITH IT
							// SPLIT BECAUSE IRODS PATH IS ALSO STORED SEPARATED BY $$
							String tokens[] = block.split("\\$\\$");
							String summaryString = fs.getSummaryDataString(tokens[0]);
							
							//logger.info("RIKI: SUMMARY STRING: "+ summaryString);
							
							// RETURNING BOTH THE GALILEO AND IRODS PATH
							//filePaths.put(block+"$$"+irodsBlockPath);
							filePaths.put(block);
							plotSummaries.put(tokens[0]+"\n"+summaryString);
							
						} else {
							// IN INITIAL ROOTS CODE, THE IRODS BLOCKPATH WAS IN THE BLOCK'S CONTENTS, WHICH IS
							// WHY THE BLOCK IS BEING READ HERE
							filePaths.put(new String(Files.readAllBytes(Paths.get(block))));
						}
						
						if(fsName!= null && fsName.startsWith("roots-arizona")) {
							String tokens[] = block.split("\\$\\$");
							hostFileSize += new File(tokens[0]).length();
							
							//logger.info("RIKI: FILESIZE: "+tokens[0]+" "+hostFileSize);
						} else {
							hostFileSize += new File(block).length();
						}
					}
				}

				
				totalProcessingTime = System.currentTimeMillis() - processingTime;
				totalNumPaths = filePaths.length();
				JSONObject resultJSON = new JSONObject();
				resultJSON.put("filePath", filePaths);
				resultJSON.put("numPaths", totalNumPaths);
				resultJSON.put("fileSize", hostFileSize);
				resultJSON.put("hostName", this.canonicalHostname);
				resultJSON.put("hostPort", this.port);
				resultJSON.put("processingTime", totalProcessingTime);
				
				if(fsName!= null && fsName.startsWith("roots-arizona")) {
					resultJSON.put("summaries", plotSummaries);
				}
				
				
				resultsJSON.put(resultJSON);
			} else {
				logger.log(Level.SEVERE, "Requested file system(" + fsName
						+ ") not found. Ignoring the query and returning empty results.");
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE,
					"Something went wrong while querying the filesystem. No results obtained. Sending blank list to the client. Issue details follow:",
					e);
		}

		JSONObject responseJSON = new JSONObject();
		responseJSON.put("filesystem", event.getFilesystemName());
		responseJSON.put("queryId", event.getQueryId());
		if (hostFileSize == 0) {
			responseJSON.put("result", new JSONArray());
			responseJSON.put("hostFileSize", new JSONObject());
			responseJSON.put("totalFileSize", 0);
			responseJSON.put("totalNumPaths", 0);
			responseJSON.put("hostProcessingTime", new JSONObject());
		} else {
			responseJSON.put("result", resultsJSON);
			responseJSON.put("hostFileSize", new JSONObject().put(this.canonicalHostname, hostFileSize));
			responseJSON.put("totalFileSize", hostFileSize);
			responseJSON.put("totalNumPaths", totalNumPaths);
			responseJSON.put("hostProcessingTime", new JSONObject().put(this.canonicalHostname, totalProcessingTime));
		}
		responseJSON.put("totalProcessingTime", totalProcessingTime);
		responseJSON.put("totalBlocksProcessed", blocksProcessed);
		QueryResponse response = new QueryResponse(event.getQueryId(), header, responseJSON);
		try {
			context.sendReply(response);
		} catch (IOException ioe) {
			logger.log(Level.SEVERE, "Failed to send response back to ClientRequestHandler", ioe);
		}
	}

	@EventHandler
	public void handleQueryResponse(QueryResponse response, EventContext context) throws IOException {
		QueryTracker tracker = queryTrackers.get(response.getId());
		if (tracker == null) {
			logger.log(Level.WARNING, "Unknown query response received: {0}", response.getId());
			return;
		}
	}

	/**
	 * Triggered when the request is completed by the
	 * {@link ClientRequestHandler}
	 */
	@Override
	public void onRequestCompleted(Event response, EventContext context, MessageListener requestHandler) {
		try {
			logger.info("Sending collective response to the client");
			this.requestHandlers.remove(requestHandler);
			context.sendReply(response);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Failed to send response to the client.", e);
		} finally {
			System.gc();
		}
	}

	public void persistFilesystems() {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(fsFile))) {
			JSONObject fsJSON = new JSONObject();
			for (String fsName : fsMap.keySet()) {
				FileSystem fs = fsMap.get(fsName);
				switch (fs.getType()) {
				case "geospatial":
					fsJSON.put(fsName, ((GeospatialFileSystem)fs).obtainState());
					break;
//				case "temporal":
//					fsJSON.put(fsName, ((TemporalFileSystem)fs).obtainState());
//					break;
				}
				
			}
			bw.write(fsJSON.toString());
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	/**
	 * Handles cleaning up the system for a graceful shutdown.
	 */
	private class ShutdownHandler extends Thread {
		@Override
		public void run() {
			/*
			 * The logging subsystem may have already shut down, so we revert to
			 * stdout for our final messages
			 */
			System.out.println("Initiated shutdown.");

			try {
				connectionPool.forceShutdown();
				messageRouter.shutdown();
				ingestor.shutdown();
			} catch (Exception e) {
				e.printStackTrace();
			}

			nodeStatus.close();

			if (pidFile != null && pidFile.exists()) {
				pidFile.delete();
			}

			persistFilesystems();
			dataStoreHandler.killThreads();
			for (FileSystem fs : fsMap.values())
				fs.shutdown();
			listener.kill(); //hopefully kill the thread correctly...
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			ProcessBuilder pb = new ProcessBuilder().command("killall", "-KILL", "rmiregistry");
			Process p;
			try {
				p = pb.start();
				p.waitFor();
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			} 
			
			if (listener.isAlive())
				System.out.println("Failed to kill listener");
			else 
				System.out.println("Successfully killed listener");
			System.out.println("Goodbye!");
		}
	}

	
	public SpatialHierarchyPartitioner getPartitioner(String fs) {
		GeospatialFileSystem gfs = (GeospatialFileSystem) this.fsMap.get(fs);
		return (SpatialHierarchyPartitioner)gfs.getPartitioner();
	}
	/**
	 * Executable entrypoint for a Galileo DHT Storage Node
	 */
	public static void main(String[] args) {
		try {
			StorageNode node = new StorageNode();
			
			node.listener = new Listener(node);
			node.listener.start();
//			node.throughputTimer.scheduleAtFixedRate(new TimerTask() {
//				  @Override
//				  public void run() {
//							logger.info("Last message processed at time: " + new Date(node.dataStoreHandler.getLastProcessedTime()));
//							long sum = 0;
//							synchronized(node.dataStoreHandler.metadataTimes) {
//								for (Long l : node.dataStoreHandler.metadataTimes)
//									sum += l;
//							}
//							if (!node.dataStoreHandler.metadataTimes.isEmpty())
//								logger.info("Average metadata extraction time: " + sum/node.dataStoreHandler.metadataTimes.size());
//							sum = 0;
//							synchronized(node.dataStoreHandler.irodsTimes) {
//								for (Long l : node.dataStoreHandler.irodsTimes)
//									sum += l;
//							}
//							if (!node.dataStoreHandler.irodsTimes.isEmpty())
//								logger.info("Average IRODS message processing time: " + sum/node.dataStoreHandler.irodsTimes.size());
//							sum = 0;
//							synchronized(node.dataStoreHandler.unprocessedTimes) {
//								for (Long l : node.dataStoreHandler.unprocessedTimes)
//									sum += l;
//							}
//							if (!node.dataStoreHandler.unprocessedTimes.isEmpty())
//								logger.info("Average unprocessed message type processing time: " + sum/node.dataStoreHandler.unprocessedTimes.size());
//						
//					  
//				  }
//				}, 60*1000, 60*1000);
			DataIngestor ingestor = new DataIngestor(node);
			node.ingestor = ingestor;
			ingestor.start();
			node.start();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Could not start StorageNode.", e);
		}
	}

	public HashGrid getGlobalGrid(String fsName) {
		GeospatialFileSystem fileSystem = (GeospatialFileSystem)fsMap.get(fsName);
		return fileSystem.getGlobalGrid();
	}
}
