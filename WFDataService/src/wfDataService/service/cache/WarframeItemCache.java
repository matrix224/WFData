package wfDataService.service.cache;

import java.io.File;

import wfDataModel.model.cache.WarframeItemCacheBase;
import wfDataService.service.util.ServiceSettingsUtil;

/**
 * Item cache implementation to support proper naming of items in DB.
 * @author MatNova
 *
 */
public class WarframeItemCache extends WarframeItemCacheBase {

	private static final long REFRESH_INTERVAL = 259200000; // 3 days in milliseconds
	private static WarframeItemCache singleton;

	private WarframeItemCache() {
	}

	public static WarframeItemCache singleton() {
		if (singleton == null) {
			singleton = new WarframeItemCache();
			singleton.cacheID = System.currentTimeMillis();
		} else {
			singleton.updateCacheIfNeeded(false);
		}
		return singleton;
	}

	public synchronized boolean updateCacheIfNeeded(boolean forceRefresh) {
		if (forceRefresh || System.currentTimeMillis() - cacheID >= REFRESH_INTERVAL) {
			cacheID = System.currentTimeMillis();
			init(true);
			return true;
		}
		return false;
	}
	
	@Override
	protected File getCacheFile(String type) {
		return new File(TAG_WEAPONS.equals(type) ? ServiceSettingsUtil.getCacheDir() + "Weapons.json" : ServiceSettingsUtil.getCacheDir() + "Warframes.json");
	}

	@Override
	protected File getCustomItemsFile() {
		return new File(ServiceSettingsUtil.getCustomItemsConfig());
	}
}
