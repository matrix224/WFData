package wfDataManager.client.task;

import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jdtools.util.MiscUtil;
import wfDataManager.client.db.RetryDataDao;
import wfDataManager.client.util.RequestUtil;
import wfDataModel.model.logging.Log;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.type.GameDataType;

/**
 * Task for retrying any previously failed game data requests
 * @author MatNova
 *
 */
public class RetryDataTask implements Runnable {

	private static final String LOG_ID = RetryDataTask.class.getSimpleName();
	public static final int GAME_DATA_RETRY_COUNT = 10;
	
	@Override
	public void run() {
		try {
			Map<Integer, String> historicalData = RetryDataDao.getRetryData(GAME_DATA_RETRY_COUNT);
			
			if (!MiscUtil.isEmpty(historicalData)) {
				Log.info(LOG_ID + "() : Will retry " + historicalData.size() + " failed game data requests");
				for (int dataID : historicalData.keySet()) {
					String data = historicalData.get(dataID);
					JsonObject dataObj = JsonParser.parseString(data).getAsJsonObject();
					dataObj.addProperty(JSONField.DATA_ID, dataID);
					RequestUtil.sendAddDataRequest(dataObj, GameDataType.RETRY_GAME_DATA);
				}
			}
		} catch (Throwable t) {
			Log.error(LOG_ID + "() : Exception while processing -> ", t);
		}
	}

}
