package wfDataManager.client.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdtools.util.MiscUtil;
import wfDataManager.client.cache.WarframeItemCache;
import wfDataManager.client.db.GameDataDao;
import wfDataModel.model.commands.BaseCmd;
import wfDataModel.model.logging.Log;

public class UpdateItemsCmd extends BaseCmd {

	public UpdateItemsCmd() {
		super(0);
	}

	@Override
	public String getDescription() {
		StringBuilder desc = new StringBuilder("Attempts to find any weapon info in the DB that is unmapped, and map it").append("\n");
		desc.append("updateitems - Find all unmapped weapon info entries in the DB and try to map them");
		return desc.toString();
	}

	@Override
	public void runCmd(String... args) {
		List<String> unmappedItems = GameDataDao.findUnmappedItems();
		
		if (MiscUtil.isEmpty(unmappedItems)) {
			Log.info("No unmapped items found");
		} else {
			List<String> mappedItems = new ArrayList<String>();
			Collections.sort(unmappedItems);
			
			for (String item : unmappedItems) {
				String itemName = WarframeItemCache.singleton().getItemName(item);
				if (!MiscUtil.isEmpty(itemName) && !itemName.equals(item)) {
					if (GameDataDao.updateItemName(item, itemName)) {
						Log.info("Mapped " + item + " to " + itemName);
						mappedItems.add(item);
					} else {
						Log.info("Could not update " + item + " to " + itemName);
					}
				}
			}
			
			Log.info("Mapped " + mappedItems.size() + " / " + unmappedItems.size() + " items");
			unmappedItems.removeAll(mappedItems);
			if (!MiscUtil.isEmpty(unmappedItems)) {
				Log.info("Remaining unmapped items: " + unmappedItems);
			}
		}
	}
}
