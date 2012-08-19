import java.io.*;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import GivenTools.TorrentInfo;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/**
 * The Class Manager is resonsible for the main operation of the torrent client
 * and handles setting up connection with peers, handling incoming messages from
 * peers and handling writing/reading from the file on disk.
 * 
 * @author Deepak, Mike, Josh
 */
public class Manager extends Thread {

	/** The Constant PEER_ID_WE_NEED. */
	public static final byte[] PEER_ID_WE_NEED = new byte[] {'R', 'U', 'B', 'T', '1', '1'};
	
	/** The Constant log. */
	private static final Logger log = Log2.getLogger(Manager.class);

	/** The tracker. */
	Tracker tracker;
	
	/** The torrent info. */
	TorrentInfo torrentInfo;
	
	/** The output file. */
	File outputFile;
	
	/** The peers. */
	ArrayList<Peer> peers;
	
	/** The server socket. */
	ServerSocket serverSocket = null;
	
	/** The listen port. */
	int listenPort = -1;
	
	/** The message queue. */
	LinkedBlockingQueue<PeerMessage> messageQueue = null;
	
	/** The peer id. */
	public static byte[] peerId = Utils.genPeerId();
	
	/** The opt unchoking obj. */
	OptimisticUnchoking optUnchokingObj;
	
	/** The opt unchoke. */
	public Timer optUnchoke;
	
	/** The max unchoked. */
	public int maxUnchoked = 4;
	
	/** The cur unchoked. */
	public int curUnchoked = 0;
	
	/** The is running. */
	boolean isRunning = false;
	
	/** The downloading status. */
	boolean downloadingStatus = true;
	
	/** The our bitfield. */
	public boolean[] ourBitfield;
	
	/** The cur requested bitfield. */
	boolean[] curRequestedBitfield;
	
	/** The piece prevalence. */
	int[] piecePrevalence;
	
	long rateCalulatorTotalDownload =0L;
	
	long rateCalculatorTotalUpload = 0L;
	
	Timer rateCalculatorTimer;
	rateCalculator rtCalc;
	public int numHashFails = 0;
	
	public static boolean haveFullFile = false;
	
	/**
	 * Update piece prevalence.
	 */
	public void updatePiecePrevalence(){
		Arrays.fill(this.piecePrevalence, 0);
		for(Peer peer : this.peers){
			for(int i = 0; i < this.piecePrevalence.length; i++){
				if(peer.bitfield[i] == true){
					this.piecePrevalence[i] += 1;
					if(this.curRequestedBitfield[i])
					this.piecePrevalence[i] += 10;
				}
			}
		}
	}
	
	/**
	 * Gets the rarest piece.
	 *
	 * @param peer the peer
	 * @return the rarest piece
	 */
	public int getRarestPiece(Peer peer){
		updatePiecePrevalence();		
		int min = Integer.MAX_VALUE;
		
		for(int i = 0; i < this.piecePrevalence.length; i++){
			if(this.ourBitfield[i] == false && peer.bitfield[i] == true){
				if(min > this.piecePrevalence[i]){
					min = piecePrevalence[i];
				}
			}
		}	
		
		if(min == Integer.MAX_VALUE){
			return -1;
		}
		
		ArrayList<Integer> indices = new ArrayList<Integer>();
		
		for(int i = 0; i < this.piecePrevalence.length; i++){
			if(this.ourBitfield[i] == false && peer.bitfield[i] == true){
				if(min == this.piecePrevalence[i]){
					indices.add(i);
				}
			}
		}
		
		Random random = new Random();
		int n = random.nextInt(indices.size());
		
		return indices.get(n);
		
	}
	
	
	/**
	 * Instantiates a new manager.
	 *
	 * @param torrentInfo the torrent info
	 * @param file the file
	 */
	public Manager(TorrentInfo torrentInfo, File file) {
		super();

		this.outputFile = file;
		this.torrentInfo = torrentInfo;
		this.peers = new ArrayList<Peer>();
	}



	/**
	 * Pick the port to connect on
	 *
	 * @return the int
	 */
	public int pickPort() {
		for (int i = 6881; i <= 6889; i++) {
			try {
				this.serverSocket = new ServerSocket(i);
				return this.listenPort = i;
			} catch (IOException e) {
				log.severe("Unable to create sSocket at port " + i);
			}
		}
		log.severe("Unable to create sSocket at all. Giving up....");
		return -1;
	}

	
	/**
	 * Close down manager
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void close() throws IOException {
		this.isRunning = false;
		if (this.peers != null) {
			for (Peer p : this.peers) {
				try {
					p.disconnect();
				} catch (Exception e) {
					log.severe("Exception while shutting-down peer " + p + " : " + e);
					continue;
				}
			}
		}
	}

	
	/**
	 * Initializes the manager
	 * @throws IOException 
	 */
	
	public void init() throws IOException {
		this.tracker = new Tracker(this.torrentInfo, Manager.peerId, this.listenPort, this);
		

		this.curRequestedBitfield = new boolean[this.torrentInfo.piece_hashes.length];
		this.piecePrevalence = new int[this.torrentInfo.piece_hashes.length];
			
		Arrays.fill(this.curRequestedBitfield, false);		
		Arrays.fill(this.piecePrevalence, 0);
		
		ArrayList<Peer> allPeers = this.tracker.update("started", this);

		this.tracker.timer = new Timer();
		this.tracker.trackerUpdate = new TrackerUpdate(this.tracker, this);
		this.tracker.timer.schedule(this.tracker.trackerUpdate,  this.tracker.interval * 1000, this.tracker.interval * 1000);
		
		// Check if we have the full file on disk and turn off downloading if true

		
		this.messageQueue = new LinkedBlockingQueue<PeerMessage>();
		
		//Try to setup a new thread that will accept peer connections on a server socket
		//PeerConnect pConnect = new PeerConnect(this);
		//pConnect.start();
		
		if (allPeers != null) {
			int i = 1;
			for (Peer p : allPeers) {
				log.fine(p.toString());
				if (!p.init()) {
					log.severe("Wrong Peer IP or unable to contact peer" + p);
				} else {
					this.peers.add(p);
					RUBTClient.addPeer(i, p.ip, p, p.downloadRate, p.uploadRate, p.weAreChokingPeer);
					++i;
				}
				
			}
			RUBTClient.setNumPeers(peers.size());
		}
		
		if(haveFullFile) {
			this.downloadingStatus = false;
			this.tracker.update("completed", this);
			RUBTClient.log("Finished downloading. Will now seed.");
			Tracker.downloaded = torrentInfo.file_length;
			
			byte[] bitfield = Utils.boolToBitfieldArray(this.ourBitfield);
			Message.Bitfield bitMsg = new Message.Bitfield(bitfield);
			
			// Send the Peers our completed Bitfield!
			for (Peer p : this.peers) {
				try {
					p.sendMessage(bitMsg); 
				} catch (Exception e) {
					log.severe("Exception sending have message to peer " + p + ": " + e);
					continue;
				}
			}
		}
		
		//getUpload();
		this.optUnchoke = new Timer();
		this.optUnchokingObj = new OptimisticUnchoking(this);
		this.optUnchoke.schedule(this.optUnchokingObj, 30000, 30000);
		
		rateCalculatorTimer = new Timer();
		rtCalc = new rateCalculator(this);
		rateCalculatorTimer.schedule(rtCalc, 3000,1000);
		log.fine("Finished connecting to initial peers.");
	}

	/**
	 * Resume download.
	 */
	public void resumeDownload(){
		RUBTClient.log("Resuming download");
		this.isRunning = true;
		this.curRequestedBitfield = new boolean[this.torrentInfo.piece_hashes.length];
		this.piecePrevalence = new int[this.torrentInfo.piece_hashes.length];
		this.peers = new ArrayList<Peer>();
		
		Arrays.fill(this.curRequestedBitfield, false);		
		Arrays.fill(this.piecePrevalence, 0);
		
		ArrayList<Peer> allPeers = this.tracker.update("started", this);
		this.tracker.timer = new Timer();
		this.tracker.trackerUpdate = new TrackerUpdate(this.tracker, this);
		this.tracker.timer.schedule(this.tracker.trackerUpdate,  this.tracker.interval * 1000, this.tracker.interval * 1000);


		if (allPeers != null) {
			for (Peer p : allPeers) {
				log.fine(p.toString());
				
				if (!p.init()) {
					log.severe("Wrong Peer IP or unable to contact peer" + p);
				} else {
					this.peers.add(p);
				}
			}
			RUBTClient.setNumPeers(peers.size());
		}
		
		this.start();
		log.info("Resumed download");
	}
	
	
	/**
	 * Pause download.
	 */
	public void pauseDownload(){
		RUBTClient.log("Pausing download");
		this.isRunning = false;
		
		RUBTClient.setAmountDownloaded(0);
		RUBTClient.setAmountUploaded(0);
		if (this.peers != null) {
			for (Peer p : this.peers) {
				try {
					p.disconnect();
				} catch (Exception e) {
					log.severe("Exception while shutting-down peer " + p + " : " + e);
					continue;
				}
			}
		}
		
		this.peers = null;
		this.tracker.update("stopped", this);
		this.tracker.timer.cancel();
		log.info("Paused download");
	}
	
	/**
	 * Recieve message.
	 * 
	 * Adds message to a queue to be handled
	 *
	 * @param message the message
	 */
	public synchronized void recieveMessage(PeerMessage message) {
		if (message == null) {
			log.warning("Null messages should not be handed to the Manager.");
			return;
		}
		this.messageQueue.add(message);
		log.info("Added Message: " + message);
	}
	
	/**
	 * Checks if is file complete.
	 *
	 * @return true, if is file complete
	 */
	public boolean isFileComplete() {
		for(int i = 0; i < this.ourBitfield.length; i++){
			if(this.ourBitfield[i] == false){
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Decode.
	 * 
	 * Handles decoding messages as they are recieved from the 
	 * peers
	 *
	 * @throws Exception the exception
	 */
	public void decode() throws Exception {

		PeerMessage peerMsg;

		if ((peerMsg = this.messageQueue.take()) != null) {
			log.info("Decoding message: " + peerMsg.message);
			
			switch (peerMsg.message.id) {
				case Message.chokeID:
					peerMsg.peer.peerChokingUs = true;
					break;
				case Message.unchokeID:
					peerMsg.peer.peerChokingUs = false;
					if(peerMsg.peer.weAreInterestedInPeer == true){
						peerMsg.peer.sendMessage(peerMsg.peer.getNextRequest());						
					}
					break;
				case Message.interestedID:
					peerMsg.peer.peerInterestedInUs = true;
					peerMsg.peer.sendMessage(new Message(1, Message.unchokeID));
					break;
				case Message.uninterestedID:
					peerMsg.peer.peerInterestedInUs = false;
					break;
				case Message.haveID:
					if (peerMsg.peer.bitfield != null)
						peerMsg.peer.bitfield[((Message.Have) peerMsg.message).index] = true;
					
					if(peerMsg.peer.bitfield!= null)
					{
					for(int j = 0; j < peerMsg.peer.bitfield.length; j++){
						if(peerMsg.peer.bitfield[j] == true && this.ourBitfield[j] == false){
							peerMsg.peer.sendMessage(Message.INTERESTED);
							peerMsg.peer.weAreInterestedInPeer = true;
							break;
						}
					}
					}	
					
					break;
				case Message.bitfieldID:
					boolean[] bitfield = Utils.bitfieldToBoolArray(((Message.Bitfield)peerMsg.message).bitfield, this.torrentInfo.piece_hashes.length);
					boolean isSeed = true;
					for (int i = 0; i < bitfield.length; ++i) {
						peerMsg.peer.bitfield[i] = bitfield[i];
						if(!bitfield[i])
							isSeed = false;
					}
					
					for(int j = 0; j < peerMsg.peer.bitfield.length; j++){
						if(peerMsg.peer.bitfield[j] == true && this.ourBitfield[j] == false){
							peerMsg.peer.sendMessage(Message.INTERESTED);
							peerMsg.peer.weAreInterestedInPeer = true;
							break;
						}
					}
					if(isSeed)
					{
						RUBTClient.addSeed();
					}
					break;
			case Message.pieceID:
				Message.Have haveMsg = new Message.Have(((Message.Piece)peerMsg.message).index);
				//peerMsg.peer.downloadRate += ((Message.Piece)peerMsg.message).block.length;
				
				if (!this.ourBitfield[((Message.Piece)peerMsg.message).index]) {
						if (peerMsg.peer.appendToPieceAndVerifyIfComplete((Message.Piece)peerMsg.message, this.torrentInfo.piece_hashes, this) == true) {
							this.ourBitfield[((Message.Piece) (peerMsg.message)).index] = true;
							RUBTClient.addProgressBar(1);
							
							for (Peer p : this.peers) {
								try {
								 p.sendMessage(haveMsg); 
								} catch (Exception e) {
									log.severe("Exception sending have message to peer " + p + ": " + e);
									continue;
								}
							}
						}
				}			
				if (this.isFileComplete()) {
						//log.fine("Completed download: shutting down manager");
						this.downloadingStatus = false;
						this.tracker.update("completed", this);
						RUBTClient.log("Finished downloading. Will now seed.");
						RUBTClient.toggleProgressBarLoading();
						return;
				}
			//	totalDownload += (((Message.Piece)peerMsg.message).block.length / (System.currentTimeMillis() - currentDownloadTime)/10.0);
			//	downloadCount++;
				//RUBTClient.setDownloadRate((totalDownload/downloadCount));
				//currentDownloadTime = System.currentTimeMillis();
				//RUBTClient.updatePeerDownRate(peerMsg.peer, peerMsg.peer.downloadRate/10.0);
				rateCalulatorTotalDownload += ((Message.Piece)peerMsg.message).length;
				//Tracker.downloaded+=((Message.Piece)peerMsg.message).length;
				if(!peerMsg.peer.peerChokingUs)
					peerMsg.peer.sendMessage(peerMsg.peer.getNextRequest());
				break;
			case Message.cancelID:
				log.info("Not responsible for cancel....");
				break;
			default:
				break;
			}
		} else
			return;
	}


	/**
	 * Update file.
	 * 
	 * Updates the file with a piece if the piece verifies sha1 check and updates tracker progress/downloaded
	 *
	 * @param piece the piece
	 * @param SHA1Hash the sH a1 hash
	 * @param data the data
	 * @return true, if successful
	 * @throws Exception the exception
	 */
	public boolean UpdateFile(Message.Piece piece, ByteBuffer SHA1Hash,	byte[] data) throws Exception {
		if (verifySHA1(data, SHA1Hash, piece.index)) {
			RandomAccessFile raf = new RandomAccessFile(this.outputFile, "rws");

			raf.seek((this.torrentInfo.piece_length * piece.index));
			raf.write(data);
			raf.close();
			
			Tracker.downloaded += data.length;
			RUBTClient.addProgress(data.length);
			RUBTClient.addAmountDownloaded(data.length);
			RUBTClient.log("Saved piece " + piece.index);
			
			log.info("Wrote to output file piece: " + piece.index);
			return true;
		} else{
			numHashFails++;
			RUBTClient.increaseNumHashFails(numHashFails);
			log.info("Failed to verify piece: " + piece.index);
			return false;
		}
	}

	/**
	 * Read file.
	 * 
	 * Reads a piece from file on the disk in instances
	 * where we are uploading a piece or if we are 
	 * checking the pieces of the file we already have
	 *
	 * @param index the index
	 * @param offset the offset
	 * @param length the length
	 * @return the byte[]
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public byte[] readFile(int index, int offset, int length) throws IOException {
		RandomAccessFile raf = new RandomAccessFile(this.outputFile, "r");
		byte[] data = new byte[length];
		
		raf.seek(this.torrentInfo.piece_length * index + offset);
		raf.readFully(data);
		raf.close();
		
		return data;
	}

	/* (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {
		log.info("##### Manager has started. #####");
		while (this.isRunning == true) {
			try {
				decode();
			} catch (Exception e) {
				log.severe("Error decoding message " + e);
			}
		}
		log.info("##### Manager has finished. #####");
	}
	
	/**
	 * Verify SHA1.
	 * 
	 * Verifies the SHA1 of a piece of a file against the info hash 
	 * values
	 *
	 * @param piece the piece
	 * @param SHA1Hash the sH a1 hash
	 * @return true, if successful
	 */
	public static boolean verifySHA1(byte[] piece, ByteBuffer SHA1Hash, int index) {
			MessageDigest SHA1;

			try {
				SHA1 = MessageDigest.getInstance("SHA-1");
			} catch (NoSuchAlgorithmException e) {
				log.severe("Unable to find SHA1 Algorithm");
				RUBTClient.log("Unable to find SHA1 Algorithm");
				return false;
			}
			
			SHA1.update(piece);
			byte[] pieceHash = SHA1.digest();

			if (Arrays.equals(pieceHash, SHA1Hash.array())) {
				log.info("Verified - " + SHA1.digest() + " - " + SHA1Hash.array() + " for index " + index);
				return true;
			} else {
				return false;							
			}
	}

	
	/**
	 * Set the download upload.
	 * 
	 * Writes the downloaded uploaded stats to a file when 
	 * we stop the program
	 *
	 * @param download the download
	 * @param upload the upload
	 */
	public void setDownloadUpload(int download, int upload) {
		String down = Integer.toString(download);
		String up = Integer.toString(upload);

		try {
			File tFile = new File(outputFile.getName() + ".stats");
			BufferedWriter out = new BufferedWriter(new FileWriter(tFile));
			out.write(up);
			out.close();
		} catch (IOException e) {
			log.severe("Error: Unable to write tracker stats to a file.");
		}
	}

	
	/**
	 * Check pieces.
	 * 
	 * Check the pieces of the file we have on disk so that we can increment
	 * downloaded stats and send appropriate bitfield
	 *
	 * @return the boolean[]
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public boolean[] checkPieces() throws IOException {

		int numPieces = this.torrentInfo.piece_hashes.length;
		int pieceLength = this.torrentInfo.piece_length;
		int fileLength = this.torrentInfo.file_length;
		ByteBuffer[] pieceHashes = this.torrentInfo.piece_hashes;
		int lastPieceLength = fileLength % pieceLength == 0 ? pieceLength : fileLength % pieceLength;

		byte[] piece = null;
		boolean[] verifiedPieces = new boolean[numPieces];

		for (int i = 0; i < numPieces; i++) {
			if (i != numPieces - 1) {
				piece = new byte[pieceLength];
				piece = readFile(i, 0, pieceLength);
			} else {
				piece = new byte[lastPieceLength];
				piece = readFile(i, 0, lastPieceLength);
			}
			
			if (verifySHA1(piece, pieceHashes[i], i)) {
				verifiedPieces[i] = true;
				RUBTClient.log("Verified piece " + i);
			}
		}
		
		for(int i = 0; i < verifiedPieces.length; i++){
			if(verifiedPieces[i] != false){
				RUBTClient.addProgress(torrentInfo.piece_length);
			}
			
			if(i == verifiedPieces.length - 1){
				this.downloadingStatus = false;
			}
		}
		
		return verifiedPieces;
	}

	/**
	 * Gets the download upload.
	 * 
	 * Grabs the download/upload stats from the file
	 *
	 * @return the download upload
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void getUpload() throws IOException {
		String input;
		File tFile = new File(outputFile.getName() + ".stats");
		BufferedReader in = null;

		if (tFile.exists()) {
			in = new BufferedReader(new FileReader(tFile));
			input = in.readLine();
			Tracker.uploaded = Integer.parseInt(input);
			log.info("Increased upload amount");
			RUBTClient.addAmountUploaded(Tracker.uploaded);
		} else {
			Tracker.downloaded = 0;
			Tracker.uploaded = 0;
		}
		if(in != null) {
			in.close();
		}
	}
}
