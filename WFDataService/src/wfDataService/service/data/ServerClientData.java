package wfDataService.service.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import wfDataModel.service.codes.JSONField;
import wfDataModel.service.type.RegionType;

/**
 * Stores data about a server client, i.e. someone using this for their servers
 * @author MatNova
 *
 */
public class ServerClientData {

	private String displayName; // Friendly name
	private int serverID; 
	private RegionType region = RegionType.UNKNOWN;
	private long lastBanPollTime;
	private boolean isValidated = false;
	private boolean isNameOverridden = false;
	private Map<String, JsonObject> currentServerData = new HashMap<String, JsonObject>();
	
	public ServerClientData(int serverID, String displayName) {
		this.serverID = serverID;
		this.displayName = displayName;
	}
	
	public int getServerID() {
		return serverID;
	}
	
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
	
	public String getDisplayName() {
		return displayName;
	}
 	
	public void setRegion(RegionType region) {
		this.region = region;
	}
	
	public RegionType getRegion() {
		return region;
	}
	
	public void setLastBanPollTime(long lastBanPollTime) {
		this.lastBanPollTime = lastBanPollTime;
	}
	
	public long getLastBanPollTime() {
		return lastBanPollTime;
	}
	
	public void setValidated(boolean isValidated) {
		this.isValidated = isValidated;
	}
	
	public boolean isValidated() {
		return isValidated;
	}
	
	public void setNameOverridden(boolean nameOverridden) {
		isNameOverridden = nameOverridden;
	}
	
	public boolean isNameOverridden() {
		return isNameOverridden;
	}
	
	public List<String> getServerStatusIDs() {
		synchronized (currentServerData) {
			return new ArrayList<String>(currentServerData.keySet()); 
		}
	}
	
	public void updateServerStatusData(String serverId, JsonObject serverData) {
		synchronized (currentServerData) {
			if (serverData != null) {
				currentServerData.put(serverId, serverData);
			} else {
				currentServerData.remove(serverId);
			}
		}
	}
	
	public JsonObject getServerStatusData() {
		JsonObject serverStatus = new JsonObject();
		synchronized (currentServerData) {
			JsonArray serversArr = new JsonArray();
			long oldest = Long.MAX_VALUE;
			int curPlayers = 0;
			int maxPlayers = 0;
			for (JsonObject serverData : currentServerData.values()) {
				serversArr.add(serverData);
				if (serverData.has(JSONField.TIMESTAMP)) {
					oldest = Math.min(oldest, serverData.get(JSONField.TIMESTAMP).getAsLong());
				}
				if (serverData.has(JSONField.PLAYERS)) {
					curPlayers += serverData.getAsJsonArray(JSONField.PLAYERS).size();
				}
				if (serverData.has(JSONField.SETTINGS)) {
					maxPlayers += serverData.getAsJsonObject(JSONField.SETTINGS).get("maxPlayers").getAsInt();
				}
			}
			serverStatus.add(JSONField.DATA, serversArr);
			serverStatus.addProperty(JSONField.SERVER_NAME, displayName);
			serverStatus.addProperty(JSONField.OLDEST, oldest);
			serverStatus.addProperty(JSONField.TOTAL, curPlayers);
			serverStatus.addProperty(JSONField.MAX, maxPlayers);
		}
		return serverStatus;
	}
}
