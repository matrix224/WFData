package wfDataManager.client.parser.logging;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import wfDataManager.client.type.ParseResultType;
import wfDataManager.client.type.ProcessModeType;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.logging.Log;
import wfDataModel.model.util.PlayerUtil;

/**
 * Parser for detecting mission stats start and end, and for parsing the mission stats data. <br>
 * 
 * If this detects the mission stats start and the current log position is not equal to the log position for this
 * server at the start of the current parsing session, this will tell the log parser to stop parsing. 
 * This is to avoid situations where the mission stats have not completely printed to the log yet, and so we will
 * wait until the next parsing session to try and actually parse them. <br>
 * 
 * If this detects the mission stats start and the current log position is equal to the log position for this server
 * at the start of the current parsing session, it will then go ahead and let it try to parse. <br>
 * 
 * If this detects mission stats data, it will parse it and apply it to the proper player.
 * For Lunaro, this will parse the kills and deaths stats since those are actually passes and interceptions, respectively.
 * For any other game mode, it does not parse kills and deaths because those are tracked via the death messages in the log.
 * For any game mode, it will track mechanics and will add 1 to the player's current round count. <br>
 * 
 * If this detects the mission stats end, then this will indicate that the full mission stats section has been parsed successfully. 
 * 
 * @author MatNova
 *
 */
public class MissionStatsParser extends BaseLogParser {

	private Matcher MISSION_STATS_DATA_PATTERN;
	private Matcher MISSION_STATS_START_PATTERN;
	private Matcher MISSION_STATS_END_PATTERN;
	
	@Override
	protected List<Matcher> initMatchers() {
		MISSION_STATS_DATA_PATTERN = Pattern.compile(".*\\[Info]: (.+) -- kills: ([\\d]+), deaths: ([\\d]+), mechanics: ([\\d]+)").matcher("");
		MISSION_STATS_START_PATTERN = Pattern.compile(".*Mission stats:$").matcher("");
		MISSION_STATS_END_PATTERN = Pattern.compile(".*Mission stats$").matcher("");
		return Arrays.asList(MISSION_STATS_DATA_PATTERN, MISSION_STATS_START_PATTERN, MISSION_STATS_END_PATTERN);
	}

	@Override
	public ParseResultType parse(ServerData serverData, long offset, int lastLogTime) throws ParseException {
		if (MISSION_STATS_START_PATTERN.matches()) {
			// If this is historical, we just keep going once it finds start of mission stats under the assumption that the log is already finished
			// Otherwise for normal, we only keep going if this is found as the first line of the read
			if (offset == serverData.getLogPosition() || ProcessModeType.HISTORICAL.equals(ClientSettingsUtil.getProcessMode())) {
				if (ProcessModeType.HISTORICAL.equals(ClientSettingsUtil.getProcessMode())) {
					Log.debug(LOG_ID + ".parse() : Found mission stat start, will get stats");
				} else {
					Log.debug(LOG_ID + ".parse() : Found mission stat start at beginning of read, will get stats");
				}
				return ParseResultType.START_MISSION;
			} else {
				// If this is a normal processing session, stop processing here to avoid mission stats being cut off
				Log.debug(LOG_ID + ".parse() : Found mission stat mid-read, will stop reading...");
				return ParseResultType.STOP;
			}
		} else if (MISSION_STATS_END_PATTERN.matches()) {
			Log.debug(LOG_ID + ".parse() : Found mission stat end");
			return ParseResultType.END_MISSION;
		} else {
			String player = PlayerUtil.cleanPlayerName(MISSION_STATS_DATA_PATTERN.group(1));
			PlayerData p = serverData.getPlayerByName(player);
			int kills = Integer.parseInt(MISSION_STATS_DATA_PATTERN.group(2));
			int deaths = Integer.parseInt(MISSION_STATS_DATA_PATTERN.group(3));
			int mechanics = Integer.parseInt(MISSION_STATS_DATA_PATTERN.group(4));

			// Don't expect this, but just in case
			if (p == null) {
				Log.warn(LOG_ID + ".parse() : Did not have player data for " + player + " at round end!?");
			} else {
				// For Lunaro, the round-end kills and deaths are passes and interceptions, respectively
				// For other modes, we build actual k/d up over time, so don't add them here
				if (p.isForLunaro()) {
					p.setKills(p.getKills() + kills);
					p.setDeaths(p.getDeaths() + deaths);	
				}
				p.setMechanics(p.getMechanics() + mechanics);
				p.setRounds(p.getRounds() + 1);
				p.setLastLogTime(lastLogTime);
			}
		}
		
		return ParseResultType.OK;
	}

}
