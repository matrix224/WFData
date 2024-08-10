package wfDataModel.service.type;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;

/**
 * Denotes the type of a weapon. In the context of the parser, a Warframe itself may be considered a weapon. <br>
 * The codes associated with a type are in correlation to the "productCategory" as defined by DE's data. <br>
 * Note that all of these may not be applicable to Conclave (e.g. arch weapons)
 * @author MatNova
 *
 */
public enum WeaponType {
	AMP("OperatorAmps"),
	ARCHGUN("SpaceGuns"),
	ARCHMELEE("SpaceMelee"),
	ARCHWING("SpaceSuits"),
	EXALTED_WEAPON("SpecialItems", "Exalted"),
	MELEE("Melee", "Melee"),
	NECRAMECH("MechSuits"),
	PRIMARY("LongGuns", "Primary"),
	RAILJACK("CrewShipWeapons"),
	SECONDARY("Pistols", "Secondary"),
	SENTINEL("SentinelWeapons"),
	WARFRAME("Suits", "Warframe"),
	UNKNOWN;
	
	private static final String LOG_ID = WeaponType.class.getSimpleName();
	private static final String DEF_DISPLAY_NAME = "Other";
	private final String code;
	private final String displayName;
	
	private WeaponType() {
		this(null);
	}
	
	private WeaponType(String code) {
		this(code, DEF_DISPLAY_NAME);
	}
	
	private WeaponType(String code, String displayName) {
		this.code = code;
		this.displayName = displayName;
	}
	
	public String getCode() {
		return code;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	public static WeaponType codeToType(String code) {
		WeaponType type = UNKNOWN;
		
		for (WeaponType t : values()) {
			if (!MiscUtil.isEmpty(t.getCode()) && t.getCode().equals(code)) {
				type = t;
				break;
			}
		}
		
		if (type.equals(UNKNOWN)) {
			Log.warn(LOG_ID, ".codeToType() : Unknown code provided -> ", code);
		}
		
		return type;
	}
}
