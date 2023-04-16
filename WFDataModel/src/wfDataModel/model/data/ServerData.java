package wfDataModel.model.data;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import jdtools.util.MiscUtil;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.type.PlatformType;

/**
 * Data model for storing information about a currently parsed server
 * @author MatNova
 *
 */
public class ServerData {

	@Expose()
	@SerializedName(JSONField.PLAYERS)
	private Map<String, PlayerData> parsedPlayers = new HashMap<String, PlayerData>(10); // UID -> player
	@Expose()
	@SerializedName(JSONField.SETTINGS)
	private JsonObject gameSettings;
	@Expose()
	@SerializedName(JSONField.ACTIVITY)
	private List<ActivityData> serverActivity = new ArrayList<ActivityData>();
	@Expose()
	@SerializedName(JSONField.TIMESTAMP)
	private ServerTimeStats timeStats = new ServerTimeStats();
	@Expose ()
	@SerializedName(JSONField.ID)
	private String id;
	@Expose (serialize = false, deserialize = false) 
	private int serverPort;
	@Expose (serialize = false, deserialize = false) 
	private long logPosition; // The current log position
	@Expose (serialize = false, deserialize = false) 
	private long startLogPosition; // The log position at the start of this parsing session
	@Expose (serialize = false, deserialize = false) 
	private long buildId = -1; // The current build ID
	@Expose (serialize = false, deserialize = false) 
	private Map<String, Map<String, Integer>> playerItems = new HashMap<String, Map<String, Integer>>(8); // UID -> Item -> kill count. Stored until player leaves
	@Expose (serialize = false, deserialize = false) 
	private boolean isParsing; // If this server is currently being parsed
	@Expose (serialize = false, deserialize = false) 
	private boolean hasError; // If this server had an error while parsing
	@Expose (serialize = false, deserialize = false) 
	private boolean hasNRSIssue; // If this server has an NRS server issue
	@Expose (serialize = false, deserialize = false) 
	private int numRepeats; // Number of times the log was parsed but got no new data (i.e. log position didn't change)
	@Expose (serialize = false, deserialize = false) 
	private int missCount = 0; // Number of times this ServerData had no log file parsed for it (i.e. was parsed at one point but log file has disappeared)
	@Expose () 
	private Map<String, Integer> miscKills = new HashMap<String, Integer>(4); // Item -> kills. Only stored per parsing session. Used for things like DamageTrigger
	@Expose (serialize = false, deserialize = false) 
	private String currentProfileDir = null; // The current "profile dir" for the server
	@Expose (serialize = false, deserialize = false) 
	private String currentLaunchDir = null; // The current "launch dir" for the server
	@Expose (serialize = false, deserialize = false) 
	private List<String> proxyServers = new ArrayList<String>(); // NRS / Proxy servers that are used by DE for routing clients through them
	@Expose (serialize = false, deserialize = false) 
	private Map<Integer, String> connHandles = new HashMap<Integer, String>(); // ConnHandle -> IP:Port. Remains set until associated player disconnects
	
	public ServerData(String id, long logPosition) {
		this.id = id;
		this.logPosition = logPosition;
	}

	public ServerData(String id, JsonObject dataObj) throws ParseException {
		this.id = id;
		buildFromDB(dataObj);
	}
	
	public String getId() {
		return id;
	}

	public int getNumericId() {
		int i = id.length() - 1;
		while (i >= 0 && Character.isDigit(id.charAt(i))) {
			--i;
		}
		return Integer.parseInt(id.substring(i + 1));
	}
	
	public void setServerPort(int serverPort) {
		this.serverPort = serverPort;
	}
	
	public int getServerPort() {
		return serverPort;
	}
	
	public long getLogPosition() {
		return logPosition;
	}

	public void setLogPosition(long logPosition) {
		this.logPosition = logPosition;
	}

	public ServerTimeStats getTimeStats() {
		return timeStats;
	}
	
	public void setNumRepeat(int numRepeat) {
		this.numRepeats = numRepeat;
	}

	public void addNumRepeat() {
		numRepeats++;
	}

	public int getNumRepeats() {
		return numRepeats;
	}

	public void clearNumRepeats() {
		numRepeats = 0;
	}

	public void setNumMiss(int missCount) {
		this.missCount = missCount;
	}

	public void addNumMiss() {
		missCount++;
	}

	public int getNumMiss() {
		return missCount;
	}

	public void clearNumMiss() {
		missCount = 0;
	}

	public void addPlayer(PlayerData player) {
		if (!parsedPlayers.containsKey(player.getUID())) {
			parsedPlayers.put(player.getUID(), player);
		}
	}

	public void removePlayer(PlayerData player) {
		parsedPlayers.remove(player.getUID());
		playerItems.remove(player.getUID());
	}

	public void removeDisconnectedPlayers() {
		for(Iterator<Map.Entry<String, PlayerData>> it = parsedPlayers.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<String, PlayerData> entry = it.next();
			if (entry.getValue().leftServer()) {
				playerItems.remove(entry.getValue().getUID());
				// It's possible that if someone joins and leaves, and someone else joins soon after them, that the same
				// connhandle will be used for both of them
				// The person who left will be processed here on the next run
				// If the person who joined had their connHandle processed before this point,
				// we don't want to remove our connHandle mapping if it's mapped to a different IP than
				// that of the person who left, since it should currently be mapped to the person who joined right after
				// Note that we check if the person who left even has a connHandle since it's possible they did not get assigned one
				// if they joined and timed out early on in the loading process
				if (entry.getValue().getConnHandle() > -1 && entry.getValue().getIPAndPort().equals(getIPAndPort(entry.getValue().getConnHandle()))) {
					removeConnHandle(entry.getValue().getConnHandle());
				}
				it.remove();
			}
		}
	}

	public void addPlayerItemKill(String uid, String item) {
		playerItems.computeIfAbsent(uid, k -> new HashMap<String, Integer>(4)).merge(item, 1, Integer::sum);
	}

	public int getPlayerItemKillCount(String uid, String item) {
		return playerItems.containsKey(uid) && playerItems.get(uid).containsKey(item) ? playerItems.get(uid).get(item) : 0;
	}

	public void addMiscKill(String item) {
		miscKills.compute(item, (k,v) -> v == null ? 1 : v + 1);
	}

	public Map<String, Integer> getMiscKills() {
		return miscKills;
	}

	public void clearMiscKills() {
		miscKills.clear();
	}

	public void clearPlayers() {
		parsedPlayers.clear();
		playerItems.clear();
	}

	public List<ActivityData> getServerActivity() {
		return serverActivity;
	}
	
	public void markServerActivity() {
		List<PlayerData> curPlayers = getConnectedPlayers();
		if (!MiscUtil.isEmpty(curPlayers)) {
			ActivityData actData = new ActivityData(timeStats.getServerActivityTime());
			actData.addToPlayerCount(curPlayers.size());
			serverActivity.add(actData);
		}
		timeStats.markNextActivityTime();
	}
	
	public void clearServerActivity() {
		serverActivity.clear();
	}
	
	/**
	 * Signals that a new parsing session has begun for this server <br>
	 * If freshLog is true, this signals that we have started parsing a new log file. 
	 * This will then clear all parsed players and their kill data
	 * @param freshLog
	 */
	public void startNewParse(boolean freshLog) {
		clearMiscKills();
		clearNumMiss(); // If we're calling this method, we're actually parsing something. So clear any miss counts
		clearServerActivity();
		hasError = false;

		if (freshLog) {
			// If starting a new log, start at beginning and clear any player data plus game settings
			startLogPosition = 0;
			hasNRSIssue = false;
			clearNumRepeats();
			clearPlayers();
			clearGameSettings();
			clearProxyServers();
			clearConnHandles();
		} else {
			// Important that this gets set first
			// If anything went wrong with below methods, want to make sure it just gets stuck parsing here
			// as opposed to repeatedly doing the previous parse and then failing here, reverting to previous, etc
			startLogPosition = logPosition;
			// If starting a new parse on an existing log, then clear any disconnected players,
			// and reset any parsing session data for each remaining connected player
			// Also update our starting log position in case any errors occur
			removeDisconnectedPlayers();
			resetParse(false);
		}
		timeStats.startNewParse(freshLog);
	}

	/**
	 * This will reset any parsed data from the current parsing session. <br>
	 * For each parsed player, will call {@link PlayerData#resetParse(boolean)}, with the provided
	 * isError value passed to that method. <br>
	 * If isError is true, this will reset the log's position to what it was at the start of this parse session,
	 * and will also reset any player weapon kill counts to their previous value
	 *  @param isError
	 */
	public void resetParse(boolean isError) {
		for (PlayerData player : getParsedPlayers()) {
			// If we're resetting due to an error, update the player's total weapon kill counts
			if (isError) {
				String uid = player.getUID();
				if (playerItems.containsKey(uid)) {
					for (String weapon : player.getWeaponKills().keySet()) {
						if (playerItems.get(uid).containsKey(weapon)) {
							int resetKills = playerItems.get(uid).get(weapon) - player.getWeaponKills().get(weapon);
							if (resetKills == 0) {
								playerItems.get(uid).remove(weapon);
							} else {
								playerItems.get(uid).put(weapon, resetKills);
							}
						}
					}
					if (playerItems.get(uid).isEmpty()) {
						playerItems.remove(uid);
					}
				}
				
				// Reset the connHandle mapping for this player if they had been marked as having left the server,
				// and if they actually had a connHandle set
				// We check if the person who left has a connHandle since it's possible they did not get assigned one
				// if they joined and timed out early on in the loading process
				if (player.leftServer() && player.getConnHandle() > -1) {
					addConnHandle(player.getConnHandle(), player.getIPAndPort());
				}
			}

			player.resetParse(isError);
		}

		if (isError) {
			logPosition = startLogPosition;
			hasError = true;
		}
		timeStats.reset(isError);
	}

	public PlayerData getPlayer(String uid) {
		return parsedPlayers.get(uid);
	}

	public PlayerData getPlayerByName(String name) {
		for (PlayerData data : getParsedPlayers()) {
			if (data.getPlayerName().equals(name)) {
				return data;
			}
		}
		return null;
	}

	/**
	 * Returns all players that were parsed during the latest parse run.
	 * @return
	 */
	public Collection<PlayerData> getParsedPlayers() {
		return parsedPlayers.values();
	}

	/**
	 * Returns all players that are currently connected to the server
	 * @return
	 */
	public List<PlayerData> getConnectedPlayers() {
		return parsedPlayers.values().stream().filter(p -> !p.leftServer()).collect(Collectors.toList());
	}

	public JsonArray getConnectedPlayersDB() {
		JsonArray dataArr = new JsonArray();
		for (PlayerData player : getConnectedPlayers()) {
			JsonObject playerObj = new JsonObject();
			playerObj.addProperty(JSONField.PLAYER_NAME, player.getPlayerName());
			playerObj.addProperty(JSONField.USER_ID, player.getUID());
			if (!MiscUtil.isEmpty(player.getIPAndPort())) {
				playerObj.addProperty(JSONField.IP, player.getIPAndPort());
				playerObj.addProperty(JSONField.CONN_HANDLE, player.getConnHandle());
			}
			playerObj.addProperty(JSONField.PLATFORM, player.getPlatform().getCode());
			playerObj.addProperty(JSONField.TIMESTAMP, player.getLastLogTime());
			playerObj.addProperty(JSONField.DATA, player.hasData());
			dataArr.add(playerObj);
		}
		return dataArr;
	}

	public void clearGameSettings() {
		gameSettings = null;
	}

	public void setGameSettings(String gameSettings) {
		if (!MiscUtil.isEmpty(gameSettings)) {
			setGameSettings(JsonParser.parseString(gameSettings).getAsJsonObject());
		}
	}
	
	public void setGameSettings(JsonObject gameSettings) {
		this.gameSettings = gameSettings;
	}

	public int getEloRating() {
		return gameSettings != null ? gameSettings.get("eloRating").getAsInt() : -1;
	}

	public int getGameModeId() {
		return gameSettings != null ? gameSettings.get("gameModeId").getAsInt() : -1;
	}

	public long getBuildId() {
		return buildId;
	}
	
	public void setBuildId(long buildId) {
		this.buildId = buildId;
	}

	public int getMaxPlayers() {
		return gameSettings != null ? gameSettings.get("maxPlayers").getAsInt() : -1;
	}

	public String getGameSettingsDB() {
		return gameSettings != null ? gameSettings.toString() : "";
	}

	public boolean isParsing() {
		return isParsing;
	}

	public void setIsParsing(boolean isParsing) {
		this.isParsing = isParsing;
	}

	public boolean hasError() {
		return hasError;
	}

	public void setHasNRSIssue(boolean hasNRSIssue) {
		this.hasNRSIssue = hasNRSIssue;
	}
	
	public boolean hasNRSIssue() {
		return hasNRSIssue;
	}
	
	/**
	 * Returns if the currently parsed data for this server should be considered valid for storing / sending. <br>
	 * It is considered valid so long as {@link #hasError()} is false, {@link #getNumMiss()} is 0, and {@link #getNumRepeats()} is 0.
	 * @return
	 */
	public boolean isDataValid() {
		return !hasError() && getNumMiss() == 0 && getNumRepeats() == 0;
	}

	public void addProxyServer(String ipAndPort) {
		proxyServers.add(ipAndPort);
	}

	public void clearProxyServers() {
		proxyServers.clear();
	}

	public boolean isProxyIP(String ip) {
		for (String ipAndPort : proxyServers) {
			if (ipAndPort.split(":")[0].equals(ip)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isProxyServer(String ipAndPort) {
		return proxyServers.contains(ipAndPort);
	}
	
	private JsonArray getProxiesDB() {
		JsonArray arr = new JsonArray();
		for (String proxy : proxyServers) {
			arr.add(proxy);
		}
		return arr;
	}

	public void addConnHandle(int handle, String ipAndPort) {
		connHandles.put(handle, ipAndPort);
	}
	
	public void clearConnHandles() {
		connHandles.clear();
	}
	
	public void removeConnHandle(int handle) {
		connHandles.remove(handle);
	}
	
	public String getIPAndPort(int handle) {
		return connHandles.get(handle);
	}
	
	private JsonObject getConnHandlesDB() {
		JsonObject handles = new JsonObject();
		for (int connHandle : connHandles.keySet()) {
			handles.addProperty(String.valueOf(connHandle), connHandles.get(connHandle));
		}
		return handles;
	}
	
	public void setCurrentProfileDir(String currentProfileDir) {
		this.currentProfileDir = currentProfileDir;
	}

	public String getCurrentProfileDir() {
		return currentProfileDir;
	}
	
	public void setCurrentLaunchDir(String currentLaunchDir) {
		this.currentLaunchDir = currentLaunchDir;
	}

	public String getCurrentLaunchDir() {
		return currentLaunchDir;
	}

	private void buildFromDB(JsonObject dataObj) throws ParseException {
		if (dataObj.has(JSONField.POSITION)) {
			setLogPosition(dataObj.get(JSONField.POSITION).getAsLong());
		}
		if (dataObj.has(JSONField.REPEAT)) {
			setNumRepeat(dataObj.get(JSONField.REPEAT).getAsInt());
		}
		if (dataObj.has(JSONField.MISS)) {
			setNumMiss(dataObj.get(JSONField.MISS).getAsInt());
		}
		if (dataObj.has(JSONField.PROFILE) && !dataObj.get(JSONField.PROFILE).isJsonNull()) {
			setCurrentProfileDir(dataObj.get(JSONField.PROFILE).getAsString());
		}
		if (dataObj.has(JSONField.DIR) && !dataObj.get(JSONField.DIR).isJsonNull()) {
			setCurrentLaunchDir(dataObj.get(JSONField.DIR).getAsString());
		}
		if (dataObj.has(JSONField.TIMESTAMP)) {
			timeStats = new ServerTimeStats(dataObj.getAsJsonObject(JSONField.TIMESTAMP));
		}
		if (dataObj.has(JSONField.PORT)) {
			setServerPort(dataObj.get(JSONField.PORT).getAsInt());
		}
		if (dataObj.has(JSONField.CONN_HANDLE)) {
			JsonObject handlesObj = dataObj.getAsJsonObject(JSONField.CONN_HANDLE);
			for (String handle : handlesObj.keySet()) {
				addConnHandle(Integer.valueOf(handle), handlesObj.get(handle).getAsString());
			}
		}
		if (dataObj.has(JSONField.SETTINGS) && !dataObj.get(JSONField.SETTINGS).isJsonNull()) {
			setGameSettings(dataObj.getAsJsonObject(JSONField.SETTINGS));
		}
		if (dataObj.has(JSONField.PROXIES)) {
			JsonArray proxiesArr = dataObj.getAsJsonArray(JSONField.PROXIES);
			for (JsonElement proxyEle : proxiesArr) {
				addProxyServer(proxyEle.getAsString());
			}
		}
		if (dataObj.has(JSONField.PLAYERS)) {
			JsonArray playersArr = dataObj.getAsJsonArray(JSONField.PLAYERS);
			for (JsonElement playerData : playersArr) {
				JsonObject playerObj = playerData.getAsJsonObject();
				PlayerData player = new PlayerData();
				player.setPlayerName(playerObj.get(JSONField.PLAYER_NAME).getAsString());
				player.setLogID(id);
				player.setUID(playerObj.get(JSONField.USER_ID).getAsString());
				if (playerObj.has(JSONField.IP)) {
					player.setIPAndPort(playerObj.get(JSONField.IP).getAsString());
					player.setConnHandle(playerObj.get(JSONField.CONN_HANDLE).getAsInt());
				}
				player.setPlatform(PlatformType.codeToType(playerObj.get(JSONField.PLATFORM).getAsInt()));
				player.setLastLogTime(playerObj.get(JSONField.TIMESTAMP).getAsInt());
				player.setHasParticipated(playerObj.get(JSONField.DATA).getAsBoolean());
				player.setEloRating(getEloRating());
				player.setGameMode(getGameModeId());
				addPlayer(player);
			}
		}
	}
	
	// Note we do not store buildId here because that is guaranteed to be parsed every time
	// from the log, as it comes before the current time
	public String getServerDataDB() {
		JsonObject dataObj = new JsonObject();
		dataObj.addProperty(JSONField.POSITION, logPosition);
		dataObj.addProperty(JSONField.REPEAT, numRepeats);
		dataObj.addProperty(JSONField.MISS, missCount);
		dataObj.addProperty(JSONField.PROFILE, currentProfileDir);
		dataObj.addProperty(JSONField.DIR, currentLaunchDir);
		dataObj.addProperty(JSONField.PORT, serverPort);
		dataObj.add(JSONField.TIMESTAMP, timeStats.getTimeStatDB());
		dataObj.add(JSONField.CONN_HANDLE, getConnHandlesDB());
		dataObj.add(JSONField.SETTINGS, gameSettings);
		dataObj.add(JSONField.PLAYERS, getConnectedPlayersDB());
		dataObj.add(JSONField.PROXIES, getProxiesDB());
		
		return dataObj.toString();
	}
	
	public JsonObject getServerInfo() {
		JsonObject serverInfo = new JsonObject();
		serverInfo.add(JSONField.SETTINGS, gameSettings);
		JsonArray curPlayers = new JsonArray();
		for (PlayerData player : getConnectedPlayers()) {
			curPlayers.add(player.getPlayerName());
		}
		serverInfo.add(JSONField.PLAYERS, curPlayers);
		serverInfo.addProperty(JSONField.SERVER_ID, getId());
		serverInfo.addProperty(JSONField.TIMESTAMP, System.currentTimeMillis());
		return serverInfo;
	}
}
