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
package galileo.util;

import java.util.List;

public class Math {
	public static Float getFloat(String number) {
		try {
			return Float.parseFloat(number);
		} catch (Exception e) {
			return Float.NaN;
		}
	}
	
	public static Integer getInteger(String number){
		try {
			return Integer.parseInt(number);
		} catch (Exception e) {
			return 0;
		}
	}
	
	public static Long getLong(String number){
		try {
			return Long.parseLong(number);
		} catch (Exception e) {
			return 0l;
		}
	}
	
	public static Double getDouble(String number){
		try {
			return Double.parseDouble(number);
		} catch (Exception e) {
			return Double.NaN;
		}
	}
	
	public static Double computeAvg(List<Double> list) {
		double sum = 0;
		for (double d : list)
			sum += d;
		return sum/list.size();
	}
	
	public static Double computeStdDev(List<Double> list) {
		double avg = computeAvg(list);
		double squareDiff = 0;
		for (double d : list) 
			squareDiff += ((d-avg) * (d-avg));
		squareDiff /= list.size();
		return java.lang.Math.sqrt(squareDiff);
	}

}
