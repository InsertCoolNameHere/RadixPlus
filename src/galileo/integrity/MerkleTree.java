package galileo.integrity;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.zip.Adler32;


public class MerkleTree {

	public static final int MAGIC_HDR = 0xcdaace99;
	public static final int INT_BYTES = 4;
	public static final int LONG_BYTES = 8;
	public static final byte LEAF_SIG_TYPE = 0x0;
	public static final byte INTERNAL_SIG_TYPE = 0x01;

	private final Adler32 crc = new Adler32();
	
	// ONLY LEAF NODES HAVE A PATH TO THEM
	private List<Long> leafSigs;
	private Node root;
	private int depth;
	private int nnodes;

	/**
	 * Use this constructor to create a MerkleTree from a list of leaf signatures.
	 * The Merkle tree is built from the bottom up.
	 * 
	 * @param leafSignatures
	 */
	public MerkleTree(List<Long> leafSignatures, List<String> paths) {
		
		if(leafSignatures.size() == 1) {
			Node n = constructLeafNode(leafSignatures.get(0), paths.get(0));
			root = n;
			return;
		}
		constructTree(leafSignatures, paths);
	}
	

	/**
	 * Use this constructor when you have already constructed the tree of Nodes
	 * (from deserialization).
	 * 
	 * @param treeRoot
	 * @param numNodes
	 * @param height
	 * @param leafSignatures
	 */
	public MerkleTree(Node treeRoot, int numNodes, int height, List<Long> leafSignatures) {
		root = treeRoot;
		nnodes = numNodes;
		depth = height;
		leafSigs = leafSignatures;
	}

	/**
	 * Serialization format:
	 * (magicheader:int)(numnodes:int)[(nodetype:byte)(siglength:int)(signature:[]byte)]
	 * 
	 * @return
	 */
	public byte[] serialize() {
		int magicHeaderSz = INT_BYTES;
		int nnodesSz = INT_BYTES;
		int hdrSz = magicHeaderSz + nnodesSz;

		int typeByteSz = 1;
		int siglength = INT_BYTES;

		int parentSigSz = LONG_BYTES;
		int leafSigSz = leafSigs.get(0).length;

		// some of the internal nodes may use leaf signatures (when "promoted")
		// so ensure that the ByteBuffer overestimates how much space is needed
		// since ByteBuffer does not expand on demand
		int maxSigSz = leafSigSz;
		if (parentSigSz > maxSigSz) {
			maxSigSz = parentSigSz;
		}

		int spaceForNodes = (typeByteSz + siglength + maxSigSz) * nnodes;

		int cap = hdrSz + spaceForNodes;
		ByteBuffer buf = ByteBuffer.allocate(cap);

		buf.putInt(MAGIC_HDR).putInt(nnodes); // header
		serializeBreadthFirst(buf);

		// the ByteBuf allocated space is likely more than was needed
		// so copy to a byte array of the exact size necesssary
		byte[] serializedTree = new byte[buf.position()];
		buf.rewind();
		buf.get(serializedTree);
		return serializedTree;
	}

	/**
	 * Serialization format after the header section:
	 * [(nodetype:byte)(siglength:int)(signature:[]byte)]
	 * 
	 * @param buf
	 */
	void serializeBreadthFirst(ByteBuffer buf) {
		Queue<Node> q = new ArrayDeque<Node>((nnodes / 2) + 1);
		q.add(root);

		while (!q.isEmpty()) {
			Node nd = q.remove();
			buf.put(nd.type).putInt(nd.sig.length).put(nd.sig);

			if (nd.left != null) {
				q.add(nd.left);
			}
			if (nd.right != null) {
				q.add(nd.right);
			}
		}
	}

	/**
	 * Create a tree from the bottom up starting from the leaf signatures.
	 * 
	 * @param signatures
	 * @param paths 
	 */
	void constructTree(List<Long> signatures, List<String> paths) {
		if (signatures.size() <= 1) {
			throw new IllegalArgumentException("Must be at least two signatures to construct a Merkle tree");
		}

		leafSigs = signatures;
		nnodes = signatures.size();
		List<Node> parents = bottomLevel(signatures, paths);
		nnodes += parents.size();
		depth = 1;

		while (parents.size() > 1) {
			parents = internalLevel(parents);
			depth++;
			nnodes += parents.size();
		}

		root = parents.get(0);
	}

	public int getNumNodes() {
		return nnodes;
	}

	public Node getRoot() {
		return root;
	}

	public int getHeight() {
		return depth;
	}

	/**
	 * Constructs an internal level of the tree
	 */
	List<Node> internalLevel(List<Node> children) {
		List<Node> parents = new ArrayList<Node>(children.size() / 2);

		for (int i = 0; i < children.size() - 1; i += 2) {
			Node child1 = children.get(i);
			Node child2 = children.get(i + 1);

			Node parent = constructInternalNode(child1, child2);
			parents.add(parent);
		}

		if (children.size() % 2 != 0) {
			Node child = children.get(children.size() - 1);
			Node parent = constructInternalNode(child, null);
			parents.add(parent);
		}

		return parents;
	}

	/**
	 * Constructs the bottom part of the tree - the leaf nodes and their immediate
	 * parents. Returns a list of the parent nodes.
	 * @param paths 
	 */
	List<Node> bottomLevel(List<Long> signatures, List<String> paths) {
		List<Node> parents = new ArrayList<Node>(signatures.size() / 2);

		for (int i = 0; i < signatures.size() - 1; i += 2) {
			Node leaf1 = constructLeafNode(signatures.get(i), paths.get(i));
			Node leaf2 = constructLeafNode(signatures.get(i + 1), paths.get(i + 1));

			Node parent = constructInternalNode(leaf1, leaf2);
			parents.add(parent);
		}

		// if odd number of leafs, handle last entry
		if (signatures.size() % 2 != 0) {
			Node leaf = constructLeafNode(signatures.get(signatures.size() - 1), paths.get(paths.size() - 1));
			Node parent = constructInternalNode(leaf, null);
			parents.add(parent);
		}

		return parents;
	}
	

	private Node constructInternalNode(Node child1, Node child2) {
		Node parent = new Node();
		parent.type = INTERNAL_SIG_TYPE;

		if (child2 == null) {
			parent.sig = child1.sig;
		} else {
			parent.sig = internalHash(child1.sig, child2.sig);
		}

		parent.left = child1;
		parent.right = child2;
		return parent;
	}

	private static Node constructLeafNode(long signature, String path) {
		Node leaf = new Node();
		leaf.type = LEAF_SIG_TYPE;
		leaf.sig = signature;
		leaf.path = path;
		return leaf;
	}
	
	

	public long internalHash(long leftChildSig, long rightChildSig) {
		crc.reset();
		
		
		crc.update(longToByteArray(leftChildSig));
		crc.update(longToByteArray(rightChildSig));
		return crc.getValue();
	}

	/* ---[ Node class ]--- */

	/**
	 * Big-endian conversion
	 */
	public static byte[] longToByteArray(long value) {
		return new byte[] { (byte) (value >> 56), (byte) (value >> 48), (byte) (value >> 40), (byte) (value >> 32),
				(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value };
	}
	
	public static String sigAsString(byte[] sig) {
		StringBuffer sb = new StringBuffer();
		sb.append('[');
		for (int i = 0; i < sig.length; i++) {
			sb.append(sig[i]).append(' ');
		}
		sb.insert(sb.length() - 1, ']');
		return sb.toString();
	}
	
	public static void main(String arg[]) {
		
		String val1 = "val1";
		
		Adler32 a1 = new Adler32();
		a1.update(val1.getBytes());
		
		System.out.println(a1.getValue());
		byte[] checksum1 = longToByteArray(a1.getValue());

		Adler32 a32 = new Adler32();
		a32.update(checksum1, 12, checksum1.length - 12);
		int sum = (int) a32.getValue();
		checksum1[8] = (byte) sum;
		checksum1[9] = (byte) (sum >> 8);
		checksum1[10] = (byte) (sum >> 16);
		checksum1[11] = (byte) (sum >> 24);
		
	}
	
	public static void main1(String arg[]) {
		
		/*List<String> signatures = Arrays.asList(
		        "52e422506d8238ef3196b41db4c41ee0afd659b6", 
		        "6d0b51991ac3806192f3cb524a5a5d73ebdaacf8",
		        "461848c8b70e5a57bd94008b2622796ec26db657",
		        "c938037dc70d107b3386a86df7fef17a9983cf53");
		MerkleTree mt = new MerkleTree(signatures);
		
		System.out.println(mt.getRoot().toString());
		signatures = Arrays.asList(
		        "52e422506d8238ef3196b41db4c41ee0afd659b666", 
		        "6d0b51991ac3806192f3cb524a5a5d73ebdaacf8",
		        "461848c8b70e5a57bd94008b2622796ec26db657",
		        "c938037dc70d107b3386a86df7fef17a9983cf53");
		MerkleTree mt1 = new MerkleTree(signatures);
		System.out.println(mt1.getRoot().toString());
		
		signatures = Arrays.asList(
		        "52e422506d8238ef3196b41db4c41ee0afd659b6", 
		        "6d0b51991ac3806192f3cb524a5a5d73ebdaacf8",
		        "461848c8b70e5a57bd94008b2622796ec26db657",
		        "c938037dc70d107b3386a86df7fef17a9983cf53");
		MerkleTree mt2 = new MerkleTree(signatures);
		System.out.println(mt2.getRoot().toString());*/
		
		String val1 = "val1";
		String val2 = "val2";
		String val3 = "val3";
		
		Adler32 a1 = new Adler32();
		a1.update(val1.getBytes());
		byte[] checksum1 = longToByteArray(a1.getValue());
		
		Adler32 a2 = new Adler32();
		a2.update(val2.getBytes());
		byte[] checksum2 = longToByteArray(a2.getValue());
		
		Adler32 a3 = new Adler32();
		a3.update(val3.getBytes());
		byte[] checksum3 = longToByteArray(a3.getValue());
		
		Adler32 crc = new Adler32();
		crc.reset();
		crc.update(checksum1);
		crc.update(checksum2);
		crc.update(checksum3);
		
		//System.out.println(crc.getValue());
		System.out.println(sigAsString(longToByteArray(crc.getValue())));
		
		crc = new Adler32();
		crc.reset();
		
		crc.update(checksum1);
		crc.update(checksum2);
		
		byte[] checksumTmp = longToByteArray(crc.getValue());
		
		crc = new Adler32();
		crc.reset();
		crc.update(checksumTmp);
		crc.update(checksum3);
		
		//System.out.println(crc.getValue());
		System.out.println(sigAsString(longToByteArray(crc.getValue())));
	}
}
