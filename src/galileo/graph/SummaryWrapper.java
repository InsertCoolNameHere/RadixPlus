package galileo.graph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import galileo.serialization.ByteSerializable;
import galileo.serialization.SerializationException;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.SerializationOutputStream;

public class SummaryWrapper implements ByteSerializable{
	
	// SPECIFIES WHETHER THIS ENTRY NEEDS TO BE PUT/UPDATED IN THE CACHE OR NOT
	private boolean resolvedOnce = false;
	
	private List<SummaryStatistics> stats;
	
	public String toString() {
		
		String sString = "";
		
		int i=0;
		for(SummaryStatistics ss : stats) {
			if(i==0)
				sString = ss.toString();
			else
				sString+=","+ss.toString();
			i++;
		}
		
		return sString;
	}
	
	public SummaryWrapper(boolean resolvedOnce, List<SummaryStatistics> stats) {
		
		this.resolvedOnce = resolvedOnce;
		this.stats = stats;
	}

	public boolean isResolvedOnce() {
		return resolvedOnce;
	}

	public void setResolvedOnce(boolean resolvedOnce) {
		this.resolvedOnce = resolvedOnce;
	}

	public List<SummaryStatistics> getStats() {
		return stats;
	}

	public void setStats(List<SummaryStatistics> stats) {
		this.stats = stats;
	}

	@Override
	public void serialize(SerializationOutputStream out) throws IOException {
		out.writeSerializableCollection(stats);
		
	}
	
	@Deserialize
	public SummaryWrapper(SerializationInputStream in) throws IOException, SerializationException {
		
		stats = new ArrayList<SummaryStatistics>();
		in.readSerializableCollection(SummaryStatistics.class, stats);
		
	}
	
	/**
	 * CALCULATE THE AVERAGES BEFORE BEING SENT OUT
	 * @author sapmitra
	 */
	public void cleanHouse() {
		
		for(SummaryStatistics ss : stats) {
			ss.setResolved(true);
			ss.calculateAvg();
		}
	}
	
	public void addSummary(SummaryStatistics ss) {
		if(stats == null)
			stats = new ArrayList<SummaryStatistics>();
		stats.add(ss);
		
	}
	
	
	
	

}
