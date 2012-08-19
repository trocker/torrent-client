import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Random;

import GivenTools.ToolKit;
import GivenTools.TorrentInfo;

/**
 * The Class Utils handles miscellaneous functions that help us
 * perform tasks throughout the torrent client.
 * 
 * @author Deepak, Mike, Josh
 */
public class Utils extends ToolKit {
	
	/** The Constant HEX_CHARS. */
	public static final char[] HEX_CHARS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	/**
	 * To hex string.
	 * 
	 * Converts a byte array to a hex string
	 *
	 * @param bytes the bytes
	 * @return the string
	 */
	public static String toHexString(byte[] bytes) {
		if (bytes == null) {
			return null;
		}
		
		if (bytes.length == 0) {
			return "";
		}

		StringBuilder sb = new StringBuilder(bytes.length * 3);

		for (byte b : bytes) {
			byte hi = (byte) ((b >> 4) & 0x0f);
			byte lo = (byte) (b & 0x0f);

			sb.append('%').append(HEX_CHARS[hi]).append(HEX_CHARS[lo]);
		}
		return sb.toString();
	}
	
	/**
	 * Gen peer id.
	 * 
	 * Generates a random peer id to identify ourself
	 *
	 * @return the byte[]
	 */
	protected static byte[] genPeerId() {
		Random rand = new Random(System.currentTimeMillis());
		byte[] peerId = new byte[20];

		peerId[0] = 'G';
		peerId[1] = 'P';
		peerId[2] = '0';
		peerId[3] = '2';

		for (int i = 4; i < 20; ++i) {
			peerId[i] = (byte) ('A' + rand.nextInt(26));
		}
		return peerId;
	}

	/**
	 * Bitfield to bool array.
	 * 
	 * Converts a given bitfield to a boolean array
	 *
	 * @param bitfield the bitfield
	 * @param numPieces the num pieces
	 * @return the boolean[]
	 */
	public static boolean[] bitfieldToBoolArray(byte[] bitfield, int numPieces) {
		if (bitfield == null)
			return null;
		else {
			boolean[] retArray = new boolean[numPieces];
			for (int i = 0; i < retArray.length; i++) {
				int byteIndex = i / 8;
				int bitIndex = i % 8;

				if (((bitfield[byteIndex] << bitIndex) & 0x80) == 0x80)
					retArray[i] = true;
				else
					retArray[i] = false;

			}
			return retArray;
		}
	}

	/**
	 * Bool to bitfield array.
	 * 
	 * Converts a given boolean array to a bitfield
	 *
	 * @param verifiedPieces the verified pieces
	 * @return the byte[]
	 */
	public static byte[] boolToBitfieldArray(boolean[] verifiedPieces) {
		int length = verifiedPieces.length / 8;

		if (verifiedPieces.length % 8 != 0) {
			++length;
		}

		int index = 0;
		byte[] bitfield = new byte[length];

		for (int i = 0; i < bitfield.length; ++i) {
			for (int j = 7; j >= 0; --j) {

				if (index >= verifiedPieces.length) {
					return bitfield;
				}

				if (verifiedPieces[index++]) {
					bitfield[i] |= (byte) (1 << j);
				}
			}
		}
		return bitfield;
	}
	
	/**
	 * Check pieces.
	 * 
	 * Check the pieces of the file we have on disk so that we can increment
	 * downloaded stats and send appropriate bitfield
	 *
	 * @return the boolean[]
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static boolean[] checkPieces(TorrentInfo torrentInfo, File outputFile) throws IOException {

		int numPieces = torrentInfo.piece_hashes.length;
		int pieceLength = torrentInfo.piece_length;
		int fileLength = torrentInfo.file_length;
		ByteBuffer[] pieceHashes = torrentInfo.piece_hashes;
		int lastPieceLength = fileLength % pieceLength == 0 ? pieceLength : fileLength % pieceLength;

		byte[] piece = null;
		boolean[] verifiedPieces = new boolean[numPieces];

		for (int i = 0; i < numPieces; i++) {
			if (i != numPieces - 1) {
				piece = new byte[pieceLength];
				piece = readFile(i, 0, pieceLength, torrentInfo, outputFile);
			} else {
				piece = new byte[lastPieceLength];
				piece = readFile(i, 0, lastPieceLength, torrentInfo, outputFile);
			}
			
			
			
			if (Manager.verifySHA1(piece, pieceHashes[i], i)) {
				verifiedPieces[i] = true;
				RUBTClient.log("Verified piece " + i);
			}
		}
		
		for(int i = 0; i < verifiedPieces.length; i++){
			if(verifiedPieces[i] != false){
				
				if(torrentInfo.file_length % torrentInfo.piece_length != 0 && i == torrentInfo.piece_hashes.length -1){
					RUBTClient.addProgress(torrentInfo.file_length % torrentInfo.piece_length);
					RUBTClient.addAmountDownloaded(torrentInfo.file_length % torrentInfo.piece_length);
				}
				else {
					RUBTClient.addProgress(torrentInfo.piece_length);
					RUBTClient.addAmountDownloaded(torrentInfo.piece_length);
				}
			}
			
		}
		
		return verifiedPieces;
	}
	
	/**
	 * Read file.
	 * 
	 * Reads a piece from file on the disk in instances
	 * where we are uploading a piece or if we are 
	 * checking the pieces of the file we already have
	 *
	 * @param index the index
	 * @param offset the offset
	 * @param length the length
	 * @return the byte[]
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static byte[] readFile(int index, int offset, int length, TorrentInfo torrentInfo, File outputFile) throws IOException {
		RandomAccessFile raf = new RandomAccessFile(outputFile, "r");
		byte[] data = new byte[length];
		
		raf.seek(torrentInfo.piece_length * index + offset);
		raf.read(data);
		raf.close();
		
		return data;
	}
}
