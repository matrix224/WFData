package wfDataManager.client.parser.http;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.google.gson.GsonBuilder;

import jdtools.http.HTTPResponseData;
import jdtools.logging.Log;
import wfDataManager.client.db.ProcessorVarDao;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.model.util.AuthUtil;
import wfDataModel.service.codes.ResponseCode;
import wfDataModel.service.data.ResponseData;

/**
 * Base class for handling the parsing of all responses to the service
 * @author MatNova
 *
 */
public abstract class BaseResponseParser {
	
	public void parseResponse(HTTPResponseData response, byte[] seed) {
		if (!response.hasResponse()) {
			Log.warn(getClass().getSimpleName() + ".parseResponse() : No response data came back");
		} else {
			if (!response.isResultSuccess() && response.getHTTPResponseCode() != -1) {
				Log.warn(getClass().getSimpleName() + ".parseResponse() : Request was not handled properly, response=" + response.getResponse());
			} else {
				ResponseData respData = null;
				if (response.getHTTPResponseCode() == -1) {
					respData = new ResponseData(response.getResponse(), ResponseCode.ERROR, -1);
				} else {
					String resp = new String(Base64.getDecoder().decode(response.getResponse().getBytes()), StandardCharsets.UTF_8);
					if (ClientSettingsUtil.getServerID() > 0) {
						resp = AuthUtil.decode(resp, ProcessorVarDao.getSymKey(), seed);
					}
										
					respData = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(resp, ResponseData.class);
				}
				parseData(respData);
			}
		}

	}

	protected abstract void parseData(ResponseData responseData);
}
