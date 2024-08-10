package wfDataManager.client.parser.logging;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdtools.logging.Log;
import wfDataManager.client.db.ServerDao;
import wfDataManager.client.type.ParseResultType;
import wfDataManager.client.type.ProcessModeType;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.model.data.ServerData;

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
	public ParseResultType parse(ServerData serverData, long offset, long lastLogTime) throws ParseException {
		String curDir = CURRENT_DIRECTORY_PATTERN.group(1);
		serverData.setCurrentLaunchDir(curDir);
		// This is to properly track and check against logs that were already processed so that we don't process them again.
		// This can happen if, e.g., a log was processed in normal mode first then in historical mode at a later time
		// For Test mode, we don't care and ignore this
		if (!ProcessModeType.TEST.equals(ClientSettingsUtil.getProcessMode()) && ServerDao.hasLogBeenProcessed(serverData)) {
			Log.info(LOG_ID + ".parse() : Server with timestamp (" + serverData.getTimeStats().getStartTime() + ") for logId " + serverData.getId() + " has already been marked as processed. Will skip this log file.");
			return ParseResultType.FINISH_LOG;
		}
		return ParseResultType.OK;
	}
}
