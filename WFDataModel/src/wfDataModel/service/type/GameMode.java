package wfDataModel.service.type;

import jdtools.logging.Log;

/**
 * The game mode IDs
 * @author MatNova
 *
 */
public enum GameMode {

	CTF(406000, 3, "CTF"),
	TA(406009, 2, "TDM"),
	FFA(406010, 1, "DM"),
	LUNARO(406011, 4, "Lunaro"),
	CTF_VAR(406012, 8, "CTFAlt"),
	TA_VAR(406013, 5, "TDMAlt"),
	FFA_VAR(406014, 6, "DMAlt"),
	VT(406015, 7, "VT");
	
	private final int gameId;
	private final int displayOrder;
	private final String allocatorName;
	
	private GameMode(int gameId, int displayOrder, String allocatorName) {
		this.gameId = gameId;
		this.displayOrder = displayOrder;
		this.allocatorName = allocatorName;
	}
	
	public static GameMode idToType(int id) {
		GameMode type = null;
		
		for (GameMode gameType : values()) {
			if (gameType.gameId == id) {
				type = gameType;
				break;
			}
		}
		
		if (type == null) {
			Log.warn("GameMode.idToType() : Could not determine type for id: " + id);
		}
		
		return type;
	}
	
	public String getAllocatorName(EloType elo) {
		return "Sp" + allocatorName + (EloType.NON_RC.equals(elo) ? "" : elo.name());
	}
	
	public int getId() {
		return gameId;
	}
	
	public int getDisplayOrder() {
		return displayOrder;
	}
}
