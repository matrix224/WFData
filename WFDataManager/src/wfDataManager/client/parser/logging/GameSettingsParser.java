package wfDataManager.client.parser.logging;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import wfDataManager.client.type.ParseResultType;
import wfDataModel.model.data.ServerData;
import wfDataModel.service.type.GameMode;

/**
 * Parser for getting and setting the game settings field
 * @author MatNova
 *
 */
public class GameSettingsParser extends BaseLogParser {

	private Matcher GAME_SETTINGS_PATTERN;
	//private Matcher GAME_SETTINGS_PATTERN_2;
	private Matcher GAME_SETTINGS_PATTERN_OLD;

	@Override
	protected List<Matcher> initMatchers() {
		GAME_SETTINGS_PATTERN = Pattern.compile(".*UpdateSessionCallback returned with body=(.*)$").matcher("");
		//GAME_SETTINGS_PATTERN_2 = Pattern.compile(".*-settings:Sp([A-Z]+)$").matcher("");
		GAME_SETTINGS_PATTERN_OLD = Pattern.compile(".*[Ss]ettings:\\s*(\\{.*\\})$").matcher("");

		return Arrays.asList(GAME_SETTINGS_PATTERN, /*GAME_SETTINGS_PATTERN_2,*/ GAME_SETTINGS_PATTERN_OLD);
	}
	
	@Override
	public ParseResultType parse(ServerData serverData, long offset, long lastLogTime) {
		if (serverData.getGameSettings() == null) {
			if (GAME_SETTINGS_PATTERN.matches()) {
				serverData.setGameSettings(GAME_SETTINGS_PATTERN.group(1));
				GameMode mode = GameMode.idToType(serverData.getGameModeId());
				int maxPlayers = GameMode.LUNARO.equals(mode) ? 6 : 8;
				serverData.getGameSettings().addProperty("maxPlayers", maxPlayers);
			} /*else if (GAME_SETTINGS_PATTERN_2.matches()) {
				String gameModeName = GAME_SETTINGS_PATTERN_2.group(1);
				GameMode mode = null;
				
				if (gameModeName.startsWith("DM")) {
					mode = GameMode.FFA;
				} else if (gameModeName.startsWith("TDM")) {
					mode = GameMode.TA;
				} else if (gameModeName.startsWith("CTF")) {
					mode = GameMode.CTF;
				} else if (gameModeName.startsWith("Lunaro")) {
					mode = GameMode.LUNARO;
				} else if (gameModeName.startsWith("TDMAlt")) {
					mode = GameMode.TA_VAR;
				} else if (gameModeName.startsWith("DMAlt")) {
					mode = GameMode.FFA_VAR;
				} else if (gameModeName.startsWith("CTFAlt")) {
					mode = GameMode.CTF_VAR;
				} else if (gameModeName.startsWith("VT")) {
					mode = GameMode.VT;
				}
				int maxPlayers = GameMode.LUNARO.equals(mode) ? 6 : 8;
				EloType elo = gameModeName.endsWith("RC") ? EloType.RC : EloType.NON_RC;
				JsonObject settingsObj = new JsonObject();
				settingsObj.addProperty("gameModeId", mode.getId());
				settingsObj.addProperty("eloRating", elo.getCode());
				settingsObj.addProperty("maxPlayers", maxPlayers);
				serverData.setGameSettings(settingsObj);
				
			} */else if (GAME_SETTINGS_PATTERN_OLD.matches()) {
				serverData.setGameSettings(GAME_SETTINGS_PATTERN_OLD.group(1));
			}
		}
		return ParseResultType.OK;
	}
}
