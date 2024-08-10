package wfDataModel.service.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import wfDataModel.service.type.EloType;
import wfDataModel.service.type.GameMode;

/**
 * Defines information about a loadout config that should be banned
 * @author MatNova
 *
 */
public class LoadoutData {

	private List<LoadoutItemData> loadout = new ArrayList<LoadoutItemData>();
	private String loadoutName;
	private EloType elo;
	private Set<GameMode> gameModes = new HashSet<GameMode>();
	private Integer loadoutID = null;

	public LoadoutData(String loadoutName) {
		this.loadoutName = loadoutName;
	}

	public String getLoadoutName() {
		return loadoutName;
	}

	public void addLoadoutItem(LoadoutItemData item) {
		loadout.add(item);
	}

	public List<LoadoutItemData> getLoadoutItems() {
		return loadout;
	}

	public void addGameMode(GameMode gameMode) {
		gameModes.add(gameMode);
	}
	
	public boolean isForGameMode(GameMode gameMode) {
		return gameModes.isEmpty() || gameModes.contains(gameMode);
	}
	
	public Set<GameMode> getGameModes() {
		return gameModes;
	}
	
	public void setElo(EloType elo) {
		this.elo = elo;
	}
	
	public boolean isForElo(EloType elo) {
		return this.elo == null || this.elo.equals(elo);
	}
	
	public EloType getElo() {
		return elo;
	}
	
	public int getLoadoutID() {
		if (loadoutID == null) {
			Set<String> itemNames = new TreeSet<String>();
			String itemStr = "";
			for (LoadoutItemData item : loadout) {
				itemNames.add(item.getItemName().toLowerCase());
			}
			for (String item : itemNames) {
				itemStr += item;
			}
			loadoutID = itemStr.hashCode();
		}
		return loadoutID;
	}
}
