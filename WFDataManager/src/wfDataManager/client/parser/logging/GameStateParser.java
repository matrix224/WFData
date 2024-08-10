package wfDataManager.client.parser.logging;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import wfDataManager.client.type.ParseResultType;
import wfDataModel.model.data.ServerData;
import wfDataModel.service.type.LevelType;

/**
 * Parser for getting and setting values about the current game state, such as current level and match start time.
 * @author MatNova
 *
 */
public class GameStateParser extends BaseLogParser {

	private Matcher CURRENT_LEVEL_PATTERN;
	private Matcher MATCH_START_PATTERN;
	private Matcher MATCH_END_PATTERN;

	@Override
	protected List<Matcher> initMatchers() {
		CURRENT_LEVEL_PATTERN = Pattern.compile(".*RegionMgrImpl::SetLevel .*/(.*)\\.level$").matcher("");
		MATCH_START_PATTERN = Pattern.compile(".*LotusPvpGameRules - changing pvp state from .* to PVP_MATCH_STARTED$").matcher("");
		MATCH_END_PATTERN = Pattern.compile(".*LotusPvpGameRules - changing pvp state from .* to (?:PVP_MATCH_ENDED|PVP_SELECTING_TEAMS)$").matcher("");

		return Arrays.asList(CURRENT_LEVEL_PATTERN, MATCH_START_PATTERN, MATCH_END_PATTERN);
	}

	@Override
	public ParseResultType parse(ServerData serverData, long offset, long lastLogTime) throws ParseException {
		if (CURRENT_LEVEL_PATTERN.matches()) {
			serverData.setLevel(LevelType.internalToType(CURRENT_LEVEL_PATTERN.group(1)));
		} else if (MATCH_START_PATTERN.matches()) {
			serverData.getTimeStats().setMatchStartTime(lastLogTime);
		} else if (MATCH_END_PATTERN.matches()) {
			serverData.getTimeStats().setMatchStartTime(0);
		}
		
		return ParseResultType.OK;
	}

}
