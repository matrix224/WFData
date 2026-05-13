package wfDataService.service.commands;

import java.util.List;

import jdtools.logging.Log;
import wfDataModel.model.commands.BaseCmd;
import wfDataModel.model.data.WeaponData;
import wfDataService.service.cache.WarframeItemCache;
import wfDataService.service.db.GameDataDao;

/**
 * Command to go through all items from the cache and attempt to map them into the DB.
 * @author MatNova
 *
 */
public class UpdateItemsCmd extends BaseCmd {

	public UpdateItemsCmd() {
		super(1);
	}

	@Override
	public String getDescription() {
		StringBuilder desc = new StringBuilder("Attempts to find any weapon info in the DB that is unmapped, and map it. Optionally, will also refresh the item cache").append("\n");
		desc.append("updateitems - Find all unmapped weapon info entries in the DB and try to map them").append("\n");
		desc.append("updateitems refresh - Same as above, but will refresh the item cache before trying to map anything");
		return desc.toString();
	}

	@Override
	public void runCmd(String... args) {
		if (args != null && args.length > 0 && !args[0].equalsIgnoreCase("refresh")) {
			Log.warn("Invalid arguments supplied, usage: \n " + getDescription());
		} else {
			if (args != null && args.length > 0) {
				WarframeItemCache.singleton().updateCacheIfNeeded(true);
			}

			List<WeaponData> weaponData = WarframeItemCache.singleton().getWeaponData();
			int numProcessed = 0;
			Log.info("Will process " + weaponData.size(), " items for mapping");
			for (WeaponData data : weaponData) {
				if (GameDataDao.updateItem(data.getInternalName(), data.getRealName(), data.getType())) {
					numProcessed++;
				} else {
					Log.info("Could not update " + data.getInternalName() + " to " + data.getRealName() + " and type " + data.getType());
				}
			}

			Log.info("Successfully processed " + numProcessed + " / " + weaponData.size() + " items");

		}

	}

}
