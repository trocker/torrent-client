/**
 * The Class PeerMessage.
 * 
 * Peer , Message tuple.
 * 
 * @author Deepak, Mike, Josh
 */
public class PeerMessage{
	
	/** The message. */
	public Message message;
	
	/** The peer. */
	public Peer peer;
	
	/**
	 * Instantiates a new peer message.
	 *
	 * @param peer the peer
	 * @param message the message
	 */
	PeerMessage(Peer peer, Message message){
		this.message = message;
		this.peer = peer;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString(){
		return this.peer.toString() + " " + this.message.toString();
	}
}