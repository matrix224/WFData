package wfDataModel.model.data;

import wfDataModel.service.type.WeaponType;

/**
 * Denotes basic information about a weapon
 * @author MatNova
 *
 */
public class WeaponData {
	
	private final String internalName; // The name of the weapon internally to DE
	private String realName; // The actual display name
	private WeaponType type = WeaponType.UNKNOWN; // What type of weapon this is
	
	public WeaponData(String internalName) {
		this.internalName = internalName;
	}
	
	public String getInternalName() {
		return internalName;
	}
	
	public void setRealName(String realName) {
		this.realName = realName;
	}
	
	public String getRealName() {
		return realName;
	}
	
	public void setType(WeaponType type) {
		this.type = type;
	}
	
	public WeaponType getType() {
		return type;
	}
}
