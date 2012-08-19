import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * The Class Peer Connect.
 * 
 * This class is responsible for creating a connection with 
 * other peers by creating and running a server socket.
 * 
 * @author Deepak, Mike, Josh
 */
public class PeerConnect extends Thread{
	
	
	/** The Constant log. */
	public static final Logger log = Log2.getLogger(PeerUploader.class);

	/** The peer object we will be adding to our list of peers */
	public Peer peer;
	
	/** Server connection for the peer to connect to */
	public ServerSocket peerConnectSocket;
	
	/** Socket we accept connection on */
	public Socket socket;
	
	/** port to use to connect to the peer */
	public int port;
	
	/** The manager */
	Manager manager;
	
	/** The output stream we will be writing our data on */
	DataOutputStream os;
	
	/** Input stream to read data on */
	DataInputStream is;
	
	/** the id of the peer we are connecting to */
	byte[] peerId;
	
	/** The ip of the peer we are connecting to */
	String ip;
	
	/**
	 * Instantiates a new peer connection.
	 *
	 * @param peer the peer
	 * @param manager the manager
	 */
	PeerConnect(Manager manager){
		
		this.manager = manager;
		for (int i = 6881; i <= 6889; i++) {
			try {
				peerConnectSocket = new ServerSocket(i);
				RUBTClient.log("Established server socket with peer on port " + i);
				log.info("Established server socket with peer on port " + i);
			} catch (Exception e) {
				RUBTClient.log("Couldn't establish server socket with peer on port " + i);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	public void run(){
		try {
			socket = peerConnectSocket.accept();
			is = new DataInputStream(socket.getInputStream());
			os = new DataOutputStream(socket.getOutputStream());
			
			os.write(Peer.generateHandshake(manager.peerId,	manager.torrentInfo.info_hash.array()));
			os.flush();

			byte[] response = new byte[68];
			
			this.socket.setSoTimeout(1000);
			is.readFully(response);		
			this.socket.setSoTimeout(1000);
	
			RUBTClient.log("Handshake Response: " + Arrays.toString(response));
			//Parse the handshake response here to get their info
			InetAddress peerIP = socket.getInetAddress();
			ip = peerIP.toString();
			port = socket.getPort();
			peerId = getPeerID(response);
			
			//Create the peer, initialize it and then add it to our list
			peer = new Peer(peerId, port, ip, manager);
			peer.init();
			manager.peers.add(peer);
		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Grabs the byte array of the peer ID 
	 * from the handshake response byte array
	 *
	 * @param byte[] the handshake byte array response
	 * 
	 * @return byte[] the byte array of the peer ID
	 */
	public byte[] getPeerID(byte[] response) {
		byte[] peerID = new byte[20];
		System.arraycopy(response, 48, peerID, 0, 20);
		
		return peerID;
	}
}