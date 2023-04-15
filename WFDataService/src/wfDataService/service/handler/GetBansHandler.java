package wfDataService.service.handler;

import java.net.HttpURLConnection;
import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;

import jdtools.util.MiscUtil;
import wfDataModel.model.logging.Log;
import wfDataModel.service.codes.ResponseCode;
import wfDataModel.service.data.BanData;
import wfDataModel.service.data.ResponseData;
import wfDataService.service.cache.BanDataCache;
import wfDataService.service.data.ServerClientData;

/**
 * Handler for a new ban retrieval request from a client
 * @author MatNova
 *
 */
public class GetBansHandler extends BaseHandler {

	@Override
	protected ResponseData getResponse(HttpExchange exchange, ServerClientData clientData, JsonObject inputObj) {
		String response = null;
		int rc = ResponseCode.SUCCESS;
		int httpCode = HttpURLConnection.HTTP_OK;

		List<BanData> bans = BanDataCache.singleton().getBansSinceLastCheck(clientData);
		if (!MiscUtil.isEmpty(bans)) {
			rc = ResponseCode.NEW_BANS;
			response = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(bans);
			Log.info(LOG_ID + ".getResponse() : Sending new ban(s) to " + clientData.getDisplayName());
		} else {
			rc = ResponseCode.NO_NEW_BANS;
		}
		clientData.setLastBanPollTime(System.currentTimeMillis());

		return new ResponseData(response, rc, httpCode);		
	}

}
