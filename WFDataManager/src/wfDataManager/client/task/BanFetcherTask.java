package wfDataManager.client.task;

import com.google.gson.JsonObject;

import jdtools.logging.Log;
import wfDataManager.client.util.RequestUtil;

/**
 * Task to periodically ask the service for any new shared bans
 * @author MatNova
 *
 */
public class BanFetcherTask implements Runnable {

	@Override
	public void run() {
		try {
			RequestUtil.sendGetBansRequest(new JsonObject());
		} catch (Throwable t) {
			Log.error(BanFetcherTask.class.getSimpleName() + "() : Exception fetching bans -> ", t);
		}
	}

}
