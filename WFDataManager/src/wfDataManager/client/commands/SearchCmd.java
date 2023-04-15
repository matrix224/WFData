package wfDataManager.client.commands;

import java.util.List;

import jdtools.collection.Pair;
import jdtools.util.MiscUtil;
import wfDataManager.client.db.GameDataDao;
import wfDataModel.model.commands.BaseCmd;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.logging.Log;

/**
 * Command for searching for a weapon or player by name
 * @author MatNova
 *
 */
public class SearchCmd extends BaseCmd {

	public SearchCmd() {
		super(2);
	}

	@Override
	public String getDescription() {
		StringBuilder desc = new StringBuilder("Search for a weapon or player by name").append("\n");
		desc.append("search player <name> - Look up a player profile by their name. Use % for wildcard \n");
		desc.append("search weapon <name> - Look up a weapon profile by its name. Use % for wildcard");
		return desc.toString();
	}

	@Override
	public void runCmd(String... args) {
		if (args == null || args.length < 2 || (!args[0].equalsIgnoreCase("player") && !args[0].equalsIgnoreCase("weapon"))) {
			Log.warn("Invalid arguments supplied, usage: \n " + getDescription());
		} else {
			boolean isPlayer = args[0].equalsIgnoreCase("player");
			String query = args[1];
			
			if (isPlayer) {
				List<PlayerData> players = GameDataDao.findPlayersByName(query);
				if (MiscUtil.isEmpty(players)) {
					Log.info("No player matches found");
				} else {
					StringBuilder resp = new StringBuilder("Found " + players.size() + " matches: \n");
					for (PlayerData player : players) {
						resp.append(player.getPlayerName()).append(" - ").append(player.getUID()).append(" - ").append(player.getPlatform()).append("\n");
					}
					Log.info(resp.toString());
				}
			} else {
				List<Pair<String, String>> weapons = GameDataDao.findMatchingItems(query, true);
				if (MiscUtil.isEmpty(weapons)) {
					Log.info("No weapon matches found");
				} else {
					StringBuilder resp = new StringBuilder("Found " + weapons.size() + " matches: \n");
					for (Pair<String, String> wep : weapons) {
						resp.append(wep.getKey()).append(" - ").append(wep.getValue()).append("\n");
					}
					Log.info(resp.toString());
				}
			}
		}
	}

}
