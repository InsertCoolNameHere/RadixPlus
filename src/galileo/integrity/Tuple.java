package galileo.integrity;

public class Tuple{
	
    public long lonValue;
    public String stringValue;
    
    public Tuple(long lonValue, String stringValue){
        this.lonValue = lonValue;
        this.stringValue = stringValue;
    }
    public String toString(){
        return "(" + this.lonValue + ", " + this.stringValue + ")";
    }

}