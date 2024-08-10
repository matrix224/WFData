package wfDataModel.model.data;

/**
 * Class for holding basic info about a player for processing.
 * @author MatNova
 *
 */
public class DBPlayerData {
	private String curDBName = null;
	private String curDBUID = null;
	private String curDBAID = null;
	private String pastNames = null;
	private String pastUIDs = null;
	private int curPlatform = 0;
	
	public String getCurDBName() {
		return curDBName;
	}
	
	public void setCurDBName(String curDBName) {
		this.curDBName = curDBName;
	}
	
	public String getCurDBUID() {
		return curDBUID;
	}
	
	public void setCurDBUID(String curDBUID) {
		this.curDBUID = curDBUID;
	}
	
	public String getCurDBAID() {
		return curDBAID;
	}
	
	public void setCurDBAID(String curDBAID) {
		this.curDBAID = curDBAID;
	}
	
	public String getPastNames() {
		return pastNames;
	}
	
	public void setPastNames(String pastNames) {
		this.pastNames = pastNames;
	}
	
	public String getPastUIDs() {
		return pastUIDs;
	}
	
	public void setPastUIDs(String pastUIDs) {
		this.pastUIDs = pastUIDs;
	}
	
	public int getCurPlatform() {
		return curPlatform;
	}
	
	public void setCurPlatform(int curPlatform) {
		this.curPlatform = curPlatform;
	}
}
