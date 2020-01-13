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
 * This class handles all interaction with IRODS. Mostly just inserting data there. As is this code doesn't hammer
 * IRODS services too hard, but if emails are received at radix_subterra@gmail.com complaining of issues, change ""data.iplantcollaborative.org" 
 * to "davos.cyverse.org" below.*/
package galileo.dht;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.DataNotFoundException;
import org.irods.jargon.core.exception.DuplicateDataException;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.exception.JargonFileOrCollAlreadyExistsException;
import org.irods.jargon.core.exception.OverwriteException;
import org.irods.jargon.core.exception.UnixFileCreateException;
import org.irods.jargon.core.packinstr.TransferOptions;
import org.irods.jargon.core.pub.DataTransferOperations;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.pub.io.IRODSFileFactory;
import org.irods.jargon.core.transfer.TransferStatus;
import org.irods.jargon.core.transfer.TransferStatusCallbackListener;
import org.irods.jargon.core.transfer.TransferStatusCallbackListener.CallbackResponse;
import org.irods.jargon.core.transfer.TransferStatusCallbackListener.FileStatusCallbackResponse;
import org.irods.jargon.core.transfer.DefaultTransferControlBlock;
import org.irods.jargon.core.transfer.TransferControlBlock;

import galileo.config.SystemConfig;
import galileo.dht.DataStoreHandler.MessageHandler;
public class IRODSManager {
	private static Logger logger = Logger.getLogger("galileo");
	private IRODSAccount account;
	private IRODSFileSystem filesystem;
	private IRODSFileFactory fileFactory;
	private DataTransferOperations dataTransferOperationsAO;
	
	public static String IRODS_BASE = "/iplant/home/radix_subterra";
	
	public static String IRODS_SEPARATOR = "/";
	public static String GALILEO_SEPARATOR = File.separator;
	
	
	public IRODSManager() {	//davos.cyverse.org
		account = new IRODSAccount("data.iplantcollaborative.org", 1247, "radix_subterra", "roots&radix2018", "/iplant/home/radix_subterra", "iplant", "");
		try {
			filesystem = IRODSFileSystem.instance();
			fileFactory = filesystem.getIRODSFileFactory(account);
			dataTransferOperationsAO = filesystem.getIRODSAccessObjectFactory()
					.getDataTransferOperations(account);

		} catch (JargonException e) {
			logger.severe("Error initializing IRODS FileSystem: " + e);
		}	 
	}
	
	
	/**
	 * 
	 * @author sapmitra
	 * @param data ACTUAL DATA TO BE WRITTEN
	 * @param irodspath path to the file after radix_subterra directory
	 */
	public void writeRemoteData(String data, String irodspath) {
		try {
			
			//logger.info("RIKI: ARGUMENTS: "+irodspath);
			//logger.info("RIKI: DATA: "+data);
			String fileName = irodspath.substring(irodspath.lastIndexOf("/")+1);
			
			//logger.info("RIKI: FILENAME: "+fileName);
			File temp = File.createTempFile(fileName, ".txt");

			if (!temp.exists())
				temp.createNewFile();
			PrintWriter tempWriter = new PrintWriter(temp);
			tempWriter.write(data);
			tempWriter.close();
			
			
			try {
				writeRemoteFileAtSpecificPath(temp, irodspath);
			} catch (JargonException e) {
				// TODO Auto-generated catch block
				logger.severe("RIKI: ERROR WRITING,,,"+e);
			}
			
			
			temp.delete();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public void writeRemoteFileAtSpecificPath(File toExport, String remoteDirectory)throws JargonException{
		TransferOptions opts = new TransferOptions();
		opts.setComputeAndVerifyChecksumAfterTransfer(true);
		opts.setIntraFileStatusCallbacks(true);
		TransferControlBlock tcb = DefaultTransferControlBlock.instance();
		tcb.setTransferOptions(opts);
		tcb.setMaximumErrorsBeforeCanceling(10);
		tcb.setTotalBytesToTransfer(toExport.length());
		
		//logger.info("RIKI: RDIR: "+remoteDirectory);
		remoteDirectory = remoteDirectory.substring(0, remoteDirectory.lastIndexOf("/"));
		
		logger.info("RIKI: RemoteDirectory FOR RIG DUMP: " + remoteDirectory);
		IRODSFile remoteDir = null;
		try {
			remoteDir = fileFactory.instanceIRODSFile(remoteDirectory);
			//logger.info("Created remoteDir: " + remoteDir.getAbsolutePath());
			try {
				remoteDir.mkdirs();
				dataTransferOperationsAO.putOperation(toExport, remoteDir, null, tcb);
			} catch(UnixFileCreateException e) {
				logger.info("UnixFileCreateException caught, trying again.");
				dataTransferOperationsAO.putOperation(toExport, remoteDir, null, tcb);
			}//shouldn't need to catch overwrite exceptions now...
			catch(DataNotFoundException e) {
				//stupid IRODS... directory was not created properly, so create it again and retry
				remoteDir = fileFactory.instanceIRODSFile(remoteDirectory);
				remoteDir.mkdirs();
				dataTransferOperationsAO.putOperation(toExport, remoteDir, null, tcb);
			} catch (OverwriteException | JargonFileOrCollAlreadyExistsException | DuplicateDataException e){//append to existing file
				logger.severe("CAUGHT OVERWRITE EXCEPTION!");
				
			}
		} catch (JargonException e) {
			logger.severe("Error with IRODS: " + e + ": " + Arrays.toString(e.getStackTrace())+"\nFile: " + toExport.getAbsolutePath());
		}
		
		
	}
	
	
	
	
	public void writeRemoteFile(File toExport, MessageHandler caller)throws JargonException{
		TransferOptions opts = new TransferOptions();
		opts.setComputeAndVerifyChecksumAfterTransfer(true);
		opts.setIntraFileStatusCallbacks(true);
		TransferControlBlock tcb = DefaultTransferControlBlock.instance();
		tcb.setTransferOptions(opts);
		tcb.setMaximumErrorsBeforeCanceling(10);
		tcb.setTotalBytesToTransfer(toExport.length());
		
		// PATH USED TO LOOK LIKE /iplant/home/radix_subterra/plots/970/2018/12.../xyz.gblock
		// NOW: 
		
		String remoteDirectory = toExport.getAbsolutePath().replaceAll(SystemConfig.getRootDir(), "");
		remoteDirectory = remoteDirectory.substring(1, remoteDirectory.lastIndexOf("/"));
		//logger.info("remoteDirectory string: " + remoteDirectory);
		IRODSFile remoteDir = null;
		try {
			remoteDir = fileFactory.instanceIRODSFile(remoteDirectory);
			//logger.info("RIKI: WRITE OUT " + remoteDirectory+ "<<"+ toExport.getAbsolutePath());
			try {
				remoteDir.mkdirs();
				dataTransferOperationsAO.putOperation(toExport, remoteDir, null, tcb);
			} catch(UnixFileCreateException e) {
				logger.info("UnixFileCreateException caught, trying again.");
				dataTransferOperationsAO.putOperation(toExport, remoteDir, null, tcb);
			}//shouldn't need to catch overwrite exceptions now...
			catch(DataNotFoundException e) {
				//stupid IRODS... directory was not created properly, so create it again and retry
				logger.info("UnixFileCreateException caught, trying again.");
				remoteDir = fileFactory.instanceIRODSFile(remoteDirectory);
				remoteDir.mkdirs();
				dataTransferOperationsAO.putOperation(toExport, remoteDir, null, tcb);
			} catch (OverwriteException | JargonFileOrCollAlreadyExistsException | DuplicateDataException e){//append to existing file
				logger.severe("CAUGHT OVERWRITE EXCEPTION!");
				//Get existing file, append to it locally, then put with a forced overwrite
				//Corner case: One thread begins to put, and file exists but is in incomplete state. Meanwhile,
				// another thread sees the file exists, and gets it in incomplete state, appends, and overwrites a partial file.
//				TransferStatusCallbackListener tscl = new TransferStatusCallbackListener()
//				{
//					@Override
//					public FileStatusCallbackResponse statusCallback(TransferStatus transferStatus) { return FileStatusCallbackResponse.CONTINUE; }
//					@Override
//					public void overallStatusCallback(TransferStatus transferStatus) {}
//					@Override
//					public CallbackResponse transferAsksWhetherToForceOperation(String irodsAbsolutePath, boolean isCollection) { return CallbackResponse.YES_FOR_ALL; }
//				};
//				logger.info("\n\nCaught overwrite exception. Writing " + toWrite.split("\n").length + " lines for plot " + fileName.substring(0, fileName.indexOf("/"))+"\n\n");
//				File temp = File.createTempFile("plot", ".gblock");
//				if (!temp.exists())
//					temp.createNewFile();
////				IRODSFile remoteFile = fileFactory.instanceIRODSFile("plots/" + fileName.substring(0, fileName.lastIndexOf(File.separator)));
//				IRODSFile toFetch = fileFactory.instanceIRODSFile("plots/" + fileName);
//				while (!toFetch.exists()){
//					try {
//						Thread.sleep(100);
//					} catch (InterruptedException e1) {
//						e1.printStackTrace();
//					}
//				}
//				dataTransferOperationsAO.getOperation(toFetch, temp, tscl, tcb);
//				String remoteContents = new String(Files.readAllBytes(Paths.get(temp.getAbsolutePath())));
//				logger.info("****************\nReceived " + remoteContents.split(System.lineSeparator()).length + " lines from IRODS for plot " + fileName.substring(0, fileName.lastIndexOf(File.separator))+ "\n****************");
//				fileWriter.write(System.lineSeparator() + remoteContents);
//				fileWriter.close();
//				logger.info("\n*********\nAfter combining remote data and local, wrote a new file of length " + new String(Files.readAllBytes(Paths.get(localFile.getAbsolutePath()))).split("\n").length);
////				File temp = File.createTempFile("plot", ".gblock");
////				if (!temp.exists())
////					temp.createNewFile();
////				PrintWriter tempWriter = new PrintWriter(temp);
////				tempWriter.write(toWrite);
////				tempWriter.close();
//				dataTransferOperationsAO.putOperation(localFile, remoteDir, tscl, tcb);
//				temp.delete();
				
			}
		} catch (JargonException e) {
			logger.severe("Error with IRODS: " + e + ": " + Arrays.toString(e.getStackTrace())+"\nFile: " + toExport.getAbsolutePath());
		}
		finally {
//			fileWriter.close();
			//toExport.delete();
		}
		
	}


	/**
	 * RETURNS THE PATH LINE LIST READ FROM THE DUMP FILES
	 * @author sapmitra
	 * @param fsName
	 * @return
	 * @throws JargonException
	 * @throws IOException 
	 */
	public String[] readAllRemoteFiles(String fsName) throws JargonException, IOException{
		StringBuffer sb = new StringBuffer();
		
		TransferOptions opts = new TransferOptions();
		//opts.setComputeAndVerifyChecksumAfterTransfer(true);
		opts.setIntraFileStatusCallbacks(true);
		TransferControlBlock tcb = DefaultTransferControlBlock.instance();
		tcb.setTransferOptions(opts);
		
		TransferStatusCallbackListener tscl = new TransferStatusCallbackListener() {
			@Override
			public FileStatusCallbackResponse statusCallback(TransferStatus transferStatus) {
				return FileStatusCallbackResponse.CONTINUE;
			}

			@Override
			public void overallStatusCallback(TransferStatus transferStatus) {
			}

			@Override
			public CallbackResponse transferAsksWhetherToForceOperation(String irodsAbsolutePath,
					boolean isCollection) {
				return CallbackResponse.YES_FOR_ALL;
			}
		};
		
		File temp = new File("/tmp/sampleData");
		
		if(temp.exists())
			FileUtils.deleteDirectory(temp);
		
		temp.mkdirs();
		
		//IRODSFile remoteFile = fileFactory.instanceIRODSFile("plots/");
		IRODSFile toFetch = fileFactory.instanceIRODSFile(fsName+"/rig");
		//IRODSFile toFetch = fileFactory.instanceIRODSFile("util/me");
		
		if (!toFetch.exists()) {
			
			logger.info("RIKI: NOT EXISTS:"+"/"+fsName+"/rig");
			//System.out.println("RIKI: NOT EXISTS:"+"/util/me");
			//Thread.sleep(100);
			
		}
		dataTransferOperationsAO.getOperation(toFetch, temp, tscl, tcb);
		
		temp = new File("/tmp/sampleData/rig");
		
		//temp = new File("/tmp/sampleData/me");
		for(File f : temp.listFiles()) {
		
			String remoteContents = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath())));
			sb.append(remoteContents);
		}
		
		FileUtils.deleteDirectory(temp);
		
		String[] paths = sb.toString().split("\\n");
		logger.info("RIKI: PATHS READ: " + paths.length);
		
		return paths;
		
	}
	
	/**
	 * DOWNLOAD A FULL DIRECTORY FROM IRODS
	 * @author sapmitra
	 * @throws JargonException
	 * @throws IOException
	 */
	public void readRemoteDirectory(String whereToPut, String whatToDownload) throws JargonException, IOException {

		TransferOptions opts = new TransferOptions();
		//opts.setComputeAndVerifyChecksumAfterTransfer(true);
		opts.setIntraFileStatusCallbacks(true);
		TransferControlBlock tcb = DefaultTransferControlBlock.instance();
		tcb.setTransferOptions(opts);
		
		TransferStatusCallbackListener tscl = new TransferStatusCallbackListener() {
			@Override
			public FileStatusCallbackResponse statusCallback(TransferStatus transferStatus) {
				return FileStatusCallbackResponse.CONTINUE;
			}

			@Override
			public void overallStatusCallback(TransferStatus transferStatus) {
			}

			@Override
			public CallbackResponse transferAsksWhetherToForceOperation(String irodsAbsolutePath,
					boolean isCollection) {
				return CallbackResponse.YES_FOR_ALL;
			}
		};
		
		String sufixDir = whatToDownload.replace(IRODS_BASE, "");
		
		File temp = new File(whereToPut+sufixDir);
		
		if (!temp.exists())
			temp.mkdirs();
		//IRODSFile remoteFile = fileFactory.instanceIRODSFile("plots/");
		IRODSFile toFetch = fileFactory.instanceIRODSFile(whatToDownload);
		while (!toFetch.exists()) {
			try {
				System.out.println("THE PATH YOU ARE TRYING TO DOWNLOAD DIES NOT EXIST");
				Thread.sleep(100);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		}
		
		dataTransferOperationsAO.getOperation(toFetch, temp, tscl, tcb);
		
	}
	
	
	/**
	 * DOWNLOAD A FULL DIRECTORY FROM IRODS
	 * @author sapmitra
	 * @throws JargonException
	 * @throws IOException
	 */
	public void readRemoteDirectory_validation(String whereToPut, String whatToDownload) throws JargonException, IOException {

		TransferOptions opts = new TransferOptions();
		//opts.setComputeAndVerifyChecksumAfterTransfer(true);
		opts.setIntraFileStatusCallbacks(true);
		TransferControlBlock tcb = DefaultTransferControlBlock.instance();
		tcb.setTransferOptions(opts);
		
		TransferStatusCallbackListener tscl = new TransferStatusCallbackListener() {
			@Override
			public FileStatusCallbackResponse statusCallback(TransferStatus transferStatus) {
				return FileStatusCallbackResponse.CONTINUE;
			}

			@Override
			public void overallStatusCallback(TransferStatus transferStatus) {
			}

			@Override
			public CallbackResponse transferAsksWhetherToForceOperation(String irodsAbsolutePath,
					boolean isCollection) {
				return CallbackResponse.YES_FOR_ALL;
			}
		};
		
		String sufixDir = whatToDownload.replace(IRODS_BASE, "");
		int indx = sufixDir.lastIndexOf("/");
		sufixDir = sufixDir.substring(0,indx);
		
		File temp = new File(whereToPut+sufixDir);
		
		if (!temp.exists())
			temp.mkdirs();
		//IRODSFile remoteFile = fileFactory.instanceIRODSFile("plots/");
		IRODSFile toFetch = fileFactory.instanceIRODSFile(whatToDownload);
		while (!toFetch.exists()) {
			try {
				System.out.println("THE PATH YOU ARE TRYING TO DOWNLOAD DIES NOT EXIST");
				Thread.sleep(100);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		}
		
		dataTransferOperationsAO.getOperation(toFetch, temp, tscl, tcb);
		
	}
	
	
	public static void main(String arg[]) throws JargonException, IOException {
		
		IRODSManager im = new IRODSManager();
		String[] readAllRemoteFiles = im.readAllRemoteFiles("roots-arizona");
		
		System.out.println(readAllRemoteFiles.length);
	}
	
	
	public void readRemoteFile(String whereToPut, String whatToDownload) throws JargonException, IOException {

		TransferOptions opts = new TransferOptions();
		opts.setComputeAndVerifyChecksumAfterTransfer(true);
		opts.setIntraFileStatusCallbacks(true);
		TransferControlBlock tcb = DefaultTransferControlBlock.instance();
		tcb.setTransferOptions(opts);
		
		TransferStatusCallbackListener tscl = new TransferStatusCallbackListener() {
			@Override
			public FileStatusCallbackResponse statusCallback(TransferStatus transferStatus) {
				return FileStatusCallbackResponse.CONTINUE;
			}

			@Override
			public void overallStatusCallback(TransferStatus transferStatus) {
			}

			@Override
			public CallbackResponse transferAsksWhetherToForceOperation(String irodsAbsolutePath,
					boolean isCollection) {
				return CallbackResponse.YES_FOR_ALL;
			}
		};
		
		String suffix = whatToDownload.replace(IRODS_BASE, "");
		suffix = suffix.substring(0,suffix.lastIndexOf(IRODS_SEPARATOR));
		File temp = new File(whereToPut+suffix);
		if (!temp.exists())
			temp.mkdirs();
		//IRODSFile remoteFile = fileFactory.instanceIRODSFile("plots/");
		IRODSFile toFetch = fileFactory.instanceIRODSFile(whatToDownload);
		if (!toFetch.exists()) {
			
			logger.info("NOT EXISTS");
			
		}
		dataTransferOperationsAO.getOperation(toFetch, temp, tscl, tcb);
		
	}
	
}
