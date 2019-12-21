package galileo.bmp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;

import galileo.dataset.Coordinates;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

public class Utility {
	
	public void getCoordinatesFromOffset_backup() {

		//By definition North is 0 deg, East is 90 deg, South is 180 deg and West is 270 deg. North can also be called 360 deg.
		double lat1 = 40.581; double lon1 = -105.0968;
        double azi1 = 270; double s12 = 500;
        GeodesicData g = Geodesic.WGS84.Direct(lat1, lon1, azi1, s12);
        System.out.println(g.lat2 + " " + g.lon2 + " " + g.azi2);

	}
	
	
	public static Coordinates getCoordinatesFromOffset(double lat1, double lon1, double s12, String dir) {

		double azi1 = 0;
		
		//By definition North is 0 deg, East is 90 deg, South is 180 deg and West is 270 deg. North can also be called 360 deg.
		if(dir.equalsIgnoreCase("north")) {
			azi1 = 0;
		} else if(dir.equalsIgnoreCase("south")) {
			azi1 = 180;
		} else if(dir.equalsIgnoreCase("east")) {
			azi1 = 90;
		} else if(dir.equalsIgnoreCase("west")) {
			azi1 = 270;
		}
		
        GeodesicData g = Geodesic.WGS84.Direct(lat1, lon1, azi1, s12);
        
        Coordinates c = new Coordinates(g.lat2, g.lon2);
        //System.out.println(g.lat2 + " " + g.lon2 + " " + g.azi2);
        
        return c;

	}
	
	/**
	 * GET ACTUAL LATLON VALUE FROM DEGREES AND MINUTES
	 * @author sapmitra
	 * @param latfm
	 * @param latmin
	 * @return
	 */
	public static double getLatLonDecimal(String latfm, String latmin) {
		
		String min1 = latfm.substring(latfm.length() - 2);
		
		double min = Double.valueOf(min1+"."+latmin);
		double lat = Double.valueOf(latfm.substring(0, 2));
		
		lat+=(min/60);
		
		//System.out.println(lat);
		
		
		return lat;
	}
	
	/**
	 * 1: NORTH
	 * 2: SOUTH
	 * @author sapmitra
	 * @param angle
	 * @return
	 */
	private static int getHeading(double angle) {
		
		if(angle >= 90 && angle <= 270 ) {
			return 2;
		} else {
			return 1;
		}
	}

	
	// GET TIMESTAMP OUT OF THE TIMESTAMP AND GPSTIME
	public static long getTimestamp(String dateString) throws ParseException {
		
		if(!dateString.contains(".")) {
			dateString+=".0";
		}
		
		/*if(!timeString.contains(".")) {
			timeString+=".0";
		}*/
		/*
		 * DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss.t");
		 * String actualDate = null;
		 */
		
	    Date date1=new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.S").parse(dateString);  
	    //System.out.println(dateString+"\t"+date1);  
	    
	    /*
	    String tokens[] = timeString.split("\\.");
	    
	    String tString = tokens[0];
	    
	    int hour = Integer.valueOf(tString.substring(0,2));
	    int min = Integer.valueOf(tString.substring(2,4));
	    int sec = Integer.valueOf(tString.substring(4,6));
	    int milSec = Integer.valueOf(tokens[1])*100;
	    */
	    
	    Calendar cal = Calendar.getInstance();
	    
	    cal.setTime(date1);
	    
	    /*
	    cal.set(Calendar.HOUR_OF_DAY, hour);
	    cal.set(Calendar.MINUTE, min);
	    cal.set(Calendar.SECOND, sec);
	    cal.set(Calendar.MILLISECOND, milSec);
	    
	    System.out.println(cal.getTime());*/
	    
		return cal.getTimeInMillis();
		
	}
	
	public static void main(String arg[]) throws ParseException {
		
		//getTimestamp("2018-09-28 10:13:29", "171302.4");
		getTimestamp("2018-09-28 10:13:29");
	}
	
	/**
	 * 1,2,3,4 N,S,E,W
	 * @author sapmitra
	 * @param direction
	 * @return
	 */
	public static Coordinates getActualCoordinateOnStarboard(int directionOfTractor, int index, Coordinates baseCoordinates) {
		
		if(directionOfTractor == 1) {
			// NORTH FACING
			
			
		} else {
			// SOUTH FACING
		}
		return null;
		
		
	}
	
	
	public static double getAziAngleFromDirection(String dir) {
		
		double azi1 = 0;
		//By definition North is 0 deg, East is 90 deg, South is 180 deg and West is 270 deg. North can also be called 360 deg.
		if(dir.equalsIgnoreCase("north")) {
			azi1 = 0;
		} else if(dir.equalsIgnoreCase("south")) {
			azi1 = 180;
		} else if(dir.equalsIgnoreCase("east")) {
			azi1 = 90;
		} else if(dir.equalsIgnoreCase("west")) {
			azi1 = 270;
		}
				
		return azi1;
		
		
	}
	
	
	public static double getSideBySideOffset(int sensorNum) {
		
		if(sensorNum == 1 || sensorNum == 4) {
			
			return 1.143;
			
		} else if(sensorNum == 7) {
			// LIDAR 0
			return 0.771;
		} else if(sensorNum == 8) {
			// LIDAR 1
			return 0.765;
		}
			
		return 0.381;
			
		
		
	}
	
	public static double getHorizontalOffset(String sensorType) {
		
		if(sensorType.equals("greenseeker")) {
			
			return 0.2905;
			
		} else if(sensorType.equals("irt")) {
			
			return 0.035;
			
		} else if(sensorType.equals("ultrasonic")) {
			
			return 0.142;
			
		} else if(sensorType.equals("lidar0")) {
			
			return 0.173;
			
		} else if(sensorType.equals("lidar1")) {
			
			return 0.163;
		}
			
		return 0;
		
	}
	
	
	
	
	
	public static Coordinates getActualCoordinates(double latGPS, double lonGPS, double horizontalOffset, double sideBySideOffset, 
			String dirHorizontal, String dirSideBySide) {
		
		double newLat = latGPS;
		double newLon = lonGPS;
		
		Coordinates c1 = getCoordinatesFromOffset(newLat, newLon, horizontalOffset, dirHorizontal);
		
		newLat = c1.getLatitude();
		newLon = c1.getLongitude();
		
		Coordinates c2 = getCoordinatesFromOffset(newLat, newLon, sideBySideOffset, dirSideBySide);
		
		return c2;
		
	}
	
	
	/**
	 * Given an axis and sensorType, get the direction of the azimuth
	 * @author sapmitra
	 * @param directionofMovement : direction of tractor movement -1 or 1
	 * @param sensorType
	 * @param axis
	 * @return
	 */
	public static String getDirection(int directionofMovement, String sensorType, String axis, int sensorNum) {
		// 1 : N to S
		// -1 : S to N
		
		// axis: sidebyside / horizontal
		// HORIZONTAL IS THE FRONT/BACK OFFSET
		
		if(axis.equals("sidebyside")) {
			// SIDE TO SIDE CALCULATION
			
			if(directionofMovement > 0) {
				// SOUTHBOUND
				
				// LIDARS
				if(sensorNum == 7) {
					// LIDAR 0
					return "east";
					
				} else if(sensorNum == 8) {
					// LIDAR 1
					return "west";
				}
				
				if(sensorNum <= 2) {
					return "east";
				} else {
					return "west";
				}
				
			} else {
				// NORTHBOUND
				
				// LIDARS
				if(sensorNum == 7) {
					// LIDAR 0
					return "west";
					
				} else if(sensorNum == 8) {
					// LIDAR 1
					return "east";
				}
				
				if(sensorNum <= 2) {
					return "west";
				} else {
					return "east";
				}
			}
			
		} else {
			// HORIZONTAL OFFSET
			
			if(directionofMovement > 0) {
				
				// SOUTHBOUND
				if(sensorType.contains("lidar")) {
					return "south";
				}
				
				
				if(sensorType.equals("ultrasonic")||sensorType.equals("irt")) {
					return "north";
				} else {
					return "south";
				}
				
			} else {
				
				if(sensorType.contains("lidar")) {
					return "north";
				}
				
				
				// NORTHBOUND
				if(sensorType.equals("ultrasonic")||sensorType.equals("irt")) {
					return "south";
				} else {
					return "north";
				}
			}
			
		}
		
	}
	
	
	
	public static double getActualElevation(String sensorType, double elevation) {
		
		double gs = -0.387;
		double irt = -1.435;
		double us = -1.373;
		double lidar = -0.333;
		
		if(sensorType.equals("greenseeker")) {
			
			return (elevation + gs);
			
		} else if(sensorType.equals("ultrasonic")) {
			
			return (elevation + us);
			
		} else if(sensorType.equals("irt")) {
			
			return (elevation + irt);
			
		} else if(sensorType.contains("lidar")) {
			
			return (elevation + lidar);
			
		}  else {
			
			return (elevation);
			
		}
		
		
	}
	
	
	
}
