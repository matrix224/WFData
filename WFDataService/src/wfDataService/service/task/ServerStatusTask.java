package wfDataService.service.task;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import jdtools.logging.Log;
import wfDataModel.service.codes.JSONField;
import wfDataService.service.cache.ServerClientCache;
import wfDataService.service.data.ServerClientData;
import wfDataService.service.db.ProcessorVarDao;
import wfDataService.service.db.ServerClientDao;

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
						regionData.addProperty(JSONField.OUTDATED, false);
						serverOutput.add(regionCode, regionData);
					}
					JsonObject serverStatusData = serverClient.getServerStatusData();
					if (serverStatusData.getAsJsonArray(JSONField.DATA).size() > 0) {
						regionData.addProperty(JSONField.MAX, regionData.get(JSONField.MAX).getAsInt() + serverStatusData.get(JSONField.MAX).getAsInt());
						regionData.addProperty(JSONField.TOTAL, regionData.get(JSONField.TOTAL).getAsInt() + serverStatusData.get(JSONField.TOTAL).getAsInt());
						regionData.addProperty(JSONField.OLDEST, Math.min(regionData.get(JSONField.OLDEST).getAsLong(), serverStatusData.get(JSONField.OLDEST).getAsLong()));
						regionData.addProperty(JSONField.OUTDATED, regionData.get(JSONField.OUTDATED).getAsBoolean() || serverStatusData.get(JSONField.OUTDATED).getAsBoolean());
						regionArr.add(serverStatusData);
						
						// If this server has data, update basic info in the DB
						ServerClientDao.updateClientData(serverClient);
					}

				}
			}

			// Update the DB values of client data and the formatted server status data
			ServerClientCache.singleton().updateClientDataDB();
			ProcessorVarDao.updateVar(ProcessorVarDao.VAR_SERVER_STATUS, serverOutput.toString());
		} catch (Throwable t) {
			Log.error(ServerStatusTask.class.getSimpleName() + "() : Exception -> ", t);
		}
	}

}
