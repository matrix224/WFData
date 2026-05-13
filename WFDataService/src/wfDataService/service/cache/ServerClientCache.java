package wfDataService.service.cache;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataModel.model.util.DBUtil;
import wfDataService.service.data.ServerClientData;
import wfDataService.service.db.ProcessorVarDao;
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
		// Check why client statuses are not being printed (only an empty object is)
		// from ServerStatusTask
		String dbData = ProcessorVarDao.getVar(ProcessorVarDao.VAR_CLIENT_STATUS);
		// If we have no data in the DB for the clients, then just default to their basic info
		if (MiscUtil.isEmpty(dbData)) {
			for (ServerClientData data : ServerClientDao.fetchClientData()) {
				clientData.put(data.getServerClientID(), data);
			}
		} else {
			// Otherwise populate with latest available data, which includes server statuses
			clientData = DBUtil.parseDBMap(dbData, Integer.class, ServerClientData.class);
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

	public void updateClientDataDB() {
		ProcessorVarDao.updateVar(ProcessorVarDao.VAR_CLIENT_STATUS, DBUtil.createDBMap(clientData));
	}

	public synchronized String calculateAbbrevName(ServerClientData client) {
		List<String> abbrevNames = clientData.values().stream().filter(c -> c.getServerClientID() != client.getServerClientID()).map(ServerClientData::getAbbrevName).collect(Collectors.toList());
		String[] serverNameParts = client.getDisplayName().replaceAll("\\s+|_", " ").split(" ", 3);
		String abbrevName = "";
		if (serverNameParts.length == 3) {
			abbrevName = serverNameParts[0].substring(0, 1) + serverNameParts[1].substring(0, 1) + serverNameParts[2].substring(0, 1);
		} else {
			if (serverNameParts.length == 2) {
				if (serverNameParts[0].length() > 1) {
					abbrevName = serverNameParts[0].substring(0, 1) + serverNameParts[0].substring(serverNameParts[0].length() - 1, serverNameParts[0].length()) + serverNameParts[1].substring(0, 1);
				} else if (serverNameParts[1].length() > 1) {
					abbrevName = serverNameParts[0].substring(0, 1) + serverNameParts[1].substring(0, 1) + serverNameParts[1].substring(serverNameParts[1].length() - 1, serverNameParts[1].length());
				} else {
					abbrevName = serverNameParts[0].substring(0, 1) + serverNameParts[1].substring(0, 1) + 1;
				}
			} else {
				if (serverNameParts[0].length() > 2) {
					abbrevName = serverNameParts[0].substring(0, 1) + serverNameParts[0].substring(serverNameParts[0].length() / 2 - 1, serverNameParts[0].length() / 2) + serverNameParts[0].substring(serverNameParts[0].length() - 1, serverNameParts[0].length());
				} else if (serverNameParts[0].length() > 1) {
					abbrevName = serverNameParts[0].substring(0, 1) + serverNameParts[0].substring(serverNameParts[0].length() - 1, serverNameParts[0].length()) + 1;
				} else {
					abbrevName = serverNameParts[0].substring(0, 1) + 11;
				}
			}
		}

		abbrevName = abbrevName.replaceAll("&", "n");
		abbrevName = abbrevName.replaceAll("@", "a");
		abbrevName = abbrevName.replaceAll("\\+", "p");

		while (abbrevNames.contains(abbrevName)) {
			if (abbrevName.length() == 3) {
				abbrevName += "1";
			} else {
				abbrevName = abbrevName.substring(0, 3) + (Integer.parseInt(abbrevName.substring(3)) + 1);
			}
		}

		return abbrevName;
	}
}
