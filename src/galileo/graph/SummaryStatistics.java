package galileo.graph;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

import org.json.JSONObject;

import galileo.serialization.ByteSerializable;
import galileo.serialization.SerializationException;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.SerializationOutputStream;

public class SummaryStatistics implements ByteSerializable{
	
	private static final Logger logger = Logger.getLogger("galileo");
	
	private int count;
	private double max;
	private double min;
	private double avg;
	private double stdDev;
	
	private boolean resolved = false;
	
	public SummaryStatistics() {
		count = 0;
		max = 0;
		min = 0;
		avg = 0;
		stdDev = 0;
		resolved = false;
	}
	
	public int getCount() {
		return count;
	}
	public void setCount(int count) {
		this.count = count;
	}
	
	public void increaseCount() {
		this.count++;
	}
	
	public void increaseCount(int cnt) {
		this.count+= cnt;
	}
	public double getMax() {
		return max;
	}
	public void setMax(double max) {
		this.max = max;
	}
	public double getMin() {
		return min;
	}
	public void setMin(double min) {
		this.min = min;
	}
	public double getAvg() {
		return avg;
	}
	public void setAvg(double avg) {
		this.avg = avg;
	}
	public double getStdDev() {
		return stdDev;
	}
	public void setStdDev(double tmpSum) {
		this.stdDev = tmpSum;
	}
	
	public void updateStdDev(double val) {
		this.stdDev += val;
	}
	
	public boolean isResolved() {
		return resolved;
	}
	public void setResolved(boolean resolved) {
		this.resolved = resolved;
	}
	
	public void calculateAvg() {
		this.avg = stdDev/count;
	}

	/**
	 * 
	 * @author sapmitra
	 * @param oldStats
	 * @param statsUpdate
	 * @return
	 */
	public static SummaryStatistics[] mergeSummaries(SummaryStatistics[] oldStats, SummaryStatistics[] statsUpdate) {
		
		//logger.info("RIKI: MERGE SUMMARY CALLED HERE");
		
		SummaryStatistics[] newStats = new SummaryStatistics[oldStats.length];
		for(int i=0; i< oldStats.length; i++) {
			
			SummaryStatistics old = oldStats[i];
			SummaryStatistics upd = statsUpdate[i];
			
			SummaryStatistics newstat = mergeSummary(old, upd);
			newStats[i] = newstat;
		}
		return newStats;
	}
	
	/**
	 * NOT FULL MERGING, ATTRIBUTES SUCH AS AVG ARE LEFT OUT AND ONLY TOTAL AND COUNTE ARE UPDATED
	 * @author sapmitra
	 * @param oldStats
	 * @param statsUpdate
	 * @return
	 */
	public static SummaryStatistics[] preMergeSummaries(SummaryStatistics[] oldStats, SummaryStatistics[] statsUpdate) {
		
		SummaryStatistics[] newStats = new SummaryStatistics[oldStats.length];
		for(int i=0; i< oldStats.length; i++) {
			
			SummaryStatistics old = oldStats[i];
			SummaryStatistics upd = statsUpdate[i];
			
			SummaryStatistics newstat = preMergeSummary(old, upd);
			newStats[i] = newstat;
		}
		return newStats;
	}
	
	/**
	 * 
	 * @author sapmitra
	 * @param old
	 * @param upd
	 * @return
	 */
	public static SummaryStatistics mergeSummary(SummaryStatistics old, SummaryStatistics upd) {
		if(old == null && upd == null) {
			logger.info("RIKI: NOT SUPPOSED TO HAPPEN");
			return null;
		}
		if(upd == null)
			return old;
		if(old == null)
			return upd;
		
		if(old.getMax() < upd.getMax()) {
			old.setMax(upd.getMax());
		}
		if(old.getMin() < upd.getMin()) {
			old.setMin(upd.getMin());
		}
		
		// UPDATING AVERAGE
		
		double oldCount = (double)old.count;
		double updCount = (double)upd.count;
		
		double newTotal = oldCount + updCount;
		double newAvg = (oldCount*old.avg + updCount*upd.avg)/(oldCount+updCount);
		
		
		
		// UPDATING STANDARD DEVIATION - BESSEL CORRECTED
		
		// VARIANCE
		double updV = Math.pow(upd.stdDev, 2);
		double oldV = Math.pow(old.stdDev, 2);
		
		double firstTerm = updV*(updCount-1) + oldV*(oldCount-1);
		double secondTerm = oldCount*Math.pow((newAvg-old.avg), 2) + updCount*Math.pow((newAvg-upd.avg), 2);
		
		double newVar = (firstTerm+secondTerm)/(newTotal-1);
		double newSD = Math.pow(newVar, 0.5);
		old.setStdDev(newSD);
		
		old.setAvg(newAvg);
		old.setCount((int)newTotal);
		
		return old;
		
	}
	
	public static SummaryStatistics preMergeSummary(SummaryStatistics old, SummaryStatistics upd) {
		
		if(old.getMax() < upd.getMax()) {
			old.setMax(upd.getMax());
		}
		if(old.getMin() < upd.getMin()) {
			old.setMin(upd.getMin());
		}
		old.updateStdDev(upd.getStdDev());
		old.increaseCount(upd.getCount());
		
		return old;
		
	}
	
	@Override
	public String toString() {
		String retStr = "min,max,avg,count,stdDev\n"+min+","+max+","+avg+","+count+","+stdDev;
		return retStr;
	}
	
	public JSONObject toJson() {
		
		JSONObject myobj = new JSONObject();
		myobj.put("min", min);
		myobj.put("max", max);
		myobj.put("avg", avg);
		myobj.put("count", count);
		myobj.put("stdDev", stdDev);
		
		return myobj;
	}

	
	@Override
	public void serialize(SerializationOutputStream out) throws IOException {
		
		out.writeInt(count);
		out.writeDouble(max);
		out.writeDouble(min);
		out.writeDouble(avg);
		out.writeDouble(stdDev);
		
	}
	
	@Deserialize
	public SummaryStatistics(SerializationInputStream in) throws IOException, SerializationException {
		
		this.count = in.readInt();
		this.max = in.readDouble();
		this.min = in.readDouble();
		this.avg = in.readDouble();
		this.stdDev = in.readDouble();
		
	}
	
	
	public JSONObject getJsonRepresentation() {
		
		JSONObject summary = new JSONObject();
		
		//logger.info("RIKI: WO SUMMARY: "+toString());
		summary.put("count", count);
		summary.put("max",max);
		summary.put("min",min);
		summary.put("avg",avg);
		summary.put("stdDev",stdDev);
		
		return summary;
	}
	
	public void populateObject(JSONObject jsonObj) {
		
		this.count = jsonObj.getInt("count");
		this.max = jsonObj.getDouble("max");
		this.min = jsonObj.getDouble("min");
		this.avg = jsonObj.getDouble("avg");
		this.stdDev = jsonObj.getDouble("stdDev");
		
	}

}
