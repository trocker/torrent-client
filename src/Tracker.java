import GivenTools.*;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.logging.Logger;

/**
 * The Class Tracker handles announcing our status to the swarm of 
 * peers and getting updated lists of peers that are in the swarm.
 * 
 * @author Deepak, Mike, Josh
 */
public class Tracker {
	
	/** The Constant log. */
	private static final Logger log = Log2.getLogger(Tracker.class);

	/** The Constant KEY_FAILURE. */
	public static final ByteBuffer KEY_FAILURE = ByteBuffer.wrap(new byte[] {
			'f', 'a', 'i', 'l', 'u', 'r', 'e', ' ', 'r', 'e', 'a', 's', 'o',
			'n' });

	/** The Constant KEY_PEERS. */
	public static final ByteBuffer KEY_PEERS = ByteBuffer.wrap(new byte[] {
			'p', 'e', 'e', 'r', 's' });

	/** The Constant KEY_IP. */
	public static final ByteBuffer KEY_IP = ByteBuffer.wrap(new byte[] { 'i',
			'p' });

	/** The Constant KEY_PORT. */
	public static final ByteBuffer KEY_PORT = ByteBuffer.wrap(new byte[] { 'p',
			'o', 'r', 't' });

	/** The Constant KEY_PEERID. */
	public static final ByteBuffer KEY_PEERID = ByteBuffer.wrap(new byte[] {
			'p', 'e', 'e', 'r', ' ', 'i', 'd' });

	/** The Constant KEY_INTERVAL. */
	public static final ByteBuffer KEY_INTERVAL = ByteBuffer.wrap(new byte[] {
			'i', 'n', 't', 'e', 'r', 'v', 'a', 'l' });

	/** The Constant requestSize. */
	public static final int requestSize = 16000;
	
	/** The infohash. */
	public byte[] infohash;
	
	/** The peerid. */
	public byte[] peerid;
	
	/** The uploaded. */
	public static int uploaded;
	
	/** The downloaded. */
	public static int downloaded;
	
	/** The left. */
	public int left;
	
	/** The port. */
	private int port;
	
	/** The announce. */
	private URL announce;
	
	/** The event. */
	private String event;
	
	/** The request string. */
	private URL requestString;
	
	/** The is running. */
	public boolean isRunning = true;
	
	/** The interval. */
	public int interval = -1;
	
	/** The manager. */
	public Manager manager;
	
	/** The timer. */
	public Timer timer;
	
	/** The tracker update. */
	public TrackerUpdate trackerUpdate;

	/**
	 * Instantiates a new tracker.
	 *
	 * @param torrentData the torrent data
	 * @param peerId the peer id
	 * @param port the port
	 * @param manager the manager
	 */
	Tracker(TorrentInfo torrentData, final byte[] peerId, final int port, Manager manager) {
		this.infohash = torrentData.info_hash.array();
		this.peerid = peerId;
		this.left = torrentData.file_length;
		this.port = port;
		this.event = null;
		this.announce = torrentData.announce_url;
		this.requestString = createURL(torrentData.announce_url);
		this.manager = manager;
	}

	// TODO: Correct this hook for shutdown/stop gotta figure this out
	/*
	 * Runtime.getRuntime().addShutdownHook(new Thread() { public void run() {
	 * Tracker.this.update("stopped", manager); }
	 * 
	 * });
	 */

	/**
	 * Update.
	 *
	 * @param event the event
	 * @param manager the manager
	 * @return the array list
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<Peer> update(String event, Manager manager) {
		this.event = event;
		
		if(this.event.equals("started")){
			try {
				manager.getUpload();
				
			} catch (IOException e) {
				this.downloaded = 0;
				this.uploaded = 0;
			}
		} else {
			manager.setDownloadUpload(this.downloaded, this.uploaded);
		}

		this.requestString = createURL(this.announce);
		
		HashMap<ByteBuffer, Object> response = null;

		try {
			byte[] trackerResponse = sendGETRecieveResponse();

			if (trackerResponse == null) {
				log.severe("Error communicating with tracker");
				return null;
			}
			response = (HashMap<ByteBuffer, Object>) Bencoder2.decode(trackerResponse);
		} catch (BencodingException e1) {
			log.severe("Error decoding tracker response");
			return null;
		}

		if (response.containsKey(KEY_FAILURE)) {
			log.severe("Failure from the tracker.");
			return null;
		}

		ArrayList<Peer> peers = new ArrayList<Peer>();

		this.interval = (Integer)response.get(KEY_INTERVAL);

		List<Map<ByteBuffer, Object>> peersList = (List<Map<ByteBuffer, Object>>) response
				.get(KEY_PEERS);

		if (peersList == null) {
			log.severe("List of peers given by tracker is null");
			return null;
		}

		for (Map<ByteBuffer, Object> rawPeer : peersList) {
			int peerPort = ((Integer) rawPeer.get(KEY_PORT)).intValue();
			byte[] peerId = ((ByteBuffer) rawPeer.get(KEY_PEERID)).array();
			String ip = null;
			try {
				ip = new String(((ByteBuffer) rawPeer.get(KEY_IP)).array(),
						"ASCII");
			} catch (UnsupportedEncodingException e) {
				log.severe("Unable to parse encoding");
				continue;
			}

			peers.add(new Peer(peerId, peerPort, ip, manager));
		}

		if(this.interval < 0){
			this.interval = 120000;
		}
		
		log.fine("Converted: " + peers);
		return peers;
	}

	/**
	 * Send get recieve response.
	 *
	 * @return the byte[]
	 */
	public byte[] sendGETRecieveResponse() {
		try {
			HttpURLConnection httpConnection = (HttpURLConnection)this.requestString.openConnection();
			DataInputStream dataInputStream = new DataInputStream(httpConnection.getInputStream());

			int dataSize = httpConnection.getContentLength();
			byte[] retArray = new byte[dataSize];

			dataInputStream.readFully(retArray);
			dataInputStream.close();

			return retArray;
		} catch (IOException e) {
			log.severe("Error communicating with tracker");
			return null;
		}
	}

	/**
	 * Creates the url.
	 *
	 * @param announceURL the announce url
	 * @return the url
	 */
	public URL createURL(URL announceURL) {
		String newURL = announceURL.toString();
		newURL += "?info_hash=" + Utils.toHexString(this.infohash)
				+ "&peer_id=" + Utils.toHexString(this.peerid) + "&port="
				+ this.port + "&uploaded=" + this.uploaded + "&downloaded="
				+ this.downloaded + "&left=" + this.left;
		if (this.event != null)
			newURL += "&event=" + this.event;

		try {
			return new URL(newURL);
		} catch (MalformedURLException e) {
			log.severe("Unable to create URL");
			return null;
		}
	}
}