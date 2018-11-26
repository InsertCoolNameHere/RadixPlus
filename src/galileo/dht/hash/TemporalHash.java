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
package galileo.dht.hash;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

import galileo.comm.TemporalType;
import galileo.dataset.Metadata;
import galileo.dataset.TemporalProperties;

public class TemporalHash implements HashFunction<Metadata> {
	public static final TimeZone TIMEZONE = TimeZone.getTimeZone("GMT");
	private Random random = new Random();
	private int temporalType;
	
	/**
	 * @param temporalType:
	 * Must be one of the constants of java.util.Calendar
	 * @throws HashException
	 * If temporalType does not match the one of the supported constants.
	 */
	public TemporalHash(TemporalType tType) throws HashException {
		this.temporalType = tType.getType();
		List<Integer> temporalTypes = Arrays.asList(new Integer[] { Calendar.DAY_OF_MONTH, Calendar.DAY_OF_WEEK,
				Calendar.DAY_OF_YEAR, Calendar.HOUR, Calendar.HOUR_OF_DAY, Calendar.WEEK_OF_MONTH,
				Calendar.WEEK_OF_YEAR, Calendar.MONTH, Calendar.YEAR });
		if (!temporalTypes.contains(temporalType)) {
			throw new HashException("Unsupported temporal type for hashing.");
		}
	}

	@Override
	public BigInteger hash(Metadata data) throws HashException {
		TemporalProperties temporalProps = data.getIntervalStartEnd();
		Calendar c = Calendar.getInstance();
		c.setTimeZone(TIMEZONE);
		c.setTimeInMillis(temporalProps.getStart());
		switch (this.temporalType) {
		case Calendar.DAY_OF_MONTH:
			return BigInteger.valueOf(c.get(Calendar.DAY_OF_MONTH));
		case Calendar.DAY_OF_WEEK:
			return BigInteger.valueOf(c.get(Calendar.DAY_OF_WEEK));
		case Calendar.DAY_OF_YEAR:
			return BigInteger.valueOf(c.get(Calendar.DAY_OF_YEAR));
		case Calendar.HOUR:
			return BigInteger.valueOf(c.get(Calendar.HOUR));
		case Calendar.HOUR_OF_DAY:
			return BigInteger.valueOf(c.get(Calendar.HOUR_OF_DAY));
		case Calendar.WEEK_OF_MONTH:
			return BigInteger.valueOf(c.get(Calendar.WEEK_OF_MONTH));
		case Calendar.WEEK_OF_YEAR:
			return BigInteger.valueOf(c.get(Calendar.WEEK_OF_YEAR));
		case Calendar.MONTH:
			return BigInteger.valueOf(c.get(Calendar.MONTH));
		case Calendar.YEAR:
			return BigInteger.valueOf(c.get(Calendar.YEAR) % 100);
		default:
			throw new HashException("Unsupported temporal type for hashing.");
		}
	}

	@Override
	public BigInteger maxValue() {
		switch (this.temporalType) {
		case Calendar.DAY_OF_MONTH:
			return BigInteger.valueOf(31);
		case Calendar.DAY_OF_WEEK:
			return BigInteger.valueOf(7);
		case Calendar.DAY_OF_YEAR:
			return BigInteger.valueOf(366);
		case Calendar.HOUR:
			return BigInteger.valueOf(12);
		case Calendar.HOUR_OF_DAY:
			return BigInteger.valueOf(24);
		case Calendar.WEEK_OF_MONTH:
			return BigInteger.valueOf(5);
		case Calendar.WEEK_OF_YEAR:
			return BigInteger.valueOf(53);
		case Calendar.MONTH:
			return BigInteger.valueOf(12);
		case Calendar.YEAR:
			return BigInteger.valueOf(100);
		default:
			return BigInteger.valueOf(31);
		}
	}

	@Override
	public BigInteger randomHash() {
		switch (this.temporalType) {
		case Calendar.DAY_OF_MONTH:
			return BigInteger.valueOf(random.nextInt(31));
		case Calendar.DAY_OF_WEEK:
			return BigInteger.valueOf(random.nextInt(7));
		case Calendar.DAY_OF_YEAR:
			return BigInteger.valueOf(random.nextInt(366));
		case Calendar.HOUR:
			return BigInteger.valueOf(random.nextInt(12));
		case Calendar.HOUR_OF_DAY:
			return BigInteger.valueOf(random.nextInt(24));
		case Calendar.WEEK_OF_MONTH:
			return BigInteger.valueOf(random.nextInt(5));
		case Calendar.WEEK_OF_YEAR:
			return BigInteger.valueOf(random.nextInt(53));
		case Calendar.MONTH:
			return BigInteger.valueOf(random.nextInt(12));
		case Calendar.YEAR:
			return BigInteger.valueOf(random.nextInt(100));
		default:
			return BigInteger.valueOf(random.nextInt(31));
		}
	}
}
