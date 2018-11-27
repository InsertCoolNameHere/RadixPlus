package galileo.model;

import org.json.JSONObject;

import galileo.query.Expression;
import galileo.query.Operation;
import galileo.query.Query;

public class ComplexCondition extends Condition{
	private Condition left;
	private String joint;
	private Condition right;
	
	public ComplexCondition(){
		
	}
	
	public ComplexCondition(JSONObject json){
		if(json.has("left")){
			JSONObject leftJSON = json.getJSONObject("left");
			if(leftJSON.has("left"))
				this.left = new ComplexCondition(leftJSON);
			else
				this.left = new SimpleCondition(leftJSON);
			
			this.joint = json.getString("joint");
			
			JSONObject rightJSON = json.getJSONObject("right");
			if(rightJSON.has("left"))
				this.right = new ComplexCondition(rightJSON);
			else
				this.right = new SimpleCondition(rightJSON);
		} else {
			throw new IllegalArgumentException("invalid json for a complex condition");
		}
	}
	
	private Operation andQuery(Condition c) throws IllegalAccessException{
		if (c instanceof ComplexCondition){
			if (!c.getComplexCondition().joint.equals("and"))
				throw new IllegalArgumentException("Condition is not in disjunctive normal form");
			Operation left = andQuery(c.getComplexCondition().left); //4
			Operation right = andQuery(c.getComplexCondition().right); //5
			for(Expression expression : right.getExpressions())
				left.addExpressions(expression);
			return left;
		} else {
			return c.getSimpleCondition().getOperation(); //6
		}
	}
	
	private void orQuery(Condition c, Query q) throws IllegalAccessException{
		if (c instanceof ComplexCondition){
			if (c.getComplexCondition().joint.equals("and"))
				q.addOperation(andQuery(c)); //0
			else {
				orQuery(c.getComplexCondition().left, q); //1
				orQuery(c.getComplexCondition().right, q); //2
			}
		} else {
			q.addOperation(c.getSimpleCondition().getOperation()); //3
		}
	}
	
	public void buildQuery(Query q) throws IllegalAccessException{	
		orQuery(this, q);
	}
	
	//getters and setters
	
	public Condition getLeft() {
		return left;
	}
	public void setLeft(Condition left) {
		this.left = left;
	}
	public String getJoint() {
		return joint;
	}
	public void setJoint(String joint) {
		this.joint = joint;
	}
	public Condition getRight() {
		return right;
	}
	public void setRight(Condition right) {
		this.right = right;
	}
	
	@Override
	public String toString() {
		if(joint.equals("and"))
			return "(" + left.toString() + " && " + right.toString() + ")";
		return "(" + left.toString() + " || " + right.toString() + ")";
	}
}
