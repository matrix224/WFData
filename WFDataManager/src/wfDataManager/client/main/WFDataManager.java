package wfDataManager.client.main;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

import jdtools.exception.InvalidArgException;
import jdtools.exception.ProcessingException;
import jdtools.logging.Log;
import jdtools.util.ArgsUtil;
import jdtools.util.MiscUtil;
import wfDataManager.client.db.DBManagementDao;
import wfDataManager.client.type.ProcessModeType;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataManager.client.util.ClientTaskUtil;
import wfDataManager.client.util.RequestUtil;
import wfDataManager.client.versioning.BuildVersion;
import wfDataModel.model.processor.commands.CommandProcessor;

public class WFDataManager {
	private static final String LOG_ID = WFDataManager.class.getSimpleName();

	private static final String OPT_MODE = "mode";

	private static final List<String> VALID_OPTS = Arrays.asList(OPT_MODE, ArgsUtil.ARG_NO_INPUT);
	private static final List<ProcessModeType> VALID_MODES = Arrays.asList(ProcessModeType.NORMAL, ProcessModeType.HISTORICAL, ProcessModeType.TEST);

	public static boolean shouldExit = false;

	public static void main(String[] args) {
		int rc = 0;

		ProcessModeType mode = null;

		try {
			if (!ClientSettingsUtil.settingsLoaded()) {
				throw new ProcessingException("Issue occurred loading settings");
			}
			ClientSettingsUtil.loadID();

			Map<String, String> parsedArgs = ArgsUtil.parseArgs(args, VALID_OPTS);
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

			// Perform any DB upgrades that may be necessary before we start any processing
			DBManagementDao.upgradeDB();

			Log.info("///////////////////////////////////////////////////////////////////");
			Log.info("WFDataManager, version: " + BuildVersion.getBuildVersion());
			Log.info("Starting processing...");

			if (ClientSettingsUtil.serviceEnabled()) {
				if (MiscUtil.isEmpty(ClientSettingsUtil.getDisplayName())) {
					throw new ProcessingException("Setting for displayName is required if using the service");
				}

				RequestUtil.sendRegisterRequest();

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
					ClientTaskUtil.addTask(ClientTaskUtil.TASK_BAN_CHECKER);

					if (ClientSettingsUtil.enableBanSharing()) {
						ClientTaskUtil.addTask(ClientTaskUtil.TASK_BAN_FETCHER);
					}
				}
			} else if (ProcessModeType.HISTORICAL.equals(mode) || ProcessModeType.TEST.equals(mode)) { 
				if (!anyLogDirsExist(ClientSettingsUtil.getHistoricalLogsDirs())) {
					throw new InvalidArgException("Unknown historicalLogsDir supplied -> " + ClientSettingsUtil.getHistoricalLogsDirStr());
				}

				if (MiscUtil.isEmpty(ClientSettingsUtil.getHistoricalLogPattern())) {
					throw new InvalidArgException("Unknown historicalLogPattern supplied -> " + ClientSettingsUtil.getHistoricalLogPattern());
				}
			}

			ClientTaskUtil.addTask(ClientTaskUtil.TASK_LOG_PROCESSOR);

			if (parsedArgs.containsKey(ArgsUtil.ARG_NO_INPUT)) {
				new CountDownLatch(1).await(); // Blocks indefinitely without burning resources
			} else {
				CommandProcessor cmdProcessor = new CommandProcessor("wfDataManager.client.commands");
				try (Scanner scanner = new Scanner(System.in)) {
					while (!shouldExit) {
						String cmd = scanner.nextLine();
						cmdProcessor.processCommand(cmd);
					}
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
}
