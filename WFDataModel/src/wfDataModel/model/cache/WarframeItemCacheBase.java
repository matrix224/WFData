package wfDataModel.model.cache;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jdtools.http.HTTPRequest;
import jdtools.http.HTTPRequestListener;
import jdtools.http.HTTPResponseData;
import jdtools.logging.Log;
import jdtools.util.IOUtil;
import jdtools.util.MiscUtil;
import lzma.sdk.lzma.Decoder;
import lzma.streams.LzmaInputStream;
import wfDataModel.model.data.WeaponData;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.type.WeaponType;

/**
 * Base implementation for WF item caching classes
 * @author MatNova
 *
 */
public abstract class WarframeItemCacheBase implements HTTPRequestListener {

	private static final String LOG_ID = WarframeItemCacheBase.class.getSimpleName();
	private static final String INDEX_URL = "https://content.warframe.com/PublicExport/index_en.txt.lzma";
	private static final String BASE_MANIFEST_URL = "https://content.warframe.com/PublicExport/Manifest/";
	protected static final String TAG_WEAPONS = "Weapons";
	protected static final String TAG_WARFRAMES = "Warframes";

	private Map<String, WeaponData> customItems = new HashMap<String, WeaponData>(2); // Custom item key -> custom item definitions. Will be merged into warframeItems, but stored separately to survive through cache refreshes
	private Map<String, WeaponData> warframeItems = new HashMap<String, WeaponData>(); // Item internal name, info about weapon
	private Map<String, String> manifests = new HashMap<String, String>(); // key = tag (e.g. weapons), value = manifest file name
	protected boolean hasInit = false;
	protected long cacheID = -1;

	protected void init(boolean isRefresh) {
		long startMs = System.currentTimeMillis();

		buildData(TAG_WEAPONS, isRefresh);
		buildData(TAG_WARFRAMES, isRefresh);

		// Only load custom item config on the first init
		// No need to if cache loads then refreshes, i.e. due to a game update
		if (!hasInit) {
			File customItemConfig = getCustomItemsFile();
			if (customItemConfig != null && customItemConfig.exists()) {
				try {
					String cfgData = new String(Files.readAllBytes(customItemConfig.toPath()));
					JsonObject cfgObj = JsonParser.parseString(cfgData).getAsJsonObject();
					for (String key : cfgObj.keySet()) {
						JsonObject wepCfg = cfgObj.getAsJsonObject(key);
						String itemName = wepCfg.get(JSONField.ITEM_NAME).getAsString();
						WeaponType type = WeaponType.valueOf(wepCfg.get(JSONField.TYPE).getAsString());
						addCustomItem(key, itemName, type);
					}
				} catch (Exception e) {
					Log.error(LOG_ID + ".init() : Error parsing file for custom item config -> ", e);
				}
			}
		}

		hasInit = true;

		// If we already have an item for this key in our warframeItems cache, don't override it
		for (String itemKey : getCustomItems().keySet()) {
			if (!hasItemName(itemKey)) {
				WeaponData custData =  getCustomItem(itemKey);
				addWarframeItem(itemKey, custData.getRealName(), custData.getType());
			}
		}

		Log.info(LOG_ID + ".init() : Cache built in " + ((System.currentTimeMillis() - startMs)/1000.0) + " seconds");

	}

	protected void buildData(String type, boolean isRefresh) {
		File cacheFile = getCacheFile(type);
		if (isRefresh || (cacheFile == null || !cacheFile.exists())) {
			if (cacheFile != null && !cacheFile.getParentFile().exists() && !cacheFile.getParentFile().mkdirs()) {
				Log.warn(LOG_ID + ".buildCache() : Could not make dirs for " + type + " cache file -> " + cacheFile);
			} else {
				// If no manifests present or this is for a refresh, fetch them first
				if (isRefresh || MiscUtil.isEmpty(manifests)) {
					fetchManifests();
				}
				if (!MiscUtil.isEmpty(manifests)) {
					HTTPRequest dataRequest = new HTTPRequest(BASE_MANIFEST_URL + manifests.get(type), null, null, false, type, this);
					dataRequest.fetch();
				} else {
					Log.warn(LOG_ID + ".buildCache() : Could not get manifest files, will not make / parse " + type + " cache");
				}
			}
		} else {
			try {
				String cacheData = new String(Files.readAllBytes(cacheFile.toPath()));
				JsonObject cacheObj = JsonParser.parseString(cacheData).getAsJsonObject();
				if (cacheObj.has(JSONField.DATA_ID) && cacheID == -1) {
					cacheID = cacheObj.get(JSONField.DATA_ID).getAsLong();
				}
				parseItems(JsonParser.parseString(cacheData).getAsJsonObject(), type);
			} catch (Exception e) {
				Log.error(LOG_ID + ".buildCache() : Error parsing file for " + type + " cache -> ", e);
			}
		}
	}

	public String getItemName(String itemKey) {
		if (!hasInit) {
			init(false);
		}
		WeaponData data = warframeItems.get(itemKey);
		return data == null ? null : data.getRealName();
	}
	
	public WeaponData getItemInfo(String itemKey) {
		if (!hasInit) {
			init(false);
		}
		return warframeItems.get(itemKey);
	}

	public boolean hasItemName(String itemKey) {
		if (!hasInit) {
			init(false);
		}
		return warframeItems.get(itemKey) != null && !MiscUtil.isEmpty(warframeItems.get(itemKey).getRealName());
	}

	protected void addWarframeItem(String itemKey, String itemName, WeaponType type) {
		WeaponData wepData = new WeaponData(itemKey);
		wepData.setRealName(itemName);
		wepData.setType(type);
		warframeItems.put(itemKey, wepData);
	}

	protected void addCustomItem(String itemKey, String itemName, WeaponType type) {
		WeaponData wepData = new WeaponData(itemKey);
		wepData.setRealName(itemName);
		wepData.setType(type);
		customItems.put(itemKey, wepData);
	}

	protected Map<String, WeaponData> getCustomItems() {
		return customItems;
	}

	protected WeaponData getCustomItem(String itemKey) {
		return customItems.get(itemKey);
	}

	private void parseItems(JsonObject baseObj, String type) {
		JsonArray dataArr = TAG_WEAPONS.equals(type) ? baseObj.getAsJsonArray("ExportWeapons") : baseObj.getAsJsonArray("ExportWarframes");

		for (JsonElement itemElement : dataArr) {
			JsonObject item = itemElement.getAsJsonObject();
			String itemKey = item.get("uniqueName").getAsString();
			String itemName = item.get("name").getAsString();
			WeaponType itemType = WeaponType.codeToType(item.get("productCategory").getAsString());
			WeaponData wepData = new WeaponData(itemKey.substring(itemKey.lastIndexOf("/") +1 ));
			wepData.setRealName(itemName);
			wepData.setType(itemType);
			warframeItems.put(wepData.getInternalName(), wepData);
		}
	}

	private void fetchManifests() {
		try (LzmaInputStream stream = new LzmaInputStream(new BufferedInputStream(new URL(INDEX_URL).openStream()),  new Decoder())) {
			byte[] data = IOUtil.readAllBytes(stream);
			String[] mfsts = new String(data).split("\n");
			for (String manifest : mfsts) {
				if (manifest.contains("ExportWarframes")) {
					manifests.put(TAG_WARFRAMES, manifest);
				} else if (manifest.contains("ExportWeapons")) {
					manifests.put(TAG_WEAPONS, manifest);
				}	
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".fetchManifests() : Exception trying to fetch manifests -> ", e);
		}
	}

	@Override
	public void onRequestCompleted(HTTPResponseData result) {
		Object tag = result.getTagData();

		if (result.hasResponse() && result.isHTTPSuccess() && result.isResultSuccess()) {
			String response = result.getResponse();
			try {
				JsonObject respObj = JsonParser.parseString(response).getAsJsonObject();
				parseItems(respObj, (String)tag);
				File cacheFile = getCacheFile((String)tag);
				if (cacheFile != null) {
					respObj.addProperty(JSONField.DATA_ID, cacheID);
					try (BufferedWriter writer = new BufferedWriter(new FileWriter(cacheFile))) {
						writer.write(respObj.toString());
					}
				}
			} catch (Exception e) {
				Log.error(LOG_ID + ".onRequestCompleted() : Exception trying to parse response for tag " + tag + " -> ", e);
			}
		} else {
			Log.warn(LOG_ID + ".onRequestCompleted() : Error with response for tag " + tag + ", http=" + result.getHTTPResponseCode() + ", rc=" + result.getResultCode() + ", resp=" + result.getResponse());
		}
	}

	protected abstract File getCacheFile(String tag);
	protected abstract File getCustomItemsFile();

}
