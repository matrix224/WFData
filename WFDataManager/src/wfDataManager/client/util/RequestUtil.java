package wfDataManager.client.util;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.KeyAgreement;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import jdtools.util.MiscUtil;
import wfDataManager.client.cache.BanManagerCache;
import wfDataManager.client.request.AddBanRequest;
import wfDataManager.client.request.AddDataRequest;
import wfDataManager.client.request.GetBansRequest;
import wfDataManager.client.request.RegistrationRequest;
import wfDataModel.model.util.AuthUtil;
import wfDataModel.model.util.NetworkUtil;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.data.LoadoutData;
import wfDataModel.service.data.LoadoutItemData;
import wfDataModel.service.type.GameDataType;
import wfDataModel.service.type.GameMode;

/**
 * Utility class for sending specific requests to the service
 * @author MatNova
 *
 */
public final class RequestUtil {

	public static void sendAddBanRequest(JsonObject data) {
		new AddBanRequest().sendRequest(data);
	}

	public static void sendGetBansRequest(JsonObject data) {
		new GetBansRequest().sendRequest(data);
	}

	public static void sendRegisterRequest() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
		JsonObject data = new JsonObject();
		JsonObject clientProps = new JsonObject();
		data.addProperty(JSONField.SERVER_NAME, ClientSettingsUtil.getDisplayName());
		data.add(JSONField.PROPERTIES, clientProps);
		if (!MiscUtil.isEmpty(ClientSettingsUtil.getRegion())) {
			data.addProperty(JSONField.REGION, ClientSettingsUtil.getRegion());
		}
		if (!MiscUtil.isEmpty(ClientSettingsUtil.getHost())) {
			clientProps.addProperty(JSONField.HOST, ClientSettingsUtil.getHost());
		}
		if (ClientSettingsUtil.enableBanning() && ClientSettingsUtil.enableBanLoadoutSharing()) {
			JsonArray bansArr = new JsonArray();
			for (LoadoutData loadout : BanManagerCache.singleton().getBannedLoadouts()) {
				JsonObject banObj = new JsonObject();
				JsonArray gamesArr = new JsonArray();
				JsonArray itemsArr = new JsonArray();
				for (GameMode gm : loadout.getGameModes()) {
					gamesArr.add(gm.getId());
				}
				for (LoadoutItemData itemData : loadout.getLoadoutItems()) {
					itemsArr.add(itemData.getItemName());
				}
				banObj.addProperty(JSONField.ELO, loadout.getElo() != null ? loadout.getElo().getCode() : -1);
				banObj.add(JSONField.GAME_MODES, gamesArr);
				banObj.add(JSONField.ITEMS, itemsArr);
				bansArr.add(banObj);
			}
			clientProps.add(JSONField.BANS, bansArr);
		}
		KeyAgreement ka = null;
		if (ClientSettingsUtil.getServerID() == 0) {
			KeyPair kp = AuthUtil.generateDiffieHellman();
			ka = KeyAgreement.getInstance(AuthUtil.DIFFIE_HELLMAN); 
			ka.init(kp.getPrivate());
			data.addProperty(JSONField.AUTH, Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
		}

		new RegistrationRequest(ka).sendRequest(data);
	}

	public static void sendAddDataRequest(JsonObject data, GameDataType dataType) {
		data.addProperty(JSONField.TYPE, dataType.name());
		if (!GameDataType.RETRY_GAME_DATA.equals(dataType)) {
			if (!data.has(JSONField.TIMESTAMP)) {
				data.addProperty(JSONField.TIMESTAMP, System.currentTimeMillis());
			}
		}
		if (!data.has(JSONField.TRANSACTION_ID)) {
			data.addProperty(JSONField.TRANSACTION_ID, NetworkUtil.generateTransactionID(ClientSettingsUtil.getServerID()));
		}
		new AddDataRequest(data.toString(), dataType).sendRequest(data);
	}
}
