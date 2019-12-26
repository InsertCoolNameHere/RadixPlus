package galileo.integrity;

/**
 * The Node class should be treated as immutable, though immutable is not
 * enforced in the current design.
 * 
 * A Node knows whether it is an internal or leaf node and its signature.
 * 
 * Internal Nodes will have at least one child (always on the left). Leaf Nodes
 * will have no children (left = right = null).
 */
public class Node {
	public byte type; // INTERNAL_SIG_TYPE or LEAF_SIG_TYPE
	public long sig; // signature of the node
	public Node left;
	public Node right;
	public String path;
	
	public Node() {}
	
	public Node(long sig, String path, byte type) {
		
		this.sig = sig;
		this.path = path;
		this.type = type;
	}
	
	@Override
	public String toString() {
		String leftType = "<null>";
		String rightType = "<null>";
		if (left != null) {
			leftType = String.valueOf(left.type);
		}
		if (right != null) {
			rightType = String.valueOf(right.type);
		}
		return String.format("MerkleTree.Node<type:%d, sig:%s, left (type): %s, right (type): %s>", type, sigAsString(),
				leftType, rightType);
	}

	/*public String sigAsString() {
		StringBuffer sb = new StringBuffer();
		sb.append('[');
		for (int i = 0; i < sig.length; i++) {
			sb.append(sig[i]).append(' ');
		}
		sb.insert(sb.length() - 1, ']');
		return sb.toString();
	}*/
	
	public String sigAsString() {
		return sig+"";
	}
	
	public long getHash() {
		return sig;
	}
	
	
}