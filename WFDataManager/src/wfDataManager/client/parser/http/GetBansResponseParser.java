package wfDataManager.client.parser.http;

import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataManager.client.cache.BanManagerCache;
import wfDataManager.client.cache.ServerDataCache;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.model.data.PlayerData;
import wfDataModel.service.codes.ResponseCode;
import wfDataModel.service.data.BanData;
import wfDataModel.service.data.BanSpec;
import wfDataModel.service.data.ResponseData;
import wfDataModel.service.type.BanActionType;

/**
 * Parser for handling the response for retrieving any shared bans from the service
 * @author MatNova
 *
 */
public class GetBansResponseParser extends BaseResponseParser {

	private static final String LOG_ID = GetBansResponseParser.class.getSimpleName();

	@Override
	protected void parseData(ResponseData responseData) {
		if (ResponseCode.NO_NEW_BANS == responseData.getRC()) {
			Log.debug(LOG_ID + ".parseData() : No new bans from service");
		} else if (ResponseCode.NEW_BANS == responseData.getRC()) {
			try {
				List<BanData> fetchedBans = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(responseData.getResponse(), new TypeToken<List<BanData>>() {}.getType());
				if (!MiscUtil.isEmpty(fetchedBans)) {
					int numAdded = 0;
					for (BanData banData : fetchedBans) {
						for (BanSpec spec : banData.getBanSpecs()) {
							// If this person has an active ban already on our side, then don't add anything
							if (!ClientSettingsUtil.enforceLoadoutBans() || BanManagerCache.singleton().isBannedLoadout(spec.getLoadoutID())) {
								if (!BanManagerCache.singleton().isCurrentlyBanned(banData.getUID()) && !spec.isExpired(ClientSettingsUtil.getBanTime(spec.isPrimary(), spec.isKick()))) {
									BanManagerCache.singleton().manageBan(banData, BanActionType.ADD, spec.getIP());
									numAdded++;
									PlayerData player = ServerDataCache.singleton().getPlayer(banData.getUID());
									if (player != null && !player.getIPAndPort().equals(spec.getIP())) {
										Log.info(LOG_ID + ".parseData() : Received ban for player currently in server, and IP is different. Will add secondary ban for them -> player=" + banData.getPlayerName() + " (" + banData.getUID() + ")");
										BanManagerCache.singleton().manageBan(banData, BanActionType.ADD, player.getIPAndPort(), "Ban evasion");
									}
								} else {
									Log.info(LOG_ID + ".parseData() : Received new ban from service, but user is already currently banned or ban would've already expired. Will ignore -> player=" + banData.getPlayerName() + " (" + banData.getUID() + ")");
								}
							}
						}
					}
					Log.info(LOG_ID + ".parseData() : Added " + numAdded + " / " + fetchedBans.size() + " ban(s) received from service");
				} else {
					Log.warn(LOG_ID + ".parseData() : Received new bans from service, but couldn't parse them? data=" + responseData.getResponse());
				}
			} catch (Exception e) {
				Log.error(LOG_ID + ".parseData() : Exception while parsing fetched bans, data=" + responseData.getResponse() +"   -> " + e.getLocalizedMessage());
			}
		} else if (ResponseCode.ERROR == responseData.getRC()) {
			Log.warn(LOG_ID + ".parseData() : Error getting bans from service -> " + responseData.getResponse());
		} else {
			Log.warn(LOG_ID + ".parseData() : Unhandled RC: rc=" + responseData.getRC() + ", response=" + responseData.getResponse());
		}		
	}

}
