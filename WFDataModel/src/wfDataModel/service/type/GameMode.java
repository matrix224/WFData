package wfDataModel.service.type;

import jdtools.logging.Log;

/**
 * The game mode IDs
 * @author MatNova
 *
 */
public enum GameMode {

	CTF(406000, 3),
	TA(406009, 2),
	FFA(406010, 1),
	LUNARO(406011, 4),
	CTF_VAR(406012, 8),
	TA_VAR(406013, 5),
	FFA_VAR(406014, 6),
	VT(406015, 7),
	TOTAL(999999); // Made up value for data aggregated across all game modes
	
	private int gameId;
	private int displayOrder;
	
	private GameMode(int gameId) {
		this(gameId, Integer.MAX_VALUE);
	}
	
	private GameMode(int gameId, int displayOrder) {
		this.gameId = gameId;
		this.displayOrder = displayOrder;
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
	
	public int getId() {
		return gameId;
	}
	
	public int getDisplayOrder() {
		return displayOrder;
	}
}
