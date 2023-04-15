package wfDataModel.service.type;

/**
 * The protocol types for bans
 * @author MatNova
 *
 */
public enum BanProtocolType {
	ANY("any"), TCP("tcp"), UDP("udp");
	
	private String code;
	
	private BanProtocolType(String code) {
		this.code = code;
	}
	
	public String getCode() {
		return code;
	}
}
