package wfDataModel.service.type;

/**
 * The direction that a ban is going
 * @author MatNova
 *
 */
public enum BanDirectionType {
	IN("in"),
	OUT("out");
	
	private String code;
	
	private BanDirectionType(String code) {
		this.code = code;
	}
	
	public String getCode() {
		return code;
	}
}
