package wfDataModel.service.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import wfDataModel.service.codes.JSONField;
import wfDataModel.service.type.BanDirectionType;
import wfDataModel.service.type.BanProtocolType;

/**
 * Class that stores overall data about a player's ban, across all IPs
 * @author MatNova
 *
 */
public class BanData {

	public static final String PERM_BAN_IP = "PERM";

	@Expose()
	@SerializedName(JSONField.PLAYER_NAME)
	private String playerName;
	@Expose()
	@SerializedName(JSONField.USER_ID)
	private String UID;
	@Expose (serialize = false, deserialize = false) 
	private List<Integer> offensiveLoadouts = new ArrayList<Integer>(); //  Past loadouts that have gotten them banned
	@Expose (serialize = false, deserialize = false) 
	private Map<String, Integer> strikes = new HashMap<String, Integer>(2); // Loadout -> number of times offended
	@Expose()
	private Map<String, BanSpec> banSpecs = new HashMap<String, BanSpec>(2); // IP:Port -> BanSpec (banspec = data about a ban, i.e. ban time, primary, etc)

	public BanData(String playerName, String UID) {
		this.playerName = playerName;
		this.UID = UID;
	}

	public String getPlayerName() {
		return playerName;
	}

	public String getUID() {
		return UID;
	}

	public void addOffensiveLoadout(int loadoutID) {
		if (!offensiveLoadouts.contains(loadoutID)) {
			offensiveLoadouts.add(loadoutID);
		}
	}

	public List<Integer> getOffensiveLoadouts() {
		return offensiveLoadouts;
	}

	public String getOffensiveLoadoutsForDB() {
		JsonObject obj = new JsonObject();
		JsonArray loadoutsArr = new JsonArray();
		for (Integer loadout : offensiveLoadouts) {
			loadoutsArr.add(loadout);
		}
		obj.add(JSONField.LOADOUTS, loadoutsArr);
		return obj.toString();
	}

	public Long getBanTime(String ipAndPort) {
		return banSpecs.containsKey(ipAndPort) ? banSpecs.get(ipAndPort).getBanTime() : null;
	}

	public boolean isPrimary(String ipAndPort) {
		return banSpecs.containsKey(ipAndPort) ? banSpecs.get(ipAndPort).isPrimary() : false;
	}

	public boolean isProxy(String ipAndPort) {
		return banSpecs.containsKey(ipAndPort) ? banSpecs.get(ipAndPort).isProxy() : false;
	}

	public String getReportedBy(String ipAndPort) {
		return banSpecs.containsKey(ipAndPort) ? banSpecs.get(ipAndPort).getReportedBy() : null;
	}

	public int getReportingID(String ipAndPort) {
		return banSpecs.containsKey(ipAndPort) ? banSpecs.get(ipAndPort).getReportingID() : null;
	}

	public BanSpec addOrGetBanSpec(String ipAndPort) {
		BanSpec spec = getBanSpec(ipAndPort);
		if (spec == null) {
			spec = new BanSpec(ipAndPort);
			banSpecs.put(spec.getIP(), spec);
			if (banSpecs.size() == 1) {
				spec.setPrimary(true);
			}
		}
		return spec;
	}
	
	public void addBanSpec(BanSpec spec) {
		banSpecs.put(spec.getIP(), spec);
	}

	public void removeBanSpec(String ipAndPort) {
		banSpecs.remove(ipAndPort);
	}

	public BanSpec getBanSpec(String ipAndPort) {
		return banSpecs.get(ipAndPort);
	}

	public Collection<BanSpec> getBanSpecs() {
		return banSpecs.values();
	}

	public String getBanReason(String ipAndPort) {
		return banSpecs.containsKey(ipAndPort) ? banSpecs.get(ipAndPort).getBanReason() : null;
	}

	public int getBanLoadoutID(String ipAndPort) {
		return banSpecs.containsKey(ipAndPort) ? banSpecs.get(ipAndPort).getLoadoutID() : 0;
	}

	public void addStrike(String loadout) {
		strikes.compute(loadout, (k,v) -> v == null ? 1 : v + 1);
	}

	public int getStrikes(String loadout) {
		return strikes.containsKey(loadout) ? strikes.get(loadout) : 0;
	}

	public boolean isPermanent() {
		return getBanSpec(PERM_BAN_IP) != null;
	}

	public String getBanKey(String ipAndPort) {
		return "WF_" + getUID() + "_" + ipAndPort;
	}

	public String getBanKey(String ipAndPort, BanDirectionType direction) {
		return getBanKey(ipAndPort, direction, BanProtocolType.ANY);
	}
	
	public String getBanKey(String ipAndPort, BanDirectionType direction, BanProtocolType protocol) {
		return getBanKey(ipAndPort) + "_" + direction + "_" + protocol;
	}
}
