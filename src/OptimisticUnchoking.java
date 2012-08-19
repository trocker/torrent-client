import java.util.ArrayList;
import java.util.Random;
import java.util.TimerTask;

/**
 * The Class OptimisticUnchoking.
 * 
 * @author Deepak, Mike, Josh
 */
public class OptimisticUnchoking extends TimerTask{
	
	/** The manager. */
	Manager manager;
	
	/**
	 * Instantiates a new optimistic unchoking.
	 *
	 * @param manager the manager
	 */
	OptimisticUnchoking(Manager manager){
		this.manager = manager;
	}
	
	/* (non-Javadoc)
	 * @see java.util.TimerTask#run()
	 */
	public void run(){
		
		Peer worst = null;
		long worstRate = Long.MAX_VALUE;
		for(Peer p : manager.peers){
			if(p.weAreChokingPeer == false){
				if(manager.downloadingStatus == true){
					if(worstRate > p.totalDownload){
						worst = p;
						worstRate = p.totalDownload;
					}
				} else {
					if(worstRate > p.totalUpload){
						worst = p;
						worstRate = p.totalUpload;
					}
				}
			}
		}
		
		ArrayList<Integer> indices = new ArrayList<Integer>();
		for(Peer p : manager.peers){
			if(p.weAreChokingPeer == true){
				indices.add(manager.peers.indexOf(p));
			}
		}
		
		System.out.println(indices);
		
		Random random = new Random();
		int n = random.nextInt(indices.size());
		
		worst.weAreChokingPeer = true;
		RUBTClient.updatePeerChokeStatus(worst, true);
		manager.peers.get(indices.get(n)).weAreChokingPeer = false;
		RUBTClient.updatePeerChokeStatus(manager.peers.get(indices.get(n)),false);
		
		worst.choke();
		manager.peers.get(indices.get(n)).unchoke();
		
		for(Peer p : manager.peers){
			p.totalDownload = 0;
			p.totalDownload = 0;
		}
		
	}
}