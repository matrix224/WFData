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
 * Parser class for detecting and tracking cephalon captures in CTF game mode
 * @author MatNova
 *
 */
public class CephalonCaptureParser extends BaseLogParser {

	private Matcher CAPTURE_CEPHALON_PATTERN;

	@Override
	protected List<Matcher> initMatchers() {
		CAPTURE_CEPHALON_PATTERN = Pattern.compile(".*Player (.+) captured the Cephalon$").matcher("");
		return Arrays.asList(CAPTURE_CEPHALON_PATTERN);
	}

	@Override
	public ParseResultType parse(ServerData serverData, long offset, long lastLogTime) throws ParseException {
		String playerName = PlayerUtil.cleanPlayerName(CAPTURE_CEPHALON_PATTERN.group(1));
		int platform = PlayerUtil.getPlatform(CAPTURE_CEPHALON_PATTERN.group(1));
		PlayerData player = serverData.getPlayerByNameAndPlatform(playerName, platform);
		if (player != null) {
			player.setCaptures(player.getCaptures() + 1);
			player.setLastLogTime(lastLogTime);
		} else {
			Log.warn(LOG_ID + ".parse() : Could not match ID to player for Cephalon Capture log, player=" + player);
		}
		return ParseResultType.OK;
	}

}
