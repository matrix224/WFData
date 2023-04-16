package wfDataManager.client.request;

import wfDataManager.client.parser.http.GetBansResponseParser;
import wfDataModel.service.type.RequestType;

/**
 * Handles retrieving new bans from the service
 * @author MatNova
 *
 */
public class GetBansRequest extends BaseServiceRequest {

	@Override
	protected GetBansResponseParser getResponseParser() {
		return new GetBansResponseParser();
	}

	@Override
	protected RequestType getRequestType() {
		return RequestType.GET_BANS;
	}

}
