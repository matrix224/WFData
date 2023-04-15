package wfDataManager.client.parser.logging;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import wfDataManager.client.cache.WarframeItemCache;
import wfDataManager.client.db.GameDataDao;
import wfDataManager.client.type.ParseResultType;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.logging.Log;
import wfDataModel.model.util.PlayerUtil;

/**
 * Parser for detecting player kills. <br>
 * Not all kill messages have a weapon associated with them, so not every kill will also yield a weapon kill. <br>
 * 
 * For messages that do have weapons, this will attempt to validate the weapon against the item cache to ensure it's a valid parse.
 * It is unlikely that a weapon name would be cut off since we stop parsing once we get to the last line of the file, but just in case,
 * we still attempt to validate the weapon is known.
 * If the weapon is unknown, it will stop parsing under the assumption that maybe something went wrong and it was cut off.
 * If the weapon is unknown and the log position is the last stored log position, this will then let it through as-is under the assumption
 * that it may be a new weapon that is currently unknown to the cache. In addition, it will add the weapon to the cache as an "allowed item"
 * to just let it through for future encounters with it. This "allowed item" designation only exists for the lifetime of the program, i.e. not persisted anywhere.
 * @author MatNova
 *
 */
public class PlayerKillParser extends BaseLogParser {
	private Matcher WEAPON_PATTERN;
	private Matcher RAW_KILL_PATTERN; // Some kill log messages just don't have a weapon, not sure if bullet jump or some status causes it
	
	@Override
	protected List<Matcher> initMatchers() {
		WEAPON_PATTERN = Pattern.compile(".*\\[Info\\]: (.+) was .* damage from (.+) using a (.+)").matcher("");
		RAW_KILL_PATTERN = Pattern.compile(".*\\[Info\\]: (.+) was .* damage from (.+)$").matcher("");
		return Arrays.asList(WEAPON_PATTERN, RAW_KILL_PATTERN);
	}
	
	@Override
	public ParseResultType parse(ServerData serverData, long offset, int lastLogTime) {
		Matcher lineMatch = WEAPON_PATTERN.matches() ? WEAPON_PATTERN : RAW_KILL_PATTERN;
		boolean hasWeapon = lineMatch.equals(WEAPON_PATTERN);

		String victim = PlayerUtil.cleanPlayerName(lineMatch.group(1));
		String killer = PlayerUtil.cleanPlayerName(lineMatch.group(2));
		String weapon = hasWeapon ? lineMatch.group(3) : null;

		PlayerData vp = serverData.getPlayerByName(victim);
		PlayerData kp = serverData.getPlayerByName(killer);

		if (hasWeapon) {
			// If this item can't be found in the valid item cache...
			if (!WarframeItemCache.singleton().hasItemName(weapon)) {
				List<String> possibleMatches = ClientSettingsUtil.persist() ? GameDataDao.findMatchingItems(weapon+"%", false).stream().map(p -> p.getKey()).collect(Collectors.toList()) : Collections.singletonList(weapon); // Try to find any similar items in the DB. If not using DB, just let it continue
				if (possibleMatches.size() == 1) { // If we have one exact match, we'll assume it's that
					Log.info(LOG_ID + ".parse() : Will assume unknown item " + weapon + " is " + possibleMatches.get(0));
					weapon = possibleMatches.get(0);
					WarframeItemCache.singleton().addAllowedItem(weapon);
				} else if (offset == serverData.getLogPosition()) { // If this is the second time we're processing this, just let it be added as-is
					Log.info(LOG_ID + ".parse() : Encountered weapon of length " + weapon.length() + " (" + weapon + "), but this is second time trying to read it; will insert anyway");
					WarframeItemCache.singleton().addAllowedItem(weapon);
				} else {
					// Otherwise, we have 0 or > 1 matches, so we'll log the situation
					// If multiple matches with an exact match, take it
					// Otherwise if 0 or multiple with no exact match, mark this file for re-process
					if (possibleMatches.size() > 1) {
						if (possibleMatches.indexOf(weapon) > -1) {
							Log.info(LOG_ID + ".parse() : Found multiple matches with an exact match, will assume unknown item " + weapon + " is as-is");
							WarframeItemCache.singleton().addAllowedItem(weapon);
						} else {
							Log.info(LOG_ID + ".parse() : Found multiple matches with no exact match for unknown item " + weapon + ": " + possibleMatches.toString() + ", will stop and try to re-read on next run");
							return ParseResultType.STOP;
						}
					} else {
						Log.info(LOG_ID + ".parse() : Found no matches for unknown item " + weapon + ", will stop and try to re-read on next run");
						return ParseResultType.STOP;
					}
				}
			}

			if (kp != null) {
				kp.addWeaponKill(weapon);
				serverData.addPlayerItemKill(kp.getUID(), weapon);
			} else {
				serverData.addMiscKill(weapon); // A player didn't kill someone, but we still have a "weapon" that killed the person. e.g. DamageTrigger
			}
		}

		vp.setDeaths(vp.getDeaths() + 1);
		vp.setLastLogTime(lastLogTime);

		// TODO: Any better, more generic way to handle this? Or is DamageTrigger the only one that comes through in this format?
		// DamageTriggers might have full name in them (e.g. "/Layer2/DamageTrigger0"), so just store as DamageTrigger instead
		vp.addKilledBy(kp != null ? kp.getUID() : killer.contains("DamageTrigger") ? "DamageTrigger" : killer, weapon);

		// Certain nonsense things can be listed as killer (e.g. "/Layer2/DamageTrigger0", or "a level 30 RUMBLER")
		// So don't count as an actual player kill if so
		if (kp != null) {
			kp.setKills(kp.getKills() + 1);
			kp.setLastLogTime(lastLogTime);
		}

		return ParseResultType.OK;
	}
}
