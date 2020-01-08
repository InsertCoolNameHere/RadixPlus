package dev;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.Adler32;

import org.apache.commons.lang.Validate;

import galileo.dht.IRODSManager;
import galileo.integrity.RadixIntegrityGraph;

public class VerifyPathsNDirectories {
	
	public static String irodsBase = IRODSManager.IRODS_BASE;
	public static String fsBase = "/s/chopin/b/grad/sapmitra/Documents/radix/";
	
	public boolean validatePaths(String pathStr) throws IOException {
		
		String[] tokens = pathStr.split("\\$\\$");
		
		long checkSum = Long.valueOf(tokens[1]);
		String filePath = tokens[0];
		
		
		if(checkSum == getFileCRC(filePath)) {
			return true;
		} else {
			return false;
		}
		
	}
	
	public boolean validateDirectories(String suffixPath) throws IOException {
		String fullDirectoryPath = fsBase+suffixPath;
		
		List<String> paths = listFileTree(new File(fullDirectoryPath));
		RadixIntegrityGraph rig = new RadixIntegrityGraph();
		rig.rootPath = irodsBase+suffixPath;
		for(String p : paths) {
			System.out.println(p);
			rig.addPath(p);
		}
		
		
		
		return false;
	}
	
	
	public static List<String> listFileTree(File dir) {
	    List<String> fileTree = new ArrayList<String>();
	    
	    if(dir==null||dir.listFiles()==null){
	        return fileTree;
	    }
	    for (File entry : dir.listFiles()) {
	        if (entry.isFile() && entry.getName().endsWith(".gblock"))
	        	fileTree.add(entry.getAbsolutePath());
	        else 
	        	fileTree.addAll(listFileTree(entry));
	    }
	    return fileTree;
	}
	 
	
	
	public long getFileCRC(String suffixPath) throws IOException {
		long crc = -1;
		
		Adler32 a1 = new Adler32();
		a1.update(Files.readAllBytes(Paths.get(fsBase+suffixPath)));
		crc = a1.getValue();
		
		return crc;
	}
	

	public static void main(String[] args) throws IOException {
		irodsBase = "/s/chopin/b/grad/sapmitra/Documents/radix";
		VerifyPathsNDirectories vpd = new VerifyPathsNDirectories();
		vpd.validateDirectories("/testdir/");

	}

}
