package wfDataManager.client.util;

import wfDataManager.client.type.ProcessModeType;
import wfDataManager.client.util.data.ClientSettingsCfg;
import wfDataModel.model.logging.Log;

/**
 * Utility class for accessing and updating client settings
 * @author MatNova
 *
 */
public class ClientSettingsUtil {

	public static final int HISTORICAL_POLLING_INTERVAL = 1;
	private static ClientSettingsCfg singleton;
	
	private static synchronized ClientSettingsCfg singleton() {
		if (singleton == null) {
			try {
				singleton = ClientSettingsCfg.create(ClientSettingsCfg.class);
			} catch (Exception e) {
				Log.error(ClientSettingsUtil.class.getSimpleName() + ": Exception trying to create singleton, settings will fail! -> ", e);
			}
		}
		return singleton;
	}
	
	public static boolean settingsLoaded() {
		return singleton().settingsLoaded();
	}
	
	public static void loadID() {
		singleton().loadID();
	}

	public static int getServerID() {
		return singleton().getServerID();
	}

	public static boolean setServerID(int sid) {
		return singleton().setServerID(sid);
	}

	public static String getServerLogsDirStr() {
		return singleton().getServerLogsDir();
	}
	
	public static String[] getServerLogsDirs() {
		return singleton().getServerLogsDir().split(";");
	}
	
	public static String getHistoricalLogsDirStr() {
		return singleton().getHistoricalLogsDir();
	}
	
	public static String[] getHistoricalLogsDirs() {
		return singleton().getHistoricalLogsDir().split(";");
	}

	public static String getServerLogPattern() {
		return singleton().getServerLogPattern();
	}
	
	public static String getHistoricalLogPattern() {
		return singleton().getHistoricalLogPattern();
	}
	
	public static boolean cleanServerProfiles() {
		return singleton().cleanServerProfiles();
	}
	
	public static String getDisplayName() {
		return singleton().getDisplayName();
	}

	public static String getRegion() {
		return singleton().getRegion();
	}
	
	public static String getServiceHost() {
		return singleton().getServiceHost();
	}

	public static int getServicePort() {
		return singleton().getServicePort();
	}
	
	public static int getServiceTimeout() {
		return singleton().getServiceTimeout();
	}

	public static boolean enableDataSharing() {
		return singleton().enableDataSharing();
	}
	
	public static boolean enableAlerts() {
		return singleton().enableAlerts();
	}

	public static boolean persist() {
		return singleton().persist();
	}

	public static boolean printServerData() {
		return singleton().printServerData();
	}

	public static String getServerDataFile() {
		return singleton().getServerDataFile();
	}
	
	public static String getCustomItemsConfig() {
		return singleton().getCustomItemsConfig();
	}

	public static boolean enableBanning() {
		return singleton().enableBanning();
	}

	public static boolean enableBanSharing() {
		return singleton().enableBanSharing();
	}

	public static boolean enforceLoadoutBans() {
		return singleton().enforceLoadoutBans();
	}
	
	public static int getBanTime(boolean isPrimary) {
		return (isPrimary ? getPrimaryBanTime() : getSecondaryBanTime()) * 1000;
	}

	public static int getPrimaryBanTime() {
		return singleton().getPrimaryBanTime();
	}

	public static int getSecondaryBanTime() {
		return singleton().getSecondaryBanTime();
	}

	public static String getBannedItemsConfig() {
		return singleton().getBannedItemsConfig();
	}

	public static int getPollInterval() {
		return singleton().getPollInterval();
	}

	public static int getJamThreshold() {
		return singleton().getJamThreshold();
	}
	
	public static int getLogCheckInterval() {
		return singleton().getLogCheckInterval();
	}
	
	public static int getBanCheckInterval() {
		return singleton().getBanCheckInterval();
	}
	
	public static int getBanFetchInterval() {
		return singleton().getBanFetchInterval();
	}

	public static String getCacheDir() {
		return singleton().getCacheDir();
	}
	
	/**
	 * Returns if the service is going to be used at all. <br/>
	 * Currently returns true if any of: {@link ClientSettingsUtil#enableDataSharing()}, {@link #enableBanSharing()}, or
	 * {@link #enableAlerts()} are true
	 * @return
	 */
	public static boolean serviceEnabled() {
		return enableDataSharing() || enableBanSharing() || enableAlerts();
	}
	
	public static void setProcessMode(ProcessModeType processMode) {
		singleton().setProcessMode(processMode);
	}
	
	public static ProcessModeType getProcessMode() {
		return singleton().getProcessMode();
	}
}
