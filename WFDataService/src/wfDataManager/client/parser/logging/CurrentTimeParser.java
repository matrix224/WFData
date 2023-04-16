package wfDataManager.client.parser.logging;

import java.io.File;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdtools.util.MiscUtil;
import wfDataManager.client.type.ParseResultType;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.logging.Log;

/**
 * Parser for checking the current starting log time <br>
 * If the parsed log time is different than the last parsed one for the given server, this will indicate that a new log 
 * file has started for the given server. This will then reset the server and let it start over from the beginning. <br>
 * Otherwise if the parsed log time is the same as the currently parsed one for the given server, this will indicate
 * that the log can skip ahead to the last parsed log time and continue where it left off.
 * @author MatNova
 *
 */
public class CurrentTimeParser extends BaseLogParser {

	private Matcher CURRENT_TIME_PATTERN;

	@Override
	protected List<Matcher> initMatchers() {
		CURRENT_TIME_PATTERN = Pattern.compile(".*Current time: (.*) \\[.*").matcher("");
		return Arrays.asList(CURRENT_TIME_PATTERN);
	}

	@Override
	public ParseResultType parse(ServerData serverData, long offset, int lastLogTime) throws ParseException {
		String curTime = CURRENT_TIME_PATTERN.group(1);

		if (!curTime.equals(serverData.getTimeStats().getStartTime())) {
			Log.info(LOG_ID + ".parse() : Current timestamp (" + serverData.getTimeStats().getStartTime() + ") for logId " + serverData.getId() + " doesn't equal parsed one (" + curTime + "), will continue reading from beginning");
			serverData.startNewParse(true);
			serverData.getTimeStats().setStartTime(curTime);
			if (ClientSettingsUtil.cleanServerProfiles() && !MiscUtil.isEmpty(serverData.getCurrentProfileDir())) {
				File profileDir = new File(serverData.getCurrentProfileDir());
				if (profileDir.exists() && profileDir.isDirectory()) {
					File[] files = profileDir.listFiles();
					if (files != null && files.length > 0) {
						Log.debug(LOG_ID + ".parse() : Profile dir is not empty for server " + serverData.getId() + ". Will not delete -> " + profileDir.getAbsolutePath());
					} else {
						profileDir.delete();
					}
				}
			}
		} else if (serverData.getLogPosition() > 0) {
			// Note this removes D/C'd players. If an error occurs while parsing, the D/C'd players will not be added back into ServerInfo
			serverData.startNewParse(false);
			return ParseResultType.SKIP; // Return SKIP so file reader will skip ahead to last read portion
		}
		return ParseResultType.OK;
	}

}
