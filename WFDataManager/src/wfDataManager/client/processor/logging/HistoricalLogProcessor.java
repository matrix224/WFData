package wfDataManager.client.processor.logging;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdtools.util.MiscUtil;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.logging.Log;
import wfDataModel.service.type.GameDataType;

/**
 * Log parser for handling historic log files
 * @author MatNova
 *
 */
public class HistoricalLogProcessor extends BaseLogProcessor {
	private final Matcher LOG_PATTERN = Pattern.compile(ClientSettingsUtil.getHistoricalLogPattern()).matcher("");
	private Map<String, Queue<String>> foundFiles = null; // key = log ID, value = queue of files to be parsed for that ID
	private String[] logDirectories = ClientSettingsUtil.getHistoricalLogsDirs();

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
			Log.info(LOG_ID + ".findLogFiles() : Found " + numFound + " historical files to process");
		} else if (foundFiles.isEmpty()) {
			Log.warn(LOG_ID + ".findLogFiles() : All log files have been parsed. Process can be shut down");
		}
	}

	@Override
	protected GameDataType getDataType() {
		return GameDataType.HISTORICAL_GAME_DATA;
	}

	@Override
	protected void postProcessing(ServerData serverData) {
		// Nothing to do here
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
