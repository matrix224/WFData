package wfDataModel.model.data;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import jdtools.collection.Pair;
import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.type.LevelType;

/**
 * Data model for storing information about a currently parsed server
 * @author MatNova
 *
 */
public class ServerData {

	private static final String LOG_ID = ServerData.class.getSimpleName();
	
	// Time (in seconds) that an accountId mapping is considered expired (i.e. no longer valid)
	// This is an arbitrary number that essentially says "if an accountId has been a candidate for >= this long and has not been touched or mapped since, consider it dead"
	private static final long ACCOUNT_ID_EXPIRE = 180;
	private static final long ACCOUNT_ID_RELAY_EXPIRE = 30;

	
	// These numbers are used for the confidence levels in terms of an accountID to IP:Port mapping
	// The lower the number, the higher the confidence
	private static final int MAPPING_PRIMARY_DIRECT = 1;
	private static final int MAPPING_PRIMARY = 2;
	private static final int MAPPING_SECONDARY = 3;


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
	@Expose()
	@SerializedName(JSONField.LEVEL)
	private LevelType level = LevelType.UNKNOWN;
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
	@Expose (serialize = false, deserialize = false) 
	private Map<String, Pair<String, Long>> acctIds = new HashMap<String, Pair<String, Long>>(); //IP:Port -> AcctID, time checked (or Long.MAX_VALUE if we've fully assigned to a player). Remains set until associated player disconnects

	@Expose (serialize = false, deserialize = false) 
	private Map<Integer, Map<String, List<Pair<String, Long>>>> accountIDCandidates = new HashMap<Integer, Map<String, List<Pair<String, Long>>>>(); // Mapping confidence -> IP:Ports -> potential acctId. Used to map possible accountID candidates
	@Expose (serialize = false, deserialize = false) 
	private int lastConnHandle = -1; // The last parsed connHandle from the SET_TIMEOUT_PATTERN parse
	@Expose (serialize = false, deserialize = false) 
	private Map<String, Integer> guessConnMapping = new HashMap<String, Integer>(2); // playerName -> connHandle. Temporarily stores best-guess mappings of players to their connHandle via the SET_TIMEOUT_PATTERN and the subsequent CONTACT_RECEIVED_PATTERN matches
	@Expose (serialize = false, deserialize = false) 
	private Map<Integer, Pair<String, String>> connToAcctId = new HashMap<Integer, Pair<String, String>>(2); // connHandle -> acctId, ip:port.  Temporarily stores connHandle to accountID for assignment later

	@Expose (serialize = false, deserialize = false) 
	private Map<String, String> relayIPMapping = new HashMap<String, String>(2);

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

	public void setRelayIPMapping(String relayIP, String ip) {
		relayIPMapping.put(relayIP, ip);
	}

	public String getRelayIPMapping(String relayIP) {
		return relayIPMapping.get(relayIP);
	}

	public String getRelayReverseIPMapping(String ip) {
		String relayIP = null;
		if (relayIPMapping.values().contains(ip)) {
			for (String relay : relayIPMapping.keySet()) {
				if (ip.equals(relayIPMapping.get(relay))) {
					relayIP = relay;
					break;
				}
			}
		}
		return relayIP;
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

	public void setLevel(LevelType level) {
		this.level = level;
	}
	
	public LevelType getLevel() {
		return level;
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
					removeAcctIdForConn(entry.getValue().getConnHandle());
				}
				if (!MiscUtil.isEmpty(entry.getValue().getAccountID())) {
					removeAccountIDCandidate(entry.getValue().getAccountID());
					removeAccountIDMapping(entry.getValue().getAccountID());
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
			clearAccountIDCandidates();
			clearAccountIDMappings();
			clearGuessConnMappings();
			clearConnToAcctMappings();
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

	public PlayerData getPlayerByNameAndPlatform(String name, int platform) {
		for (PlayerData data : getParsedPlayers()) {
			if (data.getPlayerName().equals(name) && data.getPlatform() == platform) {
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
			if (!MiscUtil.isEmpty(player.getAccountID())) {
				playerObj.addProperty(JSONField.ACCOUNT_ID, player.getAccountID());
			}
			if (!MiscUtil.isEmpty(player.getIPAndPort())) {
				playerObj.addProperty(JSONField.IP, player.getIPAndPort());
				playerObj.addProperty(JSONField.CONN_HANDLE, player.getConnHandle());
			}
			playerObj.addProperty(JSONField.PLATFORM, player.getPlatform());
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

	private void clearConnHandles() {
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

	/**
	 * Given an ipAndPort, an accountId, a log time, if this is a primary candidate, and if this is a direct candidate, 
	 * this will add the candidate to a bucket that is weighted based on the provided parameters. <br>
	 * If isDirect is true, this means that the candidate was a direct connection (i.e. not relayed) and was not a proxy, which is the best you can get.
	 * Otherwise if isPrimaryCandidate is true, this means the acct was mapped under our primary candidate log messages / logic, which is the next best case.
	 * Lastly, if isPrimaryCandidate and isDirect are false, then this is considered a secondary mapping, which is the least trustworthy we can have, but can still
	 * fall back onto it if we never receive anything better.
	 * 
	 * @param ipAndPort
	 * @param accountId
	 * @param logTime
	 * @param isPrimaryCandidate
	 * @param isDirect
	 */
	public void addAccountIDCandidate(String ipAndPort, String accountId, long logTime, boolean isPrimaryCandidate, boolean isDirect) {
		int candidateKey = isDirect ? MAPPING_PRIMARY_DIRECT : isPrimaryCandidate ? MAPPING_PRIMARY : MAPPING_SECONDARY;
		accountIDCandidates.computeIfAbsent(candidateKey, k -> new HashMap<String, List<Pair<String, Long>>>()).computeIfAbsent(ipAndPort, k -> new ArrayList<Pair<String, Long>>());

		Pair<String, Long> existing = null;
		for (Pair<String, Long> acctCandidate : accountIDCandidates.get(candidateKey).get(ipAndPort)) {
			if (acctCandidate.getKey().equals(accountId)) {
				existing = acctCandidate;
				break;
			}
		}
		if (existing != null) {
			accountIDCandidates.get(candidateKey).get(ipAndPort).remove(existing);
		}
		// For each ipAndPort, we bump the acctId that last referenced it to the top
		// This will be our candidate that we use for mapping
		// This is a instead of a 1:1 mapping because when multiple people with the same IP join at once,
		// several mismatched requests of acctIds and IP:Port are found
		accountIDCandidates.get(candidateKey).get(ipAndPort).add(0, new Pair<String, Long>(accountId, logTime));
		Log.info("ADD ACCTID CANDIDATE " + ipAndPort + ", " + accountId + ", key=" + candidateKey + ", logtime=" + logTime);
	}

	private void clearAccountIDCandidates() {
		accountIDCandidates.clear();
	}

	/**
	 * Given an ipAndPort, will remove all accountID candidates that are mapped to this IP and port
	 * @param ipAndPort
	 */
	public void removeAccountIDCandidates(String ipAndPort) {
		for (Integer candidateKey : accountIDCandidates.keySet()) {
			accountIDCandidates.get(candidateKey).remove(ipAndPort);
		}
	}

	/**
	 * Given an acctId, will remove this acctId as a candidate from all current candidate mappings. <br>
	 * If any IPPort no longer has any acctIds mapped to it after this acctId is removed from under it,
	 * then the mapping for that IPPort as a whole will be removed as well.
	 * @param acctId
	 */
	public void removeAccountIDCandidate(String acctId) {
		List<String> toRemove = new ArrayList<>();
		for (Integer candidateKey : accountIDCandidates.keySet()) {
			for (String ipAndPort : accountIDCandidates.get(candidateKey).keySet()) {
				Pair<String, Long> existing = null;
				for (Pair<String, Long> acctCandidate : accountIDCandidates.get(candidateKey).get(ipAndPort)) {
					if (acctCandidate.getKey().equals(acctId)) {
						existing = acctCandidate;
						break;
					}
				}

				if (existing != null && accountIDCandidates.get(candidateKey).get(ipAndPort).remove(existing)) {
					Log.info("REMOVE ACCT CANDIDATE " + ipAndPort + ", " + acctId + ", key=" + candidateKey);
					/*String relayIP = getRelayReverseIPMapping(ipAndPort);
					if (!MiscUtil.isEmpty(relayIP)) {
						relayIPMapping.remove(relayIP);
						Log.info("REMOVE RELAY IP " + relayIP);
					}*/
				}
				if (accountIDCandidates.get(candidateKey).get(ipAndPort).isEmpty()) {
					toRemove.add(ipAndPort);
				}
			}
			for (String ipAndPort : toRemove) {
				accountIDCandidates.get(candidateKey).remove(ipAndPort);
			}
		}
	}

	public String getAccountIDCandidate(String ipAndPort, long logTime) {
		if (accountIDCandidates.containsKey(MAPPING_PRIMARY_DIRECT) && !MiscUtil.isEmpty(accountIDCandidates.get(MAPPING_PRIMARY_DIRECT).get(ipAndPort)) && logTime - accountIDCandidates.get(MAPPING_PRIMARY_DIRECT).get(ipAndPort).get(0).getValue() < ACCOUNT_ID_EXPIRE) {
			return accountIDCandidates.get(MAPPING_PRIMARY_DIRECT).get(ipAndPort).get(0).getKey();
		} 
		if (accountIDCandidates.containsKey(MAPPING_PRIMARY) && !MiscUtil.isEmpty(accountIDCandidates.get(MAPPING_PRIMARY).get(ipAndPort)) && logTime - accountIDCandidates.get(MAPPING_PRIMARY).get(ipAndPort).get(0).getValue() < ACCOUNT_ID_EXPIRE) {
			return accountIDCandidates.get(MAPPING_PRIMARY).get(ipAndPort).get(0).getKey();
		} else {
			String relayIP = getRelayReverseIPMapping(ipAndPort);
			if (!MiscUtil.isEmpty(relayIP) && accountIDCandidates.containsKey(MAPPING_PRIMARY) && !MiscUtil.isEmpty(accountIDCandidates.get(MAPPING_PRIMARY).get(relayIP)) && logTime - accountIDCandidates.get(MAPPING_PRIMARY).get(relayIP).get(0).getValue() < ACCOUNT_ID_RELAY_EXPIRE) {
				Log.info("USING PRIMARY RELAY CANDIDATE FOR " + ipAndPort + "(RELAY=" + relayIP+")");
				return accountIDCandidates.get(MAPPING_PRIMARY).get(relayIP).get(0).getKey();
			} else if (accountIDCandidates.containsKey(MAPPING_SECONDARY) && !MiscUtil.isEmpty(accountIDCandidates.get(MAPPING_SECONDARY).get(ipAndPort)) && logTime - accountIDCandidates.get(MAPPING_SECONDARY).get(ipAndPort).get(0).getValue() < ACCOUNT_ID_EXPIRE) {
				Log.info("USING SECONDARY CANDIDATE FOR " + ipAndPort);
				return accountIDCandidates.get(MAPPING_SECONDARY).get(ipAndPort).get(0).getKey();
			} else if (!MiscUtil.isEmpty(relayIP) && accountIDCandidates.containsKey(MAPPING_SECONDARY) && !MiscUtil.isEmpty(accountIDCandidates.get(MAPPING_SECONDARY).get(relayIP)) && logTime - accountIDCandidates.get(MAPPING_SECONDARY).get(relayIP).get(0).getValue() < ACCOUNT_ID_RELAY_EXPIRE) {
				Log.info("USING SECONDARY RELAY CANDIDATE FOR " + ipAndPort + "(RELAY=" + relayIP+")");
				return accountIDCandidates.get(MAPPING_SECONDARY).get(relayIP).get(0).getKey();
			}
		}
		return null;
	}

	public List<String> getIPAndPortsForAccountIDCandidates(String acctId, long logTime) {
		List<String> ipAndPorts = new ArrayList<String>();
		for (Integer candidateKey : accountIDCandidates.keySet()) {
			for (String ipAndPort : accountIDCandidates.get(candidateKey).keySet()) {
				if (!accountIDCandidates.get(candidateKey).get(ipAndPort).isEmpty() && accountIDCandidates.get(candidateKey).get(ipAndPort).get(0).getKey().equals(acctId) && logTime - accountIDCandidates.get(candidateKey).get(ipAndPort).get(0).getValue() < ACCOUNT_ID_EXPIRE) {
					ipAndPorts.add(ipAndPort);
				}
			}
		}


		return ipAndPorts;
	}

	private JsonElement getAccountIDCandidatesDB() {
		/*
		JsonObject accts = new JsonObject();
		for (Integer candidateKey) {
			for (String ipAndPort : accountIDCandidates.keySet()) {
				JsonArray acctsArr = new JsonArray();
				for (String accountID : accountIDCandidates.get(ipAndPort)) {
					acctsArr.add(accountID);
				}
				accts.add(ipAndPort, acctsArr);
			}
		}
		return accts;*/
		return new GsonBuilder().disableHtmlEscaping().create().toJsonTree(accountIDCandidates);
	}

	public void addAccountIDMapping(String ipAndPort, String accountID, long logTime, boolean redistribute) {
		Log.info("ADD ACCT MAPPING " + ipAndPort + ", " + accountID + ", " + logTime);
		acctIds.put(ipAndPort, new Pair<String, Long>(accountID, logTime));

		if (redistribute) {
			for (Integer candidateKey : accountIDCandidates.keySet()) {
				List<String> ips = new ArrayList<String>();
				List<Pair<String, Long>> distributes = new ArrayList<Pair<String,Long>>();

				for (String cndIP : accountIDCandidates.get(candidateKey).keySet()) {
					for (Pair<String, Long> acctCandidate : accountIDCandidates.get(candidateKey).get(cndIP)) {
						if (cndIP.equals(ipAndPort) && !acctCandidate.getKey().equals(accountID)) {
							distributes.add(acctCandidate);
						} else if (!cndIP.equals(ipAndPort) && acctCandidate.getKey().equals(accountID)) {
							ips.add(cndIP);
							break;
						}
					}
				}

				if (!MiscUtil.isEmpty(distributes)) {
					for (String cndIP : ips) {
						accountIDCandidates.get(candidateKey).get(cndIP).addAll(distributes);
					}
				}
			}
		}
	}

	public void clearAccountIDMappings() {
		acctIds.clear();
	}

	public void removeAccountIDMapping(String accountID) {
		String targetIPPort = null;
		for (String ipAndPort : acctIds.keySet()) {
			if (acctIds.get(ipAndPort).getKey().equals(accountID)) {
				targetIPPort = ipAndPort;
				break;
			}
		}
		if (!MiscUtil.isEmpty(targetIPPort)) {
			acctIds.remove(targetIPPort);
		}
	}

	public String getAccountIDMapping(String ipAndPort) {
		return acctIds.get(ipAndPort) != null ? acctIds.get(ipAndPort).getKey() : null;
	}

	public boolean hasAccountIDMapping(String ipAndPort, long logTime) {
		String acctId = getAccountIDMapping(ipAndPort);
		boolean hasMapping = !MiscUtil.isEmpty(acctId);

		if (hasMapping) {
			Log.info("CHECKING VALID MAPPING -> " + ipAndPort + ", " + acctId + ", " + logTime + ", " + acctIds.get(ipAndPort).getValue());
			if (logTime - acctIds.get(ipAndPort).getValue() >= ACCOUNT_ID_EXPIRE) {
				Log.info("DOES NOT HAVE ACCTID MAPPING (expire) -> " + ipAndPort + ", " + acctId + ", " + logTime + ", " + acctIds.get(ipAndPort).getValue());
				hasMapping = false;
			} else {
				boolean anyPlayerHas = false;
				for (PlayerData player : getParsedPlayers()) {
					if (acctId.equals(player.getAccountID())) {
						anyPlayerHas = true;
						if(player.leftServer()) {
							hasMapping = false;
							Log.info("DOES NOT HAVE ACCTID MAPPING (d/c player) -> " + ipAndPort + ", " + acctId + ", " + logTime + ", " + acctIds.get(ipAndPort).getValue());
							break;
						}
					}
				}
				if (!anyPlayerHas && acctIds.get(ipAndPort).getValue() == Long.MAX_VALUE) {
					hasMapping = false;
					Log.info("DOES NOT HAVE ACCTID MAPPING (no player) -> " + ipAndPort + ", " + acctId + ", " + logTime + ", " + acctIds.get(ipAndPort).getValue());
				}
			}
		}

		return hasMapping;
	}

	public void markAccountIDFullyMapped(int connId) {
		String targetIPPort = connToAcctId.get(connId) != null ? connToAcctId.get(connId).getValue() : null;
		if (!MiscUtil.isEmpty(targetIPPort) && acctIds.get(targetIPPort) != null) {
			acctIds.put(targetIPPort, new Pair<String, Long>(acctIds.get(targetIPPort).getKey(), Long.MAX_VALUE));
			removeAcctIdForConn(connId);
			removeAccountIDCandidate(acctIds.get(targetIPPort).getKey());
		} else {
			Log.warn(ServerData.class.getSimpleName() + ".markAccountIDFullyMapped() : No targetIP or acctId found for connHandle " + connId);
		}
	}

	private JsonObject getAccountIDMappingsDB() {
		JsonObject accts = new JsonObject();

		for (String ipAndPort : acctIds.keySet()) {
			JsonObject acctObj = new JsonObject();
			acctObj.addProperty(acctIds.get(ipAndPort).getKey(), acctIds.get(ipAndPort).getValue());
			accts.add(ipAndPort, acctObj);
		}

		return accts;
	}

	public void setLastConnHandle(int lastConnHandle) {
		this.lastConnHandle = lastConnHandle;
	}

	public int getLastConnHandle() {
		return lastConnHandle;
	}

	public void addGuessConnMapping(int connHandle, String playerName) {
		guessConnMapping.put(playerName, connHandle);
	}

	public Integer getGuessConnMapping(String playerName) {
		return guessConnMapping.get(playerName);
	}

	public void removeGuessConnMapping(String playerName) {
		guessConnMapping.remove(playerName);
	}

	private void clearGuessConnMappings() {
		guessConnMapping.clear();
	}

	private JsonObject getGuessConnMappingDB() {
		JsonObject guessObj = new JsonObject();
		for (String playerName : guessConnMapping.keySet()) {
			guessObj.addProperty(playerName, guessConnMapping.get(playerName));
		}
		return guessObj;
	}

	public void setAcctIdForConn(int connHandle, String acctId, String ipAndPort) {
		connToAcctId.put(connHandle, new Pair<String, String>(acctId, ipAndPort));
	}

	public String getAcctIdForConn(int connHandle) {
		return connToAcctId.get(connHandle) != null ? connToAcctId.get(connHandle).getKey() : null;
	}

	public void removeAcctIdForConn(int connHandle) {
		connToAcctId.remove(connHandle);
	}

	private void clearConnToAcctMappings() {
		connToAcctId.clear();
	}

	private JsonObject getAccountIDsForConnDB() {
		JsonObject connsAndAcct = new JsonObject();
		for (int connId : connToAcctId.keySet()) {
			JsonObject connObj = new JsonObject();
			connObj.addProperty(connToAcctId.get(connId).getKey(), connToAcctId.get(connId).getValue());
			connsAndAcct.add(String.valueOf(connId), connObj);
		}
		return connsAndAcct;
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

		if (dataObj.has(JSONField.LAST_CONN)) {
			setLastConnHandle(dataObj.get(JSONField.LAST_CONN).getAsInt());
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

		if (dataObj.has(JSONField.CONN_HANDLE_TMP)) {
			JsonObject handlesObj = dataObj.getAsJsonObject(JSONField.CONN_HANDLE_TMP);
			for (String handle : handlesObj.keySet()) {
				setAcctIdForConn(Integer.valueOf(handle), handlesObj.get(handle).getAsJsonObject().keySet().stream().findFirst().get(), handlesObj.get(handle).getAsJsonObject().entrySet().stream().findFirst().get().getValue().getAsString());
			}
		}

		if (dataObj.has(JSONField.ACCOUNT_ID)) {
			JsonObject acctsObj = dataObj.getAsJsonObject(JSONField.ACCOUNT_ID);
			for (String ipAndPort : acctsObj.keySet()) {
				addAccountIDMapping(ipAndPort, acctsObj.get(ipAndPort).getAsJsonObject().keySet().stream().findFirst().get(), acctsObj.get(ipAndPort).getAsJsonObject().entrySet().stream().findFirst().get().getValue().getAsLong(), false);
			}
		}

		if (dataObj.has(JSONField.ACCOUNT_ID_TMP)) {
			/*
			JsonObject acctsObj = dataObj.getAsJsonObject(JSONField.ACCOUNT_ID_TMP);
			for (String ipAndPort : acctsObj.keySet()) {
				JsonArray acctArr = acctsObj.getAsJsonArray(ipAndPort);
				for (int i = 0; i < acctArr.size(); i++) {
					addAccountIDCandidate(ipAndPort, acctArr.get(i).getAsString());
				}
			}*/
			accountIDCandidates = new GsonBuilder().create().fromJson(dataObj.get(JSONField.ACCOUNT_ID_TMP), new TypeToken<Map<Integer, Map<String, List<Pair<String, Long>>>>>(){}.getType());
		}

		if (dataObj.has(JSONField.GUESS)) {
			JsonObject guessObj = dataObj.getAsJsonObject(JSONField.GUESS);
			for (String playerName : guessObj.keySet()) {
				addGuessConnMapping(guessObj.get(playerName).getAsInt(), playerName);
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
				if (playerObj.has(JSONField.ACCOUNT_ID)) {
					player.setAccountID(playerObj.get(JSONField.ACCOUNT_ID).getAsString());
				}
				if (playerObj.has(JSONField.IP)) {
					player.setIPAndPort(playerObj.get(JSONField.IP).getAsString());
					player.setConnHandle(playerObj.get(JSONField.CONN_HANDLE).getAsInt());
				}
				player.setPlatform(playerObj.get(JSONField.PLATFORM).getAsInt());
				player.setLastLogTime(playerObj.get(JSONField.TIMESTAMP).getAsInt());
				player.setHasParticipated(playerObj.get(JSONField.DATA).getAsBoolean());
				player.setEloRating(getEloRating());
				player.setGameMode(getGameModeId());
				addPlayer(player);
			}
		}
		
		if (dataObj.has(JSONField.LEVEL)) {
			String levelStr = dataObj.get(JSONField.LEVEL).getAsString();
			try {
				setLevel(LevelType.valueOf(levelStr));
			} catch (Exception e) {
				Log.warn(LOG_ID, ".buildFromDB() : Unknown level type in server data -> ", levelStr);
				setLevel(LevelType.UNKNOWN);
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
		dataObj.addProperty(JSONField.LAST_CONN, lastConnHandle);
		dataObj.addProperty(JSONField.LEVEL, level.name());
		dataObj.add(JSONField.TIMESTAMP, timeStats.getTimeStatDB());
		dataObj.add(JSONField.CONN_HANDLE, getConnHandlesDB());
		dataObj.add(JSONField.ACCOUNT_ID, getAccountIDMappingsDB());
		dataObj.add(JSONField.ACCOUNT_ID_TMP, getAccountIDCandidatesDB());
		dataObj.add(JSONField.CONN_HANDLE_TMP, getAccountIDsForConnDB());
		dataObj.add(JSONField.GUESS, getGuessConnMappingDB());
		dataObj.add(JSONField.SETTINGS, gameSettings);
		dataObj.add(JSONField.PLAYERS, getConnectedPlayersDB());
		dataObj.add(JSONField.PROXIES, getProxiesDB());

		return dataObj.toString();
	}

	public JsonObject getServerInfo() {
		JsonObject serverInfo = new JsonObject();
		JsonObject settingsObj = new JsonObject();
		settingsObj.addProperty(JSONField.GAME_MODE, getGameModeId());
		settingsObj.addProperty(JSONField.ELO, getEloRating());
		settingsObj.addProperty(JSONField.MAX, getMaxPlayers());
		serverInfo.add(JSONField.SETTINGS, settingsObj);
		JsonArray curPlayers = new JsonArray();
		for (PlayerData player : getConnectedPlayers()) {
			JsonObject playerObj = new JsonObject();
			playerObj.addProperty(JSONField.PLAYER_NAME, player.getPlayerName());
			playerObj.addProperty(JSONField.USER_ID, player.getUID());
			playerObj.addProperty(JSONField.PLATFORM, player.getPlatform());
			curPlayers.add(playerObj);
		}
		serverInfo.add(JSONField.PLAYERS, curPlayers);
		serverInfo.addProperty(JSONField.UTC, timeStats.getStartTimeEpoch());
		serverInfo.addProperty(JSONField.START, timeStats.getMatchStartTime());
		serverInfo.addProperty(JSONField.TIMESTAMP, System.currentTimeMillis());
		serverInfo.addProperty(JSONField.LEVEL, level == null ? LevelType.UNKNOWN.name() : level.name());
		return serverInfo;
	}
}
