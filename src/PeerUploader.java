import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/**
 * The Class PeerUploader.
 * 
 * @author Deepak, Mike, Josh
 */
public class PeerUploader extends Thread{
	
	
	/** The Constant log. */
	public static final Logger log = Log2.getLogger(PeerUploader.class);
	
	/** The upload queue. */
	LinkedBlockingQueue<Message.Request> uploadQueue = null;

	/** The peer. */
	public Peer peer;
	
	/** The is running. */
	public boolean isRunning = false;
	
	/**
	 * Instantiates a new peer uploader.
	 *
	 * @param peer the peer
	 */
	PeerUploader(Peer peer){
		this.peer = peer;
		this.uploadQueue = new LinkedBlockingQueue<Message.Request>();
	}
	
	/**
	 * Recieve request.
	 *
	 * @param message the message
	 */
	public void recieveRequest(Message.Request message) {
		if (message == null) {
			log.warning("Null messages should not be handed to the uploader.");
			return;
		}
		
		this.uploadQueue.add(message);
		log.info("Added Message: " + message);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	public void run(){

		Message.Request requestMessage = null;
		while(this.isRunning == true){
			if(this.peer.weAreChokingPeer == false){
				try {
					if (( requestMessage = this.uploadQueue.take()) != null) {
						try {
							byte[] dataToUpload;
							dataToUpload = this.peer.manager.readFile(requestMessage.index, requestMessage.start, requestMessage.mlength);
							Tracker.uploaded += dataToUpload.length;
							this.peer.uploadRate += dataToUpload.length;
							this.peer.sendMessage(new Message.Piece(requestMessage.index, requestMessage.start, dataToUpload));
							
							peer.totalUpload += dataToUpload.length;
							peer.rateCalculatorTotalUpload += dataToUpload.length;
							peer.manager.rateCalculatorTotalUpload+= dataToUpload.length;
							//peer.manager.tracker.uploaded += dataToUpload.length;
							RUBTClient.addAmountUploaded(dataToUpload.length);
						} catch(Exception e){
							log.severe("Error uploading to Peer: " + this.peer);
						}
					}
				} catch (InterruptedException e) {
					break;
				}
				
			}
			else{
				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
					break;
				}
			}
		}
	}
}