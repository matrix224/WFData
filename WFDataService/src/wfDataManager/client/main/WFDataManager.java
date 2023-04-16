package wfDataManager.client.main;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.google.gson.JsonObject;

import jdtools.util.MiscUtil;
import wfDataManager.client.cache.BanManagerCache;
import wfDataManager.client.type.ProcessModeType;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataManager.client.util.ClientTaskUtil;
import wfDataManager.client.util.RequestUtil;
import wfDataManager.client.versioning.BuildVersion;
import wfDataModel.model.exception.InvalidArgException;
import wfDataModel.model.exception.ProcessingException;
import wfDataModel.model.logging.Log;
import wfDataModel.model.processor.commands.CommandProcessor;
import wfDataModel.service.codes.JSONField;

public class WFDataManager {
	private static final String LOG_ID = WFDataManager.class.getSimpleName();

	private static final String OPT_MODE = "mode";

	private static final List<String> VALID_OPTS = Arrays.asList(OPT_MODE);
	private static final List<ProcessModeType> VALID_MODES = Arrays.asList(ProcessModeType.NORMAL, ProcessModeType.HISTORICAL);
	
	public static boolean shouldExit = false;

	public static void main(String[] args) {
		int rc = 0;

		ProcessModeType mode = null;

		try {

			if (!ClientSettingsUtil.settingsLoaded()) {
				throw new ProcessingException("Issue occurred loading settings");
			}
			ClientSettingsUtil.loadID();

			Map<String, String> parsedArgs = parseArgs(args);
			if (parsedArgs.get(OPT_MODE) != null ) {
				mode = ProcessModeType.valueOf(parsedArgs.get(OPT_MODE).toUpperCase());
				if (!VALID_MODES.contains(mode)) {
					throw new InvalidArgException("Unknown mode provided -> " + mode);
				}
			} else {
				throw new InvalidArgException("Expecting arg: -" + OPT_MODE + " <Mode>");
			}

			ClientSettingsUtil.setProcessMode(mode);

			Log.debug(LOG_ID + "() : Config -> ServerLogsDir=" + ClientSettingsUtil.getServerLogsDirStr() + ", Using DB=" + ClientSettingsUtil.persist() + ", Debug Mode=" + Log.isDebugMode());

			Log.info("///////////////////////////////////////////////////////////////////");
			Log.info("WFDataManager, version: " + BuildVersion.getBuildVersion());
			Log.info("Starting processing...");

			
			
			if (ClientSettingsUtil.serviceEnabled()) {
				if (MiscUtil.isEmpty(ClientSettingsUtil.getDisplayName())) {
					throw new ProcessingException("Setting for displayName is required if using the service");
				}

				JsonObject dataObj = new JsonObject();
				dataObj.addProperty(JSONField.SERVER_NAME, ClientSettingsUtil.getDisplayName());
				if (!MiscUtil.isEmpty(ClientSettingsUtil.getRegion())) {
					dataObj.addProperty(JSONField.REGION, ClientSettingsUtil.getRegion());
				}

				RequestUtil.sendRegisterRequest(dataObj);

				if (ClientSettingsUtil.getServerID() == 0) {
					throw new ProcessingException("No ID was assigned for service use");
				}

				ClientTaskUtil.addTask(ClientTaskUtil.TASK_RETRY_FAILED);
			}
			
			if (ProcessModeType.NORMAL.equals(mode)) {
				if (!anyLogDirsExist(ClientSettingsUtil.getServerLogsDirs())) {
					throw new InvalidArgException("Unknown serverLogsDir supplied -> " + ClientSettingsUtil.getServerLogsDirStr());
				}

				if (MiscUtil.isEmpty(ClientSettingsUtil.getServerLogPattern())) {
					throw new InvalidArgException("Unknown severLogPattern supplied -> " + ClientSettingsUtil.getServerLogPattern());
				}
				
				if (ClientSettingsUtil.enableBanning()) {
					BanManagerCache.singleton().prepareCache();
					ClientTaskUtil.addTask(ClientTaskUtil.TASK_BAN_CHECKER);

					if (ClientSettingsUtil.enableBanSharing()) {
						ClientTaskUtil.addTask(ClientTaskUtil.TASK_BAN_FETCHER);
					}
				}
			} else if (ProcessModeType.HISTORICAL.equals(mode)) { 
				if (!anyLogDirsExist(ClientSettingsUtil.getHistoricalLogsDirs())) {
					throw new InvalidArgException("Unknown historicalLogsDir supplied -> " + ClientSettingsUtil.getHistoricalLogsDirStr());
				}
				
				if (MiscUtil.isEmpty(ClientSettingsUtil.getHistoricalLogPattern())) {
					throw new InvalidArgException("Unknown historicalLogPattern supplied -> " + ClientSettingsUtil.getHistoricalLogPattern());
				}
			}

			ClientTaskUtil.addTask(ClientTaskUtil.TASK_LOG_PROCESSOR);

			CommandProcessor cmdProcessor = new CommandProcessor("wfDataManager.client.commands");
			try (Scanner scanner = new Scanner(System.in)) {
				while (!shouldExit) {
					String cmd = scanner.nextLine();
					cmdProcessor.processCommand(cmd);
				}
			}
			
		} catch (InvalidArgException iae) {
			Log.error(LOG_ID + "() : Improper args supplied -> " + iae.getLocalizedMessage());
			rc = 1;
			//new Emailer("Warframe Log Handler - Invalid args", "Improper args supplied -> " + iae.getLocalizedMessage()).sendEmail();
		} catch (Throwable t) {
			Log.error(LOG_ID + "() : Error while processing -> ", t);
			rc = 2;
			//new Emailer("Warframe Log Handler - Error", "Error occurred while processing -> " + e.getLocalizedMessage()).sendEmail();
		} finally {
			ClientTaskUtil.stopTasks();
			Log.info(LOG_ID + "() : Shutting down with RC " + rc);
			System.exit(rc);
		}
	}

	private static boolean anyLogDirsExist(String[] dirs) {
		boolean anyExist = false;
		
		for (String dir : dirs) {
			File f = new File(dir);
			if (f.exists() && f.isDirectory()) {
				anyExist = true;
				break;
			}
		}
		
		return anyExist;
	}
	
	private static Map<String, String> parseArgs(String[] args) {
		Map<String, String> parsedArgs = new HashMap<String, String>();
		if (args != null) {
			boolean isQuoted = false;
			String curCmd = "";
			String curVal = "";

			for (String arg : args) {
				if (arg.startsWith("-")) {
					if (!MiscUtil.isEmpty(curCmd)) {
						parsedArgs.put(curCmd, curVal);
						curVal = "";
					}
					curCmd = arg.substring(arg.lastIndexOf("-") + 1); // If multiple dashes, get value after it

					if (!VALID_OPTS.contains(curCmd)) {
						Log.warn(LOG_ID + ".parseArgs() : Unknown arg detected: " + arg);
					}
				} else {
					if (MiscUtil.isEmpty(curCmd)) {
						Log.warn(LOG_ID + ".parseArgs() : Missing command for associated arg, ignoring: " + arg);
					} else {
						if (!isQuoted && arg.startsWith("\"")) {
							isQuoted = true;
							curVal = arg.substring(1);
						} else if (isQuoted && arg.endsWith("\"")) {
							isQuoted = false;
							curVal += arg.substring(0, arg.length() - 1);
						} else {
							curVal += arg;
						}
					}
				}
			}

			// Put last parsed cmd in
			if (!MiscUtil.isEmpty(curCmd)) {
				parsedArgs.put(curCmd, curVal);
			}
		}

		return parsedArgs;
	}
}
