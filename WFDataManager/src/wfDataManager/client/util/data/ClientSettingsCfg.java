package wfDataManager.client.util.data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import jdtools.annotation.SettingData;
import jdtools.logging.Log;
import jdtools.settings.SettingsCfg;
import jdtools.util.MiscUtil;
import wfDataManager.client.type.ProcessModeType;
import wfDataManager.client.util.ClientSettingsUtil;

/**
 * Client setting config manager class, with use facilitated by the {@link ClientSettingsUtil} class
 * @author MatNova
 *
 */
public class ClientSettingsCfg extends SettingsCfg {

	private static final String LOG_ID = ClientSettingsCfg.class.getSimpleName();
	private static final String ID_FILE = "wfData.id";

	@SettingData(cfgName="serverLogsDir", required=true)
	private String serverLogsDir;
	@SettingData(cfgName="historicalLogsDir")
	private String historicalLogsDir;
	@SettingData(cfgName="serverLogPattern", required=true)
	private String serverLogPattern;
	@SettingData(cfgName="historicalLogPattern")
	private String historicalLogPattern;
	@SettingData(cfgName="cleanServerProfiles", wrapper=Boolean.class)
	private boolean cleanServerProfiles = false;
	@SettingData(cfgName="cacheDir")
	private String cacheDir = getUserDir() + "cache" + File.separator;
	
	@SettingData(cfgName="displayName")
	private String displayName;
	@SettingData(cfgName="host")
	private String host;
	@SettingData(cfgName="region")
	private String region;
	@SettingData(cfgName="serviceHost")
	private String serviceHost;
	@SettingData(cfgName="servicePort", wrapper=Integer.class)
	private int servicePort;
	@SettingData(cfgName="serviceTimeout", wrapper=Integer.class, minValue=10.0, maxValue=60.0)
	private int serviceTimeout = 10;
	@SettingData(cfgName="enableDataSharing", wrapper=Boolean.class)
	private boolean enableDataSharing;
	@SettingData(cfgName="enableAlerts", wrapper=Boolean.class)
	private boolean enableAlerts;

	@SettingData(cfgName="printServerData", wrapper=Boolean.class)
	protected boolean printServerOutput = false;
	@SettingData(cfgName="serverDataFile")
	private String serverOutputFile = getUserDir() + File.separator + "data" + File.separator + "ServerData.json";
	@SettingData(cfgName="customItemsConfig")
	private String customItemsConfig = getUserDir() + File.separator + "cfg" + File.separator + "customItems.json";
	
	@SettingData(cfgName="persist", wrapper=Boolean.class)
	private boolean persist;
	
	@SettingData(cfgName="enableBanning", wrapper=Boolean.class)
	private boolean enableBanning = false;
	@SettingData(cfgName="enableBanSharing", wrapper=Boolean.class)
	private boolean enableBanSharing = false;
	@SettingData(cfgName="enableBanLoadoutSharing", wrapper=Boolean.class)
	private boolean enableBanLoadoutSharing = false;
	@SettingData(cfgName="enforceSharedBanLoadouts", wrapper=Boolean.class)
	private boolean enforceLoadoutBans = true;
	@SettingData(cfgName="banTime", wrapper=Integer.class)
	private int banTime = 600;
	@SettingData(cfgName="secondaryBanTime", wrapper=Integer.class)
	private int secondaryBanTime = 10;
	@SettingData(cfgName="bannedItemsConfig")
	private String bannedItemsConfig = getUserDir() + File.separator + "cfg" + File.separator + "bannedItems.json";
	@SettingData(cfgName="pollInterval", wrapper=Integer.class, minValue=10.0)
	private int pollInterval = 30;
	@SettingData(cfgName="jamThreshold", wrapper=Integer.class)
	private int jamThreshold = 5;
	@SettingData(cfgName="logCheckInterval", wrapper=Integer.class)
	private int logCheckInterval = 4;
	@SettingData(cfgName="banCheckInterval", wrapper=Integer.class, minValue=1.0)
	private int banCheckInterval = 15;
	@SettingData(cfgName="banFetchInterval", wrapper=Integer.class, minValue=15.0)
	private int banFetchInterval = 15;
	
	private ProcessModeType processMode;
	private int serverID;

	public ClientSettingsCfg() {
	}
	
	public void loadID() {
		File idFile = getIDFile();

		if (idFile.exists()) {
			try (BufferedReader reader = new BufferedReader(new FileReader(idFile))) {
				String line = reader.readLine();
				if (!MiscUtil.isEmpty(line)) {
					serverID = Integer.parseInt(line);
				} else {
					Log.warn(LOG_ID + ".loadID() : No data was read from ID file. Is it corrupted / empty?");
				}
			} catch (FileNotFoundException e) {
				Log.error(LOG_ID + ".loadID() : ID file was not found?");
			} catch (IOException e) {
				Log.error(LOG_ID + ".loadID() : Error reading ID file -> " + e.getLocalizedMessage());
			} catch (NumberFormatException e) {
				Log.error(LOG_ID + ".loadID() : ID file contains invalid (non-numeric) data");
			}
		}
	}

	public boolean setServerID(int sid) {
		File idFile = getIDFile();
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(idFile))) {
			writer.write(String.valueOf(sid));
			serverID = sid;
			return true;
		} catch (Exception e) {
			Log.error(LOG_ID + ".parseData() : Error saving server ID -> ", e);
		}
		return false;
	}
	
	private File getIDFile() {
		return new File(getUserDir() + File.separator + "cfg" + File.separator + ID_FILE);
	}
	
	public int getServerID() {
		return serverID;
	}

	public void setProcessMode(ProcessModeType processMode) {
		this.processMode = processMode;
		if (ProcessModeType.HISTORICAL.equals(processMode) || ProcessModeType.TEST.equals(processMode)) {
			printServerOutput = false;
			enableBanning = false;
			enableBanSharing = false;
			enableBanLoadoutSharing = false;
			enableAlerts = false;
			cleanServerProfiles = false;
			pollInterval = ClientSettingsUtil.HISTORICAL_POLLING_INTERVAL;
			if (ProcessModeType.TEST.equals(processMode)) {
				Log.info(LOG_ID + ".setProcessMode() : Process is set to test mode, will disable the following for this run: printServerData, enableDataSharing, persist, enableBanning, enableBanSharing, enableBanLoadoutSharing, enableAlerts, cleanServerProfiles. Will also override poll interval to " + ClientSettingsUtil.HISTORICAL_POLLING_INTERVAL + "s");
				persist = false;
				enableDataSharing = false;
			} else {
				Log.info(LOG_ID + ".setProcessMode() : Process is set to historical mode, will disable the following for this run: printServerData, enableBanning, enableBanSharing, enableBanLoadoutSharing, enableAlerts, cleanServerProfiles. Will also override poll interval to " + ClientSettingsUtil.HISTORICAL_POLLING_INTERVAL + "s");
			}
		}
	}
	
	public ProcessModeType getProcessMode() {
		return processMode;
	}
	
	public String getServerLogsDir() {
		return serverLogsDir;
	}
	
	public String getHistoricalLogsDir() {
		return historicalLogsDir;
	}

	public String getServerLogPattern() {
		return serverLogPattern;
	}
	
	public String getHistoricalLogPattern() {
		return historicalLogPattern;
	}
	
	public boolean cleanServerProfiles() {
		return cleanServerProfiles;
	}
	
	public String getCacheDir() {
		return cacheDir;
	}
	
	public String getDisplayName() {
		return displayName;
	}

	public String getHost() {
		return host;
	}
	
	public String getRegion() {
		return region;
	}
	
	public String getServiceHost() {
		return serviceHost;
	}

	public int getServicePort() {
		return servicePort;
	}
	
	public int getServiceTimeout() {
		return serviceTimeout;
	}

	public boolean enableDataSharing() {
		return enableDataSharing;
	}
	
	public boolean enableAlerts() {
		return enableAlerts;
	}

	public boolean printServerData() {
		return printServerOutput;
	}

	public String getServerDataFile() {
		return serverOutputFile;
	}

	public String getCustomItemsConfig() {
		return customItemsConfig;
	}
	
	public boolean persist() {
		return persist;
	}

	public boolean enableBanning() {
		return enableBanning;
	}

	public boolean enableBanSharing() {
		return enableBanSharing;
	}

	public boolean enableBanLoadoutSharing() {
		return enableBanLoadoutSharing;
	}
	
	public boolean enforceLoadoutBans() {
		return enforceLoadoutBans;
	}
	
	public int getPrimaryBanTime() {
		return banTime;
	}

	public int getSecondaryBanTime() {
		return secondaryBanTime;
	}

	public String getBannedItemsConfig() {
		return bannedItemsConfig;
	}

	public int getPollInterval() {
		return pollInterval;
	}

	public int getJamThreshold() {
		return jamThreshold;
	}
	
	public int getLogCheckInterval() {
		return logCheckInterval;
	}
	
	public int getBanCheckInterval() {
		return banCheckInterval;
	}
	
	public int getBanFetchInterval() {
		return banFetchInterval;
	}
}
