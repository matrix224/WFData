package wfDataModel.model.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;

/**
 * Util for network related tasks
 * @author MatNova
 *
 */
public final class NetworkUtil {

	private static int transCount;
	
	public static boolean isPrivateIP(String ipOrHost) {
		InetAddress addr = null;
		if (!MiscUtil.isEmpty(ipOrHost)) {
			try {
				addr = InetAddress.getByName(ipOrHost.split(":")[0]);
			} catch (UnknownHostException e) {
				Log.error(NetworkUtil.class.getSimpleName() + ".isPrivateIP() : Exception -> ", e);
			}
		}
		return addr != null && addr.isSiteLocalAddress();
	}

	public static synchronized String generateTransactionID(int sid) {
		return System.currentTimeMillis() + "." + sid + "." + (int)(Math.random() * Integer.MAX_VALUE) + "." + (transCount++);
	}
	
}
