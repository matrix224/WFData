package wfDataManager.client.request;

import javax.crypto.KeyAgreement;

import wfDataManager.client.parser.http.RegistrationResponseParser;
import wfDataModel.service.type.RequestType;

/**
 * Handles sending registration requests (new registration or refreshing existing one) to the service
 * @author MatNova
 *
 */
public class RegistrationRequest extends BaseServiceRequest {

	private KeyAgreement agreement;
	
	public RegistrationRequest(KeyAgreement agreement) {
		this.agreement = agreement;
	}
	
	@Override
	protected RegistrationResponseParser getResponseParser() {
		return new RegistrationResponseParser(agreement);
	}

	@Override
	protected RequestType getRequestType() {
		return RequestType.REGISTER;
	}

}
