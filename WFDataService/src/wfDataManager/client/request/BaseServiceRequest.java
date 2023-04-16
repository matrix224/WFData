package wfDataManager.client.request;

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;

import jdtools.http.HTTPRequest;
import jdtools.http.HTTPRequestListener;
import jdtools.http.HTTPResponseData;
import jdtools.util.HTTPUtil;
import wfDataManager.client.db.ProcessorVarDao;
import wfDataManager.client.parser.http.BaseResponseParser;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.model.logging.Log;
import wfDataModel.model.util.AuthUtil;
import wfDataModel.service.codes.HeaderField;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.type.RequestType;

/**
 * Base class for sending requests to the service
 * @author MatNova
 *
 */
public abstract class BaseServiceRequest implements HTTPRequestListener {

	private static final int SERVICE_TIMEOUT = ClientSettingsUtil.getServiceTimeout() * 1000;
	private final String LOG_ID = getClass().getSimpleName();

	private byte[] aesSeed; // Used once encryption keys have been established

	public void sendRequest(JsonObject jsonData) {
		Map<String, String> headers = new HashMap<String, String>();
		boolean hasServerID = ClientSettingsUtil.getServerID() > 0;
		if (hasServerID) {
			aesSeed = AuthUtil.generateSeed();
			headers.put(HeaderField.SEED, Base64.getEncoder().encodeToString(aesSeed));
			headers.put(HeaderField.SERVER_ID, String.valueOf(ClientSettingsUtil.getServerID()));
		}
		String data = jsonData.toString();
		RequestType requestType = getRequestType();
		String url = "http://" + ClientSettingsUtil.getServiceHost() + ":" + ClientSettingsUtil.getServicePort() + requestType.getEndPoint();
		String requestData = HTTPUtil.formatPostParams(Collections.singletonMap(JSONField.DATA, (hasServerID ? AuthUtil.encode(data, ProcessorVarDao.getSymKey(), aesSeed) : data)));
		HTTPRequest request = new HTTPRequest(url, Base64.getEncoder().encodeToString(requestData.getBytes()), headers, false, requestType, this);
		request.setConnectTimeout(SERVICE_TIMEOUT);
		request.setReadTimeout(SERVICE_TIMEOUT);
		request.fetch();
	}

	@Override
	public void onRequestCompleted(HTTPResponseData responseData) {
		RequestType type = (RequestType)responseData.getTagData();
		try {
			getResponseParser().parseResponse(responseData, aesSeed);
		} catch (Exception e) {
			Log.error(LOG_ID + ".onRequestCompleted() : Error trying to parse response, request type = " + type + " -> ", e);
		}
	}

	protected abstract BaseResponseParser getResponseParser();
	protected abstract RequestType getRequestType();

}
