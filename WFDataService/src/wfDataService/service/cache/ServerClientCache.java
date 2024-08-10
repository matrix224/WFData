package wfDataService.service.cache;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import jdtools.logging.Log;
import wfDataService.service.data.ServerClientData;
import wfDataService.service.db.ServerClientDao;

/**
 * Cache for storing info about clients that are registered with the service. <br>
 * Note that even if a client is in this cache, they may not currently be using the service.
 * @author MatNova
 *
 */
public class ServerClientCache {

	private static ServerClientCache singleton;
	private Map<Integer, ServerClientData> clientData = new HashMap<Integer, ServerClientData>(); // serverID -> server client data
	
	public static synchronized ServerClientCache singleton() {
		if (singleton == null) {
			singleton = new ServerClientCache();
		}
		return singleton;
	}
	
	private ServerClientCache() {
		init();
	}
	
	private void init() {
		for (ServerClientData data : ServerClientDao.fetchClientData()) {
			clientData.put(data.getServerClientID(), data);
		}
	}
	
	public ServerClientData getClientData(int serverId) {
		return clientData.get(serverId);
	}
	
	public Collection<ServerClientData> getClientData() {
		return clientData.values();
	}
	
	public void addClientData(ServerClientData data) {
		clientData.put(data.getServerClientID(), data);
	}
	
	public void toggleValidation(int serverId, boolean isValidated) {
		ServerClientData clientData = getClientData(serverId);
		if (clientData == null) {
			Log.warn("ServerClientCache.toggleValidation() : Unknown server client for ID " + serverId);
		} else {
			clientData.setValidated(isValidated);
			ServerClientDao.updateClientData(clientData);
			Log.info("ServerClientCache.toggleValidation() : Toggled validation for " + serverId + " (" + clientData.getDisplayName() + ") -> " + isValidated);
		}
	}
	
}
