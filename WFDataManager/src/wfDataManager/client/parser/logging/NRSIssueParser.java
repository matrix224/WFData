package wfDataManager.client.parser.logging;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import wfDataManager.client.type.ParseResultType;
import wfDataModel.model.data.ServerData;

/**
 * Parser for detecting an NRS server issue has occurred
 * @author MatNova
 *
 */
public class NRSIssueParser extends BaseLogParser {

	private Matcher NRS_SERVER_ISSUE_PATTERN;
	private Matcher NRS_SERVER_SELECTED_PATTERN;

	@Override
	protected List<Matcher> initMatchers() {
		NRS_SERVER_ISSUE_PATTERN = Pattern.compile(".*Could not select an NRS server.*").matcher("");
		NRS_SERVER_SELECTED_PATTERN = Pattern.compile(".*NRS Server \\d+ selected as home.*").matcher("");
		return Arrays.asList(NRS_SERVER_ISSUE_PATTERN, NRS_SERVER_SELECTED_PATTERN);
	}

	@Override
	public ParseResultType parse(ServerData serverData, long offset, long lastLogTime) {
		if (NRS_SERVER_ISSUE_PATTERN.matches()) {
			serverData.setHasNRSIssue(true); // If this message was found, we know we have an NRS issue
		} else {
			serverData.setHasNRSIssue(false); // If this message was found, we know we found an NRS server, so no issue
		}
		return ParseResultType.OK;
	}

}
