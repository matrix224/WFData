package wfDataManager.client.util;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.util.Base64;

import javax.crypto.KeyAgreement;

import com.google.gson.JsonObject;

import wfDataManager.client.request.AddBanRequest;
import wfDataManager.client.request.AddDataRequest;
import wfDataManager.client.request.GetBansRequest;
import wfDataManager.client.request.RegistrationRequest;
import wfDataModel.model.util.AuthUtil;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.type.GameDataType;

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

	public static void sendRegisterRequest(JsonObject data) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
		KeyAgreement ka = null;
		if (ClientSettingsUtil.getServerID() == 0) {
			KeyPair kp = AuthUtil.generateDiffieHellman();
			ka = KeyAgreement.getInstance("DH"); 
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
			data.addProperty(JSONField.TIMEZONE, ZoneId.systemDefault().getId());
		}

		new AddDataRequest(data.toString(), dataType).sendRequest(data);
	}
}
