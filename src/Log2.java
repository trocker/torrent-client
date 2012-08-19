import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * The Class Log2 is a simple extensions of the Java logger made so that logging
 * will be easy throught the project.
 * 
 * @author Deepak, Mike,Josh
 */
public class Log2 extends SimpleFormatter {

	/** The root log. */
	static Logger rootLog;

	/**
	 * Initilizes the logger.
	 */
	public static void init() {
		rootLog = Logger.getLogger("");
		rootLog.setUseParentHandlers(false);
		if (Constants.isFileLoggingEnabled)
			try {
				rootLog.addHandler(new FileHandler(Constants.logFileName
						+ ".Log"));
			} catch (SecurityException e) {
				rootLog.severe("Could not Log to File. Security Exception.");
			} catch (IOException e) {
				rootLog.severe("Could not Log to File. IO Exception.");
			}
		if (!Constants.isConsoleLoggingEnabled) {
			ConsoleHandler formatedHandler = new ConsoleHandler();
			rootLog.addHandler(formatedHandler);
		}
		replaceConsoleHandler(rootLog, Level.OFF);

		rootLog.setLevel(Constants.logLevel);

	}

	/**
	 * Configures and returns a logger for some class c.
	 * 
	 * @param c
	 *            the class to get a logger for
	 * @return the configured logger.
	 */
	public static Logger getLogger(final Class<?> c) {
		Logger log = Logger.getLogger(c.getName());
		return log;
	}

	/**
	 * Configures and returns a logger for some class c.
	 * 
	 * @param c
	 *            the class to get a logger for
	 * @param l
	 *            the logging level for the logger
	 * @return the configured logger.
	 */
	public static Logger getLogger(final Class<?> c, final Level l) {
		Logger log = Logger.getLogger(c.getName());
		log.setLevel(l);
		return log;
	}

	/* Rob's example logging setup. From "Java Programming" Sakai site. */

	/**
	 * Replaces the ConsoleHandler for a specific Logger with one that will log
	 * all messages. This method could be adapted to replace other types of
	 * loggers if desired.
	 * 
	 * @param logger
	 *            the logger to update.
	 * @param newLevel
	 *            the new level to log.
	 */
	public static void replaceConsoleHandler(Logger logger, Level newLevel) {

		// Handler for console (reuse it if it already exists)
		Handler consoleHandler = null;
		// see if there is already a console handler
		for (Handler handler : logger.getHandlers()) {
			if (handler instanceof ConsoleHandler) {
				// found the console handler
				consoleHandler = handler;
				break;
			}
		}

		if (consoleHandler == null) {
			// there was no console handler found, create a new one
			consoleHandler = new ConsoleHandler();
			logger.addHandler(consoleHandler);
		}
		// set the console handler to fine:
		consoleHandler.setLevel(newLevel);
	}
	/* End borrowed code */

}
