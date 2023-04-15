package wfDataModel.model.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Util for logging all the data
 * @author MatNova
 *
 */
public class Log {

	private static final Logger LOG = LogManager.getLogger(Log.class);
	
	public static void info(String str) {
		LOG.info(str);
	}

	public static void info(String...strs) {
		if (strs != null && strs.length > 0) {
			String msg = "";
			for (String str : strs) {
				msg = msg.concat(str);
			}
			info(msg);
		}
	}
	
	public static void warn(String msg) {
		LOG.warn(msg);
	}
	
	public static void warn(String...strs) {
		if (strs != null && strs.length > 0) {
			String msg = "";
			for (String str : strs) {
				msg = msg.concat(str);
			}
			warn(msg);
		}
	}
	
	public static void error(String msg) {
		LOG.error(msg);
	}
	
	public static void error(String msg, Throwable t) {
		LOG.error(msg, t);
	}
	
	public static void debug(String msg) {
		LOG.debug(msg);
	}
	
	public static boolean isDebugMode() {
		return LOG.isDebugEnabled();
	}
	
}
