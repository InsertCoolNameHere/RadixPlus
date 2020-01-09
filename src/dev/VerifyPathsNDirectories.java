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

import galileo.dataset.feature.FeatureType;
import galileo.dht.IRODSManager;
import galileo.fs.GeospatialFileSystem;
import galileo.integrity.RadixIntegrityGraph;
import galileo.util.Pair;

public class VerifyPathsNDirectories {
	
	// ================CONFIG START=====================
	public String fsName = "";
	
	// BASE DIRECTORY OF IRODS
	public static String irodsBase = IRODSManager.IRODS_BASE;
	// THE DIRECTORY WHERE THE FILES GET DOWNLOADED
	public static String fsBase = "/s/chopin/b/grad/sapmitra/Documents/radix/";
	
	// THE FULL PATH TO THE DIRECTORY WHOSE CHECKSUM VALUE WE HAVE
	public String fsActualPath = "";
	
	public String separator = File.separator;
	
	// COMPLETE FEATURE LIST
	private List<Pair<String, FeatureType>> featureList = populateFeatures();
	
	/**
	 * @param string
	 * @param string2 
	 */
	public VerifyPathsNDirectories(String string, String string2) {
		
		fsBase = string;
		fsName = string2;
	}

	// ================CONFIG START=====================
	
	
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
	
	/**
	 * 
	 * @param fullIrodsDirPath - FULL IRODS PATH TO THE DIRECTORY WHOSE CRC WE HAVE
	 * @return
	 * @throws IOException
	 */
	public boolean validateDirectories(String fullIrodsDirPath) throws IOException {
		
		String tokens[] = fullIrodsDirPath.split("\\$\\$");
		long checkVal = Long.valueOf(tokens[1]);
		fullIrodsDirPath = tokens[0];
		
		String irodsBaseForThisFS = irodsBase+IRODSManager.IRODS_SEPARATOR+fsName;
		String relativePath = fullIrodsDirPath.substring(irodsBaseForThisFS.length());
		String fullGalileoDirPath = fsBase+relativePath.replace(IRODSManager.IRODS_SEPARATOR, IRODSManager.GALILEO_SEPARATOR);
		
		int numFeatures = getNumFeatures(relativePath);
		List<Pair<String, FeatureType>> relFeatures = getRelevantFeatures(numFeatures);
		
		List<String> paths = listFileTree(new File(fullGalileoDirPath));
		
		RadixIntegrityGraph rig = new RadixIntegrityGraph(relFeatures, fullIrodsDirPath, fsName);


		for(String p : paths) {
			
			p = p.substring(fsBase.length());
			System.out.println(p);
			
			rig.addPath(irodsBase+p.replace(IRODSManager.GALILEO_SEPARATOR, IRODSManager.IRODS_SEPARATOR));
		}
		
		rig.updatePathsIntoRIG();
		
		if(rig.hrig.getRoot().hashValue == checkVal)
			return true;
		
		return false;
	}
	
	
	public int getNumFeatures(String relativePath) {
		
		if(relativePath == null || relativePath.trim().length() == 0)
			return 0;
		
		String temp = relativePath;
		int cnt = 0;
		
		while(true) {
			if(temp.contains(IRODSManager.IRODS_SEPARATOR)) {
				int indx = temp.indexOf(IRODSManager.IRODS_SEPARATOR);
				temp = temp.substring(indx+1);
				cnt++;
			} else {
				break;
			}
		}
		
		return cnt;
	}
	
	
	public static List<String> listFileTree(File dir) {
	    List<String> fileTree = new ArrayList<String>();
	    
	    if(dir==null||dir.listFiles()==null){
	        return fileTree;
	    }
	    for (File entry : dir.listFiles()) {
	        if (entry.isFile() && entry.getName().endsWith(".gblock")) {
	        	
	        	try {
					long val = RadixIntegrityGraph.getChecksumFromFilepath(entry.getAbsolutePath());
					fileTree.add(entry.getAbsolutePath()+"$$"+val);
				} catch (IOException e) {
					e.printStackTrace();
				}
	        	
	        } else 
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
		/*
		/iplant/home/radix_subterra/roots-arizona/20420/2018/9/28/irt/20420-2018-9-28-irt.gblock$$2550387965
		/iplant/home/radix_subterra/roots-arizona/20404/2018/9/28/irt/20404-2018-9-28-irt.gblock$$1043362283
		/iplant/home/radix_subterra/roots-arizona/20403/2018/9/28/irt/20403-2018-9-28-irt.gblock$$3373363902
		/iplant/home/radix_subterra/roots-arizona/20419/2018/9/28/irt/20419-2018-9-28-irt.gblock$$3522680225
		*/
		
		VerifyPathsNDirectories vpd = new VerifyPathsNDirectories("D:\\radix\\testdir", "roots-arizona");
		//vpd.validateDirectories("/testdir/");
		vpd.validateDirectories("/iplant/home/radix_subterra/roots-arizona$$2550387965");

	}
	
	
	public List<Pair<String, FeatureType>> populateFeatures() {
		
		List<Pair<String, FeatureType>> featureList = new ArrayList<>();
		
		// FEATURELIST IS PROBABLY NOT USED AT ALL
		
		featureList.add(new Pair<>("plotID", FeatureType.INT));
		
		featureList.add(new Pair<>(GeospatialFileSystem.TEMPORAL_YEAR_FEATURE, FeatureType.INT));
		featureList.add(new Pair<>(GeospatialFileSystem.TEMPORAL_MONTH_FEATURE, FeatureType.INT));
		featureList.add(new Pair<>(GeospatialFileSystem.TEMPORAL_DAY_FEATURE, FeatureType.INT));
		
		featureList.add(new Pair<>("sensorType", FeatureType.STRING));
		
		return featureList;
	}

	/**
	 * GET THE PARTIAL FEATURES LIST THAT THIS DIRECTORY CONTAINS
	 * @return 
	 */
	public List<Pair<String, FeatureType>> getRelevantFeatures(int offset) {
		
		return featureList.subList(offset, featureList.size());
		
	}
	
	
}
