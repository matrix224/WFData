package wfDataManager.client.parser.http;

import jdtools.logging.Log;
import wfDataModel.service.codes.ResponseCode;
import wfDataModel.service.data.ResponseData;

/**
 * Parser for handling response of adding ban data to the service
 * @author MatNova
 *
 */
public class AddBanResponseParser extends BaseResponseParser {

	private static final String LOG_ID = AddBanResponseParser.class.getSimpleName();
	
	@Override
	protected void parseData(ResponseData responseData) {
		if (ResponseCode.SUCCESS == responseData.getRC()) {
			Log.info(LOG_ID + ".parseData() : Ban added to service");
		} else if (ResponseCode.ERROR == responseData.getRC()) {
			Log.warn(LOG_ID + ".parseData() : Error adding ban to service -> " + responseData.getResponse());
		} else {
			Log.warn(LOG_ID + ".parseData() : Unhandled RC: rc=" + responseData.getRC() + ", response=" + responseData.getResponse());
		}
	}

}
