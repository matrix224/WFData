package wfDataManager.client.request;

import wfDataManager.client.parser.http.AddBanResponseParser;
import wfDataModel.service.type.RequestType;

/**
 * Handles sending a ban to the service
 * @author MatNova
 *
 */
public class AddBanRequest extends BaseServiceRequest {

	@Override
	protected AddBanResponseParser getResponseParser() {
		return new AddBanResponseParser();
	}

	@Override
	protected RequestType getRequestType() {
		return RequestType.ADD_BAN;
	}
}
