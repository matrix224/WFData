package wfDataManager.client.parser.logging;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import wfDataManager.client.type.ParseResultType;
import wfDataModel.model.data.ServerData;

/**
 * Parser for getting and setting the current profile directory for the server.
 * @author MatNova
 *
 */
public class CurrentProfileParser extends BaseLogParser {

	private Matcher CURRENT_PROFILE_PATTERN;

	@Override
	protected List<Matcher> initMatchers() {
		CURRENT_PROFILE_PATTERN = Pattern.compile(".*Using profile dir (.*)$").matcher("");
		return Arrays.asList(CURRENT_PROFILE_PATTERN);
	}

	@Override
	public ParseResultType parse(ServerData serverData, long offset, int lastLogTime) throws ParseException {
		String curDir = CURRENT_PROFILE_PATTERN.group(1);
		serverData.setCurrentProfileDir(curDir);
		return ParseResultType.OK;
	}
}
