package wfDataModel.model.data;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;

import jdtools.util.MiscUtil;
import wfDataModel.model.util.DateUtil;
import wfDataModel.service.codes.JSONField;

/**
 * Data model for storing and supporting different timestamps that are relevant to server events
 * @author MatNova
 *
 */
public class ServerTimeStats {
	private static final int ACTIVITY_TIME_INTERVAL = 300; // Time in seconds that server activity should be gathered
	
	@Expose (serialize = false, deserialize = false) 
	private String serverStartTime; // Readable time of when this log started
	@Expose () 
	private int logTime; // The current log-time
	@Expose () 
	private long serverStartTimeEpoch; // Epoch time of when this log started
	@Expose (serialize = false, deserialize = false) 
	private int rolloverTime; // Log-time that is when a new day will have started
	@Expose (serialize = false, deserialize = false) 
	private int activityTime; // Log-time that denotes the next interval of 5 (in real world time) to send server activity data
	@Expose (serialize = false, deserialize = false) 
	private OffsetDateTime dailyDate;
	@Expose (serialize = false, deserialize = false) 
	private OffsetDateTime weeklyDate;
	@Expose (serialize = false, deserialize = false) 
	private int startLogTime; // Log-time at the start of this parsing session. Used in case of rolling back
	@Expose (serialize = false, deserialize = false) 
	private int startActivityTime; // Log-time for next interval of 5 at the start of this parsing session. Used in case of rolling back
	@Expose (serialize = false, deserialize = false) 
	private int startRolloverTime; // Log-time that is when a new day will have started at the start of this parsing session. Used in case of rolling back
	
	public ServerTimeStats() {
	}

	public ServerTimeStats(JsonObject dataObj) throws ParseException {
		buildFromDBData(dataObj);
	}

	public void setStartTime(String serverStartTime) throws ParseException {
		this.serverStartTime = serverStartTime;
		if (serverStartTimeEpoch <= 0 && !MiscUtil.isEmpty(serverStartTime)) {
			LocalDateTime dateTime = DateUtil.parseServerTime(serverStartTime);
			serverStartTimeEpoch = dateTime.toInstant(ZoneId.systemDefault().getRules().getOffset(dateTime)).toEpochMilli();
			if (activityTime <= 0) {
				int minOffset = dateTime.getMinute() % 5 > 0 ? 5 - (dateTime.getMinute() % 5) : 5; // How many minutes away from a 5 interval are we
				int secondOffset = dateTime.getSecond() % 60; // How many seconds into this minute are we
				activityTime = Math.abs((minOffset * 60) - secondOffset); // Number of minutes away from 5 in seconds minus the number of seconds into this next minute
			}
			if (rolloverTime <= 0) {
				rolloverTime = 0;
				markNextRolloverTime();
			}
		}
	}

	public String getStartTime() {
		return serverStartTime;
	}

	public long getStartTimeEpoch() {
		return serverStartTimeEpoch;
	}

	public int getLogTime() {
		return logTime;
	}

	public void setLogTime(int logTime) {
		this.logTime = logTime;
	}

	public int getRolloverTime() {
		return rolloverTime;
	}

	public void markNextRolloverTime() {
		long epochTime = serverStartTimeEpoch + (rolloverTime * 1000);
		if (rolloverTime > 0) {
			epochTime += 5000;
		}
		setDates(true); // Set up our dates before changing the rollover time
		// Add one day to the start of day of log start time plus our current rollover time, and subtract difference of midnight next day from log start time, and divide by 1000 to get seconds marker value
		// Add an extra 5 seconds onto our localdate calculation here to ensure we're getting the current rollover date as epochTime before adding 1 day
		rolloverTime = (int) ((LocalDate.ofInstant(Instant.ofEpochMilli(epochTime), ZoneId.systemDefault()).atStartOfDay().plusDays(1).toInstant(ZonedDateTime.now(ZoneId.systemDefault()).getOffset()).toEpochMilli() - serverStartTimeEpoch) / 1000);
	}
	
	private void setDates(boolean wantNext) {
		long epochTime = serverStartTimeEpoch + (rolloverTime * 1000);
		if (rolloverTime > 0) {
			if (wantNext) {
				epochTime += 5000;
			} else {
				epochTime -= 5000;
			}
		}
		dailyDate = DateUtil.getDateAsOffset(epochTime, ZoneId.systemDefault().getId());
		weeklyDate = DateUtil.getLatestSundayAsOffset(epochTime, ZoneId.systemDefault().getId());
	}
	
	public void setRolloverTime(int rolloverTime) {
		this.rolloverTime = rolloverTime;
	}

	public int getActivityTime() {
		return activityTime;
	}

	public void setActivityTime(int activityTime) {
		this.activityTime = activityTime;
	}

	public void markNextActivityTime() {
		activityTime += ACTIVITY_TIME_INTERVAL;
	}

	public long getServerActivityTime() {
		return serverStartTimeEpoch + (activityTime * 1000);
	}

	public long getServerTime() {
		return serverStartTimeEpoch + (logTime * 1000);
	}

	public OffsetDateTime getDailyDate() {
		return dailyDate;
	}
	
	public OffsetDateTime getWeeklyDate() {
		return weeklyDate;
	}
	
	public void startNewParse(boolean freshLog) {
		if (freshLog) {
			logTime = 0;
			serverStartTime = null;
			serverStartTimeEpoch = 0;
			activityTime = 0;
			rolloverTime = 0;
		} else {
			// If we're starting another round of parsing and we've moved past our rollover time, mark the next one and set up our dates
			if (logTime >= rolloverTime) {
				markNextRolloverTime();
			}
		}
		startLogTime = logTime;
		startActivityTime = activityTime;
		startRolloverTime = rolloverTime;
	}

	public void reset(boolean isError) {
		if (isError) {
			logTime = startLogTime;
		}
	}

	private void buildFromDBData(JsonObject dataObj) throws ParseException {
		// Note we set this here before we set the server start time
		if (dataObj.has(JSONField.ACTIVITY)) {
			setActivityTime(dataObj.get(JSONField.ACTIVITY).getAsInt());
		}
		if (dataObj.has(JSONField.ROLLOVER)) {
			setRolloverTime(dataObj.get(JSONField.ROLLOVER).getAsInt());
			setDates(false);
		}	
		if (dataObj.has(JSONField.POSITION)) {
			setLogTime(dataObj.get(JSONField.POSITION).getAsInt());
		}
		if (dataObj.has(JSONField.TIMESTAMP) && !dataObj.get(JSONField.TIMESTAMP).isJsonNull()) {
			setStartTime(dataObj.get(JSONField.TIMESTAMP).getAsString());
		}	
	}

	public JsonObject getTimeStatDB() {
		JsonObject dataObj = new JsonObject();
		dataObj.addProperty(JSONField.ACTIVITY, activityTime);
		dataObj.addProperty(JSONField.ROLLOVER, rolloverTime);
		dataObj.addProperty(JSONField.POSITION, logTime);
		dataObj.addProperty(JSONField.TIMESTAMP, serverStartTime);
		return dataObj;
	}
}
