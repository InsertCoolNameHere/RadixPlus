package galileo.integrity;

public class HashPathPair{
	
    public long hashValue;
    public String[] pathArray;
    
    public HashPathPair(long lonValue, String[] stringValue){
        this.hashValue = lonValue;
        this.pathArray = stringValue;
    }
    public String toString(){
        return "(" + this.hashValue + ", " + this.pathArray + ")";
    }

}