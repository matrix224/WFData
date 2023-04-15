package wfDataModel.service.type;

import wfDataModel.model.logging.Log;

/**
 * The game mode IDs
 * @author MatNova
 *
 */
public enum GameMode {

	CTF(406000),
	TA(406009),
	FFA(406010),
	LUNARO(406011),
	CTF_VAR(406012),
	TA_VAR(406013),
	FFA_VAR(406014),
	VT(406015);
	
	private int gameId;
	
	private GameMode(int gameId) {
		this.gameId = gameId;
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
}
