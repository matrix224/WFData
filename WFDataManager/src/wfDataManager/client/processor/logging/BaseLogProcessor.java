package wfDataManager.client.processor.logging;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataManager.client.cache.ServerDataCache;
import wfDataManager.client.db.ActivityDao;
import wfDataManager.client.db.GameDataDao;
import wfDataManager.client.db.ProcessorVarDao;
import wfDataManager.client.db.ServerDao;
import wfDataManager.client.db.manager.ResourceManager;
import wfDataManager.client.parser.logging.BaseLogParser;
import wfDataManager.client.parser.logging.BindingParser;
import wfDataManager.client.parser.logging.BuildIDParser;
import wfDataManager.client.parser.logging.CephalonCaptureParser;
import wfDataManager.client.parser.logging.CurrentDirectoryParser;
import wfDataManager.client.parser.logging.CurrentProfileParser;
import wfDataManager.client.parser.logging.CurrentTimeParser;
import wfDataManager.client.parser.logging.GPFParser;
import wfDataManager.client.parser.logging.GameSettingsParser;
import wfDataManager.client.parser.logging.GameStateParser;
import wfDataManager.client.parser.logging.IntroductionRequestParser;
import wfDataManager.client.parser.logging.LunaroGoalParser;
import wfDataManager.client.parser.logging.MissionStatsParser;
import wfDataManager.client.parser.logging.NRSIssueParser;
import wfDataManager.client.parser.logging.PlayerConnectionParser;
import wfDataManager.client.parser.logging.PlayerJoinParser;
import wfDataManager.client.parser.logging.PlayerKillParser;
import wfDataManager.client.parser.logging.PlayerLeaveParser;
import wfDataManager.client.type.ParseResultType;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataManager.client.util.RequestUtil;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.ServerData;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.type.GameDataType;

/**
 * Base parser implementation for handling any log parsing tasks
 * @author MatNova
 *
 */
public abstract class BaseLogProcessor {
	
	protected static final Matcher TIME_PATTERN = Pattern.compile("^(\\d+)\\..*").matcher("");
	protected static final String DEFAULT_SERVER_LOG_ID = "99";
	protected String LOG_ID = getClass().getSimpleName();

	protected long numRuns = 0;
	protected Map<String, File> logFiles = new ConcurrentHashMap<String, File>(8); // LogID -> data. Start at 8, will increase on its own as needed
	protected List<ServerData> serverInfos = new ArrayList<ServerData>();  // Servers that were included in current parse
	protected List<BaseLogParser> parsers = Arrays.asList(new BindingParser(), new BuildIDParser(), new CephalonCaptureParser(), new CurrentDirectoryParser(), new CurrentProfileParser(), new CurrentTimeParser(), new GameSettingsParser(), new GameStateParser(), new GPFParser(), 
			new IntroductionRequestParser(), new LunaroGoalParser(), new MissionStatsParser(), new NRSIssueParser(), new PlayerConnectionParser(), new PlayerJoinParser(), new PlayerKillParser(), new PlayerLeaveParser());
	private int jamThreshold = ClientSettingsUtil.getJamThreshold();
	private boolean enableAlerts = ClientSettingsUtil.enableAlerts();
	private boolean shouldPersist = ClientSettingsUtil.persist();
	private boolean printServerData = ClientSettingsUtil.printServerData();
	private boolean shareData = ClientSettingsUtil.enableDataSharing();

	public void processLogs() throws SQLException {
		findLogFiles();

		if (logFiles.isEmpty()) {
			Log.warn(LOG_ID + ".processLogs() : No log files found to read! Will do nothing...");
			return;
		}

		serverInfos.clear();
		
		for (String logId : logFiles.keySet()) {
			File f = logFiles.get(logId);
			ServerData serverData = ServerDataCache.singleton().getServerData(logId);

			if (!f.exists()) {
				serverData.addNumMiss();
				Log.warn(LOG_ID + ".processLogs() : File for logId " + logId + " no longer present, skipping...");
				continue;
			}

			boolean isReadingMissionStats = false;
			boolean logReachedEnd = false;
			long lastPosition = serverData.getLogPosition();

			if (serverData.isParsing()) {
				Log.warn(LOG_ID + ".processLogs() : Server is already marked as currently parsing for " + f.getName() + ". Is another thread stuck on it? Skipping");
			} else if (lastPosition == -1) {
				Log.warn(LOG_ID + ".processLogs() : Could not establish last line for " + f.getName() + ", skipping");
			} else {
				Log.info(LOG_ID + ".processLogs() : Processing for " + f.getName());
				try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(f.getAbsolutePath()), StandardOpenOption.READ), StandardCharsets.UTF_8))) {
					long offset = 0;
					String line = null;
					
					long lastLogTime = -1;
					int lineLen = 0;
					serverData.setIsParsing(true);

					while ((line = fileReader.readLine()) != null) {
						lineLen = line.length() + System.lineSeparator().length();
						fileReader.mark(2);
						// If this is the last line of the file, then break here and stop processing
						// This is because the last line might not be fully written at this point (i.e. names or values could be cut off)
						// Value of offset variable will be set to the end of the previous line here
						// On the next read, it'll pick up from there and get this line fully
						// NOTE: This technically means for a historical read, it would never parse the last line. But this should be okay
						if (fileReader.read() == -1) {
							logReachedEnd = true;
							break;
						}
						fileReader.reset();

						// Just for getting current seconds timestamp from line
						if (TIME_PATTERN.reset(line).matches()) {
							lastLogTime = Long.valueOf(TIME_PATTERN.group(1));
							if (serverData.getTimeStats().getRolloverTime() > 0 && lastLogTime >= serverData.getTimeStats().getRolloverTime()) {
								Log.info(LOG_ID + ".processLogs() : Stopping parsing for " + logId + " due to date-rollover detection");
								break;
							}
						}

						ParseResultType result = null;
						for (BaseLogParser parser : parsers) {
							if (parser.canParse(line)) {
								result = parser.parse(serverData, offset, lastLogTime);
								break;
							}
						}
						if (ParseResultType.SKIP.equals(result)) {
							fileReader.skip(lastPosition - (offset + lineLen));
							offset = lastPosition - lineLen;
						} else if (ParseResultType.START_MISSION.equals(result)) {
							isReadingMissionStats = true;
						} else if (ParseResultType.END_MISSION.equals(result)) {
							isReadingMissionStats = false;
						} else if (ParseResultType.STOP.equals(result)) {
							break;
						} else if (ParseResultType.FINISH_LOG.equals(result)) {
							logReachedEnd = true;
							break;
						}
						// Else, assumed OK or not something we cared about parsing, and continue reading
						
						offset += lineLen;

						// If the log time is >= our next determined server activity time, mark the activity at this time after this line has been parsed
						if (lastLogTime >= serverData.getTimeStats().getActivityTime()) {
							serverData.markServerActivity();
						}
					}

					// If we get to this point and isReadingMissionStats is true, this means we never found the end of the mission stats
					// This could happen if we read the log while it was in the middle of printing the stats
					// In this case, reset the parsing as if an error occurred
					// This most likely will never occur, but should handle it just in case
					if (isReadingMissionStats) {
						isReadingMissionStats = false;
						Log.warn(LOG_ID + ".processLogs() : Reached end of log while in middle of reading mission stats for server " + logId + ", will reset parse");
						serverData.resetParse(true);
						continue;
					}

					// If we ended up not reading anything at all, consider it a repeat read (i.e. nothing new was read)
					if (offset == serverData.getLogPosition()) {
						serverData.addNumRepeat();
					} else {
						serverData.clearNumRepeats();
					}

					serverData.getTimeStats().setLogTime(lastLogTime);
					serverData.setLogPosition(offset);
					serverInfos.add(serverData);
				} catch (Exception e) {
					Log.error(LOG_ID + ".processLogs() : Error parsing file " + f.getName() + " -> ", e);
					serverData.resetParse(true); // Reset all currently parsed data if error occurred
				} finally {
					serverData.setIsParsing(false); // Done parsing for this server
				}
				
				// Perform any post-processing tasks that may be specific to a given processor
				postProcessing(serverData);
				
				// Notify if the end of the log was reached, in case anything needs to happen
				if (logReachedEnd) {
					logEnded(serverData);
				}
				
			}
		}
		
		submitData();
		numRuns++;
	}

	private void submitData() throws SQLException {
		Connection conn = null;
		boolean hasServerData = false;
		boolean hasNonErrorServer = false;

		try {
			conn = ResourceManager.getDBConnection(false);

			String jammedServers = "";
			JsonObject allServerData = printServerData ? new JsonObject() : null;
			JsonArray allServerDataArr = printServerData ? new JsonArray() : null;

			for (ServerData server : serverInfos) {
				if (jamThreshold > 0 && enableAlerts && server.getNumRepeats() == jamThreshold) {
					if (MiscUtil.isEmpty(jammedServers)) {
						jammedServers = server.getId();
					} else {
						jammedServers += ", " + server.getId();
					}
				}

				if (Log.isDebugMode()) {
					String errMsgs = "";
					if (server.getNumRepeats() > 0) {
						errMsgs = "R=" + server.getNumRepeats();
					}
					if (server.getNumMiss() > 0) {
						if (!MiscUtil.isEmpty(errMsgs)) {
							errMsgs += ", ";
						}
						errMsgs += "M=" + server.getNumMiss();
					}
					if (server.hasError()) {
						if (!MiscUtil.isEmpty(errMsgs)) {
							errMsgs += ", ";
						}
						errMsgs += "E=true";
					}
					Log.debug("Server #" + server.getId() + " " + errMsgs + " - current players=" + server.getConnectedPlayersDB());
				}

				// If server had an error while parsing, or it is jammed / missing log file, then ignore it
				// Do not want to repeatedly parse any data that may be stuck or invalid
				if (!server.isDataValid()) {
					continue;
				}

				hasNonErrorServer = true; // At this point we know we have at least one server that isn't in an error state
				
				Collection<PlayerData> parsedPlayers = server.getParsedPlayers();
				if (!MiscUtil.isEmpty(parsedPlayers)) {
					hasServerData = true;

					if (shouldPersist) {
						GameDataDao.updatePlayerData(conn, parsedPlayers, getDataType());
						GameDataDao.updateWeeklyPlayerData(conn, parsedPlayers, server.getTimeStats().getWeeklyDate().toLocalDate());
						GameDataDao.updateWeaponData(conn, parsedPlayers);
						GameDataDao.addDailyWeaponData(conn, parsedPlayers, server.getTimeStats().getDailyDate().toLocalDate());

						if (!MiscUtil.isEmpty(server.getMiscKills())) {
							GameDataDao.updateWeaponData(conn, server.getMiscKills());
							GameDataDao.addDailyWeaponData(conn, server.getMiscKills(), server.getTimeStats().getDailyDate().toLocalDate());
						}
						
						if (!MiscUtil.isEmpty(server.getServerActivity())) {
							ActivityDao.addActivityData(conn, server.getServerActivity(), server.getGameModeId(), server.getEloRating());
						}
					}
					if (Log.isDebugMode()) {
						for (PlayerData data : parsedPlayers) {
							Log.debug(data.toString());
							for (String weapon : data.getWeaponKills().keySet()) {
								Log.debug(weapon + " -> " +  data.getWeaponKills().get(weapon));
							}
						}
					}
				}

				// Get server info after any players have properly been removed
				if (printServerData) {
					allServerDataArr.add(server.getServerInfo());
				}
				
				if (server.hasNRSIssue() && enableAlerts) {
					String latestDBBuild = ProcessorVarDao.getVar(ProcessorVarDao.VAR_NRS_BUILD);

					// TODO: Alerts
					if (MiscUtil.isEmpty(latestDBBuild) || Integer.parseInt(latestDBBuild) != server.getBuildId()) {
						//new Emailer("Warframe NRS Issue", "There's an NRS server issue happening").sendEmail();
						if (server.getBuildId() != -1) {
							ProcessorVarDao.updateVar(conn, ProcessorVarDao.VAR_NRS_BUILD, String.valueOf(server.getBuildId()));
						}
					}
				}
			}

			if (shouldPersist || shareData) {
				ServerDao.updateServerData(conn, serverInfos);
			}

			if (printServerData) {
				allServerData.add("servers", allServerDataArr);
				allServerData.addProperty("lastUpdated", System.currentTimeMillis());
				try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(ClientSettingsUtil.getServerDataFile()), StandardCharsets.UTF_8))) {
					writer.write(allServerData.toString());
				}
			}


			// TODO: Alerts
			if (!MiscUtil.isEmpty(jammedServers)) {
				//new Emailer("Warframe Server Jam", "Server log may be jammed for ID(s) " + jammedServers).sendEmail();
			}

			conn.commit();
			
		} catch (Exception e) { 
			Log.error(LOG_ID + ".submitData() : Error while storing data, will roll back -> ", e);
			conn.rollback();
			hasServerData = false; // Set this false here so we don't send anything to service
			hasNonErrorServer = false; // Set this false here so we don't send anything to service
			// If an error occurred, we roll back all server data
			// The DB transaction is atomic across all servers, so we just roll them all back
			// TODO: Can this be improved?
			for (ServerData server : serverInfos) {
				server.resetParse(true);
			}
		} finally {
			ResourceManager.releaseResources(conn);
		}

		// If data sharing is enabled and at least one server isn't in an error state, and either there's data or this is an interval of 5 run, then share any info parsed from the servers
		// The interval of 5 is to let server statuses be updated as needed on the service side if there's a longer period of no data
		// Note we only send server data for any servers that are marked as not having any issues (errors, repeats, etc)
		// If every server has an issue, we will not send any data
		if (shareData && hasNonErrorServer && (hasServerData || numRuns%5 == 0)) {
			GameDataType dataType = getDataType();
			JsonObject requestObj = new JsonObject();
			requestObj.add(JSONField.DATA, new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJsonTree(serverInfos.stream().filter(server -> server.isDataValid()).collect(Collectors.toList())));
			// If this is for regular game data, include a list of all the IDs we at least attempted to parse data for
			// This will be used by the service to maintain what servers are even considered active still (e.g. if server ID 4 is no longer in the logFiles, any current data for it can be removed on service) 
			if (GameDataType.GAME_DATA.equals(dataType)) {
				requestObj.add(JSONField.DATA_ID, new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJsonTree(logFiles.keySet()));
			}
			RequestUtil.sendAddDataRequest(requestObj, dataType);
		}
	}
	
	protected abstract void findLogFiles();
	protected abstract GameDataType getDataType();
	protected abstract void postProcessing(ServerData serverData);
	protected abstract void logEnded(ServerData serverData);
}
