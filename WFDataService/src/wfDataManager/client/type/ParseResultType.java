package wfDataManager.client.type;

/**
 * The type of result we got from parsing a part of the log
 * @author MatNova
 *
 */
public enum ParseResultType {
	OK, // Continue reading
	SKIP, // Skip to last logged position
	STOP, // Stop parsing for this session
	FINISH_LOG, // Stop parsing for this log in general
	START_MISSION, // Started parsing mission result
	END_MISSION; // Finished parsing mission result
}
