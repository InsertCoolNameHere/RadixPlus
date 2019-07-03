package galileo.util;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class GenerateRandomUAVData {

	public static String baseString = "1561653641000, 1, 1, -0.846472414288414, 8.97106380160182, 6.0128344864391, 7.38293442763936, 7.76533430293361, 1.4950772186001, 500336.250000001, 4500329.37, 40.6531105521, -104.99567119, -1.24468544911341, -0.435872245337225";
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		extractBaseValues();
		getRandomRows();
	}
	
	public static String[] baseValues;
 	
	public static void extractBaseValues() {
		
		baseValues = baseString.split(",");
		
	}
	
	public static void getRandomRows() {
		
		String firstStr = Arrays.toString(baseValues);
		
		//System.out.println(firstStr.substring(1,firstStr.length() - 1));
		
		for(int i=0; i< 5; i++) {
			String newLine = "";
			int k = 0;
			for(String s: baseValues) {
				
				String val = "";
				
				if(k <= 2) {
					val = s;
				} else if((k > 2 && k <= 8)|| (k > 12 && k<15)) {
					 double actVal = Double.valueOf(s);
					 
					 actVal+=ThreadLocalRandom.current().nextDouble(0d, 0.0000007d);
					 val+=actVal;
					 
				} else if(k ==11) {
					 double actVal = Double.valueOf(s);
					 
					 actVal = ThreadLocalRandom.current().nextDouble(40.6531005675d, 40.6531274303d);
					 val+=actVal;
				} else if(k ==12) {
					 double actVal = Double.valueOf(s);
					 
					 actVal = ThreadLocalRandom.current().nextDouble(-104.995675566d, -104.995671774d);
					 val+=actVal;
				} else if(k > 8 && k < 13) {
					 double actVal = Double.valueOf(s);
					 
					 actVal+=ThreadLocalRandom.current().nextDouble(0d, 0.0000005d);
					 val+=actVal;
				}
				
				
				newLine+=val+",";
				k++;
			}
			System.out.println(newLine.substring(0,newLine.length()-1));
		}
	}

}
