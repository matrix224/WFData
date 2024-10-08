package wfDataModel.service.type;

import jdtools.logging.Log;

/**
 * The elo type for a game mode
 * @author MatNova
 *
 */
public enum EloType {

	RC(0),
	NON_RC(2),
	TOTAL(999999); // Made up value for data aggregated across all game modes
	
	private int code;
	
	private EloType(int code) {
		this.code = code;
	}
	
	public static EloType codeToType(int code) {
		EloType type = null;
		
		for (EloType eloType : values()) {
			if (eloType.code == code) {
				type = eloType;
				break;
			}
		}
		
		if (type == null) {
			Log.warn("EloType.codeToType() : Could not determine type for code: " + code);
		}
		
		return type;
	}
	
	public int getCode() {
		return code;
	}
}
