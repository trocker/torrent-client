import java.io.File;
import java.util.logging.Level;

/**
 * The Class Constants contains various constants that we use throughout the
 * program. It is not up to date as of now but will be more widely used in the
 * future.
 * 
 * @author Deepak, Mike, Josh
 */
public class Constants {

	/** The the file name of the log file if loggin to file is enabled. */
	public static String logFileName = "BT.Log";

	/** Enable file logging or not. */
	public static boolean isFileLoggingEnabled = false;

	/** Enable console logging or not. */
	public static boolean isConsoleLoggingEnabled = false;

	/**
	 * The log level. This will set the level required for messages to be
	 * logged.
	 */
	public static Level logLevel = Level.OFF;
	
	/** The stats. */
	public static File stats = new File("song.mp3.stats");

}
