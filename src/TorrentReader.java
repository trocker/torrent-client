import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Logger;

import GivenTools.BencodingException;
import GivenTools.TorrentInfo;

/**
 * The Class TorrentReader is responsible for parsing the torrent file
 * and extracting out the various pieces of information pertaining to the
 * file that will be saved. The returned value is a TorrentInfo object.
 * 
 * @author Deepak, Mike, Josh
 */
public class TorrentReader {
	
	/** The Constant log. */
	private static final Logger log = Log2.getLogger(TorrentReader.class);

	/**
	 * Instantiates a new torrent reader.
	 */
	public TorrentReader() {}

	/**
	 * Parses the torrent file.
	 *
	 * @param torrentFile the torrent file
	 * @return the torrent info
	 */
	public TorrentInfo parseTorrentFile(File torrentFile) {
		log.info("Attempting to parse torrent file");
		
		try {
			DataInputStream dataInputStream = new DataInputStream(new FileInputStream(torrentFile));
			long fSize = torrentFile.length();

			if (fSize > Integer.MAX_VALUE || fSize < Integer.MIN_VALUE) {
				dataInputStream.close();
				throw new IllegalArgumentException(fSize + " is too large a torrent filesize for this program to handle");
			}

			byte[] torrentData = new byte[(int)fSize];
			dataInputStream.readFully(torrentData);
			TorrentInfo torrentInfo = new TorrentInfo(torrentData);

			dataInputStream.close();
			
			log.info("Successfully parsed torrent file.");
			
			return torrentInfo;
		} catch (FileNotFoundException e) {
			log.severe("Error: File not found");
			return null;
		} catch (IOException e) {
			log.severe("Error: Unable to interface with file");
			return null;
		} catch (IllegalArgumentException e) {
			log.severe("Error: Illegal argument");
			return null;
		} catch (BencodingException e) {
			log.severe("Error: Invalid torrent file specified");
			return null;
		}
	}
}