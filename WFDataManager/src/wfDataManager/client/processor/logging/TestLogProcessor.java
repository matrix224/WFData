package wfDataManager.client.processor.logging;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataManager.client.util.ClientTaskUtil;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.ServerData;
import wfDataModel.service.type.GameDataType;

public class TestLogProcessor extends BaseLogProcessor {

	private final Matcher LOG_PATTERN = Pattern.compile(ClientSettingsUtil.getHistoricalLogPattern()).matcher("");
	private Map<String, Queue<String>> foundFiles = null; // key = log ID, value = queue of files to be parsed for that ID
	private String[] logDirectories = ClientSettingsUtil.getHistoricalLogsDirs();
	private Map<String, Map<String, PlayerData>> parsedPlayers = new HashMap<String, Map<String, PlayerData>>();
	private Map<String, Map<String, List<PlayerData>>> unmappedPlayers = new HashMap<String, Map<String, List<PlayerData>>>();
	private Set<String> totalUIDs = new HashSet<String>();

	@Override
	protected void findLogFiles() {
		if (foundFiles == null) {
			foundFiles = new HashMap<String, Queue<String>>();
			int numFound = 0;
			for (int i = 0; i < logDirectories.length; i++) {
				String logDir = logDirectories[i];
				if (MiscUtil.isEmpty(logDir)) {
					continue;
				}

				for (String f : new File(logDir).list()) {
					if (LOG_PATTERN.reset(f).matches()) {
						String logId = LOG_PATTERN.groupCount() == 0 ? null : LOG_PATTERN.group(1);
						String fullFile = logDir + File.separator + f;
						if (MiscUtil.isEmpty(logId)) {
							logId = DEFAULT_SERVER_LOG_ID;
						}
						if (logDirectories.length > 1) {
							logId = i + "-" + logId;
						}

						logId = "h" + logId; // Historical logIds prefixed with 'h'

						foundFiles.computeIfAbsent(logId, k -> new LinkedList<String>()).add(fullFile);

						if (!logFiles.containsKey(logId)) {
							logFiles.put(logId,  new File(fullFile));
							foundFiles.get(logId).poll();
						}


						numFound++;
					}
				}
			}
			int estimatedSeconds = (int) (((numFound * ClientSettingsUtil.HISTORICAL_POLLING_INTERVAL) / logFiles.size()) * 1.5);
			int estDays = estimatedSeconds / (3600 * 24);
			int estHours = (estimatedSeconds / 3600) % 24;
			int estMinutes = (estimatedSeconds % 3600) / 60;
			int estSeconds = (estimatedSeconds % 60);

			Log.info(LOG_ID + ".findLogFiles() : Found " + numFound + " historical files to process. Estimated run time: " + estDays + " day(s), " + estHours + " hour(s), " + estMinutes + " min(s), " + estSeconds + " sec(s)");
		} else if (foundFiles.isEmpty()) {
			Log.warn(LOG_ID + ".findLogFiles() : All log files have been parsed. Process can be shut down");

			for (String uid : unmappedPlayers.keySet()) {
				StringBuilder sb = new StringBuilder(uid).append("\n");
				for (String serverTime : unmappedPlayers.get(uid).keySet()) {
					sb.append("\t").append(serverTime).append("\n");
					for (PlayerData player : unmappedPlayers.get(uid).get(serverTime)) {
						sb.append("\t\t").append(player.getPlayerName()).append(", ").append(player.getIPAndPort()).append(", ").append(player.getLastLogTime()).append("\n");
					}
				}
				Log.info(sb.toString());
			}



			Set<String> dupes = new HashSet<String>();
			Set<String> platformSwitch = new HashSet<String>();
			Set<String> renames = new HashSet<String>();
			int numAssigned = 0;
			int numSwitch = 0;
			int numRename = 0;
			int distinctDupe = 0;
			int totalDupe = 0;
			for (String acctId : parsedPlayers.keySet()) {
				if (parsedPlayers.get(acctId).size() > 1) {
					String name = null;
					String logId = null;
					String fuid = null;
					boolean hasRename = false;
					
					for (String uid : parsedPlayers.get(acctId).keySet()) {
						PlayerData player = parsedPlayers.get(acctId).get(uid);
						if (name == null) {
							name = player.getPlayerName();
							logId = player.getLogID();
							fuid = player.getUID();
						} else if (!player.getPlayerName().equals(name)) {
							if (!player.getLogID().equals(logId)) {
								hasRename = true;
							} else {
								name = null;
								break;
							}
						}
					}
					if (name == null) {
						dupes.add(acctId);
					} else {

						for (String uid : parsedPlayers.get(acctId).keySet()) {
							PlayerData player = parsedPlayers.get(acctId).get(uid);
							if (player.getPlayerName().equals(name)) {
								if (player.getUID().equals(fuid)) {
									if (hasRename) {
										renames.add(acctId);
										numRename++;
									} else {
										platformSwitch.add(acctId);
										numSwitch++;
									}
								} else {
									platformSwitch.add(acctId);
									numSwitch++;
								}
							} else {
								renames.add(acctId);
								numRename++;
							}

							Log.info("UPDATE PLAYER_PROFILE SET AID='" + acctId + "' WHERE UID='" + player.getUID() + "';");
						}
					}
				} else {
					Log.info("UPDATE PLAYER_PROFILE SET AID='" + acctId + "' WHERE UID='" + parsedPlayers.get(acctId).entrySet().stream().findFirst().get().getKey() + "';");
					numAssigned++;
				}
			}

			for (String acctId : renames) {
				StringBuilder sb = new StringBuilder();
				sb.append("RENAME ").append(acctId).append("\n");
				for (String uid : parsedPlayers.get(acctId).keySet()) {
					PlayerData player = parsedPlayers.get(acctId).get(uid);
					sb.append("\tUID ").append(uid).append(", NAME ").append(player.getPlayerName()).append(", PLATFORM ").append(player.getPlatform()).append(", IP ").append(player.getIPAndPort()).append("\n");
				}
				Log.info(sb.toString());
			}

			for (String acctId : platformSwitch) {
				StringBuilder sb = new StringBuilder();
				sb.append("PLATFORM SWITCH ").append(acctId).append("\n");
				for (String uid : parsedPlayers.get(acctId).keySet()) {
					PlayerData player = parsedPlayers.get(acctId).get(uid);
					sb.append("\tUID ").append(uid).append(", NAME ").append(player.getPlayerName()).append(", PLATFORM ").append(player.getPlatform()).append(", IP ").append(player.getIPAndPort()).append("\n");
				}
				Log.info(sb.toString());
			}


			for (String acctId : dupes) {
				StringBuilder sb = new StringBuilder();
				sb.append("DUPE ").append(acctId).append("\n");
				for (String uid : parsedPlayers.get(acctId).keySet()) {
					PlayerData player = parsedPlayers.get(acctId).get(uid);
					sb.append("\tUID ").append(uid).append(", NAME ").append(player.getPlayerName()).append(", PLATFORM ").append(player.getPlatform()).append(", IP ").append(player.getIPAndPort()).append("\n");
					totalDupe++;
				}
				Log.info(sb.toString());
				distinctDupe++;
			}

			Log.info("TOTAL ASSIGNED: " + numAssigned + ", TOTAL SWITCH: " + numSwitch + ", TOTAL RENAME: " + numRename + ", DISTINCT DUPE: " + distinctDupe + ", DUPE TOTAL: " + totalDupe + ", TOTAL UIDS: " + totalUIDs.size());

			ClientTaskUtil.stopTask(ClientTaskUtil.TASK_LOG_PROCESSOR);
		}
	}

	@Override
	protected GameDataType getDataType() {
		return GameDataType.HISTORICAL_GAME_DATA;
	}

	@Override
	protected void postProcessing(ServerData serverData) {
		for (PlayerData player : serverData.getParsedPlayers()) {
			String accountId = player.getAccountID();
			String uid = player.getUID();
			player.setLogID(serverData.getTimeStats().getStartTimeUTC());
			totalUIDs.add(uid);
			if (!MiscUtil.isEmpty(accountId) && !MiscUtil.isEmpty(uid)) {
				parsedPlayers.computeIfAbsent(accountId, k -> new HashMap<String, PlayerData>()).computeIfAbsent(uid, k -> player);
			} else if (!MiscUtil.isEmpty(uid)) {
				unmappedPlayers.computeIfAbsent(uid, k -> new HashMap<String, List<PlayerData>>()).computeIfAbsent(serverData.getTimeStats().getStartTime() + "-" + serverData.getId(), k -> new ArrayList<PlayerData>()).add(player);
			}
		}

	}

	@Override
	protected void logEnded(ServerData serverData) {
		String nextLog = foundFiles.get(serverData.getId()).poll();
		if (!MiscUtil.isEmpty(nextLog)) {
			logFiles.put(serverData.getId(), new File(nextLog));
		} else {
			foundFiles.remove(serverData.getId());
			logFiles.remove(serverData.getId());
		}
	}
}
