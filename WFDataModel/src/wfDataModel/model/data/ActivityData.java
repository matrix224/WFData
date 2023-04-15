package wfDataModel.model.data;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import wfDataModel.service.codes.JSONField;

/**
 * Data model for storing activity data on a server
 * @author MatNova
 *
 */
public class ActivityData {

	@Expose()
	@SerializedName(JSONField.PLAYERS)
	private int playerCount;
	@Expose()
	@SerializedName(JSONField.TIMESTAMP)
	private long timestamp;
	
	public ActivityData(long timestamp) {
		this.timestamp = timestamp;
	}

	public long getTimestamp() {
		return timestamp;
	}
	
	public void addToPlayerCount(int num) {
		playerCount += num;
	}
	
	public int getPlayerCount() {
		return playerCount;
	}
}
