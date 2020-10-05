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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.DataNotFoundException;
import org.irods.jargon.core.exception.DuplicateDataException;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.exception.JargonFileOrCollAlreadyExistsException;
import org.irods.jargon.core.exception.OverwriteException;
import org.irods.jargon.core.exception.UnixFileCreateException;
import org.irods.jargon.core.packinstr.TransferOptions;
import org.irods.jargon.core.pub.BulkFileOperationsAO;
import org.irods.jargon.core.pub.DataTransferOperations;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.pub.io.IRODSFileFactory;
import org.irods.jargon.core.transfer.DefaultTransferControlBlock;
import org.irods.jargon.core.transfer.TransferControlBlock;
import org.irods.jargon.core.transfer.TransferStatus;
import org.irods.jargon.core.transfer.TransferStatusCallbackListener;

import galileo.config.SystemConfig;
import galileo.dht.DataStoreHandler.MessageHandler;
public class IRODSManagerRemoveLater {
	
	// Jargon version: https://github.com/DICE-UNC/DICE-Maven/blob/master/releases/org/irods/jargon/jargon-core/4.0.2.6-RELEASE/jargon-core-4.0.2.6-RELEASE-jar-with-dependencies.jar
	private static Logger logger = Logger.getLogger("galileo");
	private IRODSAccount account;
	private IRODSFileSystem filesystem;
	private IRODSFileFactory fileFactory;
	private DataTransferOperations dataTransferOperationsAO;
	
	public static String IRODS_BASE = "/iplant/home/radix_subterra";
	
	public static String IRODS_SEPARATOR = "/";
	public static String GALILEO_SEPARATOR = File.separator;
	
	
	public IRODSManagerRemoveLater() {	//davos.cyverse.org
		account = new IRODSAccount("data.iplantcollaborative.org", 1247, System.getenv("IRODS_USER"), System.getenv("IRODS_PASSWORD"), "/iplant/home/radix_subterra", "iplant", "");
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
	
	public void initRIGPath(String fsName) {
		
		String remoteDirectory = fsName+IRODS_SEPARATOR+"rig";
		
		logger.info("RIKI: Initializing RemoteDirectory FOR RIG DUMP: " + remoteDirectory);
		IRODSFile remoteDir = null;
		try {
			
			remoteDir = fileFactory.instanceIRODSFile(remoteDirectory);
			
			if(!remoteDir.exists()) {
				remoteDir.mkdirs();
				logger.info("Created remoteDir: " + remoteDir.getAbsolutePath());
			}
			logger.info("RIKI: Initialized RIG DUMP: " + remoteDirectory);
			remoteDir.close();
			
		} catch (Exception e) {
			logger.severe("RIKI: Problem IRODS INIT: " + e );
		} finally {
			
		}
	}
	
	// HANDLES RIG DUMP
	public void writeRemoteFileAtSpecificPath(File toExport, String remoteDirectory)throws JargonException{
		
		TransferOptions opts = new TransferOptions();
		opts.setComputeAndVerifyChecksumAfterTransfer(true);
		opts.setIntraFileStatusCallbacks(true);
		TransferControlBlock tcb = DefaultTransferControlBlock.instance();
		tcb.setTransferOptions(opts);
		tcb.setMaximumErrorsBeforeCanceling(10);
		tcb.setTotalBytesToTransfer(toExport.length());
		
		logger.info("RIKI: RFFILE: "+remoteDirectory);
		remoteDirectory = remoteDirectory.substring(0, remoteDirectory.lastIndexOf("/"));
		
		logger.info("RIKI: RemoteDirectory FOR RIG DUMP: " + remoteDirectory);
		IRODSFile remoteDir = null;
		try {
			
			remoteDir = fileFactory.instanceIRODSFile(remoteDirectory);
			
			if(!remoteDir.exists()) {
				logger.info("Created remoteDir: " + remoteDir.getAbsolutePath());
				remoteDir.mkdirs();
			
			}
			
			logger.info("Created remoteDirX: " + remoteDir.getAbsolutePath());
			while(true) {
				try {
					
					dataTransferOperationsAO.putOperation(toExport, remoteDir, null, tcb);
					logger.info("RIKI: DUMPED "+toExport.getName() +" THE RIG AT: "+remoteDir.getAbsolutePath()+" "+remoteDirectory);
					break;
				} catch(UnixFileCreateException e) {
					logger.info("UnixFileCreateException caught, trying again.");
					try {
						Thread.sleep(ThreadLocalRandom.current().nextInt(0, 50));
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					//dataTransferOperationsAO.putOperation(toExport, remoteDir, null, tcb);
				} catch(DataNotFoundException e) {
					//stupid IRODS... directory was not created properly, so create it again and retry
					logger.severe("RIKI: CAUGHT DataNotFoundException EXCEPTION!");
					remoteDir = fileFactory.instanceIRODSFile(remoteDirectory);
					remoteDir.mkdirs();
					//dataTransferOperationsAO.putOperation(toExport, remoteDir, null, tcb);
					//break;
				} catch (OverwriteException | JargonFileOrCollAlreadyExistsException | DuplicateDataException e){//append to existing file
					logger.severe("CAUGHT OVERWRITE EXCEPTION!");
					
				} catch (Exception e) {
					logger.severe("RIKI: Problem IRODS: " + e + ": " + toExport.getAbsolutePath());
				} 
			}
		} catch (JargonException e) {
			logger.severe("Error with IRODS: " + e + ": " + Arrays.toString(e.getStackTrace())+"\nFile: " + toExport.getAbsolutePath());
		} catch (Exception e) {
			logger.severe("RIKI: Problem IRODS: " + e + ": " + toExport.getAbsolutePath());
		} finally {
			dataTransferOperationsAO.closeSessionAndEatExceptions();
		}
		
		
	}
	
	
	// THIS WRITES THE ACTUAL DATA BACKUP FOR EACH PLOTS
	public boolean writeRemoteFile(File toExport, MessageHandler caller)throws JargonException{
		
		boolean isSuccess = false;
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
			//logger.info("Created remoteDir: " + remoteDir.getAbsolutePath());
			remoteDir.mkdirs();
			
			int i=0;
			while(i<3) {
				try {
					i++;
					dataTransferOperationsAO.putOperation(toExport, remoteDir, null, tcb);
					isSuccess = true;
					break;
				} catch(UnixFileCreateException e) {
					logger.info("UnixFileCreateException caught, trying again.");
					try {
						Thread.sleep(ThreadLocalRandom.current().nextInt(0, 50));
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					//dataTransferOperationsAO.putOperation(toExport, remoteDir, null, tcb);
				}//shouldn't need to catch overwrite exceptions now...
				catch(DataNotFoundException e) {
					//stupid IRODS... directory was not created properly, so create it again and retry
					remoteDir = fileFactory.instanceIRODSFile(remoteDirectory);
					remoteDir.mkdirs();
					logger.info("DataNotFoundException caught, trying again.");
					try {
						Thread.sleep(ThreadLocalRandom.current().nextInt(0, 50));
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				} catch (OverwriteException | JargonFileOrCollAlreadyExistsException | DuplicateDataException e){//append to existing file
					logger.severe("RIKI: CAUGHT OVERWRITE EXCEPTION FOR "+remoteDir);
					break;
					
				}
			}
		} catch (JargonException e) {
			logger.severe("Error with IRODS: " + e + ": " + Arrays.toString(e.getStackTrace())+"\nFile: " + toExport.getAbsolutePath());
		} finally {
			dataTransferOperationsAO.closeSessionAndEatExceptions();
		}
		return isSuccess;
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
			toFetch.close();
			return null;
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
		toFetch.close();
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
		
		toFetch.close();
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
	
	
	public static void main2(String arg[]) throws JargonException, IOException {
		
		IRODSManagerRemoveLater im = new IRODSManagerRemoveLater();
		String[] readAllRemoteFiles = im.readAllRemoteFiles("roots-arizona");
		
		System.out.println(readAllRemoteFiles.length);
	}
	
	private boolean checkExists(String remoteDirectory) {
		
		logger.info("RIKI: Checking RemoteDirectory FOR RIG DUMP: " + remoteDirectory);
		IRODSFile remoteDir = null;
		try {
			remoteDir = fileFactory.instanceIRODSFile(remoteDirectory);
			if(remoteDir.exists()) {
				return true;
			}
			logger.info("Created remoteDir: " + remoteDir.getAbsolutePath());
		} catch (Exception e) {
			logger.severe("RIKI: Problem IRODS INIT: " + e );
		} 
		return false;
	}


	public static void main1(String arg[]) {
		int i = 0;
		int num = 0;
		while(true) {
			try {
				i++;
				System.out.println("TRYING AGAIN...."+i);
				if(i < 10) {
					System.out.println(5/num);
				} else {
					System.out.println("SAFE");
					System.out.println("BREAKING");
					break;
					
				}
				
			} catch(Exception e) {
				System.out.println("CAUGHT EXCEPTION");
				break;
			}
			
		}
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
	
	/**
	 * CREATES A TAR FILE OUT OF THE CONTENTS
	 * @author sapmitra
	 * @param toExport
	 * @param remoteDirectory
	 * @throws JargonException
	 */
	public void tarOnGalileoAndUntarOnIrods(File toExport, String remoteDirectory) throws JargonException {
		
		writeRemoteFileAtSpecificPath(toExport, remoteDirectory);
		
	}
	
	/**
	 * COMPRESS THE CONTENTS OF THE sourceDir folder into the destinationDir LOCATIONS
	 * @author sapmitra
	 * @param sourceDir THE DIRECTOORY YOU WANT COMPRESSED
	 * @param destinationDir THE DESTINATION FOLDER WHERE YOU WANT THE roots-arizona.tar.gz STORED
	 */
	private void createTarFile(String sourceDir, String destinationDir, String fsName) {
		
		String destFilePath = destinationDir+GALILEO_SEPARATOR+fsName+".tar";
		
		Path path = Paths.get(destFilePath);
		
		
		File temp = new File(destFilePath);

		try {
			if (temp.exists()) {
				temp.delete();
			}
			Files.deleteIfExists(path);
			
		} catch (IOException e) {
			
		}
		
		
		File resultsDir = new File(destinationDir);
		
		if (!resultsDir.exists())
			resultsDir.mkdirs();
		
		
		TarArchiveOutputStream tarOs = null;
		
		try {
			
			FileOutputStream fos = new FileOutputStream(destFilePath);
			GZIPOutputStream gos = new GZIPOutputStream(new BufferedOutputStream(fos));
			tarOs = new TarArchiveOutputStream(gos);
			addFilesToTarGZ(sourceDir, "", tarOs);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				tarOs.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	
	
	public void addFilesToTarGZ(String filePath, String parent, TarArchiveOutputStream tarArchive) throws IOException {
		File file = new File(filePath);
		// Create entry name relative to parent file path
		String entryName = parent + file.getName();
		// add tar ArchiveEntry
		tarArchive.putArchiveEntry(new TarArchiveEntry(file, entryName));
		if (file.isFile()) {
			if(file.getName().contains("metadata")) {
				return;
			}
			FileInputStream fis = new FileInputStream(file);
			BufferedInputStream bis = new BufferedInputStream(fis);
			// Write file content to archive
			IOUtils.copy(bis, tarArchive);
			tarArchive.closeArchiveEntry();
			bis.close();
		} else if (file.isDirectory()) {
			// no need to copy any content since it is
			// a directory, just close the outputstream
			tarArchive.closeArchiveEntry();
			// for files in the directories
			for (File f : file.listFiles()) {
				// recursively call the method for all the subdirectories
				addFilesToTarGZ(f.getAbsolutePath(), entryName + File.separator, tarArchive);
			}
		}
	}
	
	
	public static void main(String arg[]) throws JargonException, IOException, NullPointerException, URISyntaxException {
		
		IRODSManagerRemoveLater im = new IRODSManagerRemoveLater();
		//File f = new File("/s/chopin/b/grad/sapmitra/Documents/radix/ABC.csv");
		//im.initRIGPath("roots-arizona");
		
		File toExport = new File("/s/chopin/b/grad/sapmitra/Desktop/roots-arizona1.tar");
		im.writeRemoteFileAtSpecificPath(toExport, "/iplant/home/radix_subterra/");
		im.untarIRODSFile("/iplant/home/radix_subterra/roots-arizona1.tar","/iplant/home/radix_subterra","");
		
		System.out.println("Hello");
		
		//im.testmethod();
		
	}
	
	
	public void testmethod() {
		IRODSFile toFetch;
		try {
			toFetch = fileFactory.instanceIRODSFile("/iplant/home/radix_subterra/riki/NOTICE");
			toFetch.delete();
		} catch (JargonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	public void untarIRODSFile(String src, String dest, String resource) {
		
		try {
			BulkFileOperationsAO bo = filesystem.getIRODSAccessObjectFactory().getBulkFileOperationsAO(account);
			bo.extractABundleIntoAnIrodsCollection(src, dest, resource);
			
			IRODSFile toFetch = fileFactory.instanceIRODSFile(src);
			toFetch.delete();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	
	
}
