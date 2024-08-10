package wfDataService.service.compare;

import java.util.Comparator;

import com.google.gson.JsonObject;

import wfDataModel.service.codes.JSONField;
import wfDataModel.service.type.GameMode;

/**
 * Comparator for sorting server data by game mode for display.
 * @author MatNova
 *
 */
public class ServerComparator implements Comparator<JsonObject> {

	@Override
	public int compare(JsonObject o1, JsonObject o2) {
		int compare = 0;
		GameMode gameModeO1 = o1.has(JSONField.SETTINGS) ? GameMode.idToType(o1.getAsJsonObject(JSONField.SETTINGS).get(JSONField.GAME_MODE).getAsInt()) : null;
		GameMode gameModeO2 = o2.has(JSONField.SETTINGS) ? GameMode.idToType(o2.getAsJsonObject(JSONField.SETTINGS).get(JSONField.GAME_MODE).getAsInt()) : null;
		 
		if (gameModeO1 != null && gameModeO2 != null) {
			compare = gameModeO1.getDisplayOrder() - gameModeO2.getDisplayOrder();
		} else if (gameModeO1 != null) {
			compare = -1;
		} else if (gameModeO2 != null) {
			compare = 1;
		}
		
		if (compare == 0) {
			int playersO1 = o1.has(JSONField.PLAYERS) ? o1.getAsJsonArray(JSONField.PLAYERS).size() : 0;
			int playersO2 = o2.has(JSONField.PLAYERS) ? o2.getAsJsonArray(JSONField.PLAYERS).size() : 0;
			compare = playersO2 - playersO1;
		}
		
		return compare;
	}

}
