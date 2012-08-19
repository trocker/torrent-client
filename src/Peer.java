import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Timer;
import java.util.logging.Logger;


/**
 * The Class Peer handles the creation of connections with peers
 * that are in the swarm. It also handles generating and checking handshakes
 * as well as request messages from peers and sending messages to peers
 * 
 * @author Deepak, Mike, Josh
 */
public class Peer extends Thread {
	
	/** The Constant log. */
	private static final Logger log = Log2.getLogger(Peer.class);
	
	/** The peer id. */
	protected byte[] peerId;
	
	/** The port. */
	protected int port;
	
	/** The ip. */
	protected String ip;

	/** The we are choking peer. */
	boolean weAreChokingPeer = true;
	
	/** The we are interested in peer. */
	boolean weAreInterestedInPeer = false;
	
	/** The peer choking us. */
	boolean peerChokingUs = true;
	
	/** The peer interested in us. */
	boolean peerInterestedInUs = false;

	/** The bitfield. */
	public boolean[] bitfield = null;
	
	/** The previous index. */
	public int previousIndex = -1;
	
	/** The manager. */
	Manager manager;
	
	/** The peer uploader. */
	PeerUploader peerUploader;
	
	/** The peer keep alive. */
	PeerKeepAlive peerKeepAlive;

	/** The socket. */
	private Socket socket = null;

	/** The in. */
	protected InputStream in;
	
	/** The out. */
	protected OutputStream out;
	
	/** The upload rate. */
	public double uploadRate = 0;
	
	/** The download rate. */
	public double downloadRate = 0;
	
	/** The current piece index. */
	private int currentPieceIndex = -1;
	
	/** The current byte offset. */
	private int currentByteOffset = 0;
	
	/** The piece. */
	private ByteArrayOutputStream piece = null;
	
	/** The total bytes written. */
	private int totalBytesWritten = 0;
	
	private boolean isRunning = true;

	private long currentTime = 0L;
	
	long totalDownload =0L;
	
	int downloadCount =0;

	public long totalUpload=0L;
	
	long rateCalulatorTotalDownload =0L;
	
	long rateCalculatorTotalUpload = 0L;

	/**
	 * Instantiates a new peer.
	 *
	 * @param peerId the peer id
	 * @param port the port
	 * @param ip the ip
	 * @param manager the manager
	 */
	public Peer(byte[] peerId, int port, String ip, Manager manager) {
		super("Peer@" + ip + ":" + port);
		this.peerId = peerId;
		this.port = port;
		this.ip = ip;
		this.manager = manager;
		this.bitfield = new boolean[this.manager.torrentInfo.piece_hashes.length];
		this.peerUploader = new PeerUploader(this);
		this.peerUploader.isRunning = true;
		this.peerUploader.start();
		this.peerKeepAlive = new PeerKeepAlive(this);
		Arrays.fill(this.bitfield, false);
	}

	/**
	 * Inits the peer
	 *
	 * @return true, if successful
	 */
	public boolean init() {
		byte[] id = new byte[6];

		System.arraycopy(this.peerId, 0, id, 0, 6);
		log.info("Peer IP: " + this.ip);
		
		if (!Arrays.equals(id, Manager.PEER_ID_WE_NEED) && !Arrays.equals(id, new byte[] { '-', 'G', 'P', '0', '3', '-'})){ 
			return false;
			}
		
		try {
			isRunning = true;
			currentTime= System.currentTimeMillis();
			this.connect();

			DataOutputStream os = new DataOutputStream(this.out);
			DataInputStream is = new DataInputStream(this.in);

			if (is == null || os == null) {
				log.severe("Unable to create stream to peer");
				this.disconnect();
				return false;
			}

			os.write(Peer.generateHandshake(Manager.peerId,	manager.torrentInfo.info_hash.array()));
			os.flush();

			byte[] response = new byte[68];
			
			
			this.socket.setSoTimeout(10000);
			is.readFully(response);		
			this.socket.setSoTimeout(130000);
			
			if(!checkHandshake(manager.torrentInfo.info_hash.array(), response)){
				return false;
			}

			log.info("Handshake Response: " + Arrays.toString(response));

			if(this.manager.curUnchoked < this.manager.maxUnchoked){
				this.weAreChokingPeer = false;
				this.manager.curUnchoked++;
			} else {
				this.weAreChokingPeer = true;
			}
			
			this.start();
			
			RUBTClient.log("Connected to " + this);
			
			return true;
		} catch (Exception e) {
			log.severe("Error connecting with peer");
			return false;
		}
	}

	/**
	 * Gets the next request message
	 *
	 * @return the next request
	 */
	public Message.Request getNextRequest() {
		int piece_length = this.manager.torrentInfo.piece_length;
		int file_length = this.manager.torrentInfo.file_length;
		int requestSize = Tracker.requestSize;
		int numPieces = this.manager.torrentInfo.piece_hashes.length;
		
		if(this.currentPieceIndex == -1){
			if((this.currentPieceIndex = this.manager.getRarestPiece(this)) == -1){
				RUBTClient.log("Failed to get next piece");
				return null;
			}			
		} 
		if(this.currentPieceIndex == (numPieces - 1)){
			piece_length = file_length % this.manager.torrentInfo.piece_length;
		}
		
		if((this.currentByteOffset + requestSize) > piece_length){
			requestSize = piece_length % requestSize;
		}
		
		Message.Request request = new Message.Request(this.currentPieceIndex, this.currentByteOffset, requestSize);
		
		if((this.currentByteOffset + requestSize) >= piece_length){
			this.currentPieceIndex = -1;
			this.currentByteOffset = 0;
		} else {
			this.currentByteOffset += requestSize;
		}
		
		return request;		
	}
	
	/**
	 * Append to piece and verify if complete.
	 * 
	 * Keeps building a piece and checks if we have the whole piece
	 *
	 * @param pieceMsg the piece msg
	 * @param hashes the hashes
	 * @param manager the manager
	 * @return true, if successful
	 */
	public boolean appendToPieceAndVerifyIfComplete(Message.Piece pieceMsg, ByteBuffer[] hashes,
			Manager manager) {

		int currentPieceLength = (pieceMsg.index == (this.manager.torrentInfo.piece_hashes.length - 1)) ? this.manager.torrentInfo.file_length % this.manager.torrentInfo.piece_length : this.manager.torrentInfo.piece_length;
		
		if (this.piece == null) {
			this.piece = new ByteArrayOutputStream();
		}

		try {
			piece.write(pieceMsg.block, 0, pieceMsg.block.length);
			//RUBTClient.log(Integer.toString(this.piece.toByteArray().length));
		} catch (Exception e) {
			log.severe("Unable to write to file at " + pieceMsg.index + " with offset " + pieceMsg.start);
		}
		
		if(this.currentPieceIndex == -1){	
			this.totalBytesWritten = 0;
			this.previousIndex = pieceMsg.index;
			this.manager.curRequestedBitfield[this.previousIndex] = false;
			
			try {
				if (manager.UpdateFile(pieceMsg, hashes[pieceMsg.index], piece.toByteArray())) {
					totalDownload+= currentPieceLength;
					this.manager.ourBitfield[pieceMsg.index] = true;
					rateCalulatorTotalDownload+= totalDownload;	
					piece = null;
					return true;
				} else {
						piece = null;
				}
			} catch (Exception e) {
				log.severe("Error writing to file");
				piece = null;
			}
		}
	
		return false;
	}

	
	/**
	 * Connect.
	 * 
	 * Connect to a peer
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public synchronized void connect() throws IOException {
		this.socket = new Socket(this.ip, this.port);
		this.in = this.socket.getInputStream();
		this.out = this.socket.getOutputStream();
	}

	/**
	 * Disconnect.
	 * 
	 * Disconnect from a peer
	 */
	public synchronized void disconnect() {
		if(this.weAreChokingPeer == false){
			this.manager.curUnchoked -= 1;
		}
		if (this.socket != null) {
			this.peerKeepAlive.isRunning = false;
			this.peerUploader.isRunning = false;
			this.peerKeepAlive.interrupt();
			this.peerUploader.interrupt();
		}
			
			try {
				if(this.socket!= null)
				this.socket.close();
				
			} catch (IOException e) {
				log.severe("Error closing socket peer");
				
			} finally {
				this.socket = null;
				this.in = null;
				this.out = null;
				//this.manager.peers.remove(this);
				isRunning = false;
			
			}
		
	}

	/**
	 * Send message.
	 * 
	 * Send a message to a peer
	 *
	 * @param m the m
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public synchronized void sendMessage(Message m) throws IOException {
		if (this.out == null) {
			throw new IOException(this
					+ " cannot send a message on an empty socket.");
		}
		log.info("Sending " + m + " to " + this);
		Message.encode(m, this.out);
		this.peerKeepAlive.interrupt();
	}

	
	/* (non-Javadoc)
	 * @see java.lang.Thread#toString()
	 */
	public String toString() {
		return new String(peerId) + " " + port + " " + ip;
	}


	/**
	 * Generate handshake.
	 * 
	 * Generate a handshake on our end to send to the peer
	 *
	 * @param peer the peer
	 * @param infohash the infohash
	 * @return the byte[]
	 */
	public static byte[] generateHandshake(byte[] peer, byte[] infohash) {
		int index = 0;
		byte[] handshake = new byte[68];
		
		handshake[index] = 0x13;
		index++;
		
		byte[] BTChars = { 'B', 'i', 't', 'T', 'o', 'r', 'r', 'e', 'n', 't', ' ',
				'p', 'r', 'o', 't', 'o', 'c', 'o', 'l' };
		System.arraycopy(BTChars, 0, handshake, index, BTChars.length);
		index += BTChars.length;
		
		byte[] zero = new byte[8];
		System.arraycopy(zero, 0, handshake, index, zero.length);
		index += zero.length;
		
		log.fine("Info hash length: " + infohash.length);
		System.arraycopy(infohash, 0, handshake, index, infohash.length);
		index += infohash.length;
		
		System.arraycopy(peer, 0, handshake, index, peer.length);
		log.fine("Peer ID Length: " + peer.length);
		
		return handshake;
	}

	
	/* (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	public void run() {
		log.info("Starting " + this);
		
		while (this.socket != null && !this.socket.isClosed() && isRunning) {
				Message myMessage = null;
				try {
					myMessage = Message.decode(this.in);
				} catch (IOException e) {
					log.severe("Invalid stream for peer " + this);
					break;
				}
				if (myMessage != null) {
					if(myMessage.id == Message.requestID){
						this.peerUploader.recieveRequest((Message.Request)myMessage);
					} else {
						this.manager.recieveMessage(new PeerMessage(this, myMessage));
					}
				}
		} 
		//this.manager.peers.remove(this);

		log.info("Finished " + this);
	}
	
	public boolean checkHandshake(byte[] infoHash, byte[] response) {
		byte[] peerHash = new byte[20];
		System.arraycopy(response, 28, peerHash, 0, 20);

		if(!Arrays.equals(peerHash, infoHash))
		{
			log.info("Handshake verification failed with Peer: " + peerId);
			return false;
		}
			log.info("Verified Handshake.");
		return true;
	}
	
	public void choke(){
		try {
			this.sendMessage(Message.CHOKE);
		} catch (IOException e) {
			log.severe("Unable to send choke to peer");
		}
		this.weAreChokingPeer = true;
		RUBTClient.updatePeerChokeStatus(this, true);
	}
	
	public void unchoke(){
		try {
			this.sendMessage(Message.UNCHOKE);
		} catch (IOException e) {
			log.severe("Unable to send unchoke to peer");
		}
		this.weAreChokingPeer = false;
		RUBTClient.updatePeerChokeStatus(this, false);
	}
	
	
	
}
