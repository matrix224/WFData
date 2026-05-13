package wfDataModel.model.data;

import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import wfDataModel.service.codes.JSONField;
import wfDataModel.service.type.AllocatorType;

public class ServerAllocatorData {
	
	@Expose()
	@SerializedName(JSONField.PROCESS_ID)
	private Integer pid;
	@Expose()
	private AllocatorType type;
	
	public void setPID(Integer pid) {
		this.pid = pid;
	}
	
	public Integer getPID() {
		return pid;
	}
	
	public void setAllocatorType(AllocatorType type) {
		this.type = type;
	}
	
	public AllocatorType getAllocatorType() {
		return type;
	}
	
	public JsonObject getAllocatorDataDB() {
		JsonObject dataObj = new JsonObject();
		if (pid != null) {
			dataObj.addProperty(JSONField.PROCESS_ID, pid);
		}
		if (type != null) {
			dataObj.addProperty(JSONField.TYPE, type.name());
		}
		
		return dataObj;
	}
}
