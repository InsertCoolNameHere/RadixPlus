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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.DataNotFoundException;
import org.irods.jargon.core.exception.DuplicateDataException;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.exception.JargonFileOrCollAlreadyExistsException;
import org.irods.jargon.core.exception.OverwriteException;
import org.irods.jargon.core.exception.UnixFileCreateException;
import org.irods.jargon.core.packinstr.TransferOptions;
import org.irods.jargon.core.packinstr.TransferOptions.PutOptions;
import org.irods.jargon.core.pub.DataObjectAO;
import org.irods.jargon.core.pub.DataTransferOperations;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.core.pub.domain.DataObject;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.pub.io.IRODSFileFactory;
import org.irods.jargon.core.query.AVUQueryElement;
import org.irods.jargon.core.query.AVUQueryOperatorEnum;
import org.irods.jargon.core.transfer.TransferStatus;
import org.irods.jargon.core.transfer.TransferStatusCallbackListener;
import org.irods.jargon.core.transfer.DefaultTransferControlBlock;
import org.irods.jargon.core.transfer.TransferControlBlock;

import galileo.config.SystemConfig;
import galileo.dht.DataStoreHandler.MessageHandler;
public class IRODSManagerTest {
	private static Logger logger = Logger.getLogger("galileo");
	private IRODSAccount account;
	private IRODSFileSystem filesystem;
	private IRODSFileFactory fileFactory;
	private DataTransferOperations dataTransferOperationsAO;
	private DataObjectAO dataObjectAO;
	
	public static void main1(String arg[]) throws JargonException {
		
		IRODSManagerTest tst = new IRODSManagerTest();
		
		/*File f = new File("/s/chopin/b/grad/sapmitra/Documents/radix/SampleData.txt");
		
		System.out.println(f.getAbsolutePath());
		tst.writeRemoteFile(f);*/
		
		try {
			tst.readRemoteDirectory();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("CAUGHT: "+e);
		}
		
	}
	
	public static void main(String arg[]) throws JargonException {
		IRODSManagerTest tst = new IRODSManagerTest();
		tst.writeRemoteFile(new File("/s/chopin/b/grad/sapmitra/Desktop/NOTICE"));
	}
	
	public IRODSManagerTest() {	//davos.cyverse.org
									//host, port, userName, password, homeDirectory, userZone, defaultStorageResource
		//account = new IRODSAccount("data.iplantcollaborative.org", 1247, "radix_subterra", "roots&radix2018", "/iplant/home/radix_subterra", "iplant", "");
		account = new IRODSAccount("data.iplantcollaborative.org", 1247, "radix_subterra", "roots&radix2018", "/iplant/home/radix_subterra", "iplant", "");
		try {
			filesystem = IRODSFileSystem.instance();
			fileFactory = filesystem.getIRODSFileFactory(account);
			dataTransferOperationsAO = filesystem.getIRODSAccessObjectFactory()
					.getDataTransferOperations(account);
			dataObjectAO = filesystem.getIRODSAccessObjectFactory().getDataObjectAO(account);


		} catch (JargonException e) {
			logger.severe("Error initializing IRODS FileSystem: " + e);
		}	 
	}
	
	
	public void readRemoteFile() throws JargonException, IOException {

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
		/*logger.info("\n\nCaught overwrite exception. Writing " + toWrite.split("\n").length + " lines for plot "
				+ fileName.substring(0, fileName.indexOf("/")) + "\n\n");*/
		//File temp = File.createTempFile("D:\\Autoscaling\\plot", ".gblock");
		
		File temp = new File("D:\\Autoscaling\\temp.gblock");
		if (!temp.exists())
			temp.createNewFile();
		//IRODSFile remoteFile = fileFactory.instanceIRODSFile("plots/");
		IRODSFile toFetch = fileFactory.instanceIRODSFile("/iplant/home/radix_subterra/riki/SampleData.txt");
		while (!toFetch.exists()) {
			try {
				System.out.println("NOT EXISTS");
				Thread.sleep(100);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		}
		dataTransferOperationsAO.getOperation(toFetch, temp, tscl, tcb);
		String remoteContents = new String(Files.readAllBytes(Paths.get(temp.getAbsolutePath())));
		
		System.out.println("Hi");
		/*logger.info("****************\nReceived " + remoteContents.split(System.lineSeparator()).length
				+ " lines from IRODS for plot " + fileName.substring(0, fileName.lastIndexOf(File.separator))
				+ "\n****************");
		fileWriter.write(System.lineSeparator() + remoteContents);
		fileWriter.close();
		logger.info("\n*********\nAfter combining remote data and local, wrote a new file of length "
				+ new String(Files.readAllBytes(Paths.get(localFile.getAbsolutePath()))).split("\n").length);
		File temp = File.createTempFile("plot", ".gblock");
		if (!temp.exists())
			temp.createNewFile();
		PrintWriter tempWriter = new PrintWriter(temp);
		tempWriter.write(toWrite);
		tempWriter.close();
		dataTransferOperationsAO.putOperation(localFile, remoteDir, tscl, tcb);
		temp.delete();*/
	}
	
	
	public void readRemoteDirectory() throws JargonException, IOException {

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
		
		File temp = new File("/s/chopin/b/grad/sapmitra/Documents/radix/sampleData");
		if (!temp.exists())
			temp.createNewFile();
		//IRODSFile remoteFile = fileFactory.instanceIRODSFile("plots/");
		IRODSFile toFetch = fileFactory.instanceIRODSFile("/iplant/home/radix_subterra/plots/990");
		while (!toFetch.exists()) {
			try {
				System.out.println("NOT EXISTS");
				Thread.sleep(100);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		}
		dataTransferOperationsAO.getOperation(toFetch, temp, tscl, tcb);
		System.out.println("FINISHED FETCHING");
		//String remoteContents = new String(Files.readAllBytes(Paths.get(temp.getAbsolutePath())));
		
		//System.out.println("Hi" + remoteContents);
		/*logger.info("****************\nReceived " + remoteContents.split(System.lineSeparator()).length
				+ " lines from IRODS for plot " + fileName.substring(0, fileName.lastIndexOf(File.separator))
				+ "\n****************");
		fileWriter.write(System.lineSeparator() + remoteContents);
		fileWriter.close();
		logger.info("\n*********\nAfter combining remote data and local, wrote a new file of length "
				+ new String(Files.readAllBytes(Paths.get(localFile.getAbsolutePath()))).split("\n").length);
		File temp = File.createTempFile("plot", ".gblock");
		if (!temp.exists())
			temp.createNewFile();
		PrintWriter tempWriter = new PrintWriter(temp);
		tempWriter.write(toWrite);
		tempWriter.close();
		dataTransferOperationsAO.putOperation(localFile, remoteDir, tscl, tcb);
		temp.delete();*/
	}
	
	public void readRemoteFile_Old()throws JargonException{
		
		//String remoteDirectory = "plots" + toExport.getAbsolutePath().replaceAll(SystemConfig.getRootDir(), "").replaceAll("/dailyTemp", "");
		IRODSFile remoteDir = null;
		try {
			//remoteDir = fileFactory.instanceIRODSFile(remoteDirectory);
			//logger.info("Created remoteDir: " + remoteDir.getAbsolutePath());
			try {
				//remoteDir.mkdirs();
				
				List<AVUQueryElement> avuQueryElements = new ArrayList<>();
				
				avuQueryElements.add(AVUQueryElement.instanceForValueQuery(
		                AVUQueryElement.AVUQueryPart.ATTRIBUTE, AVUQueryOperatorEnum.EQUAL, "datasetURI"));
				
				avuQueryElements.add(AVUQueryElement.instanceForValueQuery(
		                AVUQueryElement.AVUQueryPart.ATTRIBUTE, AVUQueryOperatorEnum.EQUAL, "/iplant/home"));
				List<DataObject> dobject = dataObjectAO.findDomainByMetadataQuery(avuQueryElements);
				
				dataTransferOperationsAO.getOperation("/iplant/home/radix_subterra/", "D:\\Autoscaling", dobject.get(0).getResourceName(), null, null);
				//dataTransferOperationsAO.putOperation(toImport, remoteDir, null, tcb);
			} catch(Exception e) {
				logger.info("Exception caught, trying again. "+e);
				//dataTransferOperationsAO.getOperation("/iplant", "C:\\Users\\Saptashwa\\workspace", arg2, arg3);
			} 
		} catch (Exception e) {
			logger.severe("Error with IRODS: " + e);
		}
		finally {
//			fileWriter.close();
			//toExport.delete();
		}
		
	}
	
	
	public void writeRemoteFile(File toExport)throws JargonException{
		TransferOptions opts = new TransferOptions();
		opts.setComputeAndVerifyChecksumAfterTransfer(true);
		opts.setIntraFileStatusCallbacks(true);
		TransferControlBlock tcb = DefaultTransferControlBlock.instance();
		tcb.setTransferOptions(opts);
		tcb.setMaximumErrorsBeforeCanceling(10);
		tcb.setTotalBytesToTransfer(toExport.length());
		
		PutOptions p = opts.getPutOption();
		
		//String remoteDirectory = "plots" + toExport.getAbsolutePath().replaceAll(SystemConfig.getRootDir(), "").replaceAll("/dailyTemp", "");
		String remoteDirectory = "riki/" + toExport.getName();
		remoteDirectory = remoteDirectory.substring(0, remoteDirectory.lastIndexOf("/"));
		
		System.out.println("remoteDirectory string: " + remoteDirectory);
		logger.info("remoteDirectory string: " + remoteDirectory);
		IRODSFile remoteDir = null;
		try {
			remoteDir = fileFactory.instanceIRODSFile(remoteDirectory);
			logger.info("Created remoteDir: " + remoteDir.getAbsolutePath());
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
}
