package wfDataService.service.handler;

import java.net.HttpURLConnection;
import java.security.KeyPair;
import java.util.Base64;

import javax.crypto.KeyAgreement;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataModel.model.util.AuthUtil;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.codes.ResponseCode;
import wfDataModel.service.data.ResponseData;
import wfDataModel.service.type.RegionType;
import wfDataService.service.cache.ServerClientCache;
import wfDataService.service.data.ServerClientData;
import wfDataService.service.db.ServerClientDao;
import wfDataService.service.util.ServiceSettingsUtil;

/**
 * Handler for processing a registration request from a client
 * @author MatNova
 *
 */
public class RegisterHandler extends BaseHandler {

	@Override
	protected ResponseData getResponse(HttpExchange exchange, ServerClientData clientData, JsonObject inputObj, String requestVersion) {
		String response = null;
		int rc = ResponseCode.SUCCESS;
		int httpCode = HttpURLConnection.HTTP_OK;
		String serverName = inputObj.has(JSONField.SERVER_NAME) ? inputObj.get(JSONField.SERVER_NAME).getAsString() : null;

		if (!MiscUtil.isEmpty(serverName)) {
			RegionType region = RegionType.UNKNOWN;
			if (inputObj.has(JSONField.REGION)) {
				String regionStr = inputObj.get(JSONField.REGION).getAsString();
				try {
					region = RegionType.valueOf(regionStr);
				} catch (Exception e) {
					Log.error(LOG_ID + ".getResponse() : Unknown region provided, defaulting to Unknown -> " + regionStr);
				}
			}

			if (clientData != null) {
				// If the client name is overridden on our side, don't update it
				if (!clientData.isNameOverridden() && !clientData.getDisplayName().equals(serverName)) {
					clientData.setDisplayName(serverName);
				}
				if (!clientData.getRegion().equals(region)) {
					clientData.setRegion(region);
				}

				if (inputObj.has(JSONField.PROPERTIES)) {
					clientData.setServerClientProperties(inputObj.getAsJsonObject(JSONField.PROPERTIES));
				}

				ServerClientDao.updateClientData(clientData);

				Log.info(LOG_ID + ".getResponse() : Existing client registered from " + exchange.getRemoteAddress().toString() + " -> name=" + serverName, ", ver=", requestVersion);

			} else {
				int serverId = -1;
				do {
					serverId = Math.abs((System.currentTimeMillis() + serverName + (Math.random() * Double.MAX_VALUE)).hashCode());
				} while (ServerClientCache.singleton().getClientData(serverId) != null);

				String diffiePublic = null;
				String aes = null;
				try {
					KeyPair kp = AuthUtil.generateDiffieHellman();
					KeyAgreement ka = KeyAgreement.getInstance("DH"); 
					ka.init(kp.getPrivate());
					byte[] clientAuth = Base64.getDecoder().decode(inputObj.get(JSONField.AUTH).getAsString().getBytes());
					diffiePublic = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
					aes = Base64.getEncoder().encodeToString(AuthUtil.generateAESFromDiffieHellman(ka, clientAuth));
				} catch (Exception e) {
					Log.error(LOG_ID + ".getResponse() : Exception generating AES key for " + exchange.getRemoteAddress().toString() + " -> ", e);
				}

				if (aes != null) {
					clientData = new ServerClientData(serverId, serverName);
					clientData.setRegion(region);
					if (inputObj.has(JSONField.PROPERTIES)) {
						clientData.setServerClientProperties(inputObj.getAsJsonObject(JSONField.PROPERTIES));
					}
					ServerClientDao.updateClientData(clientData);
					ServerClientDao.updateClientKey(serverId, aes);
					ServerClientCache.singleton().addClientData(clientData);
					rc = ResponseCode.REGISTERED; // Newly registered
					JsonObject resp = new JsonObject();
					resp.addProperty(JSONField.SERVER_ID, serverId);
					resp.addProperty(JSONField.AUTH, diffiePublic);
					response = resp.toString();
					Log.info(LOG_ID + ".getResponse() : Registered new client from " + exchange.getRemoteAddress().toString() + " -> id=" + serverId + ", name=" + serverName, ", ver=", requestVersion);
					if (ServiceSettingsUtil.autoAllowRegistration()) {
						ServerClientCache.singleton().toggleValidation(serverId, true);
					} else {
						Log.info(LOG_ID + ".getResponse() : New client must be manually validated via the 'enable' command.");
					}
				} else {
					rc = ResponseCode.ERROR;
					response = "Error registering";
				}
			}
		} else {
			rc = ResponseCode.ERROR;
			response = "Invalid request";
			Log.warn(LOG_ID + ".getResponse() : Invalid request from " + exchange.getRemoteAddress().toString() + " -> " + inputObj);
		}

		return new ResponseData(response, rc, httpCode);
	}

}
