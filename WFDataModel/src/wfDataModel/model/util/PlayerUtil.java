package wfDataModel.model.util;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdtools.util.MiscUtil;
import wfDataModel.model.logging.Log;
import wfDataModel.service.type.PlatformType;

/**
 * Util for handling various tasks related to players
 * @author MatNova
 *
 */
public final class PlayerUtil {

	public static final String NON_PLAYERNAME_REGEX = "[^\\x20-\\x7E]+";
	private static final Matcher NON_PLAYERNAME_PATTERN = Pattern.compile("(" + NON_PLAYERNAME_REGEX + ")", Pattern.CASE_INSENSITIVE).matcher("");
	
	/**
	 * Given a raw player name, will return it without any platform codes appended
	 * @param name
	 * @return
	 */
	public static final String cleanPlayerName(String name) {
		return name.replaceAll(NON_PLAYERNAME_REGEX, "");
	}
	
	/**
	 * Given a raw player name (i.e. with platform code appended), will return what platform they are on.
	 * @param playerName
	 * @return
	 */
	public static synchronized PlatformType getPlatform(String playerName) {
		PlatformType type = PlatformType.UNKNOWN;
		if (!MiscUtil.isEmpty(playerName)) {
			Matcher match = NON_PLAYERNAME_PATTERN.reset(playerName);
			if (match.find()) {
				String platformStr = match.group(1);
				int platformCode = 0;
				for (byte c : platformStr.getBytes(StandardCharsets.UTF_8)) {
					platformCode += (c & 0xff);
				}
				type = PlatformType.codeToType(platformCode);
				if (PlatformType.UNKNOWN.equals(type)) {
					Log.warn("PlayerUtil.getPlatform() : Unknown platform for " + playerName + ", code=" + platformCode);
				}
			} else {
				// This makes an assumption that if no platform byte is provided on the provided playername, it is an old log file and they're PC
				Log.warn("PlayerUtil.getPlatform() : No platform code provided for player " + playerName + ", assuming platform is PC");
				type = PlatformType.PC;
			}
		} else {
			Log.warn("PlayerUtil.getPlatform() : Given playerName is null or empty, defaulting platform to UNKNOWN");
		}
		
		return type;
	}
	
}
