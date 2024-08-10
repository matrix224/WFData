package wfDataModel.model.data;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

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
	@Expose (serialize = false, deserialize = false) 
	private String serverStartTimeUTC;  // Readable UTC time of when this log started
	@Expose () 
	private long logTime; // The current log-time
	@Expose () 
	private long serverStartTimeEpoch; // Epoch time of when this log started
	@Expose (serialize = false, deserialize = false)
	private ZoneOffset zoneOffset; // The offset of this server vs UTC (e.g. -04:00)
	@Expose()
	private String zoneId;
	@Expose (serialize = false, deserialize = false) 
	private long rolloverTime; // Log-time that is when a new day will have started
	@Expose (serialize = false, deserialize = false) 
	private long activityTime; // Log-time that denotes the next interval of 5 (in real world time) to send server activity data
	@Expose (serialize = false, deserialize = false) 
	private OffsetDateTime dailyDate;
	@Expose (serialize = false, deserialize = false) 
	private OffsetDateTime weeklyDate;
	@Expose (serialize = false, deserialize = false) 
	private long startLogTime; // Log-time at the start of this parsing session. Used in case of rolling back
	@Expose (serialize = false, deserialize = false) 
	private long startActivityTime; // Log-time for next interval of 5 at the start of this parsing session. Used in case of rolling back
	@Expose (serialize = false, deserialize = false) 
	private long startRolloverTime; // Log-time that is when a new day will have started at the start of this parsing session. Used in case of rolling back
	@Expose()
	private long matchStartTime; // Epoch time of when the current match started. Set to 0 if no match is happening
	@Expose (serialize = false, deserialize = false) 
	private long startingMatchStartTime; // Epoch time at the start of this parsing session of when the current match started. Used in case of rolling back
	
	public ServerTimeStats() {
	}

	public ServerTimeStats(JsonObject dataObj) throws ParseException {
		buildFromDBData(dataObj);
	}

	public void setStartTime(String serverStartTime, String serverStartTimeUTC) throws ParseException {
		this.serverStartTime = serverStartTime;
		this.serverStartTimeUTC = serverStartTimeUTC;
		if (serverStartTimeEpoch <= 0 && !MiscUtil.isEmpty(serverStartTime)) {
			LocalDateTime dateTime = DateUtil.parseServerTime(serverStartTime);
			if (!MiscUtil.isEmpty(serverStartTimeUTC)) {
				LocalDateTime dateTimeUTC = DateUtil.parseServerTime(serverStartTimeUTC);
				int hrDiff = (int)ChronoUnit.HOURS.between(dateTimeUTC, dateTime);
				int minDiff = (int)ChronoUnit.MINUTES.between(dateTimeUTC, dateTime) % 60;
				int secDiff = (int)ChronoUnit.SECONDS.between(dateTimeUTC, dateTime) % 60;
				zoneOffset = ZoneOffset.ofHoursMinutesSeconds(hrDiff, minDiff, secDiff);
			} else {
				zoneOffset = ZoneId.systemDefault().getRules().getOffset(dateTime); // If no UTC time provided, use offset for current timezone at server start time
			}
			zoneId = zoneOffset.getId();
			serverStartTimeEpoch = dateTime.toInstant(zoneOffset).toEpochMilli();
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

	public String getStartTimeUTC() {
		return serverStartTimeUTC;
	}

	public long getStartTimeEpoch() {
		return serverStartTimeEpoch;
	}

	public String getZoneId() {
		return zoneId;
	}
	
	public long getLogTime() {
		return logTime;
	}

	public void setLogTime(long logTime) {
		this.logTime = logTime;
	}

	public long getRolloverTime() {
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
		Instant inst = Instant.ofEpochMilli(epochTime);
		rolloverTime = (DateUtil.ofInstant(inst, zoneOffset).atStartOfDay().plusDays(1).toInstant(zoneOffset).toEpochMilli() - serverStartTimeEpoch) / 1000;
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
		dailyDate = DateUtil.getDateAsOffset(epochTime, zoneOffset);
		weeklyDate = DateUtil.getLatestSundayAsOffset(epochTime, zoneOffset);
	}

	public void setRolloverTime(long rolloverTime) {
		this.rolloverTime = rolloverTime;
	}

	public long getActivityTime() {
		return activityTime;
	}

	public void setActivityTime(long activityTime) {
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
			serverStartTimeUTC = null;
			zoneOffset = null;
			serverStartTimeEpoch = 0;
			activityTime = 0;
			rolloverTime = 0;
			matchStartTime = 0;
		} else {
			// If we're starting another round of parsing and we've moved past our rollover time, mark the next one and set up our dates
			if (logTime >= rolloverTime) {
				markNextRolloverTime();
			}
		}
		startLogTime = logTime;
		startActivityTime = activityTime;
		startRolloverTime = rolloverTime;
		startingMatchStartTime = matchStartTime;
	}

	public void setMatchStartTime(long logTime) {
		this.matchStartTime = logTime == 0 ? 0 : serverStartTimeEpoch + (logTime * 1000);
	}
	
	public long getMatchStartTime() {
		return matchStartTime;
	}
	
	public void reset(boolean isError) {
		if (isError) {
			logTime = startLogTime;
			matchStartTime = startingMatchStartTime;
		}
	}

	private void buildFromDBData(JsonObject dataObj) throws ParseException {
		// Note we set this here before we set the server start time
		if (dataObj.has(JSONField.ACTIVITY)) {
			setActivityTime(dataObj.get(JSONField.ACTIVITY).getAsLong());
		}
		if (dataObj.has(JSONField.ROLLOVER)) {
			setRolloverTime(dataObj.get(JSONField.ROLLOVER).getAsLong());
		}
		if (dataObj.has(JSONField.POSITION)) {
			setLogTime(dataObj.get(JSONField.POSITION).getAsLong());
		}
		if (dataObj.has(JSONField.TIMESTAMP) && !dataObj.get(JSONField.TIMESTAMP).isJsonNull()) {
			setStartTime(dataObj.get(JSONField.TIMESTAMP).getAsString(), dataObj.has(JSONField.UTC) && !dataObj.get(JSONField.UTC).isJsonNull() ? dataObj.get(JSONField.UTC).getAsString() : null);
		}
		if (dataObj.has(JSONField.START)) {
			matchStartTime = dataObj.get(JSONField.START).getAsLong();
		}
		setDates(false);
	}

	public JsonObject getTimeStatDB() {
		JsonObject dataObj = new JsonObject();
		dataObj.addProperty(JSONField.ACTIVITY, activityTime);
		dataObj.addProperty(JSONField.ROLLOVER, rolloverTime);
		dataObj.addProperty(JSONField.POSITION, logTime);
		dataObj.addProperty(JSONField.TIMESTAMP, serverStartTime);
		dataObj.addProperty(JSONField.UTC, serverStartTimeUTC);
		dataObj.addProperty(JSONField.START, matchStartTime);

		return dataObj;
	}
}
