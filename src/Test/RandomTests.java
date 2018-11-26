package Test;

import galileo.bmp.HashGrid;
import galileo.bmp.HashGridException;
import galileo.client.EventPublisher;

import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;

import galileo.config.SystemConfig;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.awt.geom.Path2D;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.ArrayList;
import org.apache.commons.lang3.ArrayUtils;
import org.irods.jargon.core.connection.AuthScheme;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.connection.IRODSProtocolManager;
import org.irods.jargon.core.connection.IRODSSession;
import org.irods.jargon.core.connection.IRODSSimpleProtocolManager;
import org.irods.jargon.core.connection.PipelineConfiguration;
import org.irods.jargon.core.connection.SettableJargonProperties;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.packinstr.TransferOptions;
import org.irods.jargon.core.packinstr.DataObjInp.OpenFlags;
import org.irods.jargon.core.pub.DataTransferOperations;
import org.irods.jargon.core.pub.DataTransferOperationsImpl;
import org.irods.jargon.core.pub.IRODSAccessObjectFactory;
import org.irods.jargon.core.pub.IRODSAccessObjectFactoryImpl;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.core.pub.IRODSFileSystemAO;
import org.irods.jargon.core.pub.IRODSGenericAO;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.pub.io.IRODSFileFactory;
import org.irods.jargon.core.transfer.DefaultTransferControlBlock;
import org.irods.jargon.core.transfer.TransferControlBlock;
import org.irods.jargon.core.transfer.TransferStatus;
import org.irods.jargon.core.transfer.TransferStatusCallbackListener;
import org.irods.jargon.core.transfer.TransferStatusCallbackListener.CallbackResponse;
import org.irods.jargon.core.transfer.TransferStatusCallbackListener.FileStatusCallbackResponse;
import org.irods.jargon.core.utils.LocalFileUtils;
import org.irods.jargon.testutils.TestConfigurationException;
import org.irods.jargon.testutils.TestingPropertiesHelper;
import org.irods.jargon.testutils.filemanip.FileGenerator;
import org.irods.jargon.testutils.filemanip.ScratchFileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.spatial4j.context.jts.JtsSpatialContext;
import org.locationtech.spatial4j.context.jts.JtsSpatialContextFactory;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.ShapeFactory;
import org.locationtech.spatial4j.shape.impl.RectangleImpl;
import org.locationtech.spatial4j.shape.impl.ShapeFactoryImpl;
import org.xerial.snappy.Snappy;

import galileo.comm.NonBlockStorageRequest;
import galileo.comm.TemporalType;
import galileo.dataset.Coordinates;
import galileo.dataset.DataIngestor;
import galileo.dataset.InvalidFieldException;
import galileo.dataset.Metadata;
import galileo.dataset.feature.Feature;
import galileo.dataset.feature.FeatureType;
import galileo.dataset.mimicII.Bin;
import galileo.dataset.mimicII.BinaryFileCombiner;
import galileo.dataset.mimicII.ByteFormat;
import galileo.dataset.mimicII.Interval;
import galileo.dataset.mimicII.Signal;
import galileo.dataset.mimicII.TimeBinCombiner;
import galileo.dataset.mimicII.Waveform;
import galileo.dht.NodeInfo;
import galileo.dht.SpatialHierarchyPartitioner;
import galileo.dht.StorageNode;
import galileo.dht.hash.HashException;
import galileo.dht.hash.TemporalHash;
import galileo.dht.hash.TimeBinGroupHash;
import galileo.fs.GeospatialFileSystem;
import galileo.net.ClientMessageRouter;
import galileo.query.Expression;
import galileo.query.Operation;
import galileo.query.Operator;
import galileo.query.Query;
import galileo.serialization.Serializer;
import galileo.util.CustomBufferedReader;
import galileo.util.GeoHash;
import galileo.util.Pair;
import geo.main.java.com.github.davidmoten.geo.GeoHashUtils;
import web.Sampler;
public class RandomTests {
	public int x;
	static String chunk = "";
	static int count = 0;
	static public String hash(byte[] data) throws NoSuchAlgorithmException{
		MessageDigest digest = MessageDigest.getInstance("MD5");
		byte [] hash = digest.digest(data);
		BigInteger hashInt = new BigInteger(1, hash);
		String hex = hashInt.toString(16);
		while (hex.length() < 40)
			hex = "0"+hex;		
		return hex;
	}
	static public long hash(long pid, long val) {
		return ((Integer.parseInt(pid+""+val)))%4;
	}

	static void change(Metadata d) {
		d.setName("test");
		
	}
	static class ChunkProcessor extends Thread {
		private BlockingQueue<String> queue;
		private volatile boolean alive;
        public ChunkProcessor(BlockingQueue<String> queue) throws IOException {
            this.queue = queue;
            alive = true;
        }

        public void run() {
        	while(alive) {
				try {
					String data = queue.take();
					if (data.split("\n")[0].split(",").length != 15)
						System.out.println(data.split("\n")[0]);
				}catch (Exception e) {
					e.printStackTrace();
				}
	        }
        }
        
        public void kill() {
        	this.alive = false;
        }
    }
	
	public static int solution(int[] A) {
	        // write your code in Java SE 8
	        //Worst case: will need to examine every element in A (still only O(n), not too awful)
	        int max = 2, currStretch=2;
	        int first = A[0], second = A[1];
	        for (int i = 2; i < A.length; i++){
	            if (A[i] != first && A[i] != second){
	                first = A[i];
	                if (i == A.length-1)
	                    break;
	                second = A[i+1];
	                currStretch = 2;
	                i++;
	            }
	            else
	                currStretch++;
	            max = Math.max(max,  currStretch);
	            
	        }
	        return max;
	    
    }
	
	
	public static void main(String [] args) throws ParseException, IOException, NoSuchAlgorithmException, HashException, IllegalArgumentException, InvalidFieldException, InterruptedException, JargonException, TestConfigurationException{		
		int [] t1 = {1,2,1,3,4,3,4,3,4,5,8,2,4};
		int [] t2 = {1,2,1,3,4,3,5,1,2};
		int [] t3 = {1,2,1,2,1,2,5};
		System.out.println(solution(t1));
		System.out.println(solution(t2));
		System.out.println(solution(t3));
		System.exit(0);
		
		
		
//		int [] test = {0,1,2,4,5,6,8,9,11,12,15};
//		int from = test[0], to = test[0];
//		Query query = new Query();
//		for (int i = 1; i <test.length; i++) {
//			if (test[i] == to + 1)
//				to = test[i];
//			else {
//				Operation op = new Operation();
//				op.addExpressions(new Expression(Operator.GREATEREQUAL, new Feature("plotID", from)), new Expression(Operator.LESSEQUAL, new Feature("plotID", to)));
//				query.addOperation(op);
//				from = test[i];
//				to = test[i];
//			}	
//		}
//		System.out.println(query);
//		System.exit(0);
		//		IRODSSession sess = new IRODSSession();
//////		(final String host, final int port, final String userName, final String password,
//////				final String homeDirectory, final String zone, final String defaultStorageResource)
////											  //host                         port    user         pass            homeDirectory        zone      storageResource
//		IRODSAccount acct = new IRODSAccount("data.iplantcollaborative.org", 1247, "radix_subterra", "roots&radix2018", "/iplant/home/radix_subterra", "iplant", "iplant");
//		sess.setIrodsProtocolManager(new IRODSSimpleProtocolManager());
		
//		PipelineConfiguration pipeConfig = sess.buildPipelineConfigurationBasedOnJargonProperties();
//		TransferControlBlock tcb = sess.buildDefaultTransferControlBlockBasedOnJargonProperties();
//		IRODSProtocolManager connMan = sess.getIrodsConnectionManager();
//		IRODSAccessObjectFactory accessObjectFactory = IRODSAccessObjectFactoryImpl.instance(sess);
//		IRODSFileFactory fileFactory = accessObjectFactory.getIRODSFileFactory(acct);
//		IRODSFile testFile = fileFactory.instanceIRODSFile("/iplant/home/maxr1876/test/plot.txt");
//		IRODSFileSystem fs = new IRODSFileSystem(connMan);
//		DataTransferOperations transferOps = accessObjectFactory.getDataTransferOperations(acct);
//		transferOps.putOperation("/s/bach/j/under/mroseliu/errorStrings/plot", "iplant/home/maxr1876/test/plot.txt", "", null, tcb);
		long start = System.currentTimeMillis();
//		File localFile = new File("/s/bach/j/under/mroseliu/plots");

////		localFile.delete();
///////////////////////////////////////////////////////////////////////////////////
////		// now put the file
////		TestingPropertiesHelper testingPropertiesHelper = new TestingPropertiesHelper();
		IRODSAccount irodsAccount = new IRODSAccount("data.iplantcollaborative.org", 1247, "radix_subterra", "roots&radix2018", "/iplant/home/radix_subterra", "iplant", "");
		IRODSFileSystem irodsFileSystem = IRODSFileSystem.instance();
//		SettableJargonProperties settableJargonProperties = new SettableJargonProperties(
//				irodsFileSystem.getJargonProperties());
//		settableJargonProperties.setInternalCacheBufferSize(-1);
//		settableJargonProperties.setInternalOutputStreamBufferSize(65535);
//		irodsFileSystem.getIrodsSession().setJargonProperties(settableJargonProperties);
		IRODSFileFactory irodsFileFactory = irodsFileSystem.getIRODSFileFactory(irodsAccount);
//		IRODSFile destFile = irodsFileFactory.instanceIRODSFile("/test/plots/9/2018/6/26");
//		TransferControlBlock tcb = irodsFileSystem.getIrodsSession().buildDefaultTransferControlBlockBasedOnJargonProperties();
		TransferControlBlock tcb = DefaultTransferControlBlock.instance();
		TransferOptions opts = new TransferOptions();
		opts.setComputeAndVerifyChecksumAfterTransfer(true);
		opts.setIntraFileStatusCallbacks(true);
		tcb.setTransferOptions(opts);
		tcb.setMaximumErrorsBeforeCanceling(10);
//		tcb.setTotalBytesToTransfer(localFile.length());
////		irodsFileSystem.getIrodsSession().buildTransferOptionsBasedOnJargonProperties();
		String targetIrodsFile = "plots/9/2018/6/26/9-2018-6-26.gblock";
		IRODSFile remoteFile = irodsFileFactory.instanceIRODSFile(targetIrodsFile);
		new Thread() {
			@Override
			public void run() {
				try {
					IRODSFileSystem ifs = IRODSFileSystem.instance();
					IRODSAccount iacct = new IRODSAccount("data.iplantcollaborative.org", 1247, "radix_subterra", "roots&radix2018", "/iplant/home/radix_subterra", "iplant", "");
					IRODSFileFactory iff = ifs.getIRODSFileFactory(iacct);
					IRODSFile file = iff.instanceIRODSFile(targetIrodsFile);
					Thread.sleep(1500);
					System.out.println(file);
				} catch (JargonException | InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}.start();
		Thread.sleep(5000);
		System.exit(0);
//		if (!remoteFile.exists())
//			remoteFile.mkdirs();
////		System.out.println("Remote file exists? :" + remoteFile.exists());
////		System.out.println("Can read remote file? " + remoteFile.canRead());
////		if (!destFile.exists())
////			destFile.mkdirs();
		File localDir = new File("/tmp");
		DataTransferOperations dataTransferOperationsAO = irodsFileSystem.getIRODSAccessObjectFactory()
				.getDataTransferOperations(irodsAccount);
//		dataTransferOperationsAO.putOperation(localFile, remoteFile, new TransferStatusCallbackListener()
//		{
//			@Override
//			public FileStatusCallbackResponse statusCallback(TransferStatus transferStatus) { return FileStatusCallbackResponse.CONTINUE; }
//			@Override
//			public void overallStatusCallback(TransferStatus transferStatus) {}
//			@Override
//			public CallbackResponse transferAsksWhetherToForceOperation(String irodsAbsolutePath, boolean isCollection) { return CallbackResponse.NO_FOR_ALL; }
//		}, tcb);
//		System.out.println(System.currentTimeMillis() - start + " ms to put " + localFile.length() + " bytes");
//		localFile.delete();
		dataTransferOperationsAO.getOperation(remoteFile, localDir, new TransferStatusCallbackListener()
				{
				@Override
				public FileStatusCallbackResponse statusCallback(TransferStatus transferStatus) { return FileStatusCallbackResponse.CONTINUE; }
				@Override
				public void overallStatusCallback(TransferStatus transferStatus) {}
				@Override
				public CallbackResponse transferAsksWhetherToForceOperation(String irodsAbsolutePath, boolean isCollection) { return CallbackResponse.YES_FOR_ALL; }
			}, tcb);
//	    System.out.println(remoteFile.getFileDescriptor());
//	    System.out.println(remoteFile.toURI());
//	    System.out.println(remoteFile.toFileBasedURL());
		
		
///////////////////////////////////////////////////////////////////////////		
		
		
		
//		byte [] testBytes = Files.readAllBytes(Paths.get("/tmp/test.csv"));
//		System.out.println("Compressed size: " + Snappy.compress(testBytes).length);
//		System.out.println("Uncompressed size: " + testBytes.length);
//		Snappy snappy = new Snappy();
//		ArrayList<Double> compressTimes = new ArrayList<>();
//		ArrayList<Double> uncompressTimes = new ArrayList<>();
//		for (int i = 0; i < 100; i++) {
//			long start = System.nanoTime();
//			byte [] compressed = snappy.compress(testBytes);
//			long end = (System.nanoTime() - start)/1000000;
//			compressTimes.add((double)end);
//			
//			start = System.nanoTime();
//			byte [] uncompressed = snappy.uncompress(compressed);
//			end = (System.nanoTime() - start)/1000000;
//			uncompressTimes.add((double)end);
//		}
//		System.out.println("Avg compress time: " + galileo.util.Math.computeAvg(compressTimes));
//		System.out.println("Avg uncompress time: " + galileo.util.Math.computeAvg(uncompressTimes));
//		System.out.println("Compressed " + testBytes.length + " bytes to " + compressed.length + " bytes in " + end + " ms");
		
		
		
//		System.out.println("Unompressed " + compressed.length + " bytes to " + uncompressed.length + " bytes in " + end + " ms");
		//		String geohash = "wdw0x988vg8";
//		String last5 = "88vg8";
//		int mask = 31;
//		int ha)shVal = (int)GeoHa)sh.hashToLong(last5);
//		long startTime = System.nanoTime();
//		String geo = "";
//		for (int i=0; i < 5; i++) {
//			geo = GeoHash.charMap[mask & hashVal] + geo;
//			hashVal >>= 5;
//		}
//		System.out.println("Time: " + (System.nanoTime() - startTime));
//		System.out.println("Logical & geohash: " + geo);
//		
//		hashVal = (int)GeoHash.hashToLong(last5);
//		startTime = System.nanoTime();
//		String binString = Integer.toBinaryString(hashVal);
//		
//		String leadingZeros = "";
//		if (binString.length() < 32) {
//			for (int i = 0; i < 32-binString.length(); i++)
//				leadingZeros += "0";
//		}
//		binString = leadingZeros+binString;
//		System.out.println("HashGrid.toIndex: " + hashVal);
//		int start = 7;
//		String temp = "", rebuilt = "";
//		for (; start<32; start+=5) {
//			int val = Integer.valueOf(binString.substring(start, start+5), 2);
//			rebuilt+= GeoHash.charMap[val];
//		}
//		System.out.println("Time: " + (System.nanoTime() - startTime));
//		System.out.println("Rebuilt: " + rebuilt);
//		TimeBinCombiner combiner = new TimeBinCombiner();
//		File [] dirs = {new File("/tmp/mroseliu-galileo/mimic2/76/F/s00020/ABP"), new File("/tmp/mroseliu-galileo/mimic2/76/F/s00020/AVF"), new File("/tmp/mroseliu-galileo/mimic2/76/F/s00020/II"), new File("/tmp/mroseliu-galileo/mimic2/76/F/s00020/PAP")};
//		ArrayList<File> allFiles = new ArrayList<>();
//		for (File f : dirs){
//			File [] files = f.listFiles();
//			for (File f2 : files){
//				String [] split = f2.getAbsolutePath().split(File.separator);
//				String [] newPath = new String [split.length + 1];
//				split[split.length-1] = split[split.length-1].replaceAll(".gblock", "");
//				for (int i = 0; i < split.length-1;i++){
//					newPath[i] = split[i+1];
//				}
//				newPath
//			}
//		}
		
	
//		Calendar c = Calendar.getInstance();
//		DateFormat format = new SimpleDateFormat("HH:mm:ss.SSS");
//		Date d1 = format.parse("17:48:34.810");
//		c.setTime(d1);
//		c.setTimeInMillis(d1.getTime() + 56785416);
//		System.out.println(c.getTime());
//		System.out.println(c.get(c.HOUR));
//		for (int i = 0; i < 1000000; i++){
//			if (Math.sqrt(i+15) + Math.sqrt(i) == 15)
//				System.out.println(i);
//		}
//		NavigableMap <Integer, Integer> lo = new TreeMap<Integer, Integer>();
//		lo.put(0, 0);
//		lo.put(720, 1);
//		lo.put(900, 2);
//		System.out.println((double)70/60);
//		System.out.println(lo.get(lo.floorKey(719)));
//		String pID, ByteFormat byteFormat, Signal type, long start, long end, double sampPerSec, int binDuration,
//		Calendar startTime, Calendar endTime;
//		TimeBinHash tbh = new TimeBinHash(3, TemporalType.HOUR);
//		DateFormat format = new SimpleDateFormat("HH:mm:ss.SSS");
//		Date d1 = format.parse("00:00:00.000");
//		Date d2 = format.parse("00:01:00.000");
//		byte [] r1 = new byte [125*60];
//		byte [] r2 = new byte [125*60];
//		new Random().nextBytes(r1);
//		new Random().nextBytes(r2);
//		Calendar a1 = Calendar.getInstance();
//		a1.setTime(d1);
//		Calendar a2 = Calendar.getInstance();
//		a2.setTime(d2);
//		Bin b1 = new Bin("s00020", ByteFormat.TYPE80, Signal.ABP, 1620, 1621, 125, 60, a1, a2);
//		b1.setData(r1);
//		d1.setTime(d1.getTime() + 60000);
//		d2.setTime(d2.getTime() + 60000);
//		a1.setTime(d1);
//		a2.setTime(d2);
//		Bin b2 = new Bin("s00020", ByteFormat.TYPE80, Signal.ABP, 1, 2, 125, 60, a1, a2);
//		b2.setData(r2);
//		System.out.println(tbh.hash(b1));
//		b1.combine(b2);
//		System.out.println(b1);
//		int numRings = 10;
//		int len = (int)Math.sqrt(numRings);
//		HashMap<Integer, Long> locs = new HashMap<>();
//		int [][] grid;// = new int [(int)Math.sqrt(numRings)+1][(int)Math.sqrt(numRings)+1];
//		if (len*len == numRings)
//			grid = new int[(int)Math.sqrt(numRings)][(int)Math.sqrt(numRings)];
//		else
//			grid = new int [(int)Math.sqrt(numRings)+1][(int)Math.sqrt(numRings)+1];
//		int row, col;
//		for (int i = 0; i < 11000; i++){
//			System.out.println("For pid = 1, start="+i+", end="+(i+1)+" hashed to row "+hash(1, i) +", column "+hash(1,i+1));
//		}
//			System.out.println(Arrays.asList(data).subList(i, i+60).toArray());
//		bin.setData(data);
//		try {
//		      ObjectOutputStream objectOut = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("test.ser")));
//		      objectOut.writeObject(bin);
//		      objectOut.close();
//		    } catch (IOException e) {
//		      e.printStackTrace(System.err);
//		    }
//		    try {
//		      ObjectInputStream objectIn = new ObjectInputStream(new BufferedInputStream(
//		          new FileInputStream("test.ser")));
//		      Bin theLine = (Bin) objectIn.readObject();
//		      System.out.println(theLine);
//		      objectIn.close();
//		    } catch (Exception e) {
//		      e.printStackTrace(System.err);
//		    }
		
	}
}
