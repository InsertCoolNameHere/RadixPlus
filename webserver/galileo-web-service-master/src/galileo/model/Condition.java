package galileo.model;

import org.json.JSONObject;

import galileo.query.Query;

public class Condition {
	public static Condition getCondition(JSONObject json){
		if(json.has("left"))
			return new ComplexCondition(json);
		else if(json.has("feature"))
			return new SimpleCondition(json);
		else
			throw new IllegalArgumentException("Invalid json for a Condition");
	}
	
	public ComplexCondition getComplexCondition() throws IllegalAccessException{
		if(this instanceof ComplexCondition)
			return (ComplexCondition)this;
		throw new IllegalAccessException("Condition is not complex");
	}
	
	public SimpleCondition getSimpleCondition() throws IllegalAccessException {
		if(this instanceof SimpleCondition)
			return (SimpleCondition)this;
		throw new IllegalAccessException("Condition is not complex");
	}
	
	public void buildQuery(Query q) throws IllegalAccessException{
		if (this instanceof ComplexCondition)
			this.getComplexCondition().buildQuery(q);
		else
			this.getSimpleCondition().buildQuery(q);
	}
	
}
