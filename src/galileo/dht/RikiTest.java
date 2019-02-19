package galileo.dht;

public class RikiTest {

	
	public String myString;
	
	public RikiTest() {
		myString = "hello";
	}
	
	
	public static void main(String[] args) {
		
		RikiTest r = new RikiTest();
		String refStr = r.getMyString();
		
		
		refStr = "helloworld";
		
		
		System.out.println(r.getMyString());
		
		

	}


	public String getMyString() {
		return myString;
	}


	public void setMyString(String myString) {
		this.myString = myString;
	}

}
