package wfDataManager.client.parser.logging;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import wfDataManager.client.type.ParseResultType;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.logging.Log;

/**
 * Parser class for detecting when a GPF has occurred on a server
 * @author MatNova
 *
 */
public class GPFParser extends BaseLogParser {

	private Matcher GPF_PARSER;
	
	@Override
	protected List<Matcher> initMatchers() {
		GPF_PARSER = Pattern.compile(".*Error \\[Info\\]: GPF at.*").matcher("");
		return Arrays.asList(GPF_PARSER);
	}

	@Override
	public ParseResultType parse(ServerData serverData, long offset, int lastLogTime) throws ParseException {
		Log.warn(LOG_ID + ".parse() : A GPF occurred on server " + serverData.getId() + "!");
		return ParseResultType.FINISH_LOG;
	}

}
