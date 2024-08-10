package wfDataService.service.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataModel.model.commands.BaseCmd;
import wfDataModel.model.data.WeaponData;
import wfDataModel.service.type.WeaponType;
import wfDataService.service.cache.WarframeItemCache;
import wfDataService.service.db.GameDataDao;

/**
 * Command to go through all items in the DB and attempt to give them proper names.
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

			SortedMap<String, WeaponType> unmappedItems = GameDataDao.findUnmappedItems();

			if (MiscUtil.isEmpty(unmappedItems)) {
				Log.info("No unmapped items found");
			} else {
				List<String> mappedItems = new ArrayList<String>();

				for (String item : unmappedItems.keySet()) {
					WeaponData wepData = WarframeItemCache.singleton().getItemInfo(item);
					if (wepData != null && !MiscUtil.isEmpty(wepData.getRealName()) && wepData.getType() !=null && (!wepData.getRealName().equals(item) || !wepData.getType().equals(unmappedItems.get(item)))) {
						String itemName = wepData.getRealName();
						WeaponType type = wepData.getType();
						if (GameDataDao.updateItem(item, itemName, type)) {
							Log.info("Mapped " + item + " to " + itemName + " and type " + type);
							mappedItems.add(item);
						} else {
							Log.info("Could not update " + item + " to " + itemName + " and type " + type);
						}
					}
				}

				Log.info("Mapped " + mappedItems.size() + " / " + unmappedItems.size() + " items");
				unmappedItems.keySet().removeAll(mappedItems);
				if (!MiscUtil.isEmpty(unmappedItems)) {
					Log.info("Remaining unmapped items: " + unmappedItems.keySet());
				}
			}
		}
	}

}
