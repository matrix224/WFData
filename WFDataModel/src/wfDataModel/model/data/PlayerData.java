package wfDataModel.model.data;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import jdtools.util.MiscUtil;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.type.EloType;
import wfDataModel.service.type.GameMode;
import wfDataModel.service.type.PlatformType;

/**
 * Data model for storing information about a player that is in a server
 * @author MatNova
 *
 */
public class PlayerData {

	@Expose()
	@SerializedName(JSONField.PLAYER_NAME)
	private String playerName;
	@Expose()
	@SerializedName(JSONField.USER_ID)
	private String uid;
	@Expose (serialize = false, deserialize = false) 
	private String logId; // ID of server log they're in
	@Expose (serialize = false, deserialize = false) 
	private String ipAndPort; // IP and port for the player. Note that if the IP is not a proxy IP, we default the port to 0 because we don't care about it
	@Expose (serialize = false, deserialize = false) 
	private int connHandle = -1; // The player's connection handle
	@Expose()
	@SerializedName(JSONField.PLATFORM)
	private PlatformType platform;
	@Expose()
	private int kills; // Also passes for Lunaro
	@Expose()
	private int deaths; // Also interceptions for Lunaro
	@Expose()
	private int mechanics; // Goals for Lunaro, unknown for other games
	@Expose()
	private int captures; // CTF only
	@Expose()
	private int rounds;
	@Expose()
	private int roundsWon;
	@Expose (serialize = false, deserialize = false) 
	private EloType eloRating;
	@Expose (serialize = false, deserialize = false) 
	private GameMode gameModeId;
	@Expose()
	private Map<String, KillerData> killers = new HashMap<String, KillerData>(); // UID or entity (i.e. DamageTrigger) -> killer data (total kills, weapon breakdown). Should only be stored per parsing session
	@Expose()
	private Map<String, Integer> weapons = new HashMap<String, Integer>(); // Weapon -> kills. Should only be stored per parsing session
	@Expose (serialize = false, deserialize = false) 
	private int lastLogTime; // The last second count in the log that data was read for this player
	@Expose()
	private int totalTime; // Total time this player was in the server for the current data read (i.e. not persistent across reads)
	@Expose ()
	@SerializedName(JSONField.LEFT_SERVER)
	private boolean leftServer = false;
	@Expose ()
	private boolean hasParticipated = false; // If this player has done anything at all during a match (killed, died, captured, played a round, etc)

	public String getPlayerName() {
		return playerName;
	}

	public void setPlayerName(String playerName) {
		this.playerName = playerName;
	}

	public String getUID() {
		return uid;
	}

	public void setUID(String uid) {
		this.uid = uid;
	}

	public String getLogID() {
		return logId;
	}

	public void setLogID(String logId) {
		this.logId = logId;
	}

	public String getIPAndPort() {
		return ipAndPort;
	}

	public void setIPAndPort(String ipAndPort) {
		this.ipAndPort = ipAndPort;
	}

	public PlatformType getPlatform() {
		return platform;
	}

	public void setPlatform(PlatformType platform) {
		this.platform = platform;
	}

	public int getKills() {
		return kills;
	}

	public void setKills(int kills) {
		this.kills = kills;
	}

	public int getDeaths() {
		return deaths;
	}

	public void setDeaths(int deaths) {
		this.deaths = deaths;
	}

	public int getMechanics() {
		return mechanics;
	}

	public void setMechanics(int mechanics) {
		this.mechanics = mechanics;
	}

	public int getCaptures() {
		return captures;
	}

	public void setCaptures(int captures) {
		this.captures = captures;
	}

	public int getRounds() {
		return rounds;
	}

	public void setRounds(int rounds) {
		this.rounds = rounds;
	}

	public int getRoundsWon() {
		return roundsWon;
	}

	public void setRoundsWon(int roundsWon) {
		this.roundsWon = roundsWon;
	}

	public EloType getEloRating() {
		return eloRating;
	}

	public void setEloRating(int eloRating) {
		this.eloRating = EloType.codeToType(eloRating);
	}

	public GameMode getGameMode() {
		return gameModeId;
	}

	public void setGameMode(int gameModeId) {
		this.gameModeId = GameMode.idToType(gameModeId);
	}

	public boolean isForLunaro() {
		return GameMode.LUNARO.equals(getGameMode());
	}

	public boolean isForCTF() {
		return GameMode.CTF.equals(getGameMode());
	}

	public void addKilledBy(Map<String, KillerData> killedBy) {
		if (!MiscUtil.isEmpty(killedBy)) {
			for (String killer : killedBy.keySet()) {
				killers.computeIfAbsent(killer, k -> new KillerData()).combine(killedBy.get(killer));
			}
		}
	}

	public void addKilledBy(String killedBy, String weapon) {
		KillerData data = killers.computeIfAbsent(killedBy, k -> new KillerData());
		data.addToTotalKills(1);
		data.addToWeaponKills(weapon, 1);
	}

	public Map<String, KillerData> getKilledBy() {
		return killers;
	}

	public void addWeaponKill(Map<String, Integer> weaponKills) {
		if (!MiscUtil.isEmpty(weaponKills)) {
			for (String weapon : weaponKills.keySet()) {
				addWeaponKill(weapon, weaponKills.get(weapon));
			}
		}
	}

	public void addWeaponKill(String weapon) {
		addWeaponKill(weapon, 1);
	}

	private void addWeaponKill(String weapon, int kills) {
		weapons.compute(weapon, (k,v) -> v == null ? kills : v + kills);
	}

	public Map<String, Integer> getWeaponKills() {
		return weapons;
	}

	/**
	 * Resets all currently parsed data for this player (i.e. kills, deaths, total time, etc)
	 * This will also mark the player as currently being in the server. <br>
	 * This is intended to be used at the start of a new parsing session, or in the event an error occurred. <br/>
	 * If isError is true, this will additionally reset their last log time
	 * @param isError
	 */
	public void resetParse(boolean isError) {
		weapons.clear();
		killers.clear();
		kills = 0;
		deaths = 0;
		rounds = 0;
		mechanics = 0;
		roundsWon = 0;
		captures = 0;
		if (isError) {
			setLastLogTime(getLastLogTime() - getTotalTime());
		}
		totalTime = 0;
		setLeftServer(false, getLastLogTime());
	}

	public void setLastLogTime(int lastLogTime) {
		if (lastLogTime > 0) {
			// If changing last log time and it's not the first time, update the current total time
			// Also check to make sure the provided time is greater than the current one if set
			// This is to ensure if we re-parse data (i.e. an error occurs during parsing) we don't get any wacky results
			if (this.lastLogTime > 0 && lastLogTime > this.lastLogTime) {
				totalTime += (lastLogTime - this.lastLogTime);
			}
			this.lastLogTime = lastLogTime;
		}
	}

	public int getLastLogTime() {
		return lastLogTime;
	}

	public int getTotalTime() {
		return totalTime;
	}

	public void setLeftServer(boolean leftServer, int lastLogTime) {
		if (leftServer != this.leftServer) {
			setLastLogTime(lastLogTime);
			this.leftServer = leftServer;
			if (leftServer) {
				this.lastLogTime = -1;
			}
		}
	}

	public boolean leftServer() {
		return leftServer;
	}

	public void setConnHandle(int connHandle) {
		this.connHandle = connHandle;
	}

	public int getConnHandle() {
		return connHandle;
	}

	/**
	 * Returns if this player has any actual data currently, or has at one point during the current parsing session. <br>
	 * "Actual data" can be any of:
	 * kills, deaths, mechanics, captures, rounds, or roundsWon
	 * @return
	 */
	public boolean hasData() {
		return (hasParticipated = hasParticipated || kills != 0 || deaths != 0 || mechanics != 0 || captures != 0 || rounds != 0 || roundsWon != 0);
	}

	protected void setHasParticipated(boolean hasParticipated) {
		this.hasParticipated = hasParticipated;
	}

	@Override
	public boolean equals(Object other) {
		if (other != null && other instanceof PlayerData) {
			return ((PlayerData)other).getUID().equals(getUID());
		}
		return super.equals(other);
	}

	@Override
	public String toString() {
		return playerName + " (" + uid + ") -> Kills: " + kills + ", Deaths: " + deaths + ", Mechanics: " + mechanics + ", Captures: " + captures + ", Rounds: " + rounds + ", TotalTime: " + totalTime + ", Platform: " + platform + " (" + gameModeId + " " + eloRating + ")";
	}
}
