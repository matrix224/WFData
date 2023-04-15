package wfDataService.service.util.data;

import wfDataModel.model.annotation.SettingData;
import wfDataModel.model.util.data.SettingsCfg;

public class ServiceSettingsCfg extends SettingsCfg {

	@SettingData(cfgName="servicePort", wrapper=Integer.class, required=true)
	private int servicePort = 8485;
	@SettingData(cfgName="autoAllowRegistration", wrapper=Boolean.class)
	private boolean autoAllowRegistration = false;
	@SettingData(cfgName="serverStatusUpdateInterval", wrapper=Integer.class, minValue=10)
	private int serverStatusUpdateInterval = 120;
	
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
}
