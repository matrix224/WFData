package wfDataService.service.util.data;

import java.io.File;

import jdtools.annotation.SettingData;
import jdtools.settings.SettingsCfg;

public class ServiceSettingsCfg extends SettingsCfg {

	@SettingData(cfgName="servicePort", wrapper=Integer.class, required=true)
	private int servicePort = 8485;
	@SettingData(cfgName="autoAllowRegistration", wrapper=Boolean.class)
	private boolean autoAllowRegistration = false;
	@SettingData(cfgName="serverStatusUpdateInterval", wrapper=Integer.class, minValue=10)
	private int serverStatusUpdateInterval = 120;
	@SettingData(cfgName="serverStatusExpiration", wrapper=Integer.class, minValue=120)
	private int serverStatusExpiration = 86400;
	@SettingData(cfgName="serverOutdatedTime", wrapper=Integer.class, minValue=120)
	private int serverOutdatedTime = 600;
	@SettingData(cfgName="activityProcessInterval", wrapper=Integer.class, minValue=1800)
	private int activityProcessInterval = 3600;
	
	@SettingData(cfgName="trackServerStatus", wrapper=Boolean.class)
	protected boolean trackServerStatus = false;
	@SettingData(cfgName="customItemsConfig")
	private String customItemsConfig = getUserDir() + File.separator + "cfg" + File.separator + "customItems.json";
	@SettingData(cfgName="cacheDir")
	private String cacheDir = getUserDir() + "cache" + File.separator;
	
	public ServiceSettingsCfg() {
	}
	
	public int getServicePort() {
		return servicePort;
	}
	
	public boolean autoAllowRegistration() {
		return autoAllowRegistration;
	}
	
	public int getServerStatusUpdateInterval() {
		return serverStatusUpdateInterval;
	}
	
	public int getServerStatusExpiration() {
		return serverStatusExpiration;
	}
	
	public int getServerOutdatedTime() {
		return serverOutdatedTime;
	}
	
	public int getActivityProcessInterval() {
		return activityProcessInterval;
	}
	
	public boolean trackServerStatus() {
		return trackServerStatus;
	}

	public String getCustomItemsConfig() {
		return customItemsConfig;
	}
	
	public String getCacheDir() {
		return cacheDir;
	}
}
