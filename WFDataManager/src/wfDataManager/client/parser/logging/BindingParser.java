package wfDataManager.client.parser.logging;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import wfDataManager.client.type.ParseResultType;
import wfDataModel.model.data.ServerData;

/**
 * Parser for getting and setting the server proxies and port bindings
 * 
 * @author MatNova
 */
public class BindingParser extends BaseLogParser {

	private Matcher BINDING_PATTERN;
	private Matcher LOCAL_BINDING_PATTERN;
	
	@Override
	protected List<Matcher> initMatchers() {
		BINDING_PATTERN = Pattern.compile(".*Binding server address: (\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+).*").matcher("");
		LOCAL_BINDING_PATTERN = Pattern.compile(".*Local binding: .*:(\\d+).*").matcher("");
		return Arrays.asList(BINDING_PATTERN, LOCAL_BINDING_PATTERN);
	}

	@Override
	public ParseResultType parse(ServerData serverData, long offset, int lastLogTime) {
		if (BINDING_PATTERN.matches()) {
			serverData.addProxyServer(BINDING_PATTERN.group(1));
		} else {
			serverData.setServerPort(Integer.valueOf(LOCAL_BINDING_PATTERN.group(1)));
		}
		return ParseResultType.OK;
	}

}
