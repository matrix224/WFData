package wfDataModel.service.type;

import wfDataModel.model.logging.Log;

/**
 * The types of regions
 * @author MatNova
 *
 */
public enum RegionType {
	ASIA(8),
	EUROPE(7),
	NORTH_AMERICA(4),
	OCEANIA(9),
	RUSSIA(14),
	SOUTH_AMERICA(6),
	UNKNOWN(0);
	
	private int code;
	
	private RegionType(int code) {
		this.code = code;
	}
	
	public int getCode() {
		return code;
	}
	
	public static RegionType codeToType(int code) {
		RegionType type = null;
		
		for (RegionType reg : values()) {
			if (reg.getCode() == code) {
				type = reg;
				break;
			}
		}
		
		if (type == null) {
			Log.warn("RegionType.codeToType() : Unknown code, defaulting to UNKNOWN -> " + code);
			type = UNKNOWN;
		}
		
		return type;
	}
}
