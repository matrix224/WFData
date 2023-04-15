package wfDataManager.client.cache;

import java.io.File;

import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.model.cache.WarframeItemCacheBase;

public class WarframeItemCache extends WarframeItemCacheBase {

	private static WarframeItemCache singleton;

	private WarframeItemCache() {
	}

	public static WarframeItemCache singleton() {
		if (singleton == null) {
			singleton = new WarframeItemCache();
		} 
		return singleton;
	}

	public void addAllowedItem(String itemKey) {
		addWarframeItem(itemKey, itemKey + "_"); // Append a '_' to the item name so we know in the DB that it is a candidate for being remapped later
	}

	public synchronized boolean updateCacheIfNeeded(long buildId) {
		if (buildId > cacheID) {
			cacheID = buildId;
			init(true);
			return true;
		}
		return false;
	}

	@Override
	protected File getCacheFile(String type) {
		return new File(TAG_WEAPONS.equals(type) ? ClientSettingsUtil.getCacheDir() + "Weapons.json" : ClientSettingsUtil.getCacheDir() + "Warframes.json");
	}

	@Override
	protected File getCustomItemsFile() {
		return new File(ClientSettingsUtil.getCustomItemsConfig());
	}
}
