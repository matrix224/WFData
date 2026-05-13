package wfDataManager.client.data;

import wfDataModel.service.type.EloType;
import wfDataModel.service.type.GameMode;

public class AllocatorGameData {

	private GameMode gameMode;
	private EloType elo;
	private int minCount;
	private boolean isEnabled;
	private String eventTrigger; // Denotes what TargetMode to look for in worldState to consider this as available / active. Used for event modes
	private Integer instanceId;
	private Integer pid;
	private int numRunning = 0;
	
	public GameMode getGameMode() {
		return gameMode;
	}
	
	public void setGameMode(GameMode gameMode) {
		this.gameMode = gameMode;
	}
	
	public EloType getElo() {
		return elo;
	}
	
	public void setElo(EloType elo) {
		this.elo = elo;
	}
	
	public int getMinCount() {
		return minCount;
	}
	
	public void setMinCount(int minCount) {
		this.minCount = minCount;
	}
	
	public boolean isEnabled() {
		return isEnabled;
	}
	
	public void setEnabled(boolean isEnabled) {
		this.isEnabled = isEnabled;
	}
	
	public String getEventTrigger() {
		return eventTrigger;
	}
	
	public void setEventTrigger(String eventTrigger) {
		this.eventTrigger = eventTrigger;
	}
	
	public Integer getInstanceId() {
		return instanceId;
	}
	
	public void setInstanceId(Integer instanceId) {
		this.instanceId = instanceId;
	}
	
	public Integer getPID() {
		return pid;
	}
	
	public void setPID(Integer pid ) {
		this.pid = pid;
	}
	
	public int getNumRunning() {
		return numRunning;
	}
	
	public void setNumRunning(int numRunning) {
		this.numRunning = numRunning;
	}
}
