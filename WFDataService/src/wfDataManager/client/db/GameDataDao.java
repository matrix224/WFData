package wfDataManager.client.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import jdtools.collection.Pair;
import jdtools.util.MiscUtil;
import wfDataManager.client.cache.WarframeItemCache;
import wfDataManager.client.db.manager.ResourceManager;
import wfDataModel.model.data.KillerData;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.logging.Log;
import wfDataModel.model.util.DBUtil;
import wfDataModel.service.type.GameDataType;
import wfDataModel.service.type.PlatformType;

/**
 * Dao class for managing game-related data (e.g. player stats, weapon stats)
 * @author MatNova
 *
 */
public class GameDataDao {

	private static final String LOG_ID = GameDataDao.class.getSimpleName();

	public static void updatePlayerData(Connection conn, Collection<PlayerData> players, GameDataType dataType) {
		PreparedStatement psSelect = null;
		PreparedStatement psInsert = null;
		PreparedStatement psInsertProfile = null;
		PreparedStatement psUpdate = null;
		PreparedStatement psUpdateName = null;
		PreparedStatement psUpdatePastNames = null;

		ResultSet rs = null;

		try {
			psSelect = conn.prepareStatement("SELECT NAME AS PLAYER, PAST_NAMES FROM PLAYER_PROFILE WHERE UID=?");
			psUpdate = conn.prepareStatement("UPDATE PLAYER_DATA SET KILLS = KILLS + ?, DEATHS = DEATHS + ?, MECHANICS = MECHANICS + ?, GOALS = GOALS + ?, PASSES = PASSES + ?, INTERCEPTIONS = INTERCEPTIONS + ?, CAPTURES = CAPTURES + ?, ROUNDS = ROUNDS + ?, LAST_SEEN = CURRENT_DATE WHERE UID=?");

			for (PlayerData data : players) {
				boolean isLunaro = data.isForLunaro();
				int result = -1;
				String curDBName = null;
				String pastNames = null;
				boolean profileExists = false;

				psSelect.setString(1, data.getUID());
				rs = psSelect.executeQuery();

				if (rs.next()) {
					curDBName = rs.getString("PLAYER");
					pastNames = rs.getString("PAST_NAMES");
					profileExists = true; // Should always be true here
				}

				// Release these for now
				ResourceManager.releaseResources(rs);

				if (profileExists) {
					psUpdate.setInt(1, isLunaro ? 0 : data.getKills());
					psUpdate.setInt(2, isLunaro ? 0 : data.getDeaths());
					psUpdate.setInt(3, isLunaro ? 0 : data.getMechanics());
					psUpdate.setInt(4, isLunaro ? data.getMechanics() : 0);
					psUpdate.setInt(5, isLunaro ? data.getKills() : 0);
					psUpdate.setInt(6, isLunaro ? data.getDeaths() : 0);
					psUpdate.setInt(7, data.getCaptures());
					psUpdate.setInt(8, data.getRounds());
					psUpdate.setString(9, data.getUID());
					result = psUpdate.executeUpdate();
				} else {
					if (psInsertProfile == null) {
						psInsertProfile = conn.prepareStatement("INSERT INTO PLAYER_PROFILE (NAME, UID, PLATFORM) VALUES (?,?,?)");
					}
					psInsertProfile.setString(1, data.getPlayerName());
					psInsertProfile.setString(2, data.getUID());
					psInsertProfile.setInt(3, data.getPlatform().getCode());
					psInsertProfile.executeUpdate();

					if (psInsert == null) {
						psInsert = conn.prepareStatement("INSERT INTO PLAYER_DATA (UID,KILLS,DEATHS,MECHANICS,GOALS,PASSES,INTERCEPTIONS,CAPTURES,ROUNDS,LAST_SEEN) VALUES(?,?,?,?,?,?,?,?,?,CURRENT_DATE)");
					}
					psInsert.setString(1, data.getUID());
					psInsert.setInt(2, isLunaro ? 0 : data.getKills());
					psInsert.setInt(3, isLunaro ? 0 : data.getDeaths());
					psInsert.setInt(4, isLunaro ? 0 : data.getMechanics());
					psInsert.setInt(5, isLunaro ? data.getMechanics() : 0);
					psInsert.setInt(6, isLunaro ? data.getKills() : 0);
					psInsert.setInt(7, isLunaro ? data.getDeaths() : 0 );
					psInsert.setInt(8, data.getCaptures());
					psInsert.setInt(9, data.getRounds());
					result = psInsert.executeUpdate();
				}

				if (result != 1) {
					Log.warn(LOG_ID + ".updatePlayerData() : May not have updated player data for player=" + data.getPlayerName() + ", result count = " + result);
				}

				if (!MiscUtil.isEmpty(curDBName) && !curDBName.equals(data.getPlayerName())) {
					
					if (GameDataType.HISTORICAL_GAME_DATA.equals(dataType) && psUpdateName == null) {
						psUpdatePastNames = conn.prepareStatement("UPDATE PLAYER_PROFILE SET PAST_NAMES = ? WHERE UID=?");
					} else if (GameDataType.GAME_DATA.equals(dataType) && psUpdateName == null) {
						psUpdateName = conn.prepareStatement("UPDATE PLAYER_PROFILE SET NAME = ?, PAST_NAMES = ? WHERE UID=?");
					}
					
					Set<String> pastNamesArr = !MiscUtil.isEmpty(pastNames) ? new Gson().fromJson(pastNames, new TypeToken<HashSet<String>>(){}.getType()) : new HashSet<String>();
					pastNamesArr.add(curDBName);
					
					if (GameDataType.HISTORICAL_GAME_DATA.equals(dataType)) {
						psUpdatePastNames.setString(1, new Gson().toJson(pastNamesArr));
						psUpdatePastNames.setString(2, data.getUID());
						result = psUpdatePastNames.executeUpdate();
					} else {
						psUpdateName.setString(1, data.getPlayerName());
						psUpdateName.setString(2, new Gson().toJson(pastNamesArr));
						psUpdateName.setString(3, data.getUID());
						result = psUpdateName.executeUpdate();
					}

					if (result != 1) {
						Log.warn(LOG_ID + ".updatePlayerData() : Did not update player profile data for " + data.getPlayerName() + ", result count = " + result);
					} else {
						Log.info(LOG_ID + ".updatePlayerData() : Updated player name from " + curDBName + " to " + data.getPlayerName() + " for ID " + data.getUID());
					}
				}
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".updatePlayerData() : Error occurred -> " + e.getLocalizedMessage());
		} finally {
			ResourceManager.releaseResources(rs);
			ResourceManager.releaseResources(psSelect, psInsert, psInsertProfile, psUpdate, psUpdateName, psUpdatePastNames);
		}
	}

	public static void updateWeeklyPlayerData(Connection conn, Collection<PlayerData> players, LocalDate sundayDate) throws SQLException {
		PreparedStatement psSelect = null;
		PreparedStatement psUpdate = null;
		PreparedStatement psInsert = null;

		ResultSet rs = null;
		try {
			psSelect = conn.prepareStatement("SELECT WEAPON_KILLS, KILLED_BY FROM WEEKLY_DATA WHERE UID = ? AND WEEK_DATE = ? AND GAME_MODE = ? AND ELO = ?");
			psUpdate = conn.prepareStatement("UPDATE WEEKLY_DATA SET KILLS = KILLS + ?, DEATHS = DEATHS + ?, MECHANICS = MECHANICS + ?, CAPTURES = CAPTURES + ?, ROUNDS = ROUNDS + ?, WEAPON_KILLS = ?, KILLED_BY = ?, TOTAL_TIME = TOTAL_TIME + ? WHERE UID = ? AND WEEK_DATE = ? AND GAME_MODE = ? AND ELO = ?");

			for (PlayerData data : players) {
				if (data.hasData()) {
					boolean alreadyHasData = true;
					Map<String, Integer> wepKills = null;
					Map<String, KillerData> killedBy = null;

					psSelect.setString(1, data.getUID());
					psSelect.setObject(2, sundayDate);
					psSelect.setInt(3, data.getGameMode().getId());
					psSelect.setInt(4, data.getEloRating().getCode());
					rs = psSelect.executeQuery();

					if (rs.next()) {
						String weaponKills = rs.getString("WEAPON_KILLS");
						String playerKills = rs.getString("KILLED_BY");
						// Combine the DB values and current player values in a map outside of the PlayerData object
						// This is so that any data sent to the service does not end up with combined DB plus current values
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

					ResourceManager.releaseResources(rs);

					int result = -1;
					if (alreadyHasData) {
						psUpdate.setInt(1, data.getKills());
						psUpdate.setInt(2, data.getDeaths());
						psUpdate.setInt(3, data.getMechanics());
						psUpdate.setInt(4, data.getCaptures());
						psUpdate.setInt(5, data.getRounds());
						psUpdate.setString(6, DBUtil.createDBMap(wepKills));
						psUpdate.setString(7, DBUtil.createDBMap(killedBy));
						psUpdate.setInt(8, data.getTotalTime());
						psUpdate.setString(9, data.getUID());
						psUpdate.setObject(10, sundayDate);
						psUpdate.setInt(11, data.getGameMode().getId());
						psUpdate.setInt(12, data.getEloRating().getCode());
						result = psUpdate.executeUpdate();
						if (result != 1) {
							Log.warn(LOG_ID + ".updateWeeklyPlayerData() : Failed to update weekly data for player=" + data.getPlayerName() + " (" + data.getUID() + ")");
						}
					} else {
						// In this case, need to add them to DB
						if (psInsert == null) {
							psInsert = conn.prepareStatement("INSERT INTO WEEKLY_DATA (WEEK_DATE,UID,GAME_MODE,ELO,KILLS,DEATHS,MECHANICS,CAPTURES,ROUNDS,WEAPON_KILLS,KILLED_BY,TOTAL_TIME) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)");
						}
						psInsert.setObject(1, sundayDate);
						psInsert.setString(2, data.getUID());
						psInsert.setInt(3, data.getGameMode().getId());
						psInsert.setInt(4, data.getEloRating().getCode());
						psInsert.setInt(5, data.getKills());
						psInsert.setInt(6, data.getDeaths());
						psInsert.setInt(7, data.getMechanics());
						psInsert.setInt(8, data.getCaptures());
						psInsert.setInt(9, data.getRounds());
						psInsert.setString(10, DBUtil.createDBMap(data.getWeaponKills()));
						psInsert.setString(11, DBUtil.createDBMap(data.getKilledBy()));
						psInsert.setInt(12, data.getTotalTime());
						result = psInsert.executeUpdate();

						if (result != 1) {
							Log.warn(LOG_ID + ".updateWeeklyPlayerData() : Did not insert weekly data for player=" + data.getPlayerName() + " (" + data.getUID() + ")");
						}
					}
				}
			}
		} finally {
			ResourceManager.releaseResources(rs);
			ResourceManager.releaseResources(psSelect, psUpdate, psInsert);

		}
	}

	public static void updateWeaponData(Connection conn, Collection<PlayerData> players) throws SQLException {
		for (PlayerData player : players) {
			updateWeaponData(conn, player.getWeaponKills());
		}
	}

	public static void updateWeaponData(Connection conn, Map<String, Integer> weaponKills) throws SQLException {
		PreparedStatement psUpdate = null;
		PreparedStatement psInsert = null;
		PreparedStatement psInsertInfo = null;

		try {

			psUpdate = conn.prepareStatement("UPDATE WEAPON_DATA SET KILLS=KILLS + ? WHERE WEAPON=?");

			for (String weapon : weaponKills.keySet()) {
				psUpdate.setInt(1, weaponKills.get(weapon));
				psUpdate.setString(2, weapon);
				int result = psUpdate.executeUpdate();

				// In this case, need to add them to DB
				if (result != 1) {
					if (psInsert == null) {
						psInsert = conn.prepareStatement("INSERT INTO WEAPON_DATA (WEAPON,KILLS) VALUES(?,?)");
						psInsertInfo = conn.prepareStatement("INSERT INTO WEAPON_INFO (WEAPON, WEAPON_REAL) VALUES (?,?)");
					}
					psInsert.setString(1, weapon);
					psInsert.setInt(2, weaponKills.get(weapon));
					result = psInsert.executeUpdate();

					psInsertInfo.setString(1, weapon);
					psInsertInfo.setString(2, WarframeItemCache.singleton().getItemName(weapon));
					result = psInsertInfo.executeUpdate();
				}

				if (result != 1) {
					Log.warn(LOG_ID + ".updateWeaponData() : Did not update weapon data for " + weapon);
				}
			}


		} finally {
			ResourceManager.releaseResources(psUpdate, psInsert, psInsertInfo);
		}
	}

	public static void addDailyWeaponData(Connection conn, Collection<PlayerData> players, LocalDate dailyDate) throws SQLException {
		for (PlayerData player : players) {
			addDailyWeaponData(conn, player.getWeaponKills(), dailyDate);
		}
	}

	public static void addDailyWeaponData(Connection conn, Map<String, Integer> weaponKills, LocalDate dailyDate) throws SQLException {
		PreparedStatement psUpdate = null;
		PreparedStatement psInsert = null;

		try {
			psUpdate = conn.prepareStatement("UPDATE DAILY_WEAPON_DATA SET KILLS = KILLS + ? WHERE WEAPON = ? AND DATE = ?");

			for (String weapon : weaponKills.keySet()) {
				psUpdate.setInt(1, weaponKills.get(weapon));
				psUpdate.setString(2, weapon);
				psUpdate.setObject(3, dailyDate);
				int result = psUpdate.executeUpdate();

				if (result != 1) {
					if (psInsert == null) {
						psInsert = conn.prepareStatement("INSERT INTO DAILY_WEAPON_DATA (DATE,WEAPON,KILLS) VALUES(?,?,?)");
					}
					psInsert.setObject(1, dailyDate);
					psInsert.setString(2, weapon);
					psInsert.setInt(3, weaponKills.get(weapon));

					result = psInsert.executeUpdate();
					if (result != 1) {
						Log.warn(LOG_ID + ".addDailyWeaponData() : Did not add weapon data for " + weapon);
					}
				}
			}
		} finally {
			ResourceManager.releaseResources(psUpdate, psInsert);
		}
	}

	public static List<Pair<String, String>> findMatchingItems(String itemKey, boolean searchRealName) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		List<Pair<String, String>> matches = new ArrayList<Pair<String, String>>();
		try {
			conn = ResourceManager.getDBConnection(); 
			ps = conn.prepareStatement("SELECT * FROM WEAPON_INFO WHERE LOWER(" + (searchRealName ? "WEAPON_REAL" : "WEAPON") + ") LIKE LOWER(?)");
			ps.setString(1, itemKey);
			rs = ps.executeQuery();
			while (rs.next()) {
				matches.add(new Pair<>(rs.getString("WEAPON"), rs.getString("WEAPON_REAL")));
			}

		} catch (Exception e) {
			Log.error(LOG_ID + ".findMatchingItems() : Error occurred -> " + e);
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}
		return matches;
	}

	public static List<String> findUnmappedItems() {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		List<String> items = new ArrayList<String>();
		
		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("SELECT WEAPON FROM WEAPON_INFO WHERE WEAPON_REAL IS NULL OR WEAPON_REAL = WEAPON || '_'"); // NOTE: This query will NOT work with MySql as MySql requires concat() function instead!!!
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
	
	public static PlayerData getPlayerByUID(String uid) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		PlayerData playerData = null;
		try {
			conn = ResourceManager.getDBConnection(); 
			ps = conn.prepareStatement("SELECT * FROM PLAYER_PROFILE WHERE UID=?");
			ps.setString(1, uid);
			rs = ps.executeQuery();
			if (rs.next()) {
				playerData = new PlayerData();
				playerData.setPlayerName(rs.getString("NAME"));
				playerData.setUID(uid);
				playerData.setPlatform(PlatformType.codeToType(rs.getInt("PLATFORM")));
			}

		} catch (Exception e) {
			Log.error(LOG_ID + ".getPlayerByUID() : Error occurred -> " + e);
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}
		return playerData;
	}

	public static List<PlayerData> findPlayersByName(String name) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		List<PlayerData> data = new ArrayList<PlayerData>();
		try {
			conn = ResourceManager.getDBConnection(); 
			ps = conn.prepareStatement("SELECT * FROM PLAYER_PROFILE WHERE LOWER(NAME) LIKE (?)");
			ps.setString(1, name);
			rs = ps.executeQuery();
			while (rs.next()) {
				PlayerData playerData = new PlayerData();
				playerData.setPlayerName(rs.getString("NAME"));
				playerData.setUID(rs.getString("UID"));
				playerData.setPlatform(PlatformType.codeToType(rs.getInt("PLATFORM")));
				data.add(playerData);
			}

		} catch (Exception e) {
			Log.error(LOG_ID + ".findPlayersByName() : Error occurred -> " + e);
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}
		return data;
	}
}
