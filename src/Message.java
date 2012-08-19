import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

/**
 * The Message Class handles the creation of all types of messages
 * necessary for BitTorrent to function properly. This class also handles
 * encoding and decoding of messages and creating the appropriate messages
 * accordingly.
 * 
 * @author Deepak, Mike, Josh
 */
public class Message {

	/** The Constant log. */
	private static final Logger log = Log2.getLogger(Message.class);
	
	/** The Constant keepAliveID. */
	public static final byte keepAliveID = -1;
	
	/** The Constant chokeID. */
	public static final byte chokeID = 0;
	
	/** The Constant unchokeID. */
	public static final byte unchokeID = 1;
	
	/** The Constant interestedID. */
	public static final byte interestedID = 2;
	
	/** The Constant uninterestedID. */
	public static final byte uninterestedID = 3;
	
	/** The Constant haveID. */
	public static final byte haveID = 4;
	
	/** The Constant bitfieldID. */
	public static final byte bitfieldID = 5;
	
	/** The Constant requestID. */
	public static final byte requestID = 6;
	
	/** The Constant pieceID. */
	public static final byte pieceID = 7;
	
	/** The Constant cancelID. */
	public static final byte cancelID = 8;
	
	/** The Constant portID. */
	public static final byte portID = 9;

	/** The Constant KEEP_ALIVE. */
	public static final Message KEEP_ALIVE = new Message(0, (byte) 255);
	
	/** The Constant CHOKE. */
	public static final Message CHOKE = new Message(1, chokeID);
	
	/** The Constant UNCHOKE. */
	public static final Message UNCHOKE = new Message(1, unchokeID);
	
	/** The Constant INTERESTED. */
	public static final Message INTERESTED = new Message(1, interestedID);
	
	/** The Constant UNINTERESTED. */
	public static final Message UNINTERESTED = new Message(1, uninterestedID);
		
	/** The id. */
	protected final byte id;
	
	/** The length. */
	protected final int length;



	/**
	 * Instantiates a new message.
	 *
	 * @param length the length
	 * @param id the id
	 */
	protected Message(final int length, final byte id) {
		this.id = id;
		this.length = length;
	}

	/**
	 * Encode payload.
	 *
	 * @param dos the dos
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void encodePayload(DataOutputStream dos) throws IOException {
		return;
	}

	
	/**
	 * The Class Have.
	 */
	public static final class Have extends Message {
		
		/** The index. */
		public final int index;		
		
		/**
		 * Instantiates a new have.
		 *
		 * @param index the index
		 */
		public Have(final int index) {
			super(5, haveID);
			this.index = index;
		}

		/* (non-Javadoc)
		 * @see Message#encodePayload(java.io.DataOutputStream)
		 */
		public void encodePayload(DataOutputStream dos) throws IOException {
			dos.writeInt(this.index);
			return;
		}
	}

	/**
	 * The Class Bitfield.
	 */
	public static final class Bitfield extends Message {
		
		/** The bitfield. */
		public final byte[] bitfield;

		/**
		 * Instantiates a new bitfield.
		 *
		 * @param bitfield the bitfield
		 */
		public Bitfield(final byte[] bitfield) {
			super(bitfield.length + 1, bitfieldID);
			this.bitfield = bitfield;
		}

		/* (non-Javadoc)
		 * @see Message#encodePayload(java.io.DataOutputStream)
		 */
		public void encodePayload(DataOutputStream dos) throws IOException {
			dos.write(this.bitfield);
			return;
		}
	}
	
	/**
	 * The Class Request.
	 */
	public static final class Request extends Message {

		/** The index. */
		final int index;
		
		/** The start. */
		final int start;
		
		/** The mlength. */
		final int mlength;

		/**
		 * Instantiates a new request.
		 *
		 * @param index the index
		 * @param start the start
		 * @param length the length
		 */
		public Request(final int index, final int start, final int length) {
			super(13, requestID);
			this.index = index;
			this.start = start;
			this.mlength = length;
		}

		/* (non-Javadoc)
		 * @see Message#toString()
		 */
		public String toString() {
			return new String("Length: " + this.length + " ID: " + this.id
					+ " Index: " + this.index + " Start: " + this.start
					+ " Block: " + this.mlength);

		}
		
		/* (non-Javadoc)
		 * @see Message#encodePayload(java.io.DataOutputStream)
		 */
		public void encodePayload(DataOutputStream dos) throws IOException {
			dos.writeInt(this.index);
			dos.writeInt(this.start);
			dos.writeInt(this.mlength);
		}
	}

	/**
	 * The Class Piece.
	 */
	public static final class Piece extends Message {

		/** The index. */
		final int index;
		
		/** The start. */
		final int start;
		
		/** The block. */
		final byte[] block;

		/* (non-Javadoc)
		 * @see Message#toString()
		 */
		public String toString() {
			return new String("ID: " + this.id + " Length: " + this.length
					+ " index: " + this.index);
		}

		/**
		 * Instantiates a new piece.
		 *
		 * @param index the index
		 * @param start the start
		 * @param block the block
		 */
		public Piece(final int index, final int start, final byte[] block) {
			super(9 + block.length, pieceID);
			this.index = index;
			this.start = start;
			this.block = block;
		}

		/* (non-Javadoc)
		 * @see Message#encodePayload(java.io.DataOutputStream)
		 */
		public void encodePayload(DataOutputStream dos) throws IOException {
			dos.writeInt(this.index);
			dos.writeInt(this.start);
			dos.write(this.block);
			//RUBTClient.addAmountUploaded(this.block.length);
			
			//double ratio = Tracker.downloaded / Tracker.uploaded;
			//RUBTClient.setShareRatio(ratio);
		}
	}

	/**
	 * The Class Cancel.
	 */
	public static final class Cancel extends Message {

		/** The index. */
		private final int index;
		
		/** The start. */
		private final int start;
		
		/** The clength. */
		private final int clength;

		/**
		 * Instantiates a new cancel.
		 *
		 * @param index the index
		 * @param start the start
		 * @param length the length
		 */
		public Cancel(final int index, final int start,	final int length) {
			super(13, cancelID);
			this.index = index;
			this.start = start;
			this.clength = length;
		}

		/* (non-Javadoc)
		 * @see Message#encodePayload(java.io.DataOutputStream)
		 */
		public void encodePayload(DataOutputStream dos) throws IOException {
			dos.writeInt(this.index);
			dos.writeInt(this.start);
			dos.writeInt(this.clength);
			return;
		}
	}

	/**
	 * Decode.
	 * 
	 * Decodes a message and creates the new message 
	 * accordingly
	 *
	 * @param input the input
	 * @return the message
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static Message decode(final InputStream input)
			throws IOException {
		
		DataInputStream dataInput = new DataInputStream(input);

		int length = dataInput.readInt();


		if (length == 0) {
			return KEEP_ALIVE;
		}

		byte id = dataInput.readByte();

		switch (id) {
		case (chokeID):
			return CHOKE;
		case (unchokeID):
			return UNCHOKE;
		case (interestedID):
			return INTERESTED;
		case (uninterestedID):			
			return UNINTERESTED;
		case (haveID):
			int index = dataInput.readInt();
			return new Have(index);
		case (bitfieldID):
			byte[] bitfield = new byte[length - 1];
			dataInput.readFully(bitfield);
			return new Bitfield(bitfield);
		case (pieceID):
			int ind = dataInput.readInt();
			int start = dataInput.readInt();
			byte[] block = new byte[length - 9];
			dataInput.readFully(block);
			return new Piece(ind, start, block);
		case (requestID):
			int in = dataInput.readInt();
			int begin = dataInput.readInt();
			length = dataInput.readInt();
			return new Request(in, begin, length);
		}

		return null;
	}

	/**
	 * Encode.
	 * 
	 * Encodes the message we want to send
	 *
	 * @param message the message
	 * @param output the output
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void encode(final Message message, final OutputStream output)	throws IOException {
		log.info("Encoding message " + message);

		if (message != null) {
			{
				DataOutputStream dos = new DataOutputStream(output);
				dos.writeInt(message.length);
				if (message.length > 0) {
					dos.write(message.id);
					message.encodePayload(dos);
				}
				dos.flush();

			}
		}
	}
	
	/** The Constant TYPE_NAMES. */
	private static final String[] TYPE_NAMES = new String[]{"Choke", "Unchoke", "Interested", "Uninterested", "Have", "Bitfield", "Request", "Piece", "Cancel", "Port"};

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		if(this.length == 0){
			return "Keep-Alive";
		}
		return TYPE_NAMES[this.id]; 
	}
}
