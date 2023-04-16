package wfDataManager.client.parser.http;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import wfDataManager.client.db.RetryDataDao;
import wfDataModel.model.logging.Log;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.codes.ResponseCode;
import wfDataModel.service.data.ResponseData;
import wfDataModel.service.type.GameDataType;

/**
 * Parser for handling response of adding game data (current, historical, or retry) to the service
 * @author MatNova
 *
 */
public class AddDataResponseParser extends BaseResponseParser {

	private String request;
	private GameDataType dataType;
	
	public AddDataResponseParser(String request, GameDataType dataType) {
		this.request = request;
		this.dataType = dataType;
	}

	private static final String LOG_ID = AddDataResponseParser.class.getSimpleName();

	@Override
	protected void parseData(ResponseData responseData) {
		if (ResponseCode.ADD_DATA_ISSUE == responseData.getRC()) {
			JsonObject respObj = JsonParser.parseString(responseData.getResponse()).getAsJsonObject();
			handleError(respObj);
		} else if (ResponseCode.SUCCESS == responseData.getRC()) {			
			if (dataType != null) {
				if (GameDataType.RETRY_GAME_DATA.equals(dataType)) {
					JsonObject respObj = JsonParser.parseString(responseData.getResponse()).getAsJsonObject();
					RetryDataDao.deleteRetryData(respObj.get(JSONField.DATA_ID).getAsInt());
					Log.debug(LOG_ID + ".parseData() : Retried game data sent to service");
				} else {
					Log.debug(LOG_ID + ".parseData() : Game data sent to service");
				}
			} else {
				Log.debug(LOG_ID + ".parseData() : Game data sent to service, unknown data type?");
			}
		} else if (ResponseCode.ERROR == responseData.getRC()) {
			// Unexpected for it to be null here
			if (dataType != null) {
				// TODO: Better way to validate the response is actually JSON
				boolean isJSONResponse = responseData.getResponse().startsWith("{");
				if (!isJSONResponse) {
					Log.warn(LOG_ID + ".parseData() : Service had issue, and response was not JSON -> " + responseData.getResponse());
				}
				JsonObject respObj = JsonParser.parseString(isJSONResponse ? responseData.getResponse() : request).getAsJsonObject();
				handleError(respObj);
			} else {
				Log.warn(LOG_ID + ".parseData() : Service had issue and cannot store failed data. Response=" + responseData.getResponse());
			}
		} else {
			Log.warn(LOG_ID + ".parseData() : Unhandled RC: rc=" + responseData.getRC() + ", response=" + responseData.getResponse());
		}
	}

	private void handleError(JsonObject respObj) {
		if (GameDataType.RETRY_GAME_DATA.equals(dataType)) {
			Log.warn(LOG_ID + ".handleError() : Retried game data failed to be processed in service, will try again later");
		} else {
			Log.warn(LOG_ID + ".handleError() : Game data failed to be processed in service, will store and try it again later");
			respObj.addProperty(JSONField.ORIG_TYPE, dataType.name());
			RetryDataDao.addRetryData(respObj.toString());
		}
	}
}
