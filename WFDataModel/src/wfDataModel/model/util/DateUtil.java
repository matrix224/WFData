package wfDataModel.model.util;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

/**
 * Util for handling various date-related tasks
 * @author MatNova
 *
 */
public final class DateUtil {
	
	// Pattern to support different day values (two digits and one digit with an extra space before it)
	private static final DateTimeFormatter SERVER_CURRENT_TIME_FORMAT = DateTimeFormatter.ofPattern("EEE MMM [ ]d HH:mm:ss yyyy");
	
	/**
	 * Given a timestamp and a zoneID, will return a ZonedDateTime that represents the latest Sunday as of the given time for the given zone.
	 * @param time
	 * @param zoneId
	 * @return
	 */
	public static ZonedDateTime getLatestSunday(long time, String zoneId) {
		ZoneId zid = ZoneId.of(zoneId);
		return LocalDate.ofInstant(Instant.ofEpochMilli(time), zid).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).atStartOfDay(zid);
	}
	
	/**
	 * Given a timestamp and a zoneID, will return an OffsetDateTime that represents the latest Sunday as of the given time for the given zone.
	 * @param time
	 * @param zoneId
	 * @return
	 */
	public static OffsetDateTime getLatestSundayAsOffset(long time, String zoneId) {
		return getLatestSunday(time, zoneId).toOffsetDateTime();
	}
	
	/**
	 * Given a timestamp and a zoneID, will return an ZonedDateTime that represents the given time for the given zone.
	 * @param time
	 * @param zoneId
	 * @return
	 */
	public static ZonedDateTime getDate(long time, String zoneId) {
		ZoneId zid = ZoneId.of(zoneId);
		return LocalDate.ofInstant(Instant.ofEpochMilli(time), zid).atStartOfDay(zid);
	}
	
	/**
	 * Given a timestamp and a zoneID, will return an OffsetDateTime that represents the given time for the given zone.
	 * @param time
	 * @param zoneId
	 * @return
	 */
	public static OffsetDateTime getDateAsOffset(long time, String zoneId) {
		return getDate(time, zoneId).toOffsetDateTime();
	}
	
	/**
	 * Given a server start time, will return a LocalDateTime that represents that date and time.
	 * @param serverTime
	 * @return
	 */
	public static LocalDateTime parseServerTime(String serverTime) {
		return LocalDateTime.parse(serverTime, SERVER_CURRENT_TIME_FORMAT);
	}
}
