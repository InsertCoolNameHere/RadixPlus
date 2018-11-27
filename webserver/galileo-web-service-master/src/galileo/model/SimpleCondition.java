package galileo.model;

import org.json.JSONObject;

import galileo.dataset.feature.Feature;
import galileo.query.Expression;
import galileo.query.Operation;
import galileo.query.Query;

public class SimpleCondition extends Condition{
	private String feature;
	private String op;
	private String value;
	private int primitive;
	
	public SimpleCondition(){
		
	}
	
	public SimpleCondition(JSONObject json) {
		if(json.has("feature")){
			this.feature = json.getString("feature");
			this.op = json.getString("op");
			this.value = json.getString("value");
			this.primitive = json.getInt("primitive");
		} else
			throw new IllegalArgumentException("invalid json for a simple condition");
			
	}
	
	public Expression getExpression(){
		return new Expression(op, Feature.fromType(feature, primitive, value));
	}
	
	public Operation getOperation(){
		return new Operation(getExpression());
	}
	
	public void buildQuery(Query q){
		q.addOperation(getOperation());
	}
	
	//getters and setters
	
	public String getFeature() {
		return feature;
	}
	public void setFeature(String feature) {
		this.feature = feature;
	}
	public String getOp() {
		return op;
	}
	public void setOp(String op) {
		this.op = op;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public int getPrimitive() {
		return primitive;
	}
	public void setPrimitive(int primitive) {
		this.primitive = primitive;
	}
	
	@Override
	public String toString() {
		return feature + " " + op + " " + value;
	}
}
