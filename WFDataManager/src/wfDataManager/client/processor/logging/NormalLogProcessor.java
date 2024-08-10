package wfDataManager.client.processor.logging;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataManager.client.cache.BanManagerCache;
import wfDataManager.client.cache.ServerDataCache;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.ServerData;
import wfDataModel.service.type.GameDataType;

/**
 * Log parser for handling regular log files
 * @author MatNova
 *
 */
public class NormalLogProcessor extends BaseLogProcessor {
	private final Matcher LOG_PATTERN = Pattern.compile(ClientSettingsUtil.getServerLogPattern()).matcher("");

	private int logCheckInterval = ClientSettingsUtil.getLogCheckInterval();
	private String[] logDirectories = ClientSettingsUtil.getServerLogsDirs();

	@Override
	protected void findLogFiles() {
		// Check for new logs only every logCheckInterval runs or if we don't have any log files set
		if (logFiles.isEmpty() || numRuns%logCheckInterval == 0) {
			logFiles.clear();

			for (int i = 0; i< logDirectories.length; i++) {
				String logDir = logDirectories[i];
				if (MiscUtil.isEmpty(logDir)) {
					continue;
				}
				for (String f : new File(logDir).list()) {
					if (LOG_PATTERN.reset(f).matches()) {
						String logId = LOG_PATTERN.groupCount() == 0 ? null : LOG_PATTERN.group(1);
						if (MiscUtil.isEmpty(logId)) {
							logId = DEFAULT_SERVER_LOG_ID;
						}
						if (logDirectories.length > 1) {
							logId = i + "-" + logId;
						}
						
						logFiles.put(logId,  new File(logDir + File.separator + f));

						if (!ServerDataCache.singleton().hasServerData(logId)) {
							Log.info(LOG_ID + ".findLogFiles() : Found new log file " + f);
							if (logId.endsWith(DEFAULT_SERVER_LOG_ID)) {
								Log.info(LOG_ID + ".findLogFiles() : No logId found in match, using default of " + DEFAULT_SERVER_LOG_ID + ", file=" + f);
							}
						}
					}
				}
			}
		}
	}

	@Override
	protected GameDataType getDataType() {
		return GameDataType.GAME_DATA;
	}

	@Override
	protected void postProcessing(ServerData serverData) {
		// Once done parsing, if banning is enabled, check if any players are bannable
		// Note this checks any parsed players, even ones who may have just disconnected
		if (ClientSettingsUtil.enableBanning()) {
			for (PlayerData player : serverData.getParsedPlayers()) {
				BanManagerCache.singleton().checkAutoBannable(player, serverData);
			}
		}		
	}

	@Override
	protected void logEnded(ServerData serverData) {
		// Nothing to do here, we'll just try to read it again on the next iteration
	}
}
