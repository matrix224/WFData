package wfDataModel.model.data;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import jdtools.util.MiscUtil;
import wfDataModel.service.codes.JSONField;

/**
 * Data model for storing information about a killer (player or weapon)
 * @author MatNova
 *
 */
public class KillerData {

	@Expose()
	@SerializedName(JSONField.TOTAL)
	private int totalKills = 0;
	@Expose()
	private Map<String, Integer> weaponKills = new HashMap<String, Integer>(2); // UID or weapon -> kills
	
	public void combine(KillerData other) {
		addToTotalKills(other.getTotalKills());
		addToWeaponKills(other.getWeaponKills());
	}
	
	public void setTotalKills(int totalKills) {
		this.totalKills = totalKills;
	}
	
	public void addToTotalKills(int kills) {
		totalKills += kills;
	}
	
	public int getTotalKills() {
		return totalKills;
	}
	
	public void addToWeaponKills(String weapon, int kills) {
		if (!MiscUtil.isEmpty(weapon)) {
			weaponKills.compute(weapon, (k,v) -> v == null ? kills : v + kills);
		}
	}
	
	public void addToWeaponKills(Map<String, Integer> weaponKills) {
		for (String wep : weaponKills.keySet()) {
			this.weaponKills.compute(wep, (k,v) -> v == null ? weaponKills.get(wep) : v + weaponKills.get(wep));
		}
	}
	
	public int getWeaponKills(String weapon) {
		return weaponKills.getOrDefault(weapon, 0);
	}
	
	public Map<String, Integer> getWeaponKills() {
		return weaponKills;
	}
	
}
