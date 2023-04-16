package wfDataManager.client.parser.logging;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import wfDataManager.client.cache.WarframeItemCache;
import wfDataManager.client.type.ParseResultType;
import wfDataManager.client.type.ProcessModeType;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.logging.Log;

/**
 * Parser for getting and setting the current server build ID
 * 
 * @author MatNova
 */
public class BuildIDParser extends BaseLogParser {

	private Matcher BUILD_ID_PATTERN;
	
	@Override
	protected List<Matcher> initMatchers() {
		BUILD_ID_PATTERN = Pattern.compile(".*Build Label: (\\d{4}\\.\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{2}).*").matcher("");
		return Arrays.asList(BUILD_ID_PATTERN);
	}

	@Override
	public ParseResultType parse(ServerData serverData, long offset, int lastLogTime) throws ParseException {
		long buildId = Long.valueOf(BUILD_ID_PATTERN.group(1).replaceAll("\\.", "")); // We create our own build ID from the build label (comprised of date and time), since the actual build IDs in the log are not always sequential
		
		// Don't do this if historical. Not needed and will also repeatedly do it as time goes on and builds update
		if (ProcessModeType.NORMAL.equals(ClientSettingsUtil.getProcessMode()) && serverData.getBuildId() > -1 && buildId > serverData.getBuildId() && WarframeItemCache.singleton().updateCacheIfNeeded(buildId)) {
			Log.info(LOG_ID + ".parse() : Build update detected, attempted item cache refresh");
		}
		serverData.setBuildId(buildId);
		
		return ParseResultType.OK;
	}

}
