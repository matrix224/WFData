package wfDataManager.client.parser.logging;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdtools.logging.Log;
import wfDataManager.client.type.ParseResultType;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.util.PlayerUtil;

/**
 * Parser class for detecting and tracking goals in Lunaro game mode <br>
 * Important to note that this value is also parsed from the mission end stats as well,
 * but the point of this parser is to be able to more accurately denote when players have data
 * to be stored in weekly table.
 * @author MatNova
 *
 */
public class LunaroGoalParser extends BaseLogParser {

	private Matcher LUNARO_GOAL_PATTERN;

	@Override
	protected List<Matcher> initMatchers() {
		LUNARO_GOAL_PATTERN = Pattern.compile(".*Player (.+) scored a goal$").matcher("");
		return Arrays.asList(LUNARO_GOAL_PATTERN);
	}

	@Override
	public ParseResultType parse(ServerData serverData, long offset, long lastLogTime) throws ParseException {
		String playerName = PlayerUtil.cleanPlayerName(LUNARO_GOAL_PATTERN.group(1));
		int platform = PlayerUtil.getPlatform(LUNARO_GOAL_PATTERN.group(1));
		PlayerData player = serverData.getPlayerByNameAndPlatform(playerName, platform);
		if (player != null) {
			// Note we store goals in the 'mechanics' field since this is where it comes from in the mission stats
			player.setMechanics(player.getMechanics() + 1);
			player.setLastLogTime(lastLogTime);
		} else {
			Log.warn(LOG_ID + ".parse() : Could not match ID to player for Lunaro goal score log, player=" + player);
		}
		return ParseResultType.OK;
	}

}
