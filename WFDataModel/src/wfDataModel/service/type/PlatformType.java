package wfDataModel.service.type;

/**
 * The platform types that clients may be using
 * @author MatNova
 *
 */
public enum PlatformType {
	PC(494),
	XBOX(495),
	PSN(496), // Both PS4 and PS5
	NSW(497), // Switch 1
	IOS(498),
	ANDROID(499),
	NSW2(500), // Switch 1 and Switch 2 have different platform codes for whatever reason
	UNKNOWN(0);
	
	private final int code;
	
	private PlatformType(int code) {
		this.code = code;
	}
	
	public static PlatformType codeToType(int code) {
		PlatformType theType = UNKNOWN;
		
		for (PlatformType type : values()) {
			if (code == type.getCode()) {
				theType = type;
				break;
			}
		}
		
		return theType;
	}
	
	public int getCode() {
		return code;
	}
}
