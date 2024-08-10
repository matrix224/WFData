package wfDataService.service.main;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import jdtools.codes.ExitCode;
import jdtools.logging.Log;
import wfDataModel.model.util.DBUtil;
import wfDataModel.service.type.PlatformType;
import wfDataService.service.cache.WarframeItemCache;
import wfDataService.service.db.manager.ResourceManager;
import wfDataService.service.util.ServiceSettingsUtil;

public class WeaponInfoDump {

	private static final String LOG_ID = WeaponInfoDump.class.getSimpleName();
	// Mios, boltace, DSS, jat kusar, lacera, kronen, kronen prime, ohma, glaive, mire, cerata, orvius, fragor prime, sigma and octantis, silva and aegis, silva and aegis prime, ack and brunt
	private static final List<String> BANNED_WEPS = Arrays.asList("Mios", "Boltonfa", "DarkSwordDaggerDuals", "GrnKusarigamaWeapon", "StalkerMios", "TennoTonfa", "TonfaContestWinnerPrimeWeapon", "CrpTonfa", "LightGlaiveWeapon", "MireSword", "PunctureGlaiveWeapon", "TnTeshinGlaiveWep", "PrimeFragor", "SundialBoardSword", "TennoSwordShield", "PrimeSilvaAegis", "RegorAxeShield");
	// lex, skana, lato, braton, mk1-braton, paris, kunai, mk1-bo, mk1-kunai, mk1-paris, bo, excalibur, volt, mag
	private static final List<String> STARTER_WEPS = Arrays.asList("HeavyPistol", "LongSword", "Pistol", "Rifle", "StartingRifle", "HuntingBow", "Kunai", "MK1Bo", "MK1Kunai", "MK1Paris", "Staff", "Excalibur", "Volt", "Mag");

	public static void main(String[] args) {
		ExitCode rc = ExitCode.SUCCESS;
		try {
			new WeaponInfoDump().process();
		} catch (Exception e) {
			rc = ExitCode.ERROR;
			Log.error(LOG_ID + "() : Error while processing -> ", e);
		} finally {
			System.exit(rc.getCode());
		}
	}

	private void process() throws SQLException, FileNotFoundException, IOException {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		SortedMap<String, Map<String, WepTracker>> tracker = new TreeMap<String, Map<String, WepTracker>>();
		Map<Integer, Set<String>> uniquePlayers = new HashMap<Integer, Set<String>>();
		Map<Integer, Set<String>> beginnerOnly = new HashMap<Integer, Set<String>>();
		Map<Integer, Set<String>> nonBeginner = new HashMap<Integer, Set<String>>();
		Map<Integer, Set<String>> bannedWeps = new HashMap<Integer, Set<String>>();
		Map<Integer, Set<String>> nonBannedWeps = new HashMap<Integer, Set<String>>();


		Map<Integer, Set<String>> uniquePlayersRecurring = new HashMap<Integer, Set<String>>();
		Map<Integer, Set<String>> beginnerOnlyRecurring = new HashMap<Integer, Set<String>>();
		Map<Integer, Set<String>> nonBeginnerRecurring = new HashMap<Integer, Set<String>>();
		Map<Integer, Set<String>> bannedWepsRecurring = new HashMap<Integer, Set<String>>();
		Map<Integer, Set<String>> nonBannedWepsRecurring = new HashMap<Integer, Set<String>>();
		

		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("SELECT UID, PLATFORM, WEAPON_KILLS FROM WEEKLY_DATA WHERE WEEK_DATE BETWEEN '2023-01-01' and '2024-01-07' and game_mode in (406000,406009,406010) and kills>0");
			rs = ps.executeQuery();
			while (rs.next()) {
				String uid = rs.getString("UID");
				int platform = rs.getInt("PLATFORM");
				Map<String, Integer> wepKills = DBUtil.parseDBMap(rs.getString("WEAPON_KILLS"), String.class, Integer.class);

				uniquePlayers.computeIfAbsent(platform, k-> new HashSet<String>()).add(uid);

				for (String wep : wepKills.keySet()) {
					String wepReal = WarframeItemCache.singleton().getItemName(wep);
					if (wepReal == null) {
						wepReal = wep;
					}
					WepTracker track = tracker.computeIfAbsent(wepReal, k -> new HashMap<String, WepTracker>()).computeIfAbsent(uid, k -> new WepTracker(platform));
					track.kills += wepKills.get(wep);

					// This is a starter weapon and they don't have any non-beginner weapons tracked, put them into beginner
					if (STARTER_WEPS.contains(wep) && (!nonBeginner.containsKey(platform) || !nonBeginner.get(platform).contains(uid))) {
						beginnerOnly.computeIfAbsent(platform, k -> new HashSet<String>()).add(uid);
					} else {
						nonBeginner.computeIfAbsent(platform, k -> new HashSet<String>()).add(uid);
						if (beginnerOnly.containsKey(platform) && beginnerOnly.get(platform).contains(uid)) {
							beginnerOnly.get(platform).remove(uid);
						}
					}

					if (BANNED_WEPS.contains(wep)) {
						bannedWeps.computeIfAbsent(platform, k-> new HashSet<String>()).add(uid);
						if (nonBannedWeps.containsKey(platform) && nonBannedWeps.get(platform).contains(uid)) {
							nonBannedWeps.get(platform).remove(uid);
						}
					} else if (!bannedWeps.containsKey(platform) || !bannedWeps.get(platform).contains(uid)) {
						nonBannedWeps.computeIfAbsent(platform, k -> new HashSet<String>()).add(uid);
					}
				}
			}
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}

		
		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("SELECT uid,platform, weapon_kills FROM WEEKLY_DATA w WHERE WEEK_DATE BETWEEN '2023-01-01' and '2024-01-07' and game_mode in (406000,406009,406010) and (select sum(k.kills) from weekly_data k where k.uid=w.uid and k.WEEK_DATE BETWEEN '2023-01-01' and '2024-01-07' and k.game_mode in (406000,406009,406010)) >= 100");
			rs = ps.executeQuery();
			while (rs.next()) {
				String uid = rs.getString("UID");
				int platform = rs.getInt("PLATFORM");
				Map<String, Integer> wepKills = DBUtil.parseDBMap(rs.getString("WEAPON_KILLS"), String.class, Integer.class);

				uniquePlayersRecurring.computeIfAbsent(platform, k-> new HashSet<String>()).add(uid);

				for (String wep : wepKills.keySet()) {
					/*String wepReal = WarframeItemCache.singleton().getItemName(wep);
					if (wepReal == null) {
						wepReal = wep;
					}
					WepTracker track = tracker.computeIfAbsent(wepReal, k -> new HashMap<String, WepTracker>()).computeIfAbsent(uid, k -> new WepTracker(platform));
					track.kills += wepKills.get(wep);*/

					// This is a starter weapon and they don't have any non-beginner weapons tracked, put them into beginner
					if (STARTER_WEPS.contains(wep) && (!nonBeginnerRecurring.containsKey(platform) || !nonBeginnerRecurring.get(platform).contains(uid))) {
						beginnerOnlyRecurring.computeIfAbsent(platform, k -> new HashSet<String>()).add(uid);
					} else {
						nonBeginnerRecurring.computeIfAbsent(platform, k -> new HashSet<String>()).add(uid);
						if (beginnerOnlyRecurring.containsKey(platform) && beginnerOnlyRecurring.get(platform).contains(uid)) {
							beginnerOnlyRecurring.get(platform).remove(uid);
						}
					}

					if (BANNED_WEPS.contains(wep)) {
						bannedWepsRecurring.computeIfAbsent(platform, k-> new HashSet<String>()).add(uid);
						if (nonBannedWepsRecurring.containsKey(platform) && nonBannedWepsRecurring.get(platform).contains(uid)) {
							nonBannedWepsRecurring.get(platform).remove(uid);
						}
					} else if (!bannedWepsRecurring.containsKey(platform) || !bannedWepsRecurring.get(platform).contains(uid)) {
						nonBannedWepsRecurring.computeIfAbsent(platform, k -> new HashSet<String>()).add(uid);
					}
				}
			}
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}
		
		
		
		
		try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(ServiceSettingsUtil.getUserDir() + "WepInfo.csv"), StandardCharsets.UTF_8))) {
			List<PlatformType> platforms = Arrays.asList(PlatformType.PC, PlatformType.XBOX, PlatformType.PSN, PlatformType.NSW);
			
			writer.write("Weapon,PC,XB,PSN,NSW\n");
			for (String wep : tracker.keySet()) {
				StringBuilder sb = new StringBuilder(wep).append(",");
				sb.append(getNumForPlatform(tracker.get(wep), PlatformType.PC)).append(",");
				sb.append(getNumForPlatform(tracker.get(wep), PlatformType.XBOX)).append(",");
				sb.append(getNumForPlatform(tracker.get(wep), PlatformType.PSN)).append(",");
				sb.append(getNumForPlatform(tracker.get(wep), PlatformType.NSW)).append("\n");
				writer.write(sb.toString());
			}
			
			writer.write("\n");			
			StringBuilder rowTotals = new StringBuilder("Total Players,");
			StringBuilder rowBeginnerOnly = new StringBuilder("Beginner Only,");
			StringBuilder rowNonBeginner = new StringBuilder("Non-Beginner Only,");
			StringBuilder rowBanned = new StringBuilder("Banned Weps Used,");
			StringBuilder rowNonBanned = new StringBuilder("No Banned Weps Used,");
			
			StringBuilder rowTotalsRecurring = new StringBuilder("Total Players (Recurring),");
			StringBuilder rowBeginnerOnlyRecurring = new StringBuilder("Beginner Only (Recurring),");
			StringBuilder rowNonBeginnerRecurring = new StringBuilder("Non-Beginner Only (Recurring),");
			StringBuilder rowBannedRecurring = new StringBuilder("Banned Weps Used (Recurring),");
			StringBuilder rowNonBannedRecurring = new StringBuilder("No Banned Weps Used (Recurring),");
			
			for (PlatformType platform : platforms) {
				rowTotals.append(uniquePlayers.get(platform.getCode()).size() + ",");
				if (beginnerOnly.containsKey(platform.getCode())) {
					rowBeginnerOnly.append(beginnerOnly.get(platform.getCode()).size() + ",");
				} else {
					rowBeginnerOnly.append("0,");
				}
				if (nonBeginner.containsKey(platform.getCode())) {
					rowNonBeginner.append(nonBeginner.get(platform.getCode()).size() + ",");
				} else {
					rowNonBeginner.append("0,");
				}
				if (bannedWeps.containsKey(platform.getCode())) {
					rowBanned.append(bannedWeps.get(platform.getCode()).size() + ",");
				} else {
					rowBanned.append("0,");
				}
				if (nonBannedWeps.containsKey(platform.getCode())) {
					rowNonBanned.append(nonBannedWeps.get(platform.getCode()).size() + ",");
				} else {
					rowNonBanned.append("0,");
				}
				
				rowTotalsRecurring.append(uniquePlayersRecurring.get(platform.getCode()).size() + ",");
				if (beginnerOnlyRecurring.containsKey(platform.getCode())) {
					rowBeginnerOnlyRecurring.append(beginnerOnlyRecurring.get(platform.getCode()).size() + ",");
				} else {
					rowBeginnerOnlyRecurring.append("0,");
				}
				if (nonBeginnerRecurring.containsKey(platform.getCode())) {
					rowNonBeginnerRecurring.append(nonBeginnerRecurring.get(platform.getCode()).size() + ",");
				} else {
					rowNonBeginnerRecurring.append("0,");
				}
				if (bannedWepsRecurring.containsKey(platform.getCode())) {
					rowBannedRecurring.append(bannedWepsRecurring.get(platform.getCode()).size() + ",");
				} else {
					rowBannedRecurring.append("0,");
				}
				if (nonBannedWepsRecurring.containsKey(platform.getCode())) {
					rowNonBannedRecurring.append(nonBannedWepsRecurring.get(platform.getCode()).size() + ",");
				} else {
					rowNonBannedRecurring.append("0,");
				}
				
			}
			writer.write(rowTotals.toString() + "\n");
			writer.write(rowBeginnerOnly.toString() + "\n");
			writer.write(rowNonBeginner.toString() + "\n");
			writer.write(rowBanned.toString() + "\n");
			writer.write(rowNonBanned.toString() + "\n");
			
			writer.write("\n");

			writer.write(rowTotalsRecurring.toString() + "\n");
			writer.write(rowBeginnerOnlyRecurring.toString() + "\n");
			writer.write(rowNonBeginnerRecurring.toString() + "\n");
			writer.write(rowBannedRecurring.toString() + "\n");
			writer.write(rowNonBannedRecurring.toString() + "\n");
		}


	}

	private int getNumForPlatform(Map<String, WepTracker> trackers, PlatformType platform) {
		int total = 0;

		for (WepTracker tracker : trackers.values()) {
			if (tracker.platform == platform.getCode()) {
				total++;
			}
		}

		return total;
	}

	private class WepTracker {
		public int platform;
		public int kills;

		public WepTracker(int platform) {
			this.platform = platform;
		}
	}
}
