package wfDataModel.service.data;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import wfDataModel.service.codes.JSONField;

/**
 * Class that defines info about a player's specific ban, such as the specific IP for that ban, whether it's primary or not, etc
 * @author MatNova
 *
 */
public class BanSpec {
	@Expose()
	@SerializedName(JSONField.IP)
	private String ip; // Note this is IP:Port
	@Expose()
	private Long banTime;
	@Expose()
	private boolean isPrimary; // If this is the initial, main ban that lasts for the full ban time duration
	@Expose()
	private boolean isProxy; // If this is a DE proxy / NRS IP
	@Expose (serialize = false, deserialize = false) 
	private String banReason; // The reason they got banned (for automatic bans, this will be loadout name)
	@Expose()
	private int banLoadoutID; // The ID of the loadout that last got them banned. 0 if not applicable / not set
	@Expose()
	private String reportedBy; // For the service, used to denote who actually reported this ban
	@Expose (serialize = false, deserialize = false) 
	private int reportingID; // The ID of the reporter

	public BanSpec(String ipAndPort) {
		this.ip = ipAndPort;
	}

	public void setIP(String ip) {
		this.ip = ip;
	}

	public String getIP() {
		return ip;
	}

	public void setBanTime(Long banTime) {
		this.banTime = banTime;
	}

	public Long getBanTime() {
		return banTime;
	}

	public boolean isExpired(long banDuration) {
		Long expTime = getExpirationTime(banDuration);
		return expTime != null ? System.currentTimeMillis() >= expTime : false;
	}

	public Long getExpirationTime(long banDuration) {
		return banTime != null && !isPermanent() ? banTime + banDuration : null;
	}

	public void setBanReason(String banReason) {
		this.banReason = banReason;
	}

	public String getBanReason() {
		return banReason;
	}

	public void setLoadoutID(int loadoutId) {
		this.banLoadoutID = loadoutId;
	}

	public int getLoadoutID() {
		return banLoadoutID;
	}

	public void setPrimary(boolean isPrimary) {
		this.isPrimary = isPrimary;
	}

	public boolean isPrimary() {
		return isPrimary;
	}
	
	public void setIsProxy(boolean isProxy) {
		this.isProxy = isProxy;
	}

	public boolean isProxy() {
		return isProxy;
	}

	public boolean isPermanent() {
		return ip != null && BanData.PERM_BAN_IP.equalsIgnoreCase(ip);
	}

	public void setReportedBy(String reportedBy) {
		this.reportedBy = reportedBy;
	}

	public String getReportedBy() {
		return reportedBy;
	}

	public void setReportingID(int reportingID) {
		this.reportingID = reportingID;
	}

	public int getReportingID() {
		return reportingID;
	}
}
