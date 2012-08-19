import java.io.IOException;
import java.util.logging.Logger;

/**
 * The Class PeerKeepAlive.
 * 
 * @author Deepak, Mike, Josh
 */
public class PeerKeepAlive extends Thread{
	
	/** The Constant log. */
	public static final Logger log = Log2.getLogger(PeerKeepAlive.class);
	
	/** The peer. */
	public Peer peer;
	
	/** The interval. */
	public int interval = 120000;
	
	/** The is running. */
	public boolean isRunning = false;
	
	/**
	 * Instantiates a new peer keep alive.
	 *
	 * @param peer the peer
	 */
	PeerKeepAlive(Peer peer){
		this.peer = peer;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	public void run(){
		while(this.isRunning){
			try {
				Thread.sleep(interval);
			} catch (InterruptedException e) {
				continue;
			}
			try {
				peer.sendMessage(Message.KEEP_ALIVE);
			} catch (IOException e) {
				log.severe("Error sending keepalive to peer: " + this.peer);
			}
		}
	}	
}