package wfDataManager.client.parser.logging;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdtools.util.MiscUtil;
import wfDataManager.client.type.ParseResultType;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.logging.Log;
import wfDataModel.model.util.PlayerUtil;

/**
 * Parser for detecting that a player has left the game session
 * @author MatNova
 *
 */
public class PlayerLeaveParser extends BaseLogParser {
	
	private Matcher PLAYER_LEAVE_PATTERN;
	private Matcher PLAYER_LEAVE_PATTERN_B;
	
	@Override
	protected List<Matcher> initMatchers() {
		PLAYER_LEAVE_PATTERN = Pattern.compile(".*Client \"(.*)\" disconnected with HConn=(\\d+)$").matcher("");
		PLAYER_LEAVE_PATTERN_B = Pattern.compile(".*RemoveSquadMember: (.*) has been removed.*").matcher("");
		return Arrays.asList(PLAYER_LEAVE_PATTERN, PLAYER_LEAVE_PATTERN_B);
	}

	@Override
	public ParseResultType parse(ServerData serverData, long offset, int lastLogTime) {
		if (PLAYER_LEAVE_PATTERN.matches()) {
			String name = PlayerUtil.cleanPlayerName(PLAYER_LEAVE_PATTERN.group(1));
			int connHandle = Integer.valueOf(PLAYER_LEAVE_PATTERN.group(2));
			PlayerData player = serverData.getPlayerByName(name);
			if (player != null) {
				player.setLeftServer(true, lastLogTime);
				// It's possible that a player joined but timed out at some point early on during loading
				// If this happens, they may not ever get assigned the connHandle and IP,
				// so we assign it here when they disconnect to try and handle any cleanup properly
				if (player.getConnHandle() == -1 && !MiscUtil.isEmpty(serverData.getIPAndPort(connHandle))) {
					player.setConnHandle(connHandle);
					player.setIPAndPort(serverData.getIPAndPort(connHandle));
					Log.warn(LOG_ID + ".parse() : Player did not have connHandle assigned on leave, will assign now -> player=" + player.getPlayerName() + ", hndl=" + player.getConnHandle() + ", ip=" + player.getIPAndPort());
				}
			} else {
				Log.warn(LOG_ID + ".parse() : Could not find player for leaveMatcher parse -> " + name);
			}
		} else {
			String name = PlayerUtil.cleanPlayerName(PLAYER_LEAVE_PATTERN_B.group(1));
			PlayerData player = serverData.getPlayerByName(name);
			if (player != null) {
				player.setLeftServer(true, lastLogTime);
			} else {
				Log.warn(LOG_ID + ".processLogs() : Could not find player for leaveMatcherB parse -> " + name);
			}
		}
		
		return ParseResultType.OK;
	}
}
