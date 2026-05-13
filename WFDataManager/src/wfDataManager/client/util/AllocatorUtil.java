package wfDataManager.client.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdtools.collection.Pair;
import jdtools.logging.Log;
import jdtools.util.SystemUtil;
import wfDataManager.client.cache.AllocatorCache;
import wfDataManager.client.cache.ServerDataCache;
import wfDataManager.client.data.AllocatorGameData;
import wfDataModel.model.data.ServerAllocatorData;
import wfDataModel.model.data.ServerData;
import wfDataModel.service.type.EloType;
import wfDataModel.service.type.GameMode;

public final class AllocatorUtil {

	private static final String LOG_ID = AllocatorUtil.class.getSimpleName();
	private static final Pattern SERVER_ID_PATTERN = Pattern.compile("warframe.*\\.exe.*ID: (\\d*) ");
	private static final Pattern SERVER_PID_PATTERN = Pattern.compile("warframe.*\\.exe\\s*(\\d*)\\s");



	public static List<AllocatorGameData> getRunningInstances() {
		List<AllocatorGameData> instances = null;
		Pair<String, Boolean> result = SystemUtil.runCommandOutput(10, false, new String[] {"tasklist", "/v"});
		if (result.getValue()) {
			String tasksStr = result.getKey().toLowerCase();
			String[] tasks = tasksStr.split(System.lineSeparator());
			for (String taskStr : tasks) {
				if (taskStr.contains("warframe")) {
					Integer id = null;
					Integer pid = null;
					Matcher idMatcher = SERVER_ID_PATTERN.matcher(taskStr);
					Matcher pidMatcher = SERVER_PID_PATTERN.matcher(tasksStr);
					if (idMatcher.matches()) {
						id = Integer.valueOf(idMatcher.group(1));
					}
					if (pidMatcher.matches()) {
						pid = Integer.valueOf(pidMatcher.group(1));
					}

					if (pid != null) {
						AllocatorGameData gameData = new AllocatorGameData();
						gameData.setPID(pid);
						gameData.setInstanceId(id);
						if (instances == null) {
							instances = new ArrayList<AllocatorGameData>();
						}

						if (id != null) {
							ServerData serverData = ServerDataCache.singleton().getServerData(String.valueOf(id));
							if (serverData != null) {
								if (serverData.getAllocatorData() == null || serverData.getAllocatorData().getPID() == null) {
									 if (serverData.getAllocatorData() == null) {
										 serverData.setAllocatorData(new ServerAllocatorData());
									 }
									 serverData.getAllocatorData().setPID(pid);
								} else if (pid.equals(serverData.getAllocatorData().getPID())) {
									gameData.setGameMode(GameMode.idToType(serverData.getGameModeId()));
									gameData.setElo(EloType.codeToType(serverData.getEloRating()));
								}
							}
						}

						// When the game updates, it will sometimes launch a server with instance ID 0
						// This means the instance ID will not be displayed in the task name
						// So, if we have an ID in the task name, we know we want to include this
						// Additionally, if the taskStr contains Loading (which we set in the task name when launching a server ourselves),
						// then we will include it
						if (id != null || taskStr.contains("Loading")) {
							instances.add(gameData);
						}
					}
				}
			}
		} else {
			Log.warn(LOG_ID, ".getRunningInstances() : Could not get running instances, result: ", result.getKey());
		}

		return instances;
	}

	public static void launchServer(AllocatorGameData data, int instanceId) {
		// First move any existing log file for this instanceId
		moveLog(instanceId);
		Log.info(LOG_ID, ".launchServer() : Launching server " + instanceId, " (" + data.getGameMode() + " " + data.getElo(), ")");
		String[] cmd = {"start", "\"Windows x64 0 player(s) ID: " + instanceId + " Loading\"", "\"" + AllocatorCache.singleton().getExecutable() + "\"", "-fullscreen:0", "-dx10:0", "-dx11:1", "-threadedworker:1", "-cluster:public", "-language:en", "-allowmultiple", "-log:DedicatedServer" + instanceId + ".log", "-applet:/Lotus/Types/Game/DedicatedServer /Lotus/Types/GameRules/DefaultDedicatedServerSettings", "-instance:" + instanceId, "-settings:" + data.getGameMode().getAllocatorName(data.getElo())};
		SystemUtil.runCommand(-1, true, cmd);
	}

	public static void killServer(AllocatorGameData data) {
		Log.info(LOG_ID, ".killServer() : Killing server " + data.getInstanceId(), " (" + data.getGameMode() + " " + data.getElo(), ")");
		String[] cmd = {"taskkill", "/pid", String.valueOf(data.getPID())};
		SystemUtil.runCommand(-1, true, cmd);
	}

	public static String getWFExecutable() {
		String executable = null;
		Pair<String, Boolean> result = SystemUtil.runCommandOutput(10, false, getWFLauncherCmd("DownloadDir"));
		if (result.getValue()) {
			executable = getWFDir(result.getKey());
		} else {
			Log.debug(LOG_ID + ".getWFExecutable() : No executable found with DownloadDir key, will try for launcher...");
			result = SystemUtil.runCommandOutput(10, false, getWFLauncherCmd("LauncherExe"));
			if (result.getValue()) {
				executable = getWFDir(result.getKey());
			}
		}
		return executable;
	}

	private static void moveLog(int instanceId) {
		try {
			Path basePath = Paths.get(System.getProperty("user.home", ""), "AppData", "Local", "Warframe");
			File logFile = Paths.get(basePath.toString(), "Warframe", "DedicatedServer" + instanceId + ".log").toFile();
			if (logFile.exists()) {
				File movedLog = null;
				do {
					movedLog = Paths.get(basePath.toString(), "newLogs", logFile.getName() + System.currentTimeMillis() + ".log").toFile();
				} while (movedLog.exists());

				movedLog.mkdirs();
				Files.move(logFile.toPath(), movedLog.toPath());
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".moveLog() : Error moving log file " + instanceId + " -> ", e);
		}
	}

	private static String getWFDir(String result) {
		for(String line : result.split(System.lineSeparator())){
			for(String token : line.split("  ")){
				if(token.contains(":\\")) {
					if (token.trim().endsWith("Launcher.exe")) {
						File f = new File(token);
						for (File file : f.getParentFile().getParentFile().listFiles()) {
							if (isWFProgram(file)) {
								return file.getAbsolutePath();
							}
						}
					} else {
						for(File file : new File(token.trim()+"\\Public").listFiles()){
							if (isWFProgram(file)) {
								return file.getAbsolutePath();
							}
						}
					}
				}
			}
		}
		return null;
	}

	private static String[] getWFLauncherCmd(String regKey) {
		return new String[]{"reg", "query", "\"HKCU\\Software\\Digital Extremes\\Warframe\\Launcher\"", "/v", regKey};
	}

	private static boolean isWFProgram(File file) {
		return file.isFile() && file.getName().toLowerCase().contains("warframe") && file.getName().endsWith(".exe");
	}

}
