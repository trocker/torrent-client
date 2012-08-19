import java.util.ArrayList;
import java.util.Arrays;
import java.util.TimerTask;

/**
 * The Class TrackerUpdate updates our current status to the 
 * swarm of peers
 * 
 * @author Deepak, Mike, Josh
 */
public class TrackerUpdate extends TimerTask{
	
	/** The tracker. */
	Tracker tracker;
	
	/** The manager. */
	Manager manager;
	
	/**
	 * Instantiates a new tracker update.
	 *
	 * @param tracker the tracker
	 * @param manager the manager
	 */
	TrackerUpdate (Tracker tracker, Manager manager){
		this.tracker = tracker;
		this.manager = manager;
	}
	
	/* (non-Javadoc)
	 * @see java.util.TimerTask#run()
	 */
	public void run(){
		ArrayList<Peer> peers = this.tracker.update("", this.manager);
		boolean isAlreadyAPeer = false;
		
		for(Peer p : peers){
			for(Peer q : manager.peers){
				if(Arrays.equals(p.peerId, q.peerId)){
					isAlreadyAPeer = true;
					break;
				}
			}
			if(isAlreadyAPeer == false){
				this.manager.peers.add(p);
			} else {
				isAlreadyAPeer = false;
			}
		}		
	}
}