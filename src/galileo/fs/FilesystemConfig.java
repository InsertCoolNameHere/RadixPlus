package galileo.fs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import galileo.serialization.ByteSerializable;
import galileo.serialization.SerializationException;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.SerializationOutputStream;

public class FilesystemConfig implements ByteSerializable{
	
	// MANDATORY FIELDS
	private String[] allGeohashes;
	
	private String allAttributes;
	
	private String gridFilePath = "";
	private String baseHashForGrid;
	private String nw;
	private String se;
	private int hashGridPrecision;
	
	private String dateFormat = "X";
	private boolean isTimeTimestamp = true;
	
	// OPTIONAL FIELD
	// Map from SensorType to Corresponding Index
	// Indices: LAT,LON,TIME,DATA
	private Map<String, int[]> indicesMap;
	
	
	@Override
	public void serialize(SerializationOutputStream out) throws IOException {
		
		out.writeString(baseHashForGrid);
		out.writeString(gridFilePath);
		out.writeString(nw);
		out.writeString(se);
		out.writeInt(hashGridPrecision);
		out.writeString(allAttributes);
		out.writeBoolean(isTimeTimestamp);
		out.writeString(dateFormat);
		
		List<String> strings = new ArrayList<String>();
		if(allGeohashes != null && allGeohashes.length > 0)
			strings = Arrays.asList(allGeohashes);
		out.writeStringCollection(strings);
		
		if(indicesMap.size() > 0) {
			out.writeBoolean(true);
			out.writeStringCollection(indicesMap.keySet());
			
			List<String> indices = new ArrayList<String>();
			for(int[] vals : indicesMap.values()) {
				String indStr = "";
				
				for(int v: vals)
					indStr+=v+",";
				
				indices.add(indStr);
			}
			out.writeStringCollection(indices);
			
		} else {
			out.writeBoolean(false);
		}
	}
	
	@Deserialize
	public FilesystemConfig(SerializationInputStream in) throws IOException, SerializationException {
		
		this.baseHashForGrid = in.readString();
		this.gridFilePath = in.readString();
		this.nw = in.readString();
		this.se = in.readString();
		this.hashGridPrecision = in.readInt();
		this.allAttributes = in.readString();
		this.isTimeTimestamp = in.readBoolean();
		this.dateFormat = in.readString();
		
		List<String> strings = new ArrayList<String>();
		in.readStringCollection(strings);
		allGeohashes = strings.toArray(new String[0]);
		
		boolean hasIndices = in.readBoolean();
		
		if(hasIndices) {
			List<String> keys = new ArrayList<String>();
			in.readStringCollection(keys);
			
			List<String> valsStr = new ArrayList<String>();
			in.readStringCollection(valsStr);
			
			List<int[]> vals = new ArrayList<int[]>();
			for(String vs : valsStr) {
				String[] tokens = vs.split(",");
				int[] indices = new int[tokens.length];
				
				int i=0;
				for(String t: tokens) {
					indices[i] = Integer.valueOf(t);
					i++;
				}
				vals.add(indices);
				
			}
			
			indicesMap = new HashMap<String, int[]>();
			for(int i=0; i< keys.size(); i++) {
				indicesMap.put(keys.get(i), vals.get(i));
			}
			
		}
		
	}
	
	

	public String[] getAllGeohashes() {
		return allGeohashes;
	}

	public void setAllGeohashes(String[] allGeohashes) {
		this.allGeohashes = allGeohashes;
	}

	public String getBaseHashForGrid() {
		return baseHashForGrid;
	}

	public FilesystemConfig() {}
	
	public FilesystemConfig(String[] allGeohashes, String baseHashForGrid, String nw, String se,
			Map<String, int[]> indicesMap, int gridPrecision, String gridFilePath, String json) {
		super();
		this.gridFilePath = gridFilePath;
		this.allGeohashes = allGeohashes;
		this.baseHashForGrid = baseHashForGrid;
		this.nw = nw;
		this.se = se;
		this.indicesMap = indicesMap;
		this.hashGridPrecision = gridPrecision;
		this.allAttributes = json;
	}
	
	
	
	

	public void setBaseHashForGrid(String baseHashForGrid) {
		this.baseHashForGrid = baseHashForGrid;
	}

	public String getNw() {
		return nw;
	}

	public void setNw(String nw) {
		this.nw = nw;
	}

	public String getSe() {
		return se;
	}

	public void setSe(String se) {
		this.se = se;
	}

	public Map<String, int[]> getIndicesMap() {
		return indicesMap;
	}

	public void setIndicesMap(Map<String, int[]> indicesMap) {
		this.indicesMap = indicesMap;
	}
	
	
	public JSONObject getJsonRepresentation() {
		JSONObject conParams = new JSONObject();
		
		conParams.put("gridFilePath", gridFilePath);
		conParams.put("baseHashForGrid", baseHashForGrid);
		conParams.put("nw",nw);
		conParams.put("se",se);
		conParams.put("hashGridPrecision", hashGridPrecision);
		conParams.put("isTimeTimestamp",isTimeTimestamp);
		conParams.put("dateFormat",dateFormat);
		
		conParams.put("allAttributes", allAttributes);
		
		conParams.put("allGeohashes", Arrays.asList(allGeohashes));
		
		conParams.put("indicesMap", indicesMap);
		
		
		return conParams;
	}
	
	public void populateObject(JSONObject jsonObj) {
		
		this.gridFilePath = jsonObj.getString("gridFilePath");
		this.baseHashForGrid = jsonObj.getString("baseHashForGrid");
		this.nw = jsonObj.getString("nw");
		this.se = jsonObj.getString("se");
		this.hashGridPrecision = jsonObj.getInt("hashGridPrecision");
		this.isTimeTimestamp = jsonObj.getBoolean("isTimeTimestamp");
		this.dateFormat = jsonObj.getString("dateFormat");
		
		this.allAttributes = jsonObj.getString("allAttributes");
		
		JSONArray geoEntries = jsonObj.getJSONArray("allGeohashes");
		
		allGeohashes = new String[geoEntries.length()];
		
		for (int i = 0; i < geoEntries.length(); i++)
			allGeohashes[i] = geoEntries.getString(i);
		
		indicesMap = new HashMap<String, int[]>();
		JSONObject hmap = jsonObj.getJSONObject("indicesMap");
		
		
		Iterator<?> keys = hmap.keys();

		while (keys.hasNext()) {
			String key = (String) keys.next();
			JSONArray indices = hmap.getJSONArray(key);
			
			int[] indics = new int[indices.length()];
			
			for (int i = 0; i < indices.length(); i++)
				indics[i] = indices.getInt(i);
			
			
			indicesMap.put(key, indics);

		}
		
	}
	
	
	public static void main(String arg[]) {
		
		
		String s1 = "this*that";
		
		System.out.println(s1.split("\\*").length);
		
		
	}
	
	public int[] getIndices(String sensorName) {
		
		int[] indices = indicesMap.get(sensorName);
		return indices;
		
	}

	public int getHashGridPrecision() {
		return hashGridPrecision;
	}

	public void setHashGridPrecision(int hashGridPrecision) {
		this.hashGridPrecision = hashGridPrecision;
	}

	public String getGridFilePath() {
		return gridFilePath;
	}

	public void setGridFilePath(String gridFilePath) {
		this.gridFilePath = gridFilePath;
	}
	
	public int getLatIndex(String sensorType) {
		if(indicesMap.get(sensorType)!=null) {
			return indicesMap.get(sensorType)[0];
		}
		
		return -1;
	}
	
	public int getLonIndex(String sensorType) {
		if(indicesMap.get(sensorType)!=null) {
			return indicesMap.get(sensorType)[1];
		}
		
		return -1;
	}
	
	public int getTemporalIndex(String sensorType) {
		if(indicesMap.get(sensorType)!=null) {
			return indicesMap.get(sensorType)[2];
		}
		
		return -1;
	}
	
	public int getDataIndex(String sensorType) {
		if(indicesMap.get(sensorType)!=null) {
			return indicesMap.get(sensorType)[3];
		}
		
		return -1;
	}

	public String getAllAttributes() {
		return allAttributes;
	}
	
	public JSONArray getAllAttributesJson() {
		String[] ats = allAttributes.split("\\*");
		
		
		JSONArray features = new JSONArray();
    	for(String e : ats){
    		
    		features.put(e);
    	}
    	return features;
	}

	
	public void setAllAttributes(String htmlJson) {
		this.allAttributes = htmlJson;
	}

	public String getDateFormat() {
		return dateFormat;
	}

	public void setDateFormat(String dateFormat) {
		this.dateFormat = dateFormat;
	}

	public boolean isTimeTimestamp() {
		return isTimeTimestamp;
	}

	public void setTimeTimestamp(boolean isTimeTimestamp) {
		this.isTimeTimestamp = isTimeTimestamp;
	}
	
}
