package wfDataManager.client.parser.logging;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataManager.client.type.ParseResultType;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.util.PlayerUtil;

/**
 * Parser for detecting that a player has left the game session
 * @author MatNova
 *
 */
public class PlayerLeaveParser extends BaseLogParser {
	
	private Matcher PLAYER_LEAVE_PATTERN;
	private Matcher PLAYER_LEAVE_PATTERN_B;
	private Matcher REMOVE_CONN_PATTERN;

	@Override
	protected List<Matcher> initMatchers() {
		PLAYER_LEAVE_PATTERN = Pattern.compile(".*Client \"(.*)\" disconnected with HConn=(\\d+)$").matcher("");
		PLAYER_LEAVE_PATTERN_B = Pattern.compile(".*RemoveSquadMember: (.*) has been removed.*").matcher("");
		REMOVE_CONN_PATTERN = Pattern.compile(".*Server::RemoveConnection (\\d+)$").matcher("");

		return Arrays.asList(PLAYER_LEAVE_PATTERN, PLAYER_LEAVE_PATTERN_B, REMOVE_CONN_PATTERN);
	}

	@Override
	public ParseResultType parse(ServerData serverData, long offset, long lastLogTime) {
		if (PLAYER_LEAVE_PATTERN.matches()) {
			String name = PlayerUtil.cleanPlayerName(PLAYER_LEAVE_PATTERN.group(1));
			int platform = PlayerUtil.getPlatform(PLAYER_LEAVE_PATTERN.group(1));
			int connHandle = Integer.valueOf(PLAYER_LEAVE_PATTERN.group(2));
			PlayerData player = serverData.getPlayerByNameAndPlatform(name, platform);
			if (player != null) {
				player.setLeftServer(true, lastLogTime);
				// It's possible that a player joined but timed out at some point early on during loading
				// If this happens, they may not ever get assigned the connHandle and IP,
				// so we assign it here when they disconnect to try and handle any cleanup properly
				if (player.getConnHandle() == -1 && !MiscUtil.isEmpty(serverData.getIPAndPort(connHandle))) {
					player.setConnHandle(connHandle);
					player.setIPAndPort(serverData.getIPAndPort(connHandle));
					Log.warn(LOG_ID + ".parse() : Player did not have connHandle assigned on leave, will assign now -> player=" + player.getPlayerName() + ", hndl=" + player.getConnHandle() + ", ip=" + player.getIPAndPort());
					if (!MiscUtil.isEmpty(serverData.getAcctIdForConn(connHandle))) {
						player.setAccountID(serverData.getAcctIdForConn(connHandle));
						Log.info("SETTING PLAYER ACCTID " + player.getPlayerName() + "(" + player.getUID() + ") TO " + player.getAccountID() + " ON LEAVE");
						serverData.removeAccountIDCandidate(player.getAccountID());
					}
				}
				if (!MiscUtil.isEmpty(player.getAccountID())) {
					serverData.removeAccountIDMapping(player.getAccountID());
				}
			} else {
				Log.warn(LOG_ID + ".parse() : Could not find player for leaveMatcher parse -> " + name);
			}
			serverData.removeAcctIdForConn(connHandle);
		} else if (PLAYER_LEAVE_PATTERN_B.matches()) {
			String name = PlayerUtil.cleanPlayerName(PLAYER_LEAVE_PATTERN_B.group(1));
			int platform = PlayerUtil.getPlatform(PLAYER_LEAVE_PATTERN_B.group(1));
			PlayerData player = serverData.getPlayerByNameAndPlatform(name, platform);
			if (player != null) {
				player.setLeftServer(true, lastLogTime);
				if (!MiscUtil.isEmpty(player.getAccountID())) {
					serverData.removeAccountIDMapping(player.getAccountID());
				}
			} else {
				Log.warn(LOG_ID + ".processLogs() : Could not find player for leaveMatcherB parse -> " + name);
			}
		} else {
			// Do this here to make sure we don't leave any lingering connHandle : acctID mappings
			// This can happen if a player times out and we never assign them their connHandle and therefore no acctId,
			// so this is done as a safety cleanup 
			int connHandle = Integer.valueOf(REMOVE_CONN_PATTERN.group(1));
			serverData.removeAcctIdForConn(connHandle);
		}
		
		return ParseResultType.OK;
	}
}
