package wfDataManager.client.parser.logging;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import wfDataManager.client.type.ParseResultType;
import wfDataModel.model.data.ServerData;

/**
 * Parser for getting and setting the game settings field
 * @author MatNova
 *
 */
public class GameSettingsParser extends BaseLogParser {

	private Matcher GAME_SETTINGS_PATTERN;
	
	@Override
	protected List<Matcher> initMatchers() {
		GAME_SETTINGS_PATTERN = Pattern.compile(".*Session - settings: (.*)$").matcher("");
		return Arrays.asList(GAME_SETTINGS_PATTERN);
	}
	
	@Override
	public ParseResultType parse(ServerData serverData, long offset, int lastLogTime) {
		serverData.setGameSettings(GAME_SETTINGS_PATTERN.group(1));
		return ParseResultType.OK;
	}
}
