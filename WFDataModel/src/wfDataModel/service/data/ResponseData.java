package wfDataModel.service.data;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import wfDataModel.service.codes.JSONField;

/**
 * Defines the data structure of a response that will be received from the service
 * @author MatNova
 *
 */
public class ResponseData {
	
	@Expose()
	@SerializedName(JSONField.RESPONSE)
	private String response;
	@Expose()
	@SerializedName(JSONField.RESPONSE_CODE)
	private int rc;
	@Expose()
	@SerializedName(JSONField.HTTP_CODE)
	private int httpCode;
	
	public ResponseData(String response, int rc, int httpCode) {
		this.response = response;
		this.rc = rc;
		this.httpCode = httpCode;
	}
	
	public String getResponse() {
		return response;
	}
	
	public int getRC() {
		return rc;
	}
	
	public int getHTTPCode() {
		return httpCode;
	}
}
