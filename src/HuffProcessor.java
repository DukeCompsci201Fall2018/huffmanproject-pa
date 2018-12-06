
import java.util.*; 

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = readForCounts(in); 
		HuffNode root = makeTreeFromCounts(counts); 
		String[] codings = makeCodingsFromTree(root); 
		
		out.writeBits(BITS_PER_INT, HUFF_TREE); 
		writeHeader(root, out); 
		
		in.reset(); 
		writeCompressedBits(codings, in, out); 
		out.close(); 	
	}
	private int[] readForCounts(BitInputStream in) {
		//determine frequency of every 8bit chunk being compressed
		int[] frequencies = new int[ALPH_SIZE + 1];
		int value = in.readBits(BITS_PER_WORD); 
		
		while (value != -1) {
			frequencies[value] ++; 
			value = in.readBits(BITS_PER_WORD); 
		}
		frequencies[PSEUDO_EOF] = 1 ; 
		return frequencies; 
	}
	
	private HuffNode makeTreeFromCounts(int[] counts) {
		//create huffman tree from frequencies
		PriorityQueue<HuffNode> pq = new PriorityQueue<HuffNode>(); 
		for (int k=0; k<counts.length; k++) { 
			if (counts[k] > 0) {
				pq.add(new HuffNode(k, counts[k], null, null)); 
			}
		}
		while(pq.size() > 1) {
			HuffNode left = pq.remove(); 
			HuffNode right = pq.remove(); 
			HuffNode t = new HuffNode(0, left.myWeight+right.myWeight, left, right); 
			pq.add(t); 
		}
		HuffNode root = pq.remove(); 
		return root; 
	}
	
	private String[] makeCodingsFromTree(HuffNode root) {
		//create encodings for the 8bit chunks
		String[] encodings = new String[ALPH_SIZE + 1]; 
		codingHelper(root, "", encodings); 
		return encodings; 
		
	}
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		//helper code for makeCodingsFromTree
		if (root == null) return; 
		
		if (root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path; 
			return; 
		}
		codingHelper(root.myLeft, path + "0", encodings); 
		codingHelper(root.myRight, path + "1", encodings); 
		
	}

	private void writeHeader(HuffNode root, BitOutputStream out) {
		//write the magic number & the tree to the header of the compressed file
		if (!(root.myLeft == null && root.myRight == null)) { //if node is internal node
			out.writeBits(1,0); //single bit of zero
			writeHeader(root.myLeft, out); 
			writeHeader(root.myRight, out); 
		}
		else { 
			out.writeBits(1,1); //node is a leaf - single bit of one
			out.writeBits(BITS_PER_WORD + 1, root.myValue); 
		}	
	}
	
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		//read file again and write 8bit chunk,
		int value = in.readBits(BITS_PER_WORD);
		
		while (value != -1) {
			String code = codings[value]; 
			out.writeBits(code.length(), Integer.parseInt(code,2)); 
			value = in.readBits(BITS_PER_WORD); 
		}
		String code = codings[PSEUDO_EOF]; 
		out.writeBits(code.length(), Integer.parseInt(code,2));  
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with " + bits);
			}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}
	
	private HuffNode readTreeHeader(BitInputStream in) {
		// read a single bit
		int bit = in.readBits(1); 
		
		if (bit == -1) {
			throw new HuffException("readBits failed: numBits should be over 1");
		}
		if (bit == 0) {
			HuffNode left = readTreeHeader(in); 
			HuffNode right = readTreeHeader(in); 
			return new HuffNode(0,0, left, right); 
		}
		//read the leaf 
		else { 
			int value = in.readBits(BITS_PER_WORD + 1); 
			return new HuffNode(value,0, null, null);
		}	
	}
	
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {	
		// read the bits from the BitInputStream (representing compress file) one bit at a time
		HuffNode current = root; 
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if (bits == 0) {
					current = current.myLeft; 
				}
				else {
					current = current.myRight;
				}
				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) {
						break; //out of loop
					}
					else {
						out.writeBits(BITS_PER_WORD, current.myValue); 
						current = root; //start again after reaching leaf
					}
				}
			}
		}
		
	}
	
}
