package wfDataModel.service.type;

/**
 * The types of requests that the service will handle
 * @author MatNova
 *
 */
public enum RequestType {
	REGISTER("/registration/"),
	ADD_BAN("/addBan/"),
	GET_BANS("/getBans/"),
	ADD_DATA("/addData/");
	
	private String endPoint;
	
	private RequestType(String endPoint) {
		this.endPoint = endPoint;
	}
	
	public String getEndPoint() {
		return endPoint;
	}
}
