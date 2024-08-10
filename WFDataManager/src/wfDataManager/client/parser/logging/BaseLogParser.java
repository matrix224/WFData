package wfDataManager.client.parser.logging;

import java.text.ParseException;
import java.util.List;
import java.util.regex.Matcher;

import wfDataManager.client.type.ParseResultType;
import wfDataModel.model.data.ServerData;

/**
 * Base class for handling specific parsing scenarios (e.g. player kills, game settings, etc)
 * 
 * @author MatNova
 */
public abstract class BaseLogParser {
	
	protected String LOG_ID = getClass().getSimpleName();
	protected final List<Matcher> matchers = initMatchers();

	/**
	 * Given a line, will return if this parser can do anything with it. <br>
	 * If this returns true, then you can call {@link #parse(ServerData, long, int)} to actually parse the line
	 * @param line
	 * @return
	 */
	public boolean canParse(String line) {
		reset(); // Reset our matchers before checking
		return matchers.stream().anyMatch(m -> m.reset(line).matches());
	}
	
	// We reset the matchers here to avoid duplicates matching in different parses
	// e.g. if the last matcher in our list matches on one line, and then the first one matches on the next,
	// we'd want to ensure that when parsing the second match, the last matcher is not marked as matching to 
	// avoid confusion on what actually matched for that parse
	private void reset() {
		matchers.stream().forEach(m -> m.reset(""));
	}
	
	/**
	 * Initializes the matchers that are relevant for this parser implementation and returns them
	 * @return
	 */
	protected abstract List<Matcher> initMatchers();
	
	/**
	 * Given a server, the current offset, and the last log time, will parse the last provided line via the {@link #canParse(String)} method,
	 * and will return what the result of that parse was.
	 * @param serverData
	 * @param offset
	 * @param lastLogTime
	 * @return
	 * @throws ParseException
	 */
	public abstract ParseResultType parse(ServerData serverData, long offset, long lastLogTime) throws ParseException;
}
