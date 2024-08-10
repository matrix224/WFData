package wfDataManager.client.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jdtools.util.MiscUtil;

/**
 * Class that is used to track specified players for any potential alt accounts
 * @author MatNova
 *
 */
public class PlayerTracker {

	private String uid;
	private String playerName = "Unknown";
	private Set<String> knownIPs = null;
	private Map<String, String> knownAlts = null; // UID -> name
	
	public PlayerTracker(String uid) {
		this(uid, "Unknown");
	}
	
	public PlayerTracker(String uid, String playerName) {
		this(uid, playerName, new HashSet<String>(), new HashMap<String, String>());
	}
	
	public PlayerTracker(String uid, String playerName, Set<String> knownIPs, Map<String, String> knownAlts) {
		this.uid = uid;
		this.playerName = MiscUtil.isEmpty(playerName) ? "Unknown" : playerName;
		this.knownIPs = knownIPs;
		this.knownAlts = knownAlts;
	}
	
	public String getUID() {
		return uid;
	}
	
	public void setUID(String uid) {
		this.uid = uid;
	}
	
	public void setPlayerName(String playerName) {
		this.playerName = playerName;
	}
	
	public String getPlayerName() {
		return playerName;
	}
	
	public void addKnownIP(String ip) {
		knownIPs.add(ip);
	}
	
	public Set<String> getKnownIPs() {
		return knownIPs;
	}
	
	public void addKnownAlt(String altUID, String name) {
		knownAlts.put(altUID, name);
	}
	
	public void removeKnownAlt(String altUID) {
		knownAlts.remove(altUID);
	}
	
	public Map<String, String> getKnownAlts() {
		return knownAlts;
	}
	
	/**
	 * Given a UID, name, and an IP, will return if either the UID or IP were new information to this tracker,
	 * and will add them to it as well if they are
	 * @param uid
	 * @param name
	 * @param ip
	 * @return
	 */
	public boolean updateTracker(String uid, String name, String ip) {
		return (!this.uid.equals(uid) && knownAlts.putIfAbsent(uid, name) == null) || knownIPs.add(ip);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("UID: ").append(uid).append(" (").append(playerName).append(")").append("\n");
		sb.append("Known Alts: ").append("\n\t").append(knownAlts).append("\n");
		sb.append("Known IPs: ").append("\n\t").append(knownIPs);
		return sb.toString();
	}
}
