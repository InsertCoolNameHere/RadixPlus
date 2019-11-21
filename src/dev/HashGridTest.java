package dev;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Scanner;

import galileo.bmp.BitmapException;
import galileo.bmp.HashGrid;
import galileo.bmp.HashGridException;
import galileo.dataset.Coordinates;

public class HashGridTest {
	
	public static void main(String arg[]) throws HashGridException, IOException, BitmapException {
		HashGrid globalGrid = new HashGrid("9tbkh4", 11, "9tbkh4bpbpb", "9tbkh4pbpbp");
		// THIS READS THE PLOTS.JSON FILE AND MARKS THE PLOTS ON THE HASHGRID
		//globalGrid.initGrid(pathToGridFile+File.separator+gridFiles[0].getName());
		globalGrid.initGrid("/s/chopin/b/grad/sapmitra/Documents/arizona/cleanData/Roots_2018/F2 TRoots Planting/plots_arizona.json");
		//33.061608147249885, -111.96941108807584
		// FAILING
		
		FileInputStream inputStream = null;
		Scanner sc = null;
			
		inputStream = new FileInputStream("/s/chopin/b/grad/sapmitra/Desktop/irt_small.csv");
		sc = new Scanner(inputStream);
		
		int i=0;
		int inv = 0;
		while (sc.hasNextLine()) {
			
			String line = sc.nextLine();
			
			String tokens[] = line.split(",");
			
			double lat = Double.valueOf(tokens[2]);
			double lon = Double.valueOf(tokens[3]);
			
			Coordinates c = new Coordinates(lon,lat);
			//Coordinates c = new Coordinates(33.061783, -111.969358);
			//Coordinates c = new Coordinates(33.061571, -111.969501);
			int plotID = globalGrid.locatePoint(c);
			
			i++;
			if(plotID < 0) {
				System.out.println(i+" "+plotID);
				inv++;
			}
				
			
		}
		System.out.println(inv);
		sc.close();
		inputStream.close();
		
		
		
		/*
		
		Coordinates c = new Coordinates(33.06170855691658,-111.9693655800873);
		//Coordinates c = new Coordinates(33.061783, -111.969358);
		//Coordinates c = new Coordinates(33.061571, -111.969501);
		int plotID = globalGrid.locatePoint(c);
		
		
		System.out.println(plotID);*/
	}

}



