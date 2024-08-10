package wfDataService.service.handler;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataModel.model.data.ActivityData;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.util.DBUtil;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.codes.ResponseCode;
import wfDataModel.service.data.ResponseData;
import wfDataModel.service.type.GameDataType;
import wfDataService.data.adapter.PlayerDataTypeAdapterFactory;
import wfDataService.service.data.ServerClientData;
import wfDataService.service.db.ActivityDao;
import wfDataService.service.db.GameDataDao;
import wfDataService.service.db.TransactionDao;
import wfDataService.service.util.ServiceSettingsUtil;

/**
 * Handler for receiving and processing server data from a client.
 * @author MatNova
 *
 */
public class AddDataHandler extends BaseHandler {

	private static final List<String> TRANSACTIONS_IN_PROGRESS = Collections.synchronizedList(new ArrayList<String>());

	@Override
	protected ResponseData getResponse(HttpExchange exchange, ServerClientData clientData, JsonObject inputObj, String requestVersion) {
		String response = null;
		int rc = ResponseCode.SUCCESS;
		int httpCode = HttpURLConnection.HTTP_OK;
		GameDataType dataType = null;
		GameDataType origType = null;


		if (inputObj.has(JSONField.TYPE) && inputObj.has(JSONField.DATA)) {
			dataType = GameDataType.valueOf(inputObj.get(JSONField.TYPE).getAsString());
			origType = inputObj.has(JSONField.ORIG_TYPE) ? GameDataType.valueOf(inputObj.get(JSONField.ORIG_TYPE).getAsString()) : dataType;

			// The original version uses these
			// TODO: Maintain for backwards compatibility?
			long timestamp = inputObj.get(JSONField.TIMESTAMP).getAsLong();
			String timezone = inputObj.has(JSONField.TIMEZONE) ? inputObj.get(JSONField.TIMEZONE).getAsString() : null;
			int dataId = GameDataType.RETRY_GAME_DATA.equals(dataType) ? inputObj.get(JSONField.DATA_ID).getAsInt() : 0;

			String transId = null;
			
			if (inputObj.has(JSONField.TRANSACTION_ID)) {
				transId = inputObj.get(JSONField.TRANSACTION_ID).getAsString();
			} else {
				transId = timestamp + "." + clientData.getServerClientID();
				if (!DBUtil.DEFAULT_VER.equals(requestVersion)) {
					Log.warn(LOG_ID, ".getResponse() : TransactionID was not present for non-V1.0 request, generating default one -> sid=" + clientData.getServerClientID(), ", ver=", requestVersion);
				}
			}

			if (TransactionDao.alreadyProcessed(transId, clientData.getServerClientID(), origType)) {
				JsonObject resp = new JsonObject();
				resp.addProperty(JSONField.DATA_ID, dataId);
				Log.warn(LOG_ID, ".getResponse() : Transaction already marked processed, denying -> sid=" + clientData.getServerClientID(), ", ver=", requestVersion, ", transId=", transId);
				return new ResponseData(resp.toString(), ResponseCode.ALREADY_PROCESSED, httpCode);
			} else if (TRANSACTIONS_IN_PROGRESS.contains(transId)) {
				Log.warn(LOG_ID, ".getResponse() : Transaction already being processed, denying -> sid=" + clientData.getServerClientID(), ", ver=", requestVersion, ", transId=", transId);
				return new ResponseData(response, ResponseCode.ALREADY_BEING_PROCESSED, httpCode);
			} else {
				TRANSACTIONS_IN_PROGRESS.add(transId);
			}

			try {

				GsonBuilder builder = new GsonBuilder().excludeFieldsWithoutExposeAnnotation();
				if (DBUtil.DEFAULT_VER.equals(requestVersion)) {
					builder = builder.registerTypeAdapterFactory(new PlayerDataTypeAdapterFactory());
				}

				List<ServerData> serverData = builder.create().fromJson(inputObj.get(JSONField.DATA).getAsJsonArray(), new TypeToken<List<ServerData>>() {}.getType());
				List<String> serverIds = GameDataType.GAME_DATA.equals(dataType) ? builder.create().fromJson(inputObj.get(JSONField.DATA_ID).getAsJsonArray(), new TypeToken<List<String>>() {}.getType()) : null;

				if (!MiscUtil.isEmpty(serverData)) {
					List<ServerData> failed = new ArrayList<ServerData>();

					// If we want to track server statuses, first go through any currently stored client servers and remove any that are no longer detected on the client's end
					if (ServiceSettingsUtil.trackServerStatus() && GameDataType.GAME_DATA.equals(dataType) && !MiscUtil.isEmpty(serverIds)) {
						List<String> currentClientServers = clientData.getServerStatusIDs();
						for (String currentClientId : currentClientServers) {
							if (!serverIds.contains(currentClientId)) {
								clientData.updateServerStatusData(currentClientId, null);
							}
						}
					}

					for (ServerData server : serverData) {
						if (!GameDataDao.updateGameData(clientData, server, server.getTimeStats().getServerTime(), timezone, origType)) {
							failed.add(server);
						} else {
							// If we want to track server status, then update this server's status so long as it successfully went into DB and this is regular game data
							// If the gameModeId is -1, this means the server may have been sent over as it was starting up and didn't parse settings yet. So we will not update it until the next time data comes
							if (ServiceSettingsUtil.trackServerStatus() && GameDataType.GAME_DATA.equals(dataType) && server.getGameModeId() != -1) {
								clientData.updateServerStatusData(server.getId(), server.getServerInfo());
							}

							if (!MiscUtil.isEmpty(server.getServerActivity())) {
								for (ActivityData activity : server.getServerActivity()) {
									LocalDateTime time = !MiscUtil.isEmpty(timezone) ?  LocalDateTime.ofInstant(Instant.ofEpochMilli(activity.getTimestamp()), ZoneId.of(timezone)) : LocalDateTime.ofInstant(Instant.ofEpochMilli(activity.getTimestamp()), ZoneOffset.of(server.getTimeStats().getZoneId()));
									if (time.getMinute() % 5 != 0) {
										Log.warn(LOG_ID + ".getResponse() : Server activity time isn't at interval of 5. Will ignore -> server=" + clientData.getDisplayName() + ", gameMode=" + server.getGameModeId() + ", elo=" + server.getEloRating() + ", time=" + time + " (" + activity.getTimestamp() + ", " + server.getTimeStats().getZoneId() + ")");
										continue;
									}

									if (!ActivityDao.addActivityData(clientData, activity, server.getGameModeId(), server.getEloRating())) {
										// If adding activity data failed, then remove any player and misc kill data from the server,
										// and mark the server as failed
										// This will leave the activity data in place and then cause the server data to be returned without it to the client,
										// so it can be marked failed and retried at a later time
										server.clearMiscKills();
										server.clearPlayers();
										failed.add(server);
									}
								}
							}
						}
					}
					if (!MiscUtil.isEmpty(failed)) {
						rc = ResponseCode.ADD_DATA_ISSUE;
						builder = new GsonBuilder().excludeFieldsWithoutExposeAnnotation();
						if (DBUtil.DEFAULT_VER.equals(requestVersion)) {
							builder = builder.registerTypeAdapterFactory(new PlayerDataTypeAdapterFactory());
						}
						response = getDataFailResponse(builder.create().toJsonTree(failed), timestamp, timezone, transId, requestVersion);
						Log.info(LOG_ID, ".getResponse() : Processing failed for ", clientData.getDisplayName(), " (" + clientData.getServerClientID(), "), failedDataResp=", response);
					} else {
						if (dataId != 0) {
							JsonObject resp = new JsonObject();
							resp.addProperty(JSONField.DATA_ID, dataId);
							response = resp.toString();
						}
						TransactionDao.markTransaction(transId, clientData.getServerClientID(), origType);
 					}
						
				} else {
					rc = ResponseCode.ERROR;
					response = "No data provided";
					Log.warn(LOG_ID + ".getResponse() : No data provided from " + exchange.getRemoteAddress().toString() + " for " + dataType + " -> " + inputObj);
				}
			} finally {
				TRANSACTIONS_IN_PROGRESS.remove(transId);
			}
		} else {
			rc = ResponseCode.ERROR;
			response = "Unknown request";
			Log.warn(LOG_ID + ".getResponse() : Unknown request from " + exchange.getRemoteAddress().toString() + " -> " + inputObj);
		}

		return new ResponseData(response, rc, httpCode);
	}

	// TODO: Maintain references for tz for backwards compatibility? 
	private String getDataFailResponse(JsonElement data, long timestamp, String timezone, String transId, String requestVersion) {
		JsonObject resp = new JsonObject();
		if (!MiscUtil.isEmpty(timezone)) {
			resp.addProperty(JSONField.TIMEZONE, timezone);
		}
		resp.addProperty(JSONField.TIMESTAMP, timestamp);
		resp.addProperty(JSONField.TRANSACTION_ID, transId);
		resp.addProperty(JSONField.VERSION, requestVersion);
		resp.add(JSONField.DATA, data);
		return resp.toString();
	}

}
