package wfDataService.service.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import jdtools.logging.Log;
import jdtools.util.HTTPUtil;
import jdtools.util.IOUtil;
import jdtools.util.MiscUtil;
import wfDataModel.model.util.AuthUtil;
import wfDataModel.model.util.DBUtil;
import wfDataModel.service.codes.HeaderField;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.codes.ResponseCode;
import wfDataModel.service.data.ResponseData;
import wfDataService.service.cache.ServerClientCache;
import wfDataService.service.data.ServerClientData;
import wfDataService.service.db.ServerClientDao;

/**
 * Base class for receiving and processing requests from clients. <br>
 * This and all sub-classes should <b>NOT</b> have request-specific class variables, as they are shared between all requests.
 * @author MatNova
 *
 */
public abstract class BaseHandler implements HttpHandler {

	protected final String LOG_ID = getClass().getSimpleName();
	
	@Override
	public void handle(HttpExchange exchange) throws IOException {
		ResponseData response = null;
		
		int serverID = getServerID(exchange);
		byte[] aesSeed = getAESSeed(exchange);

		ServerClientData clientData = serverID > -1 ? ServerClientCache.singleton().getClientData(serverID) : null;

		if (serverID > -1 && clientData == null) {
			Log.warn(LOG_ID + ".handle() : Unknown server ID provided, will ignore -> " + serverID);
			response = new ResponseData("Invalid", ResponseCode.ERROR, HttpURLConnection.HTTP_OK);
		} else if ((serverID == -1 || aesSeed == null || aesSeed.length == 0) && !(this instanceof RegisterHandler)) {
			Log.warn(LOG_ID + ".handle() : Missing server ID or AES seed provided, will ignore -> sid=" + serverID + ", seed=" + aesSeed + ", remote=" + exchange.getRemoteAddress().toString());
			response = new ResponseData("Invalid request", ResponseCode.ERROR, HttpURLConnection.HTTP_OK);
		} else if (clientData != null && !clientData.isValidated()) {
			Log.warn(LOG_ID + ".handle() : Server client is not authorized yet -> " + serverID);
			response = new ResponseData("Denied", ResponseCode.ERROR, HttpURLConnection.HTTP_OK);
		} else {
			try (InputStream reader = exchange.getRequestBody()) {
				Map<String, String> postParams = HTTPUtil.parsePostParams(new String(Base64.getDecoder().decode(IOUtil.readAllBytes(reader)), StandardCharsets.UTF_8));
				String input = postParams.get(JSONField.DATA);
				if (MiscUtil.isEmpty(input)) {
					Log.warn(LOG_ID + ".getResponse() : Invalid request from " + exchange.getRemoteAddress().toString() + " -> " + input);
					response = new ResponseData("Invalid request", ResponseCode.ERROR, HttpURLConnection.HTTP_OK);
				} else {
					// ServerID is only allowed to not be supplied if this is an initial registration request
					JsonObject inputObj = JsonParser.parseString(serverID > 0 ? AuthUtil.decode(input, ServerClientDao.getSymKey(serverID), getAESSeed(exchange)) : input).getAsJsonObject();
					// If no version is specified, we assume it's DEFAULT_VER
					String requestVersion = DBUtil.DEFAULT_VER;
					if (inputObj.has(JSONField.VERSION)) {
						requestVersion = inputObj.get(JSONField.VERSION).getAsString();
					}
					response = getResponse(exchange, clientData, inputObj, requestVersion);
				}
			} catch (Exception e) {
				response = new ResponseData("An error occurred while processing", ResponseCode.ERROR, HttpURLConnection.HTTP_INTERNAL_ERROR);
				Log.error(LOG_ID + ".handle() : Exception occurred, remote=" + exchange.getRemoteAddress().toString() + " -> ", e);
			}
		}

		String responseStr = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJsonTree(response).toString();
		try {
			byte[] respData = Base64.getEncoder().encode(serverID > -1 ? AuthUtil.encode(responseStr, ServerClientDao.getSymKey(serverID), getAESSeed(exchange)).getBytes() : responseStr.getBytes());
			exchange.sendResponseHeaders(response.getHTTPCode(), respData.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(respData);
			} 
		}
		catch (Exception e) {
			Log.error(LOG_ID + ".handle() : Exception writing response, remote=" + exchange.getRemoteAddress().toString() + " -> ", e);
		}
	}

	protected int getServerID(HttpExchange exchange) {
		int serverID = -1;
		String sid = exchange.getRequestHeaders().getFirst(HeaderField.SERVER_ID);
		if (!MiscUtil.isEmpty(sid)) {
			serverID = Integer.valueOf(sid);
		}
		return serverID;
	}

	protected byte[] getAESSeed(HttpExchange exchange) {
		String seed = exchange.getRequestHeaders().getFirst(HeaderField.SEED);
		byte[] seedData = null;
		if (!MiscUtil.isEmpty(seed)) {
			seedData=  Base64.getDecoder().decode(seed);
		}

		return seedData;
	}

	protected abstract ResponseData getResponse(HttpExchange exchange, ServerClientData clientData, JsonObject inputObj, String requestVersion);
}
