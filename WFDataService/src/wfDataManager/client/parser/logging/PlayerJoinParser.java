package wfDataManager.client.parser.logging;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import wfDataManager.client.type.ParseResultType;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.logging.Log;
import wfDataModel.model.util.PlayerUtil;
import wfDataModel.service.type.PlatformType;

/**
 * Parser for detecting players joining the session <br>
 * Note this also detects when players (re)spawn, and in that case, will update the player's last detected log time.
 * @author MatNova
 *
 */
public class PlayerJoinParser extends BaseLogParser {

	private Matcher ADD_SQUAD_MEMBER_PATTERN;
	private Matcher PLAYER_SPAWN_PATTERN; // Technically happens whenever a player (re)spawns

	@Override
	protected List<Matcher> initMatchers() {
		ADD_SQUAD_MEMBER_PATTERN = Pattern.compile(".*AddSquadMember: (.*), mm=(.+), squadCount=.*").matcher("");
		PLAYER_SPAWN_PATTERN = Pattern.compile(".*has new player: (.*)$").matcher("");
		return Arrays.asList(ADD_SQUAD_MEMBER_PATTERN, PLAYER_SPAWN_PATTERN);
	}

	@Override
	public ParseResultType parse(ServerData serverData, long offset, int lastLogTime) {
		if (ADD_SQUAD_MEMBER_PATTERN.matches()) {
			String playerName = PlayerUtil.cleanPlayerName(ADD_SQUAD_MEMBER_PATTERN.group(1));
			PlatformType platform = PlayerUtil.getPlatform(ADD_SQUAD_MEMBER_PATTERN.group(1));
			String uid = ADD_SQUAD_MEMBER_PATTERN.group(2);
			// For PSN platforms, we encode their UID in base64
			// This is because some of them have wild values with various characters and spaces that will cause issues
			if (PlatformType.PSN.equals(platform)) {
				uid = Base64.getEncoder().encodeToString(uid.replaceAll(" ", "").getBytes());
			}
			PlayerData p = serverData.getPlayer(uid);
			if (p == null) {
				p = new PlayerData();
				p.setUID(uid);
				p.setLogID(serverData.getId());
				p.setEloRating(serverData.getEloRating());
				p.setGameMode(serverData.getGameModeId());
				p.setPlatform(platform);
				p.setLastLogTime(lastLogTime);
				serverData.addPlayer(p);
			} else {
				p.setLeftServer(false, lastLogTime);
			}
			p.setPlayerName(playerName);
		} else {
			String name = PlayerUtil.cleanPlayerName(PLAYER_SPAWN_PATTERN.group(1));
			PlayerData player = serverData.getPlayerByName(name);
			if (player != null) {
				player.setLastLogTime(lastLogTime);
			} else {
				Log.warn(LOG_ID + ".parse() : Could not find player for joinMatcher parse -> " + name);
			}
		}
		return ParseResultType.OK;
	}
}
