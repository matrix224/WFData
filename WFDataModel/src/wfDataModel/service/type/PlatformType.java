package wfDataModel.service.type;

/**
 * The platform types that clients may be using
 * @author MatNova
 *
 */
public enum PlatformType {
	PC(494),
	XBOX(495),
	PSN(496),
	NSW(497),
	IOS(498),
	ANDROID(499),  // TODO: Assuming this
	UNKNOWN(0);
	
	private int code;
	
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
