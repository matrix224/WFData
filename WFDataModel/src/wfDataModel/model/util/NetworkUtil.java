package wfDataModel.model.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

import jdtools.util.MiscUtil;
import wfDataModel.model.logging.Log;

/**
 * Util for network related tasks
 * @author MatNova
 *
 */
public final class NetworkUtil {

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

}
