import java.util.TimerTask;

public class rateCalculator extends TimerTask{
	
	Manager manager;
	double updateInterval = 3000.0;
	long srtotalDownload =0L;
	long srtotalUpload = 0L;
	
	rateCalculator(Manager manager){
		this.manager = manager;

	}
	
	public void run(){
		
		
		
		RUBTClient.setDownloadRate(manager.rateCalulatorTotalDownload / updateInterval);
		manager.rateCalulatorTotalDownload=0L;
		
		RUBTClient.setUploadRate(manager.rateCalculatorTotalUpload / updateInterval);
		manager.rateCalculatorTotalUpload=0L;
		
		for (Peer p : manager.peers)
		{
			RUBTClient.updatePeerDownRate(p, p.rateCalulatorTotalDownload/updateInterval);
			p.rateCalulatorTotalDownload=0L;
			
			RUBTClient.updatePeerUpRate(p, p.rateCalculatorTotalUpload/updateInterval);
			p.rateCalculatorTotalUpload=0L;
		}
		if(Tracker.downloaded>0)
		RUBTClient.setShareRatio((double)Tracker.uploaded /Tracker.downloaded);
		
		
		
	}
}