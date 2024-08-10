package wfDataService.service.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jdtools.util.MiscUtil;
import wfDataModel.model.data.WeaponData;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.type.EloType;
import wfDataModel.service.type.RegionType;
import wfDataModel.service.type.WeaponType;
import wfDataService.service.cache.WarframeItemCache;
import wfDataService.service.compare.ServerComparator;
import wfDataService.service.util.ServiceSettingsUtil;

/**
 * Stores data about a server client, i.e. someone using this for their servers
 * @author MatNova
 *
 */
public class ServerClientData {

	private String displayName; // Friendly name
	private int serverClientID; 
	private JsonObject serverClientProperties;
	private RegionType region = RegionType.UNKNOWN;
	private long lastBanPollTime;
	private boolean isValidated = false;
	private boolean isNameOverridden = false;
	private Map<String, JsonObject> currentServerData = new HashMap<String, JsonObject>(2); // ServerID -> data

	public ServerClientData(int serverID, String displayName) {
		this.serverClientID = serverID;
		this.displayName = displayName;
	}

	public int getServerClientID() {
		return serverClientID;
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

	public void setServerClientProperties(JsonObject serverClientProperties) {
		this.serverClientProperties = serverClientProperties;
	}

	public JsonObject getServerClientProperties() {
		return serverClientProperties;
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
			List<JsonObject> serversList = new ArrayList<JsonObject>();
			JsonArray serversArr = new JsonArray();
			long oldest = Long.MAX_VALUE;
			int curPlayers = 0;
			int maxPlayers = 0;
			long maxUptime = 0;
			long statusId = (serverClientID / 3);
			boolean anyOutdated = false;
			for (String id : currentServerData.keySet()) {
				JsonObject serverData = currentServerData.get(id);
				long timestamp = serverData.has(JSONField.TIMESTAMP) ? serverData.get(JSONField.TIMESTAMP).getAsLong() : -1;
				if (timestamp > 0) {
					// If this server is older than the allowed expiration time, don't include it
					if ((System.currentTimeMillis() - timestamp) / 1000 >= ServiceSettingsUtil.getServerStatusExpiration()) {
						continue;
					}

					oldest = Math.min(oldest, timestamp);
				}
				if (serverData.has(JSONField.PLAYERS)) {
					curPlayers += serverData.getAsJsonArray(JSONField.PLAYERS).size();
				}
				if (serverData.has(JSONField.SETTINGS)) {
					maxPlayers += serverData.getAsJsonObject(JSONField.SETTINGS).get(JSONField.MAX).getAsInt();
				}
				if (!serverData.has(JSONField.DATA_ID)) {
					serverData.addProperty(JSONField.DATA_ID, "" + statusId + serverData.getAsJsonObject(JSONField.SETTINGS).get(JSONField.GAME_MODE).getAsString() + serverData.getAsJsonObject(JSONField.SETTINGS).get(JSONField.ELO).getAsString() + id);
				}
				// Uptime for this server
				if (serverData.has(JSONField.UTC)) {
					long start = serverData.get(JSONField.UTC).getAsLong();
					long diff = System.currentTimeMillis() - start;
					serverData.addProperty(JSONField.UPTIME, diff);
					if (diff > maxUptime) {
						maxUptime = diff;
					}
				}

				if ((System.currentTimeMillis() - timestamp) / 1000 >= ServiceSettingsUtil.getServerOutdatedTime()) {
					serverData.addProperty(JSONField.OUTDATED, true);
					anyOutdated = true;
				} else {
					serverData.addProperty(JSONField.OUTDATED, false);
				}

				serversList.add(serverData);
			}

			serversList.sort(new ServerComparator());

			int orderRC = 0;
			int orderNonRC = 0;
			for (JsonObject server : serversList) {
				if (server.has(JSONField.SETTINGS)) {
					if (server.getAsJsonObject(JSONField.SETTINGS).get(JSONField.ELO).getAsInt() == EloType.NON_RC.getCode()) {
						server.addProperty(JSONField.ORDER, orderNonRC++);
					} else {
						server.addProperty(JSONField.ORDER, orderRC++);
					}
				}
				serversArr.add(server);
			}
			serverStatus.add(JSONField.DATA, serversArr);
			serverStatus.addProperty(JSONField.SERVER_NAME, displayName);
			serverStatus.addProperty(JSONField.ID, statusId);
			serverStatus.addProperty(JSONField.OLDEST, oldest);
			serverStatus.addProperty(JSONField.TOTAL, curPlayers);
			serverStatus.addProperty(JSONField.MAX, maxPlayers);
			serverStatus.addProperty(JSONField.OUTDATED, anyOutdated);
			serverStatus.addProperty(JSONField.UPTIME, maxUptime);
			if (serverClientProperties != null) {
				serverStatus.add(JSONField.PROPERTIES, serverClientProperties);
				if (serverClientProperties.has(JSONField.BANS) && !serverClientProperties.has(JSONField.ITEMS) && serverClientProperties.getAsJsonArray(JSONField.BANS).size() > 0) {
					SortedMap<String, SortedSet<String>> weps = new TreeMap<String, SortedSet<String>>();
					for (JsonElement ele : serverClientProperties.getAsJsonArray(JSONField.BANS)) {
						JsonObject banObj = ele.getAsJsonObject();
						JsonArray itemsArr = banObj.getAsJsonArray(JSONField.ITEMS);
						for (JsonElement itemEle : itemsArr) {
							WeaponData wepData = WarframeItemCache.singleton().getItemInfo(itemEle.getAsString());
							String wepKey = wepData == null || wepData.getType() == null ? WeaponType.UNKNOWN.getDisplayName() : wepData.getType().getDisplayName();
							weps.computeIfAbsent(wepKey, k -> new TreeSet<String>()).add(wepData == null ? itemEle.getAsString() : MiscUtil.firstNonEmpty(wepData.getRealName(), wepData.getInternalName()));
						}
					}
					serverClientProperties.add(JSONField.ITEMS, new GsonBuilder().disableHtmlEscaping().create().toJsonTree(weps));
				}

			}

		}
		return serverStatus;
	}
}
