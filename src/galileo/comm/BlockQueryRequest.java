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
package galileo.comm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;

import galileo.dataset.Coordinates;
import galileo.event.Event;
import galileo.query.Expression;
import galileo.query.Operation;
import galileo.query.Operator;
import galileo.query.Query;
import galileo.serialization.SerializationException;
import galileo.serialization.SerializationInputStream;
import galileo.serialization.SerializationOutputStream;

/**
 * Encapsulates query information submitted by clients to be processed by
 * StorageNodes.
 *
 * @author malensek
 */
public class BlockQueryRequest implements Event {

	private String fsName;
	private Query metadataQuery;
	private List<Coordinates> polygon;
	private String time;
	
	private void validate(String fsName) {
		if (fsName == null || fsName.trim().length() == 0 || !fsName.matches("[a-z0-9-]{5,50}"))
			throw new IllegalArgumentException("invalid filesystem name");
		this.fsName = fsName;
	}

	private void validate(Query query) {
		if (query == null || query.getOperations().isEmpty())
			throw new IllegalArgumentException("illegal query. must have at least one operation");
		Operation operation = query.getOperations().get(0);
		if (operation.getExpressions().isEmpty())
			throw new IllegalArgumentException("no expressions found for an operation of the query");
		Expression expression = operation.getExpressions().get(0);
		if (expression.getOperand() == null || expression.getOperand().trim().length() == 0
				|| expression.getOperator() == Operator.UNKNOWN || expression.getValue() == null)
			throw new IllegalArgumentException("illegal expression for an operation of the query");
	}

	public void setMetdataQuery(Query query) {
		validate(query);
		this.metadataQuery = query;
	}

	public void setPolygon(List<Coordinates> polygon) {
		if (polygon == null)
			throw new IllegalArgumentException("Spatial coordinates cannot be null");
		this.polygon = polygon;
	}

	public void setTime(String time) {
		if (time != null) {
			if (time.length() != 13)
				throw new IllegalArgumentException(
						"time must be of the form yyyy-mm-dd-hh with missing values replaced as x");
			this.time = time;
		}
	}

	public BlockQueryRequest(String fsName, List<Coordinates> polygon, Query metadataQuery, String time) {
		validate(fsName);
		setPolygon(polygon);
		setTime(time);
		if (metadataQuery == null)
			throw new IllegalArgumentException("Atleast one of the queries must be present");
		
		if (metadataQuery != null)
			setMetdataQuery(metadataQuery);
	}


	public String getFilesystemName() {
		return this.fsName;
	}
	
	public Query getMetadataQuery() {
		return this.metadataQuery;
	}

	public String getTime() {
		return this.time;
	}

	public List<Coordinates> getPolygon() {
		if (this.polygon == null)
			return null;
		return Collections.unmodifiableList(this.polygon);
	}

	public boolean isSpatial() {
		return polygon != null;
	}

	public boolean isTemporal() {
		return time != null;
	}
	
	public boolean hasMetadataQuery() {
		return this.metadataQuery != null;
	}

	public String getMetadataQueryString() {
		if (this.metadataQuery != null)
			return metadataQuery.toString();
		return "";
	}

	private static Logger logger = Logger.getLogger("galileo");
	
	@Deserialize
	public BlockQueryRequest(SerializationInputStream in) throws IOException, SerializationException {
		fsName = in.readString();
		
		boolean isTemporal = in.readBoolean();
		if (isTemporal)
			time = in.readString();
		
		boolean isSpatial = in.readBoolean();
		if (isSpatial) {
			List<Coordinates> poly = new ArrayList<Coordinates>();
			in.readSerializableCollection(Coordinates.class, poly);
			polygon = poly;
		}
		
		boolean hasMetadataQuery = in.readBoolean();
		if (hasMetadataQuery)
			this.metadataQuery = new Query(in);
		
	}

	@Override
	public void serialize(SerializationOutputStream out) throws IOException {
		out.writeString(fsName);
		out.writeBoolean(isTemporal());
		
		if (isTemporal())
			out.writeString(time);
		out.writeBoolean(isSpatial());
		logger.info("isSpatial? " + isSpatial());
		if (isSpatial())
			out.writeSerializableCollection(polygon);
		
		out.writeBoolean(hasMetadataQuery());
		if (hasMetadataQuery())
			out.writeSerializable(this.metadataQuery);
		
	}

}