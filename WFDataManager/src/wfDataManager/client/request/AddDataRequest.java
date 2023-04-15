package wfDataManager.client.request;

import wfDataManager.client.parser.http.AddDataResponseParser;
import wfDataModel.service.type.GameDataType;
import wfDataModel.service.type.RequestType;

/**
 * Handles sending game data to the service
 * @author MatNova
 *
 */
public class AddDataRequest extends BaseServiceRequest {

	private String request;
	private GameDataType dataType;

	public AddDataRequest(String request, GameDataType dataType) {
		this.request = request;
		this.dataType = dataType;
	}

	@Override
	protected AddDataResponseParser getResponseParser() {
		return new AddDataResponseParser(request, dataType);
	}

	@Override
	protected RequestType getRequestType() {
		return RequestType.ADD_DATA;
	}


}
