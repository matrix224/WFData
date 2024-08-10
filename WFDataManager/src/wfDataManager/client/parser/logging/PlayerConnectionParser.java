package wfDataManager.client.parser.logging;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataManager.client.cache.BanManagerCache;
import wfDataManager.client.cache.PlayerTrackerCache;
import wfDataManager.client.data.PlayerTracker;
import wfDataManager.client.db.PlayerTrackerDao;
import wfDataManager.client.type.ParseResultType;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.util.PlayerUtil;
import wfDataModel.service.type.BanActionType;

/**
 * Parser for detecting and mapping player connections to actual players. <br>
 * There are cases where the expected log messages for a known player-to-IP mapping do not show up,
 * presumably due to a bug on DE's side. This is a very rare instance and is not something that seems to occur
 * on a regular basis. However, this will still attempt to make a best-guess attempt at mapping players to
 * their respective IP in the event that it cannot do it definitively. <br>
 * This will also attempt to assign an accountId (parsed from {@link IntroductionRequestParser} to a player here.
 * @author MatNova
 *
 */
public class PlayerConnectionParser extends BaseLogParser {

	private Matcher VITUAL_CONNECTON_MATCHER;
	private Matcher REUSE_CONNECTON_MATCHER;
	private Matcher CREATE_PLAYER_PATTERN;
	private Matcher SET_TIMEOUT_PATTERN;
	private Matcher CONTACT_RECEIVED_PATTERN;
	private Matcher SEND_LOADOUT_PATTERN;

	@Override
	protected List<Matcher> initMatchers() {
		VITUAL_CONNECTON_MATCHER = Pattern.compile(".*Created virtual connection for: (\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+).*handle: (\\d+).*").matcher("");
		REUSE_CONNECTON_MATCHER = Pattern.compile(".*ReuseConnection: (\\d+): (\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+)$").matcher("");
		CREATE_PLAYER_PATTERN = Pattern.compile(".*CreatePlayerForClient.* id=(\\d+), user name=(.*)$").matcher("");
		SEND_LOADOUT_PATTERN = Pattern.compile(".*LotusHumanPlayer::SendLoadOut: (.*) loadout received$").matcher("");
		SET_TIMEOUT_PATTERN = Pattern.compile(".*NetDriver::SetConnectionTimeout setting connection (\\d+) timeout.*").matcher("");
		CONTACT_RECEIVED_PATTERN = Pattern.compile(".*Contact received; sending challenge to (.*)\\. Setting.*").matcher("");
		return Arrays.asList(VITUAL_CONNECTON_MATCHER, REUSE_CONNECTON_MATCHER, CREATE_PLAYER_PATTERN, SET_TIMEOUT_PATTERN, CONTACT_RECEIVED_PATTERN, SEND_LOADOUT_PATTERN);
	}

	@Override
	public ParseResultType parse(ServerData serverData, long offset, long lastLogTime) {
		if (CREATE_PLAYER_PATTERN.matches()) {
			int connId = Integer.parseInt(CREATE_PLAYER_PATTERN.group(1));
			String playerName = PlayerUtil.cleanPlayerName(CREATE_PLAYER_PATTERN.group(2));
			int platform = PlayerUtil.getPlatform(CREATE_PLAYER_PATTERN.group(2));
			mapPlayerIP(serverData, connId, playerName, platform);
		} else if (SEND_LOADOUT_PATTERN.matches()) {
			String playerName = PlayerUtil.cleanPlayerName(SEND_LOADOUT_PATTERN.group(1));
			int platform = PlayerUtil.getPlatform(SEND_LOADOUT_PATTERN.group(1));
			PlayerData player = serverData.getPlayerByNameAndPlatform(playerName, platform);
			if (player != null && (MiscUtil.isEmpty(player.getIPAndPort()) || player.getConnHandle() < 0)) {
				Integer connId = serverData.getGuessConnMapping(playerName);
				if (connId != null) {
					mapPlayerIP(serverData, connId, playerName, platform);
				} else {
					Log.warn(LOG_ID + ".parse() : No best-guess conn handle mapping found for player " + playerName);
				}
			}
		} else if (REUSE_CONNECTON_MATCHER.matches() || VITUAL_CONNECTON_MATCHER.matches()) {
			boolean isReuse = REUSE_CONNECTON_MATCHER.matches();
			String ipPort = isReuse ? REUSE_CONNECTON_MATCHER.group(2) : VITUAL_CONNECTON_MATCHER.group(1);
			int connId = Integer.valueOf(isReuse ? REUSE_CONNECTON_MATCHER.group(1) : VITUAL_CONNECTON_MATCHER.group(2));
			String acctId = serverData.getAccountIDMapping(ipPort);
			
			if (!MiscUtil.isEmpty(acctId)) {
				Log.info("MAPPING " + connId + " TO " + acctId + " SID=" + serverData.getNumericId() + ", IP=" + ipPort + ", REUSE=" + isReuse);
				serverData.setAcctIdForConn(connId, acctId, ipPort);
			} else {
				Log.warn(LOG_ID + ".parse() : No associated acctId found for " + ipPort);
			}
			
			String ip = ipPort.substring(0, ipPort.indexOf(":"));
			// This isn't a proxy, so just count as a constant port of 0
			if (!serverData.isProxyIP(ip)) {
				ipPort = ip + ":0";
			}

			serverData.addConnHandle(connId, ipPort);
		} else if (SET_TIMEOUT_PATTERN.matches()) {
			int connId = Integer.parseInt(SET_TIMEOUT_PATTERN.group(1));
			serverData.setLastConnHandle(connId);
		} else if (CONTACT_RECEIVED_PATTERN.matches()) {
			String playerName = PlayerUtil.cleanPlayerName(CONTACT_RECEIVED_PATTERN.group(1));
			if (serverData.getLastConnHandle() != -1) {
				serverData.addGuessConnMapping(serverData.getLastConnHandle(), playerName);
				serverData.setLastConnHandle(-1);
			} else {
				Log.warn(LOG_ID + ".parse() : No last parsed conn handle found, cannot map for player " + playerName);
			}
		}

		return ParseResultType.OK;
	}

	private void mapPlayerIP(ServerData serverData, int connId, String playerName, int platform) {
		String ipPort = serverData.getIPAndPort(connId);
		if (!MiscUtil.isEmpty(ipPort)) {
			PlayerData player = serverData.getPlayerByNameAndPlatform(playerName, platform);
			if (player != null && (!ipPort.equals(player.getIPAndPort()) || player.getConnHandle() != connId)) {
				player.setIPAndPort(ipPort);
				player.setConnHandle(connId);
				if (!MiscUtil.isEmpty(serverData.getAcctIdForConn(connId))) {
					player.setAccountID(serverData.getAcctIdForConn(connId));
					Log.info("SETTING PLAYER ACCTID " + playerName + "(" + player.getUID() + ") TO " + player.getAccountID());
					serverData.markAccountIDFullyMapped(connId);
				}
				
				// If this player is marked as being currently banned, then submit another ban request for them now
				if (ClientSettingsUtil.enableBanning() && BanManagerCache.singleton().isCurrentlyBanned(player.getUID())) {
					BanManagerCache.singleton().manageBan(BanManagerCache.singleton().getBanData(player.getUID()), BanActionType.ADD, player.getIPAndPort(), BanManagerCache.singleton().isPermaBanned(player.getUID()) ? "Permaban" : "Ban evasion");
				}

				String ip = ipPort.split(":")[0];
				// If this isn't a proxyIP and we just mapped it to a player, check if this player has any relation to any tracked players
				if (!serverData.isProxyIP(ip)) {
					List<PlayerTracker> trackers = PlayerTrackerCache.singleton().getPlayerTrackers(player.getUID(), ip);
					if (!MiscUtil.isEmpty(trackers)) {
						for (PlayerTracker tracker : trackers) {
							if ((tracker.getUID().equals(player.getUID()) || tracker.getKnownIPs().contains(ip) || tracker.getKnownAlts().containsKey(player.getUID())) && tracker.updateTracker(player.getUID(), player.getPlayerName(), ip)) {
								PlayerTrackerDao.updatePlayerTracker(tracker);
								Log.info(LOG_ID + ".mapPlayerIP() : Found new match for player tracker: trackerUID=" + tracker.getUID() + ", playerUID=" + player.getUID() + ", playerName=" + playerName + ", playerIP=" + ip);
							}
						}
					}
				}
			} else if (player == null) {
				Log.warn(LOG_ID + ".mapPlayerIP() : Could not find player for connHandle " + connId + ", player=" + playerName);
			}
			serverData.removeGuessConnMapping(playerName);
		} else {
			Log.warn(LOG_ID + ".mapPlayerIP() : Could not find IP mapping for connHandle " + connId);
		}
	}
}
