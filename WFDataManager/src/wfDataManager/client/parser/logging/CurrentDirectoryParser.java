package wfDataManager.client.parser.logging;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import wfDataManager.client.db.ServerDao;
import wfDataManager.client.type.ParseResultType;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.logging.Log;

/**
 * Parser for getting and setting the current launch directory for the server. <br>
 * If the parsed log time, directory, and log ID has been marked as already processed, this will indicate the log processing should be ended and
 * the log file should be skipped.
 * @author MatNova
 *
 */
public class CurrentDirectoryParser extends BaseLogParser {

	private Matcher CURRENT_DIRECTORY_PATTERN;

	@Override
	protected List<Matcher> initMatchers() {
		CURRENT_DIRECTORY_PATTERN = Pattern.compile(".*Current directory: (.*)$").matcher("");
		return Arrays.asList(CURRENT_DIRECTORY_PATTERN);
	}

	@Override
	public ParseResultType parse(ServerData serverData, long offset, int lastLogTime) throws ParseException {
		String curDir = CURRENT_DIRECTORY_PATTERN.group(1);
		serverData.setCurrentLaunchDir(curDir);
		// This is to properly track and check against logs that were already processed so that we don't process them again.
		// This can happen if, e.g., a log was processed in normal mode first then in historical mode at a later time
		if (ServerDao.hasLogBeenProcessed(serverData)) {
			Log.info(LOG_ID + ".parse() : Server with timestamp (" + serverData.getTimeStats().getStartTime() + ") for logId " + serverData.getId() + " has already been marked as processed. Will skip this log file.");
			return ParseResultType.END_LOG;
		} else {
			ServerDao.addProcessedLog(serverData);
			return ParseResultType.OK;
		}
	}
}
