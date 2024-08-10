package wfDataModel.model.util;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.TemporalAdjusters;
import java.time.zone.ZoneRules;
import java.util.Locale;
import java.util.Objects;

/**
 * Util for handling various date-related tasks
 * @author MatNova
 *
 */
public final class DateUtil {
	
	// Pattern to support different day values (two digits and one digit with an extra space before it)
	// Force the locale to English since log files always have English month names in them
	private static final DateTimeFormatter SERVER_CURRENT_TIME_FORMAT = new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("EEE MMM [ ]d HH:mm:ss yyyy").toFormatter(Locale.ENGLISH);
	private static final DateTimeFormatter WEEK_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final int SECONDS_PER_DAY = 86400;
	
	/**
	 * Given a date string in the format "yyyy-MM-dd", will return a LocalDate representation of it
	 * @param str
	 * @return
	 */
	public static LocalDate getWeekDate(String str) {
		return LocalDate.parse(str, WEEK_FORMAT);
	}
	
	/**
	 * Given a timestamp and a zoneID, will return a ZonedDateTime that represents the latest Sunday as of the given time for the given zone.
	 * @param time
	 * @param zid
	 * @return
	 */
	public static ZonedDateTime getLatestSunday(long time, ZoneId zid) {
		return ofInstant(Instant.ofEpochMilli(time), zid).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).atStartOfDay(zid);
	}
	
	/**
	 * Given a timestamp and a zoneID, will return an OffsetDateTime that represents the latest Sunday as of the given time for the given zone.
	 * @param time
	 * @param zid
	 * @return
	 */
	public static OffsetDateTime getLatestSundayAsOffset(long time, ZoneId zid) {
		return getLatestSunday(time, zid).toOffsetDateTime();
	}
	
	/**
	 * Given a timestamp and a zoneID, will return an ZonedDateTime that represents the given time for the given zone.
	 * @param time
	 * @param zid
	 * @return
	 */
	public static ZonedDateTime getDate(long time, ZoneId zid) {
		return ofInstant(Instant.ofEpochMilli(time), zid).atStartOfDay(zid);
	}
	
	/**
	 * Given a timestamp and a zoneID, will return an OffsetDateTime that represents the given time for the given zone.
	 * @param time
	 * @param zid
	 * @return
	 */
	public static OffsetDateTime getDateAsOffset(long time, ZoneId zid) {
		return getDate(time, zid).toOffsetDateTime();
	}
	
	/**
	 * Given a server start time, will return a LocalDateTime that represents that date and time.
	 * @param serverTime
	 * @return
	 */
	public static LocalDateTime parseServerTime(String serverTime) {
		return LocalDateTime.parse(serverTime, SERVER_CURRENT_TIME_FORMAT);
	}
	
	// Taken from newer Java and used here to allow for Java 8 compatibility
    public static LocalDate ofInstant(Instant instant, ZoneId zone) {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(zone, "zone");
        ZoneRules rules = zone.getRules();
        ZoneOffset offset = rules.getOffset(instant);
        long localSecond = instant.getEpochSecond() + offset.getTotalSeconds();
        long localEpochDay = Math.floorDiv(localSecond, SECONDS_PER_DAY);
        return LocalDate.ofEpochDay(localEpochDay);
    }
}
