import java.awt.EventQueue;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.JButton;
import javax.swing.JLabel;
import java.awt.Font;
import javax.swing.JTextPane;
import javax.swing.table.DefaultTableModel;
import javax.swing.JScrollPane;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;

import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JInternalFrame;
import javax.swing.border.BevelBorder;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.logging.Logger;


import GivenTools.ToolKit;
import GivenTools.TorrentInfo;


/**
 * The Class RUBTClient is the front end of the torrent client
 * that the user interacts with. Here, the user can pick the torrent
 * file that they would like to use as well as see live stats
 * as their torrent file is downloading and uploading.
 * 
 * @author Deepak, Mike, Josh
 */
public class RUBTClient {

	/** The client frame. */
	private static JFrame clientFrame;
	
	
	/** The file. */
	public static File file;
	
	/** The info table. */
	private static JTable infoTable;
	
	/** The peer table. */
	private static JTable peerTable;
	
	/** The tracker table. */
	private static JTable trackerTable;
	
	/** The gui logger. */
	private static JTextPane guiLogger;
	
	/** The progress bar. */
	private static JProgressBar progressBar;
	
	/** The Constant log. */
	private static final Logger log = Log2.getLogger(RUBTClient.class);
	
	/** The torrent reader. */
	public static TorrentReader torrentReader = new TorrentReader();
	
	/** The torrent info. */
	public static TorrentInfo torrentInfo = null;
	
	/** The manager. */
	public static Manager manager = null;
	
	/** Check is the program is paused */
	public static boolean isPaused = false;
	
	/** File that we are writing to */
	public static File outputFile;
	
	/** Torrent file we are using */
	public static File torrentFile;
	
	/** Number of hash fails that occur */
	public int numHashFails = 0;
	
	public long amountUploaded =0L;
	
	public long amountDownloaded = 0L;
	
	/** Check the command line args and make sure we have two of them */
	public static boolean checkNumArguments(String[] args){
		if(args.length != 2){
			log.fine("Invalid number of command line arguments");
			return false;
		}
		return true;
	}

	/**
	 * Launch the GUI application.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {	  
	
		if(!checkNumArguments(args)){
			System.out.println("Invalid number of command line arguments");
			return;
		}

		final String torrentFileName = args[0];
		final String outputFileName = args[1];

		outputFile = new File(outputFileName);
		torrentFile = new File(torrentFileName);

		if(!torrentFile.exists()){
			log.info("No such torrent file exists");
			return;
		}
		
		TorrentReader torrentReader = new TorrentReader();
		

		if((torrentInfo = torrentReader.parseTorrentFile(torrentFile)) == null){
			log.severe("Unable to parse torrent file; Exiting.");
			return;
		} else{
			log.info("Torrent file parsed successfully");
		}

		ToolKit.print(torrentInfo.torrent_file_map);
		

		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					RUBTClient window = new RUBTClient();
					window.clientFrame.setVisible(true);
					window.log("Hello");
					
					window.clientFrame.addWindowListener(new WindowAdapter() {
						   public void windowClosing(WindowEvent evt) {
						     onExit();
						   }
					});
	
					Manager manager = new Manager(torrentInfo, outputFile);
					
					boolean[] checkPieces;
					boolean lastPieceSmaller = false;
					
					if(outputFile.exists()){
					
						if(torrentInfo.file_length % torrentInfo.piece_length != 0) {
							checkPieces = new boolean[torrentInfo.file_length % torrentInfo.piece_length + 1];
							lastPieceSmaller = true;
						}
						else {
							checkPieces = new boolean[torrentInfo.file_length % torrentInfo.piece_length];
						}
						
						checkPieces = Utils.checkPieces(torrentInfo, outputFile);
						manager.ourBitfield = checkPieces;
						log("Using the verified pieces BitField");
						
						boolean haveFullFile = true;
						for(int i = 0; i < manager.ourBitfield.length; i++){
							if(manager.ourBitfield[i] == false){
								haveFullFile = false;
							}
						}
						
						if(haveFullFile) {
							manager.downloadingStatus = false;
							addProgressBar(torrentInfo.piece_hashes.length);
							manager.haveFullFile = true;
						}
						else {
							manager.downloadingStatus = true;
							for(int i=0;i<checkPieces.length;++i){
								if(checkPieces[i]){
									Tracker.downloaded+=torrentInfo.piece_length;
									addProgressBar(1);
								}
							}
						}
					}
					else {
						outputFile.createNewFile();
						manager.ourBitfield = new boolean[torrentInfo.piece_hashes.length];
						Arrays.fill(manager.ourBitfield, false);
					}
					
					manager.init();
					manager.isRunning = true;
					manager.start();
					
					setGuiTrackerInfo();	
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	
	
	/**
	 * Create the application.
	 */
	public RUBTClient() {
		initialize();
		setNumSeeds(0);
	}
	
	/**
	 * Stop the program
	 */
	private void Stop()
	{
		if(!isPaused)
		{
		if(manager != null) {
			manager.pauseDownload();
			isPaused = true;
			toggleProgressBarLoading();
		}
		}
	}
	
	/**
	 * Start the program
	 * @throws IOException 
	 */
	private static void Start() throws IOException
	{
		if(isPaused){
			toggleProgressBarLoading();
			manager.resumeDownload();
			isPaused = false;
				
		}
		else {
			File output = new File(torrentInfo.file_name);
			manager = new Manager(torrentInfo, output);
				
			manager.init();
			manager.isRunning = true;
			manager.start();
			//toggleProgressBarLoading();
		}
		setGuiTrackerInfo();
	}
	
	
	/**
	 * Adds the amount downloaded.
	 *
	 * @param amountDownloaded the amount downloaded
	 */
	public static synchronized void addAmountDownloaded(int amountDownloaded)
	{
		infoTable.setValueAt(((Integer)infoTable.getValueAt(3, 1)) + amountDownloaded, 3, 1);
	}
	
	/**
	 * Sets the amount downloaded.
	 *
	 * @param amountDownloaded the new amount downloaded
	 */
	public static synchronized void setAmountDownloaded(int amountDownloaded)
	{
		infoTable.setValueAt(amountDownloaded, 3, 1);
	}
	
	/**
	 * Adds the amount uploaded.
	 *
	 * @param amountUploaded the amount uploaded
	 */
	public static synchronized void addAmountUploaded(int amountUploaded)
	{
		infoTable.setValueAt(((Integer)infoTable.getValueAt(4, 1)) + amountUploaded, 4, 1);
	}
	
	/**
	 * Sets the amount uploaded.
	 *
	 * @param amountUploaded the new amount uploaded
	 */
	public static synchronized void setAmountUploaded(int amountUploaded)
	{
		infoTable.setValueAt(amountUploaded, 4, 1);
	}
	
	/**
	 * Sets the download rate.
	 *
	 * @param downloadRate the new download rate
	 */
	public static synchronized void setDownloadRate(double downloadRate)
	{
		infoTable.setValueAt(downloadRate+ " Kb/s", 1, 1);
	}
	
	/**
	 * Sets the upload rate.
	 *
	 * @param uploadRate the new upload rate
	 */
	public static synchronized void setUploadRate(double uploadRate)
	{
		infoTable.setValueAt(uploadRate+ " Kb/s", 2, 1);
	}
	
	/**
	 * Sets the num peers.
	 *
	 * @param numPeers the new num peers
	 */
	public static synchronized void setNumPeers(int numPeers)
	{
		infoTable.setValueAt(numPeers, 5, 1);
	}
	
	/**
	 * Sets the num seeds.
	 *
	 * @param numSeeds the new num seeds
	 */
	public static synchronized void setNumSeeds(int numSeeds)
	{
		infoTable.setValueAt(numSeeds, 6, 1);
	}
	
	/**
	 * Sets the num seeds.
	 *
	 * @param numSeeds the new num seeds
	 */
	public static synchronized void addSeed()
	{
		infoTable.setValueAt(((Integer)infoTable.getValueAt(6, 1)) + 1, 6, 1);
	}
	
	
	/**
	 * Sets the share ratio.
	 *
	 * @param shareRatio the new share ratio
	 */
	public static synchronized void setShareRatio(double shareRatio)
	{
		infoTable.setValueAt(shareRatio, 8, 1);
	}
	
	/**
	 * Increase num hash fails.
	 */
	public static synchronized void increaseNumHashFails(int num)
	{
		infoTable.setValueAt(num, 7, 1);
	}
	
	/**
	 * Adds the progress.
	 *
	 * @param toAdd the to add
	 */
	public static synchronized void addProgress(int toAdd)
	{
		infoTable.setValueAt((Integer)infoTable.getValueAt(0,1) + toAdd, 0, 1);
	}
	
	/**
	 * Sets the file name.
	 *
	 * @param fileName the new file name
	 */
	public static synchronized void setFileName(String fileName)
	{
		trackerTable.setValueAt(fileName, 0, 1);
	}
	
	/**
	 * Sets the file length.
	 *
	 * @param length the new file length
	 */
	public static synchronized void setFileLength(int length)
	{
		trackerTable.setValueAt(length, 1, 1);
	}
	
	/**
	 * Sets the piece length.
	 *
	 * @param length the new piece length
	 */
	public static synchronized void setPieceLength(int length)
	{
		trackerTable.setValueAt(length, 2, 1);
	}
	
	/**
	 * Sets the num pieces.
	 *
	 * @param num the new num pieces
	 */
	public static synchronized void setNumPieces(int num)
	{
		trackerTable.setValueAt(num, 3, 1);
	}
	
	/**
	 * Log.
	 *
	 * @param text the text
	 */
	public static synchronized void log(String text)
	{
		guiLogger.setText(guiLogger.getText() +text + "\n");
	}
	
	/**
	 * Adds the progress bar.
	 *
	 * @param progress the progress
	 */
	public static synchronized void addProgressBar(int progress)
	{
		progressBar.setValue(progressBar.getValue() + progress);
	}
	
	/**
	 * Toggle progress bar loading.
	 */
	public static synchronized void toggleProgressBarLoading()
	{
		progressBar.setIndeterminate(!progressBar.isIndeterminate());
	}
	
	/**
	 * Adds the peer.
	 *
	 * @param i the row we want to add to
	 * @param ip the ip
	 * @param p the name of the peer
	 * @param downloadRate the download rate
	 * @param uploadRate the upload rate
	 * @param chokeStatus the choke status of the peer
	 */
	public static synchronized void addPeer(int i, String ip, Peer p, double downloadRate, double uploadRate, boolean chokeStatus)
	{
		if(i>peerTable.getRowCount()-1) {
			( (DefaultTableModel) peerTable.getModel() ).addRow(new Object[]{p, ip, downloadRate, uploadRate, chokeStatus});
		}
		else {
			peerTable.setValueAt(p, i, 0);
			peerTable.setValueAt(ip, i, 1);
			peerTable.setValueAt(downloadRate, i, 2);
			peerTable.setValueAt(uploadRate, i, 3);
			peerTable.setValueAt(chokeStatus, i, 4);
		}
	}
	
	/**
	 * Update peer down rate.
	 *
	 * @param p the peer
	 * @param downRate the download rate
	 */
	public static synchronized void updatePeerDownRate(Peer p, double downRate) {
		for(int j = 0; j < peerTable.getRowCount(); ++j) {
			Object obj1 = getData(peerTable, j, 0);
			if(obj1 == p) {
				peerTable.setValueAt(downRate, j, 2);
			}
		}
	}
	
	/**
	 * Update peer up rate.
	 *
	 * @param p the peer
	 * @param d the upload rate
	 */
	public static synchronized void updatePeerUpRate(Peer p, double d) {
		for(int j = 0; j < peerTable.getRowCount(); ++j) {
			Object obj1 = getData(peerTable, j, 0);
			if(obj1 == p) {
				peerTable.setValueAt(d, j, 3);
			}
		}
	}
	
	/**
	 * Update peer choke status.
	 *
	 * @param p the p
	 * @param choke the choke status of the peer
	 */
	public static synchronized void updatePeerChokeStatus(Peer p, boolean choke) {
		for(int j = 0; j < peerTable.getRowCount(); ++j) {
			Object obj1 = getData(peerTable, j, 0);
			
			if(obj1!= null)
			{
			if(p.equals(obj1)) {
				if(choke)
				{
					peerTable.setValueAt(true, j, 4);
				}
				else
				{
					peerTable.setValueAt(false, j, 4);
				}
			}
			
			}
		}
	}
	
	/**
	 * Initialize the contents of the frame.
	 */
	@SuppressWarnings("serial")
	private void initialize() {
		clientFrame = new JFrame();
		clientFrame.setTitle("RUBT Client - Group 2");
		clientFrame.setBounds(100, 100, 744, 559);
		clientFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		clientFrame.getContentPane().setLayout(null);
		
		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		tabbedPane.setBounds(10, 11, 461, 445);
		clientFrame.getContentPane().add(tabbedPane);
		
		infoTable = new JTable();
		infoTable.setFillsViewportHeight(true);
		infoTable.setBorder(null);
		infoTable.setFont(new Font("Tahoma", Font.PLAIN, 15));
		infoTable.setShowVerticalLines(false);
		infoTable.setModel(new DefaultTableModel(
			new Object[][] {
				{"Progress:", new Integer(0)},
				{"Download Rate:", null},
				{"Upload Rate:", null},
				{"Downloaded:", new Integer(0)},
				{"Uploaded:", new Integer(0)},
				{"Peers:", null},
				{"Seeds:", "0"},
				{"Wasted(Hashfails):", "0"},
				{"Share Ratio:", null},
			},
			new String[] {
				"Description", "New column"
			}
		) {
			boolean[] columnEditables = new boolean[] {
				false, false
			};
			public boolean isCellEditable(int row, int column) {
				return columnEditables[column];
			}
		});
		infoTable.getColumnModel().getColumn(0).setPreferredWidth(96);
		tabbedPane.addTab("Information", null, infoTable, null);
		
		trackerTable = new JTable();
		trackerTable.setFont(new Font("Tahoma", Font.PLAIN, 15));
		trackerTable.setModel(new DefaultTableModel(
			new Object[][] {
				{"File Name", null},
				{"Length", null},
				{"Piece Length", null},
				{"Pieces", null},
			},
			new String[] {
				"Tracker Property", "Value"
			}
		) {
			boolean[] columnEditables = new boolean[] {
				false, true
			};
			public boolean isCellEditable(int row, int column) {
				return columnEditables[column];
			}
		});
		tabbedPane.addTab("File Info", null, trackerTable, null);
		
		peerTable = new JTable();
		peerTable.setFont(new Font("Tahoma", Font.PLAIN, 14));
		peerTable.setBorder(null);
		peerTable.setModel(new DefaultTableModel(
			new Object[][] {
				{"Peer ID", "IP", "Download Rate", "Upload Rate", "Choke Status"},
				{null, null, null, null, null},
				{null, null, null, null, null},
				{null, null, null, null, null},
				{null, null, null, null, null},
				{null, null, null, null, null},
				{null, null, null, null, null},
				{null, null, null, null, null},
				{null, null, null, null, null},
				{null, null, null, null, null},
				{null, null, null, null, null},
				{null, null, null, null, null},
				{null, null, null, null, null},
				{null, null, null, null, null},
			},
			new String[] {
				"Peer ID", "IP", "Download Rate", "Upload Rate", "Choke Status"
			}
		) {
			boolean[] columnEditables = new boolean[] {
				true, true, false, true, true
			};
			public boolean isCellEditable(int row, int column) {
				return columnEditables[column];
			}
		});
		peerTable.getColumnModel().getColumn(0).setPreferredWidth(140);
		peerTable.getColumnModel().getColumn(1).setPreferredWidth(81);
		peerTable.getColumnModel().getColumn(2).setPreferredWidth(94);
		tabbedPane.addTab("Peer Information", null, peerTable, null);
		
		progressBar = new JProgressBar();
		progressBar.setMaximum(55);
		progressBar.setBounds(10, 486, 398, 23);
		clientFrame.getContentPane().add(progressBar);
		
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setBounds(481, 50, 237, 406);
		clientFrame.getContentPane().add(scrollPane);
		
		guiLogger = new JTextPane();
		guiLogger.setEditable(false);
		scrollPane.setViewportView(guiLogger);
		
		JLabel lblLogger = new JLabel("Logger:");
		lblLogger.setFont(new Font("Tahoma", Font.PLAIN, 11));
		lblLogger.setBounds(498, 15, 46, 14);
		clientFrame.getContentPane().add(lblLogger);
		
		JLabel lblProgress = new JLabel("Progress:");
		lblProgress.setFont(new Font("Tahoma", Font.PLAIN, 11));
		lblProgress.setBounds(10, 467, 61, 23);
		clientFrame.getContentPane().add(lblProgress);
		
		JLabel lblProgressComplete = new JLabel("Complete!");
		lblProgress.setFont(new Font("Tahoma", Font.PLAIN, 11));
		lblProgress.setBounds(10, 467, 61, 23);
		
	}
	
	/**
	 * Parses the torrent.
	 *
	 * @param torrentFile the torrent file
	 */
	public void parseTorrent(File torrentFile) {
		
		if((torrentInfo = torrentReader.parseTorrentFile(torrentFile)) == null){
			log.severe("Unable to parse torrent file; Exiting.");
			return;
		} else{
			log.info("Torrent file parsed successfully");
		}
		
		ToolKit.print(torrentInfo.torrent_file_map);
	}
	
	
	/**
	 * Sets the gui tracker info.
	 * 
	 * Updates the tracker info table in the GUI with values
	 * from the info hash
	 */
	public static void setGuiTrackerInfo(){
		setFileName(torrentInfo.file_name);
		setFileLength(torrentInfo.file_length);
		setPieceLength(torrentInfo.piece_length);
		if(torrentInfo.file_length % torrentInfo.piece_length != 0) {
			setNumPieces(torrentInfo.file_length / torrentInfo.piece_length + 1);
			progressBar.setMaximum(torrentInfo.file_length / torrentInfo.piece_length + 1);
		} 
		else {
			setNumPieces(torrentInfo.file_length / torrentInfo.piece_length);
			progressBar.setMaximum(torrentInfo.file_length / torrentInfo.piece_length);
		}
	}
	
	/**
	 * Gets the data.
	 * 
	 * This is used when we are dynamically changing peer data on the 
	 * peer table
	 *
	 * @param table the table
	 * @param row_index the row_index
	 * @param col_index the col_index
	 * @return the object
	 */
	public static Object getData(JTable table, int row_index, int col_index){
		  return table.getModel().getValueAt(row_index, col_index);
	}
	
	public static void onExit() {
		String up = Integer.toString(Tracker.uploaded);

		try {
			File tFile = new File(outputFile.getName() + ".stats");
			BufferedWriter out = new BufferedWriter(new FileWriter(tFile));
			out.write(up);
			out.close();
		} catch (IOException e) {
			log.severe("Error: Unable to write tracker stats to a file.");
		}
		  System.exit(0);
	}

}