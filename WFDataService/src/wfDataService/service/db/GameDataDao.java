package wfDataService.service.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import jdtools.util.MiscUtil;
import wfDataModel.model.data.KillerData;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.logging.Log;
import wfDataModel.model.util.DBUtil;
import wfDataModel.model.util.DateUtil;
import wfDataModel.service.type.GameMode;
import wfDataService.service.cache.WarframeItemCache;
import wfDataService.service.data.ServerClientData;
import wfDataService.service.db.manager.ResourceManager;

/**
 * Dao that supports storage of game-related data (e.g player profiles / stats, weapon data, etc) in the DB
 * @author MatNova
 *
 */
public class GameDataDao {

	private static final String LOG_ID = GameDataDao.class.getSimpleName();

	public static boolean updateGameData(ServerClientData serverClient, ServerData server, long time, String zoneId) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		boolean isLunaro = GameMode.LUNARO.getId() == server.getGameModeId();
		// Note about dates:
		// We receive dates as UTC timestamps, relative to the timezone of the person sending them
		// When passing them into the DB, we will treat them as if they are in the zone of the person sending them
		// e.g. if this is hosted in NY and it's Friday night in NY, and someone from Germany sends us data where it's Saturday there,
		// then any daily weapon data we add will be attributed to Saturday
		// Similarly, if it's Saturday night in NY and someone from Germany sends us data where it's Sunday there,
		// then any weekly player data will be attributed to Germany's current Sunday
		// We do this because dates alone (i.e. without considering time) are irrelevant of timezone.
		// So if data is collected on a Saturday (regardless of where it was collected from), then we store it under a Saturday here
		LocalDate latestSunday = DateUtil.getLatestSunday(time, zoneId).toLocalDate();
		LocalDate dailyDate = DateUtil.getDate(time, zoneId).toLocalDate();
		Map<String, Integer> weaponData = new HashMap<String, Integer>(); // Weapon -> kills. Accumulated for all players in given server
		boolean success = true;

		try {
			conn = ResourceManager.getDBConnection();

			for (PlayerData data : server.getParsedPlayers()) {
				String curDBName = null;
				String pastNames = null;
				boolean profileExists = false;

				// First check if we have this player by UID
				// Added this much further down the line, so (at least at first) don't expect to match on this
				if (!MiscUtil.isEmpty(data.getUID())) {
					ps = conn.prepareStatement("SELECT P.NAME AS PLAYER, P.PAST_NAMES FROM PLAYER_PROFILE P WHERE P.UID=?");
					ps.setString(1, data.getUID());
					rs = ps.executeQuery();

					if (rs.next()) {
						curDBName = rs.getString("PLAYER");
						pastNames = rs.getString("PAST_NAMES");
						profileExists = true; // Should always be true here
					}

					// Release these for now
					ResourceManager.releaseResources(ps,rs);
				}

				if (profileExists) {
					// If we had data here, then we matched on UID, so update stats and player name for that UID
					ps = conn.prepareStatement("UPDATE PLAYER_DATA SET KILLS = KILLS + ?, DEATHS = DEATHS + ?, MECHANICS = MECHANICS + ?, GOALS = GOALS + ?, PASSES = PASSES + ?, INTERCEPTIONS = INTERCEPTIONS + ?, CAPTURES = CAPTURES + ?, ROUNDS = ROUNDS + ?, LAST_SEEN = CURRENT_DATE WHERE UID=? AND SID=?");
					ps.setInt(1, isLunaro ? 0 : data.getKills());
					ps.setInt(2, isLunaro ? 0 : data.getDeaths());
					ps.setInt(3, isLunaro ? 0 : data.getMechanics());
					ps.setInt(4, isLunaro ? data.getMechanics() : 0);
					ps.setInt(5, isLunaro ? data.getKills() : 0);
					ps.setInt(6, isLunaro ? data.getDeaths() : 0);
					ps.setInt(7, data.getCaptures());
					ps.setInt(8, data.getRounds());
					ps.setString(9, data.getUID());
					ps.setInt(10, serverClient.getServerID());
				} else {
					// In this case, didn't match on UID or player name, so need to add them to DB
					ps = conn.prepareStatement("INSERT INTO PLAYER_DATA (UID,KILLS,DEATHS,MECHANICS,GOALS,PASSES,INTERCEPTIONS,CAPTURES,ROUNDS,LAST_SEEN,SID) VALUES(?,?,?,?,?,?,?,?,?,CURRENT_DATE,?)");
					ps.setString(1, data.getUID());
					ps.setInt(2, isLunaro ? 0 : data.getKills());
					ps.setInt(3, isLunaro ? 0 : data.getDeaths());
					ps.setInt(4, isLunaro ? 0 : data.getMechanics());
					ps.setInt(5, isLunaro ? data.getMechanics() : 0);
					ps.setInt(6, isLunaro ? data.getKills() : 0);
					ps.setInt(7, isLunaro ? data.getDeaths() : 0 );
					ps.setInt(8, data.getCaptures());
					ps.setInt(9, data.getRounds());
					ps.setInt(10, serverClient.getServerID());
				}

				int result = ps.executeUpdate();
				if (result != 1) {
					Log.warn(LOG_ID + ".updateGameData() : Did not update player data for server " + serverClient.getDisplayName() + ", player=" + data.getPlayerName() + ", result count = " + result);
				}

				if (!profileExists) {
					ResourceManager.releaseResources(ps);

					ps = conn.prepareStatement("INSERT INTO PLAYER_PROFILE (NAME, UID, PLATFORM) VALUES (?,?,?)");
					ps.setString(1, data.getPlayerName());
					ps.setString(2, data.getUID());
					ps.setInt(3, data.getPlatform().getCode());
					ps.executeUpdate();
				}

				if (!MiscUtil.isEmpty(curDBName) && !curDBName.equals(data.getPlayerName())) {
					ResourceManager.releaseResources(ps);

					Set<String> pastNamesArr = !MiscUtil.isEmpty(pastNames) ? new Gson().fromJson(pastNames, new TypeToken<HashSet<String>>(){}.getType()) : new HashSet<String>();
					pastNamesArr.add(curDBName);

					ps = conn.prepareStatement("UPDATE PLAYER_PROFILE SET NAME = ?, PAST_NAMES = ? WHERE UID=?");
					ps.setString(1, data.getPlayerName());
					ps.setString(2, new Gson().toJson(pastNamesArr));
					ps.setString(3, data.getUID());
					result = ps.executeUpdate();
					if (result != 1) {
						Log.warn(LOG_ID + ".updateGameData() : Did not update player profile data for " + data.getPlayerName() + ", result count = " + result);
					} else {
						Log.info(LOG_ID + ".updateGameData() : Updated player name from " + curDBName + " to " + data.getPlayerName() + " for ID " + data.getUID());
					}

					/*

					ResourceManager.releaseResources(ps);

					//new Emailer("Update Player Data", "Updated player " + curDBName + " to " + data.getPlayerName()).sendEmail();

					ps = conn.prepareStatement("UPDATE LOG_WEEKLY_DATA SET PLAYER = ? WHERE PLAYER = ?");
					ps.setString(1, data.getPlayerName());
					ps.setString(2, curDBName);
					result = ps.executeUpdate();
					if (result != 1) {
						Log.warn("LoggingDao.updatePlayerData() : Did not update weekly player name data for " + data.getPlayerName());
					} else {
						Log.info("LoggingDao.updatePlayerData() : Updated weekly player name from " + curDBName + " to " + data.getPlayerName() + " for ID " + data.getUID());
					}*/
				}


				// Update their weekly player data once over-all stats and profile have been updated
				// so long as they have any tangible data we'd want to see for weekly stats
				if (data.hasData()) {
					updateWeeklyPlayerData(conn, latestSunday, serverClient, server, data);
				}

				for (String weapon : data.getWeaponKills().keySet()) {
					weaponData.compute(weapon, (k,v) -> v == null ? data.getWeaponKills().get(weapon) : v + data.getWeaponKills().get(weapon));
				}
			}

			for (String weapon : server.getMiscKills().keySet()) {
				weaponData.compute(weapon, (k,v) -> v == null ? server.getMiscKills().get(weapon) : v + server.getMiscKills().get(weapon));
			}

			// Once all player data has been updated, update all accumulated weapon data
			updateWeaponData(conn, serverClient, weaponData);
			addDailyWeaponData(conn, serverClient, dailyDate, weaponData);

		} catch (Exception e) {
			Log.error(LOG_ID + ".updateGameData() : Error occurred -> ", e);
			try {
				conn.rollback();
			} catch (SQLException e1) {
				Log.error(LOG_ID + ".updateGameData() : Error rolling back conn -> ", e1);
			}
			success = false;
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}
		return success;
	}

	private static void updateWeeklyPlayerData(Connection conn, LocalDate latestSunday, ServerClientData serverClient, ServerData server, PlayerData data) throws SQLException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			boolean alreadyHasData = true;
			Map<String, Integer> wepKills = null;
			Map<String, KillerData> killedBy = null;

			ps = conn.prepareStatement("SELECT WEAPON_KILLS, KILLED_BY FROM WEEKLY_DATA WHERE UID = ? AND WEEK_DATE = ? AND GAME_MODE = ? AND ELO = ? AND SID = ?");
			ps.setString(1, data.getUID());
			ps.setObject(2, latestSunday);
			ps.setInt(3, server.getGameModeId());
			ps.setInt(4, server.getEloRating());
			ps.setInt(5, serverClient.getServerID());
			rs = ps.executeQuery();

			if (rs.next()) {
				String weaponKills = rs.getString("WEAPON_KILLS");
				String playerKills = rs.getString("KILLED_BY");
				// Combine the DB values and current player values in a map outside of the PlayerData object
				// This is so that if anything fails, the player data won't end up with combined DB plus current values
				killedBy = DBUtil.parseDBMap(playerKills, String.class, KillerData.class);
				for (String killer : data.getKilledBy().keySet()) {
					killedBy.computeIfAbsent(killer, k -> new KillerData()).combine(data.getKilledBy().get(killer));
				}

				wepKills = DBUtil.parseDBMap(weaponKills, String.class, Integer.class);
				for (String weapon : data.getWeaponKills().keySet()) {
					int kills = data.getWeaponKills().get(weapon);
					wepKills.compute(weapon, (k,v) -> v == null ? kills : v + kills);
				}
			} else {
				alreadyHasData = false;
			}

			ResourceManager.releaseResources(ps, rs);

			int result = -1;
			if (alreadyHasData) {
				ps = conn.prepareStatement("UPDATE WEEKLY_DATA SET KILLS = KILLS + ?, DEATHS = DEATHS + ?, MECHANICS = MECHANICS + ?, CAPTURES = CAPTURES + ?, ROUNDS = ROUNDS + ?, WEAPON_KILLS = ?, KILLED_BY = ?, TOTAL_TIME = TOTAL_TIME + ? WHERE UID = ? AND WEEK_DATE = ? AND GAME_MODE = ? AND ELO = ? AND SID = ?");
				ps.setInt(1, data.getKills());
				ps.setInt(2, data.getDeaths());
				ps.setInt(3, data.getMechanics());
				ps.setInt(4, data.getCaptures());
				ps.setInt(5, data.getRounds());
				ps.setString(6, DBUtil.createDBMap(wepKills));
				ps.setString(7, DBUtil.createDBMap(killedBy));
				ps.setInt(8, data.getTotalTime());
				ps.setString(9, data.getUID());
				ps.setObject(10, latestSunday);
				ps.setInt(11, server.getGameModeId());
				ps.setInt(12, server.getEloRating());
				ps.setInt(13, serverClient.getServerID());
				result = ps.executeUpdate();
				if (result != 1) {
					Log.warn(LOG_ID + ".updateWeeklyPlayerData() : Failed to update weekly data for server " + serverClient.getDisplayName() + ", player=" + data.getPlayerName() + " (" + data.getUID() + ")");
				}
			} else {
				// In this case, need to add them to DB
				ps = conn.prepareStatement("INSERT INTO WEEKLY_DATA (WEEK_DATE,UID,GAME_MODE,ELO,KILLS,DEATHS,MECHANICS,CAPTURES,ROUNDS,WEAPON_KILLS,KILLED_BY,TOTAL_TIME,SID) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)");
				ps.setObject(1, latestSunday);
				ps.setString(2, data.getUID());
				ps.setInt(3, server.getGameModeId());
				ps.setInt(4, server.getEloRating());
				ps.setInt(5, data.getKills());
				ps.setInt(6, data.getDeaths());
				ps.setInt(7, data.getMechanics());
				ps.setInt(8, data.getCaptures());
				ps.setInt(9, data.getRounds());
				ps.setString(10, DBUtil.createDBMap(data.getWeaponKills()));
				ps.setString(11, DBUtil.createDBMap(data.getKilledBy()));
				ps.setInt(12, data.getTotalTime());
				ps.setInt(13, serverClient.getServerID());
				result = ps.executeUpdate();

				if (result != 1) {
					Log.warn(LOG_ID + ".updateWeeklyPlayerData() : Did not insert weekly data for server " + serverClient.getDisplayName() + ", player=" + data.getPlayerName() + " (" + data.getUID() + ")");
				}
			}

		} finally {
			ResourceManager.releaseResources(ps, rs);
		}
	}

	private static void updateWeaponData(Connection conn, ServerClientData server, Map<String, Integer> weaponKills) throws SQLException {
		PreparedStatement psUpdate = null;
		PreparedStatement psInsert = null;
		PreparedStatement psInsertInfo = null;
		PreparedStatement psSelect = null;
		ResultSet rs = null;
		boolean hasWeaponInfo = false;

		try {
			psSelect = conn.prepareStatement("SELECT * FROM WEAPON_INFO WHERE WEAPON=?");
			psUpdate = conn.prepareStatement("UPDATE WEAPON_DATA SET KILLS=KILLS + ? WHERE WEAPON=? AND SID=?");
			psInsert = conn.prepareStatement("INSERT INTO WEAPON_DATA (WEAPON,KILLS,SID) VALUES(?,?,?)");

			for (String weapon : weaponKills.keySet()) {
				int kills = weaponKills.get(weapon);
				int result = -1;

				psSelect.setString(1, weapon);
				rs = psSelect.executeQuery();
				hasWeaponInfo = rs.next();

				ResourceManager.releaseResources(rs);

				if (!hasWeaponInfo) {
					psInsert.setString(1, weapon);
					psInsert.setInt(2, kills);
					psInsert.setInt(3, server.getServerID());

					result = psInsert.executeUpdate();

					if (psInsertInfo == null) {
						// If no WEAPON_INFO entry, then add that here as well
						// Note we do REPLACE INTO in case another request happens to be doing this at the same time, so we'll just delete then insert
						psInsertInfo = conn.prepareStatement("REPLACE INTO WEAPON_INFO (WEAPON, WEAPON_REAL) VALUES (?,?)");
					}
					psInsertInfo.setString(1, weapon);
					psInsertInfo.setString(2, WarframeItemCache.singleton().getItemName(weapon));
					psInsertInfo.executeUpdate();
				} else {
					psUpdate.setInt(1, kills);
					psUpdate.setString(2, weapon);
					psUpdate.setInt(3, server.getServerID());
					result = psUpdate.executeUpdate();

					if (result != 1) {
						psInsert.setString(1, weapon);
						psInsert.setInt(2, kills);
						psInsert.setInt(3, server.getServerID());
						result = psInsert.executeUpdate();
					}
				}

				if (result != 1) {
					Log.warn(LOG_ID + ".updateWeaponData() : Did not update weapon data for " + weapon + " in server " + server.getDisplayName());
				}
			}

		} finally {
			ResourceManager.releaseResources(rs);
			ResourceManager.releaseResources(psUpdate, psInsert, psInsertInfo, psSelect);
		}
	}

	private static void addDailyWeaponData(Connection conn, ServerClientData server, LocalDate dailyDate, Map<String, Integer> weaponKills) throws SQLException {
		PreparedStatement psUpdate = null;
		PreparedStatement psInsert = null;


		try {
			psUpdate = conn.prepareStatement("UPDATE DAILY_WEAPON_DATA SET KILLS = KILLS + ? WHERE WEAPON = ? AND DATE = ? AND SID=?");

			for (String weapon : weaponKills.keySet()) {
				int kills = weaponKills.get(weapon);

				psUpdate.setInt(1, kills);
				psUpdate.setString(2, weapon);
				psUpdate.setObject(3, dailyDate);
				psUpdate.setInt(4, server.getServerID());
				int result = psUpdate.executeUpdate();

				if (result != 1) {
					if (psInsert == null) {
						psInsert = conn.prepareStatement("INSERT INTO DAILY_WEAPON_DATA (DATE,WEAPON,KILLS,SID) VALUES(?,?,?,?)");
					}

					psInsert.setObject(1, dailyDate);
					psInsert.setString(2, weapon);
					psInsert.setInt(3, kills);
					psInsert.setInt(4, server.getServerID());

					result = psInsert.executeUpdate();
					if (result != 1) {
						Log.warn(LOG_ID + ".addDailyWeaponData() : Did not add weapon data for " + weapon + " for server " + server.getDisplayName());
					}
				}
			}

		} finally {
			ResourceManager.releaseResources(psUpdate, psInsert);
		}
	}

	public static List<String> findUnmappedItems() {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		List<String> items = new ArrayList<String>();

		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("SELECT WEAPON FROM WEAPON_INFO WHERE WEAPON_REAL IS NULL OR WEAPON_REAL = CONCAT(WEAPON, '_')");  // NOTE: This query will NOT work with SQLite (and other DBs) as most others use '||' operator instead!!!
			rs = ps.executeQuery();
			while (rs.next()) {
				items.add(rs.getString("WEAPON"));
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".findUnmappedItems() : Error occurred -> " + e);
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}

		return items;
	}

	public static boolean updateItemName(String item, String itemName) {
		Connection conn = null;
		PreparedStatement ps = null;
		boolean mapped = false;

		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("UPDATE WEAPON_INFO SET WEAPON_REAL = ? WHERE WEAPON = ?");
			ps.setString(1, itemName);
			ps.setString(2, item);
			mapped = ps.executeUpdate() == 1;
		} catch (Exception e) {
			Log.error(LOG_ID + ".updateItemName() : Error occurred -> " + e);
		} finally {
			ResourceManager.releaseResources(conn, ps);
		}
		return mapped;
	}
}
