package wfDataService.service.handler;

import java.net.HttpURLConnection;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;

import wfDataModel.model.logging.Log;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.codes.ResponseCode;
import wfDataModel.service.data.BanData;
import wfDataModel.service.data.ResponseData;
import wfDataService.service.cache.BanDataCache;
import wfDataService.service.data.ServerClientData;

/**
 * Handler for receiving and processing a ban addition from a client.
 * @author MatNova
 *
 */
public class AddBanHandler extends BaseHandler {

	@Override
	protected ResponseData getResponse(HttpExchange exchange, ServerClientData clientData, JsonObject inputObj) {
		String response = null;
		int rc = ResponseCode.SUCCESS;
		int httpCode = HttpURLConnection.HTTP_OK;

		if (inputObj.has(JSONField.BANS)) {
			JsonObject bansObj = inputObj.getAsJsonObject(JSONField.BANS);
			BanData banData =  new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(bansObj, BanData.class);
			if (banData == null || !BanDataCache.singleton().addBanData(clientData, banData)) {
				rc = ResponseCode.ERROR;
				response = "Ban could not be added properly";
				Log.warn(LOG_ID + ".getResponse() : Could not parse ban properly, reporter=" + clientData.getDisplayName() + ", json=" + bansObj);
			}
		} else {
			rc = ResponseCode.ERROR;
			response = "Unknown request";
			Log.warn(LOG_ID + ".getResponse() : Unknown request from " + exchange.getRemoteAddress().toString() + " -> " + inputObj);
		}

		return new ResponseData(response, rc, httpCode);
	}

}
