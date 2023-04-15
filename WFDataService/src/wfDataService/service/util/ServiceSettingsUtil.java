package wfDataService.service.util;

import wfDataModel.model.logging.Log;
import wfDataService.service.util.data.ServiceSettingsCfg;

/**
 * Utility class for accessing and updating service settings
 * @author MatNova
 *
 */
public class ServiceSettingsUtil {

	private static ServiceSettingsCfg singleton;

	private static synchronized ServiceSettingsCfg singleton() {
		if (singleton == null) {
			try {
				singleton = ServiceSettingsCfg.create(ServiceSettingsCfg.class);
			} catch (Exception e) {
				Log.error(ServiceSettingsUtil.class.getSimpleName() + ": Exception trying to create singleton, settings will fail! -> ", e);
			}
		}
		return singleton;
	}

	public static boolean settingsLoaded() {
		return singleton().settingsLoaded();
	}

	public static int getServicePort() {
		return singleton().getServicePort();
	}

	public static boolean autoAllowRegistration() {
		return singleton().autoAllowRegistration();
	}

	public static String getCacheDir() {
		return singleton().getCacheDir();
	}

	public static String getCustomItemsConfig() {
		return singleton().getCustomItemsConfig();
	}
	
	public static boolean trackServerStatus() {
		return singleton().printServerData();
	}
	
	public static String getServerStatusFile() {
		return singleton().getServerDataFile();
	}
	
	public static int getServerStatusUpdateInterval() {
		return singleton().getServerStatusUpdateInterval();
	}

}
