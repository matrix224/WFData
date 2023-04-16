package wfDataManager.client.parser.http;

import java.util.Base64;

import javax.crypto.KeyAgreement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import wfDataManager.client.db.ProcessorVarDao;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.model.logging.Log;
import wfDataModel.model.util.AuthUtil;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.codes.ResponseCode;
import wfDataModel.service.data.ResponseData;

/**
 * Parser for handling the response for registering with the service
 * @author MatNova
 *
 */
public class RegistrationResponseParser extends BaseResponseParser {
	private static final String LOG_ID = RegistrationResponseParser.class.getSimpleName();

	private KeyAgreement agreement;

	public RegistrationResponseParser(KeyAgreement agreement) {
		this.agreement = agreement;
	}

	@Override
	public void parseData(ResponseData responseData) {
		if (ResponseCode.REGISTERED == responseData.getRC()) {
			try {
				JsonObject resp = JsonParser.parseString(responseData.getResponse()).getAsJsonObject();
				int sid = resp.get(JSONField.SERVER_ID).getAsInt();
				// TODO: If something fails here, technically would've made a bogus entry on service side
				byte[] serverAuth = Base64.getDecoder().decode(resp.get(JSONField.AUTH).getAsString().getBytes());
				String aes = Base64.getEncoder().encodeToString(AuthUtil.generateAESFromDiffieHellman(agreement, serverAuth));
				if (ClientSettingsUtil.setServerID(sid)) {
					ProcessorVarDao.updateVar(ProcessorVarDao.VAR_SYM_KEY, aes);
					Log.info(LOG_ID + ".parseData() : Successfully registered with service");
				}
			} catch (Exception e) {
				Log.error(LOG_ID + ".parseData() : Exception trying to finalize registration -> ", e);
			}
		} else if (ResponseCode.SUCCESS == responseData.getRC()) {
			Log.info(LOG_ID + ".parseData() : Service acknowledged");
		} else if (ResponseCode.ERROR == responseData.getRC()) {
			Log.warn(LOG_ID + ".parseData() : Service had issue, response=" + responseData.getResponse());
		} else {
			Log.warn(LOG_ID + ".parseData() : Unhandled RC: rc=" + responseData.getRC() + ", response=" + responseData.getResponse());
		}
	}

}
