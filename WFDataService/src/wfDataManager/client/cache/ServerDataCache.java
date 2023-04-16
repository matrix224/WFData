package wfDataManager.client.cache;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import wfDataManager.client.db.ServerDao;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.ServerData;

public final class ServerDataCache {

	private static ServerDataCache singleton;
	private Map<String, ServerData> serverData = new HashMap<String, ServerData>();
	
	public static synchronized ServerDataCache singleton() {
		if (singleton == null) {
			singleton = new ServerDataCache();
		}
		return singleton;
	}
	
	public synchronized ServerData getServerData(String logId) {
		if (!serverData.containsKey(logId)) {
			ServerData data = ServerDao.getServerData(logId, true);
			serverData.put(logId, data);
		}
		return serverData.get(logId);
	}
	
	public synchronized boolean hasServerData(String logId) {
		return serverData.get(logId) != null;
	}
	
	public synchronized ServerData getServerData(int port) {
		for (ServerData server : getAllServerData()) {
			if (server.getServerPort() == port) {
				return server;
			}
		}
		return null;
	}
	
	public Collection<ServerData> getAllServerData() {
		return serverData.values();
	}
	
	public boolean isProxyIP(String ip) {
		for (ServerData server : getAllServerData()) {
			if (server.isProxyIP(ip)) {
				return true;
			}
		}
		return false;
	}
	
	public int getMinServerPort() {
		int minPort = Integer.MAX_VALUE;
		for (ServerData server : getAllServerData()) {
			if (server.getServerPort() < minPort) {
				minPort = server.getServerPort();
			}
		}
		return minPort;
	}
	
	public int getMaxServerPort() {
		int maxPort = Integer.MIN_VALUE;
		for (ServerData server : getAllServerData()) {
			if (server.getServerPort() > maxPort) {
				maxPort = server.getServerPort();
			}
		}
		return maxPort;
	}
	
	/**
	 * Given a UID, will return a PlayerData object for it if the given UID is currently found
	 * in any of the running servers. <br>
	 * If no match is found, returns null
	 * @param uid
	 * @return
	 */
	public PlayerData getPlayer(String uid) {
		for (ServerData server : getAllServerData()) {
			if (server.getPlayer(uid) != null) {
				return server.getPlayer(uid);
			}
		}
		return null;
	}
}
