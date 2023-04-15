package wfDataService.service.task;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import wfDataModel.model.logging.Log;
import wfDataModel.service.codes.JSONField;
import wfDataService.service.cache.ServerClientCache;
import wfDataService.service.data.ServerClientData;
import wfDataService.service.util.ServiceSettingsUtil;

/**
 * Task to process and print statuses of all current registered client servers.
 * @author MatNova
 *
 */
public class ServerStatusTask implements Runnable {

	@Override
	public void run() {
		try {
			JsonObject serverOutput = new JsonObject();

			serverOutput.addProperty(JSONField.TIMESTAMP, System.currentTimeMillis());
			for (ServerClientData serverClient : ServerClientCache.singleton().getClientData()) {
				if (serverClient.isValidated()) {
					String regionCode = String.valueOf(serverClient.getRegion().getCode());
					JsonObject regionData = serverOutput.has(regionCode) ? serverOutput.getAsJsonObject(regionCode) : null;
					JsonArray regionArr = regionData != null ? regionData.getAsJsonArray(JSONField.DATA) : null;
					if (regionData == null) {
						regionData = new JsonObject();
						regionArr = new JsonArray();
						regionData.add(JSONField.DATA, regionArr);
						regionData.addProperty(JSONField.MAX, 0);
						regionData.addProperty(JSONField.TOTAL, 0);
						regionData.addProperty(JSONField.OLDEST, Long.MAX_VALUE);
						serverOutput.add(regionCode, regionData);
					}
					JsonObject serverStatusData = serverClient.getServerStatusData();
					regionData.addProperty(JSONField.MAX, regionData.get(JSONField.MAX).getAsInt() + serverStatusData.get(JSONField.MAX).getAsInt());
					regionData.addProperty(JSONField.TOTAL, regionData.get(JSONField.TOTAL).getAsInt() + serverStatusData.get(JSONField.TOTAL).getAsInt());
					regionData.addProperty(JSONField.OLDEST, Math.min(regionData.get(JSONField.OLDEST).getAsLong(), serverStatusData.get(JSONField.OLDEST).getAsLong()));
					regionArr.add(serverStatusData);
				}
			}

			try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(ServiceSettingsUtil.getServerStatusFile()), StandardCharsets.UTF_8))) {
				writer.write(serverOutput.toString());
			}
		} catch (Throwable t) {
			Log.error(ServerStatusTask.class.getSimpleName() + "() : Exception -> ", t);
		}
	}

}
