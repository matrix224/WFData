package wfDataManager.client.cache;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jdtools.exception.ProcessingException;
import jdtools.http.HTTPRequest;
import jdtools.http.HTTPRequestListener;
import jdtools.http.HTTPResponseData;
import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataManager.client.data.AllocatorGameData;
import wfDataManager.client.util.AllocatorUtil;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.type.AllocatorType;
import wfDataModel.service.type.EloType;
import wfDataModel.service.type.GameMode;

public final class AllocatorCache implements HTTPRequestListener {

	private static final String LOG_ID = AllocatorCache.class.getSimpleName();
	public static final int MAX_SERVERS = 6;
	private static final String WORLD_STATE_HTTP = "https://content.warframe.com/dynamic/worldState.php";
	private static final long WORLD_STATE_REFRESH = 600000; // Refresh every 10 min (in milliseconds)
	private static AllocatorCache singleton;
	
	private List<AllocatorGameData> gameCfgs = new ArrayList<AllocatorGameData>();
	private int maxExpectedServers = MAX_SERVERS;
	private String executable = AllocatorUtil.getWFExecutable();
	private long lastWorldStateRefresh = 0;
	private List<String> activeEvents = new ArrayList<String>();
	
	public static synchronized AllocatorCache singleton() {
		if (singleton == null) {
			singleton = new AllocatorCache();
			singleton.prepareCache(false);
		} else {
			singleton.refreshWorldState();
		}
		
		return singleton;
	}
	
	private boolean prepareCache(boolean isRefresh) {
		boolean success = false;
		File allocatorCfg = new File(ClientSettingsUtil.getAllocatorConfig());
		
		refreshWorldState();
		
		if (allocatorCfg.exists()) {
			try (BufferedReader br = new BufferedReader(new FileReader(allocatorCfg))) {
				String cfgStr = "";
				String line = null;
				while ((line = br.readLine()) != null) {
					if (!cfgStr.trim().startsWith("#")) {
						cfgStr += line;
					}
				}
				JsonObject cfgObject = JsonParser.parseString(cfgStr).getAsJsonObject();
				if (cfgObject.has(JSONField.MAX)) {
					maxExpectedServers = cfgObject.get(JSONField.MAX).getAsInt();
				} else {
					maxExpectedServers = MAX_SERVERS;
				}
				JsonArray gamesCfg = cfgObject.getAsJsonArray(JSONField.GAME_MODES);
				if (gamesCfg.size() > 0) {
					if (isRefresh) {
						gameCfgs.clear();
					}
					int expectedMax = 0;
					for (int i = 0; i < gamesCfg.size(); ++i) {
						JsonObject gameObj = gamesCfg.get(i).getAsJsonObject();
						AllocatorGameData gameCfg = new AllocatorGameData();
						gameCfg.setMinCount(Math.min(gameObj.get(JSONField.MIN).getAsInt(), MAX_SERVERS)); // min count of 0 means it will be handled by load balancing. min count > 0 means it will be 'fixed', so that many must exist at once
						gameCfg.setGameMode(GameMode.valueOf(gameObj.get(JSONField.GAME_MODE).getAsString()));
						gameCfg.setElo(EloType.valueOf(gameObj.get(JSONField.ELO).getAsString()));
						gameCfg.setEnabled(gameObj.get(JSONField.ENABLED).getAsBoolean());
						if (gameObj.has(JSONField.TRIGGER)) {
							gameCfg.setEventTrigger(gameObj.get(JSONField.TRIGGER).getAsString());
						}
						
						if (gameCfg.getMinCount() < 0) {
							throw new ProcessingException("Invalid minCount for " + gameCfg.getGameMode() + " " + gameCfg.getElo());
						}
						
						for (AllocatorGameData cfg : gameCfgs) {
							if (gameCfg.getGameMode().equals(cfg.getGameMode()) && gameCfg.getElo().equals(cfg.getElo())) {
								throw new ProcessingException("Duplicate entry for " + gameCfg.getGameMode() + " " + gameCfg.getElo());
							}
						}						
						gameCfgs.add(gameCfg);
						expectedMax += gameCfg.getMinCount();
					}
					
					if (expectedMax > MAX_SERVERS) {
						Log.warn(LOG_ID, ".prepareCache() : Number of total expected servers with minimum count is " + expectedMax + ", but max possible servers is " + maxExpectedServers + ". Will only launch first " + maxExpectedServers +  " servers with minimum count");
					}
				} else {
					throw new ProcessingException("gameModes array must have at least one entry in it!");
				}
				
				success = true;
			} catch (Exception e) {
				Log.error(LOG_ID + ".prepareCache() : Exception occurred -> ", e);
			}

		} else {
			Log.warn(LOG_ID + ".prepareCache() : Allocator config does not exist, no items will be marked as banned -> " + allocatorCfg.getAbsolutePath());
		}
		return success;
	}
	
	public int getMaxExpectedServers() {
		return maxExpectedServers;
	}
	
	public int getExpectedServerCount(GameMode gameMode, EloType elo) {		
		for (AllocatorGameData cfg : gameCfgs) {
			if (gameMode.equals(cfg.getGameMode()) && elo.equals(cfg.getElo())) {
				return cfg.getMinCount();
			}
		}
		return 0;
	}
	
	public String getExecutable() {
		return executable;
	}
	
	public List<AllocatorGameData> getLaunchableServers(AllocatorType type) {
		List<AllocatorGameData> servers = null;
		for (AllocatorGameData data : gameCfgs) {
			if (isLaunchable(data) && (AllocatorType.ALL.equals(type) || (AllocatorType.BALANCED.equals(type) && data.getMinCount() == 0) || (AllocatorType.FIXED.equals(type) && data.getMinCount() > 0))) {
				if (servers == null) {
					servers = new ArrayList<AllocatorGameData>();
				}
				servers.add(data);
			}
		}
		return servers;
	}
	
	private boolean isLaunchable(AllocatorGameData data) {
		return data.isEnabled() && (MiscUtil.isEmpty(data.getEventTrigger()) || activeEvents.contains(data.getEventTrigger()));
	}
	
	private void refreshWorldState() {
		if (System.currentTimeMillis() - lastWorldStateRefresh >= WORLD_STATE_REFRESH) {
			HTTPRequest req = new HTTPRequest(WORLD_STATE_HTTP, null, null, false, this);
			req.fetch();
		}
	}

	@Override
	public void onRequestCompleted(HTTPResponseData responseData) {
		if (responseData.isHTTPSuccess() && responseData.isResultSuccess()) {
			try {
				JsonObject worldstateObj = JsonParser.parseString(responseData.getResponse()).getAsJsonObject();
				JsonArray pvpModes = worldstateObj.getAsJsonArray("PVPAlternativeModes");
				activeEvents.clear();
				for (JsonElement ele : pvpModes) {
					JsonObject modeObj = ele.getAsJsonObject();
					activeEvents.add(modeObj.get("TargetMode").getAsString());
				}
			} catch (Exception e) {
				Log.error(LOG_ID + ".onRequestCompleted() : Error parsing worldstate data -> ", e);
			}
			lastWorldStateRefresh = System.currentTimeMillis();
		} else {
			Log.warn(LOG_ID, ".onRequestCompleted() : Could not refresh worldstate data -> http=" + responseData.getHTTPResponseCode() + ", resp=" + responseData.getResponse());
		}
	}
	
	
	//private static class 
	
}
