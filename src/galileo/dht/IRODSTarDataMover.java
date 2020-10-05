package galileo.dht;

import java.io.File;

import org.irods.jargon.core.exception.JargonException;

import galileo.config.SystemConfig;

public class IRODSTarDataMover {
	
	public static IRODSManager subterra = new IRODSManager();
	public static void main(String[] args) {
		
		String tars = "roots-arizona-2018_lattice-12_1601527921.tar,roots-arizona-2018_lattice-16_1601527860.tar,roots-arizona-2018_lattice-19_1601527875.tar,roots-arizona-2018_lattice-6_1601527815.tar,roots-arizona-2018_lattice-8_1601527890.tar,roots-arizona-2018_lattice-14_1601527845.tar,roots-arizona-2018_lattice-17_1601527905.tar,roots-arizona-2018_lattice-20_1601527936.tar,roots-arizona-2018_lattice-7_1601527830.tar,roots-arizona-2018_lattice-9_1601527951.tar";
		// TODO Auto-generated method stub
		String[] filenames = tars.split(",");
		

		String baseDir = "/s/chopin/e/proj/sustain/sapmitra/arizona/tar_data_2018";
		String fsName = "roots-arizona-2018";
		
		for(String fileName : filenames) {
			System.out.println("=========================WORKING WITH "+fileName);
			writeTarFile(baseDir, fileName, fsName);
			System.out.println("=========================DONE WITH "+fileName);
		}
	}
	
	public static void writeTarFile(String dir, String tarName, String fsName) {
		// SEND THE TAR FILE TO IRODS
		String tarfile = dir+IRODSManager.GALILEO_SEPARATOR+tarName;
		
		System.out.println("LOCAL FILE: "+tarfile);
		File tarToExport = new File(tarfile);
		try {
			String irodsTarDumpLocation = IRODSManager.IRODS_BASE+IRODSManager.IRODS_SEPARATOR+"tmptar"+IRODSManager.IRODS_SEPARATOR;
			
			subterra.writeRemoteFileAtSpecificPath(tarToExport, irodsTarDumpLocation);
			// UNTAR TAR FILE ON IRODS SYSTEM
			String src = irodsTarDumpLocation+tarName;
			String dest = IRODSManager.IRODS_BASE;
			
			System.out.println("UNTARRING: "+src+">>>"+dest);
			subterra.untarIRODSFile(src, dest, "");
		} catch (Exception e) {
			System.out.println("RIKI: PROBLEM SENDING TAR TO IRODS.."+ e.getMessage());
		}
		
		System.out.println("RIKI: TARRING & UNTARRING FINISHED");
	}

}
