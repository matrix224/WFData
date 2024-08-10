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
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import jdtools.collection.Pair;
import jdtools.exception.ProcessingException;
import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataManager.client.cache.BanManagerCache;
import wfDataManager.client.cache.PlayerTrackerCache;
import wfDataManager.client.cache.WarframeItemCache;
import wfDataManager.client.db.manager.ResourceManager;
import wfDataManager.client.processor.DBPlayerMergeProcessor;
import wfDataModel.model.data.DBPlayerData;
import wfDataModel.model.data.KillerData;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.WeaponData;
import wfDataModel.model.util.DBUtil;
import wfDataModel.model.util.DateUtil;
import wfDataModel.service.type.GameDataType;
import wfDataModel.service.type.PlatformType;
import wfDataModel.service.type.WeaponType;

/**
 * Dao class for managing game-related data (e.g. player stats, weapon stats)
 * @author MatNova
 *
 */
public final class GameDataDao {

	private static final String LOG_ID = GameDataDao.class.getSimpleName();

	public static void updatePlayerData(Connection conn, Collection<PlayerData> players, GameDataType dataType) {
		PreparedStatement psSelect = null;
		PreparedStatement psInsert = null;
		PreparedStatement psInsertProfile = null;
		PreparedStatement psUpdate = null;
		PreparedStatement psUpdateData = null;

		ResultSet rs = null;

		try {
			psSelect = conn.prepareStatement("SELECT NAME AS PLAYER, PAST_NAMES, PAST_UIDS, UID, AID, PLATFORM FROM PLAYER_PROFILE WHERE UID=? OR (AID IS NOT NULL AND AID=?)");
			psUpdate = conn.prepareStatement("UPDATE PLAYER_DATA SET KILLS = KILLS + ?, DEATHS = DEATHS + ?, MECHANICS = MECHANICS + ?, GOALS = GOALS + ?, PASSES = PASSES + ?, INTERCEPTIONS = INTERCEPTIONS + ?, CAPTURES = CAPTURES + ?, ROUNDS = ROUNDS + ?, LAST_SEEN = CURRENT_DATE WHERE UID=?");

			for (PlayerData data : players) {
				boolean isLunaro = data.isForLunaro();
				int result = -1;
				List<DBPlayerData> dbData = null;
				
				if (MiscUtil.isEmpty(data.getPlayerName()) || MiscUtil.isEmpty(data.getUID())) {
					Log.warn(LOG_ID + ".updatePlayerData() : Ignoring player with missing data -> name=" + data.getPlayerName() + ", uid=" + data.getUID());
					continue;
				}

				psSelect.setString(1, data.getUID());
				psSelect.setString(2, data.getAccountID());
				rs = psSelect.executeQuery();

				while (rs.next()) {
					DBPlayerData dbPlayer = new DBPlayerData();
					dbPlayer.setCurDBName(rs.getString("PLAYER"));
					dbPlayer.setCurDBUID(rs.getString("UID"));
					dbPlayer.setCurDBAID(rs.getString("AID"));
					dbPlayer.setCurPlatform(rs.getInt("PLATFORM"));
					dbPlayer.setPastNames(rs.getString("PAST_NAMES"));
					dbPlayer.setPastUIDs(rs.getString("PAST_UIDS"));
					if (dbData == null) {
						dbData = new ArrayList<DBPlayerData>();
					}
					dbData.add(dbPlayer);
				}

				// Release these for now
				ResourceManager.releaseResources(rs);

				if (!MiscUtil.isEmpty(dbData)) {
					if (dbData.size() > 1) {
						Log.info(LOG_ID, ".updatePlayerData() : Will attempt to merge ", data.getUID(), " into at least " + (dbData.size() - 1), " other players");
						DBPlayerMergeProcessor merger = new DBPlayerMergeProcessor();
						for (DBPlayerData dbPlayer : dbData) {
							if (!dbPlayer.getCurDBUID().equals(data.getUID())) {
								// If their platforms are different or they're PSN with the same name, will consider okay to merge
								// Otherwise will consider an issue for manual review
								if (dbPlayer.getCurPlatform() != data.getPlatform() ||  (PlatformType.PSN.getCode() == dbPlayer.getCurPlatform() && (dbPlayer.getCurDBAID().equals(data.getAccountID()) || dbPlayer.getCurDBName().equals(data.getPlayerName())))) {
									merger.mergePlayerData(conn, dbPlayer.getCurDBUID(), data.getUID());
									merger.mergeWeeklyData(conn, dbPlayer.getCurDBUID(), data.getUID());
									merger.mergePlayerProfile(conn, data.getAccountID(), dbPlayer.getCurDBUID(), data.getUID());
									Log.info(LOG_ID, ".updatePlayerData() : Merged players -> dbUID=" + dbPlayer.getCurDBUID(), ", dbPlatform=" + dbPlayer.getCurPlatform() + ", dbAID=", dbPlayer.getCurDBAID(), ", dbName=", dbPlayer.getCurDBName(), ", curUID=" + data.getUID(), ", curPlatform=" + data.getPlatform(), ", curAID=", data.getAccountID(), ", curName=", data.getPlayerName());
								} else {
									throw new ProcessingException("Unhandled merge scenario: dbUID=" + dbPlayer.getCurDBUID() + ", dbPlatform=" + dbPlayer.getCurPlatform() + ", dbAID=" + dbPlayer.getCurDBAID() + ", curUID=" + data.getUID() + ", curPlatform=" + data.getPlatform() + ", curAID=" + data.getAccountID());
								}
							}
						}

					} else {
						DBPlayerData dbPlayer = dbData.get(0);
						String curDBUID = dbPlayer.getCurDBUID();
						String curDBAID = dbPlayer.getCurDBAID();
						String pastUIDs = dbPlayer.getPastUIDs();
						String pastNames = dbPlayer.getPastNames();
						String curDBName = dbPlayer.getCurDBName();
						int curPlatform = dbPlayer.getCurPlatform();


						// Player's UID has changed, which means we found them as already existing based on AID
						// This is a hefty update as it requires a decent amount of updating and processing
						// Need to update:
						//  * Player profile (only past_uids if in historical mode)
						// and if NOT in Historical mode:
						//  * Ban cache (in case player happened to be banned at some point) and DB bans (permanent and temp)
						//  * Tracker cache and Tracker DB entries (including known alts)
						//  * Player data
						//  * Weekly data
						//  * Weekly data killed by value - requires fetching all rows and parsing out the field for the UID and updating it
						// Otherwise if we're in historical mode, we change the player object's UID to the current DB one so that going forward,
						// any historical data will be attributed to their current UID
						if (!curDBUID.equals(data.getUID())) {

							if (GameDataType.HISTORICAL_GAME_DATA.equals(dataType)) {
								psUpdateData = conn.prepareStatement("UPDATE PLAYER_PROFILE SET PAST_UIDS = ? WHERE UID=?");
							} else if (GameDataType.GAME_DATA.equals(dataType)) {
								psUpdateData = conn.prepareStatement("UPDATE PLAYER_PROFILE SET UID = ?, PAST_UIDS = ? WHERE UID=?");
							}

							Set<String> pastUIDsArr = !MiscUtil.isEmpty(pastUIDs) ? new Gson().fromJson(pastUIDs, new TypeToken<HashSet<String>>(){}.getType()) : new HashSet<String>();

							if (GameDataType.HISTORICAL_GAME_DATA.equals(dataType)) {
								pastUIDsArr.add(data.getUID());
								psUpdateData.setString(1, DBUtil.createDBCollection(pastUIDsArr));
								psUpdateData.setString(2, curDBUID);
							} else {
								pastUIDsArr.add(curDBUID);
								psUpdateData.setString(1, data.getUID());
								psUpdateData.setString(2, DBUtil.createDBCollection(pastUIDsArr));
								psUpdateData.setString(3, curDBUID);
							}
							result = psUpdateData.executeUpdate();

							if (result != 1) {
								Log.warn(LOG_ID + ".updatePlayerData() : Did not update player profile UID for " + data.getPlayerName() + ", result count = " + result);
							} else {
								Log.info(LOG_ID + ".updatePlayerData() : Updated player UID from " + curDBUID + " to " + data.getUID() + " for player " + data.getPlayerName());
							}

							ResourceManager.releaseResources(psUpdateData);

							// If Historical mode, update player object's UID to current DB one
							// so that any processing going forward will apply data to current UID
							if (GameDataType.HISTORICAL_GAME_DATA.equals(dataType)) {
								data.setUID(curDBUID);
							} else {
								// Otherwise for game data, then update other tables to reflect new UID

								// STEP 2: Update ban data
								BanManagerCache.singleton().updateBanData(curDBUID, data.getUID());

								// STEP 3: Update tracker data
								PlayerTrackerCache.singleton().updatePlayerTracker(curDBUID, data.getUID());

								// STEP 4: Update player data
								psUpdateData = conn.prepareStatement("UPDATE PLAYER_DATA SET UID=? WHERE UID=?");
								psUpdateData.setString(1, data.getUID());
								psUpdateData.setString(2, curDBUID);
								result = psUpdateData.executeUpdate();
								if (result <= 0) {
									Log.warn(LOG_ID + ".updatePlayerData() : Did not update player data UID for " + data.getPlayerName() + ", result count = " + result);
								} else {
									Log.info(LOG_ID + ".updatePlayerData() : Updated player data UID from " + curDBUID + " to " + data.getUID() + " for player " + data.getPlayerName());
								}
								ResourceManager.releaseResources(psUpdateData);

								// STEP 5: Update weekly player data
								psUpdateData = conn.prepareStatement("UPDATE WEEKLY_DATA SET UID=? WHERE UID=?");
								psUpdateData.setString(1, data.getUID());
								psUpdateData.setString(2, curDBUID);
								result = psUpdateData.executeUpdate();
								if (result <= 0) {
									Log.warn(LOG_ID + ".updatePlayerData() : Did not update weekly data UID for " + data.getPlayerName() + ", result count = " + result);
								} else {
									Log.info(LOG_ID + ".updatePlayerData() : Updated weekly data UID from " + curDBUID + " to " + data.getUID() + " for player " + data.getPlayerName());
								}
								ResourceManager.releaseResources(psUpdateData);

								// STEP 6: Update all weekly data killed_by references
								result = updateWeeklyDataReferences(conn, curDBUID, data.getUID());
								if (result <= 0) {
									Log.warn(LOG_ID + ".updatePlayerData() : Did not update weekly kill reference UID for " + data.getPlayerName() + ", result count = " + result);
								} else {
									Log.info(LOG_ID + ".updatePlayerData() : Updated weekly kill reference UID from " + curDBUID + " to " + data.getUID() + " for player " + data.getPlayerName());
								}
							}
						}

						// If no DB accountID is set, or DB one doesn't equal the current one,
						// and it's either missing for historical mode or otherwise just empty / not matching for regular mode,
						// then we'll update it
						if (!MiscUtil.isEmpty(data.getAccountID()) && ((MiscUtil.isEmpty(curDBAID) || !curDBAID.equals(data.getAccountID())) && ((GameDataType.HISTORICAL_GAME_DATA.equals(dataType) && MiscUtil.isEmpty(curDBAID)) || GameDataType.GAME_DATA.equals(dataType)))) {
							psUpdateData = conn.prepareStatement("UPDATE PLAYER_PROFILE SET AID=? WHERE UID=?");
							psUpdateData.setString(1, data.getAccountID());
							psUpdateData.setString(2, data.getUID());
							result = psUpdateData.executeUpdate();
							if (result <= 0) {
								Log.warn(LOG_ID + ".updatePlayerData() : Did not update player data AcctID for " + data.getUID() + ", result count = " + result);
							} else {
								Log.info(LOG_ID + ".updatePlayerData() : Updated player data AcctID from " + curDBAID + " to " + data.getAccountID() + " for player " + data.getUID());
							}
							ResourceManager.releaseResources(psUpdateData);
						}


						if (!curDBName.equals(data.getPlayerName())) {
							if (GameDataType.HISTORICAL_GAME_DATA.equals(dataType)) {
								psUpdateData = conn.prepareStatement("UPDATE PLAYER_PROFILE SET PAST_NAMES = ? WHERE UID=?");
							} else if (GameDataType.GAME_DATA.equals(dataType)) {
								psUpdateData = conn.prepareStatement("UPDATE PLAYER_PROFILE SET NAME = ?, PAST_NAMES = ? WHERE UID=?");
							}

							Set<String> pastNamesArr = !MiscUtil.isEmpty(pastNames) ? new Gson().fromJson(pastNames, new TypeToken<HashSet<String>>(){}.getType()) : new HashSet<String>();

							if (GameDataType.HISTORICAL_GAME_DATA.equals(dataType)) {
								pastNamesArr.add(data.getPlayerName());
								psUpdateData.setString(1, new Gson().toJson(pastNamesArr));
								psUpdateData.setString(2, data.getUID());
							} else {
								pastNamesArr.add(curDBName);
								psUpdateData.setString(1, data.getPlayerName());
								psUpdateData.setString(2, new Gson().toJson(pastNamesArr));
								psUpdateData.setString(3, data.getUID());
							}
							result = psUpdateData.executeUpdate();

							if (result != 1) {
								Log.warn(LOG_ID + ".updatePlayerData() : Did not update player profile name for " + data.getPlayerName() + ", result count = " + result);
							} else {
								Log.info(LOG_ID + ".updatePlayerData() : Updated player name from " + curDBName + " to " + data.getPlayerName() + " for ID " + data.getUID());
							}

							ResourceManager.releaseResources(psUpdateData);
						}

						// Ignore if this is for Historical since we don't want to set their platform to an older one
						if (curPlatform != data.getPlatform() && GameDataType.GAME_DATA.equals(dataType)) {
							psUpdateData = conn.prepareStatement("UPDATE PLAYER_PROFILE SET PLATFORM = ? WHERE UID = ?");
							psUpdateData.setInt(1, data.getPlatform());
							psUpdateData.setString(2, data.getUID());
							result = psUpdateData.executeUpdate();
							if (result != 1) {
								Log.warn(LOG_ID + ".updatePlayerData() : Did not update player profile platform for " + data.getPlayerName() + ", result count = " + result);
							} else {
								Log.info(LOG_ID + ".updatePlayerData() : Updated player platform from " + curPlatform + " to " + data.getPlatform() + " for ID " + data.getUID());
							}
						}
					}
					
					// Regardless of player info updates / merging, we know we have an entry for this player already, so want to update their data
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
						psInsertProfile = conn.prepareStatement("INSERT INTO PLAYER_PROFILE (NAME, UID, AID, PLATFORM) VALUES (?,?,?,?)");
					}
					psInsertProfile.setString(1, data.getPlayerName());
					psInsertProfile.setString(2, data.getUID());
					psInsertProfile.setString(3, data.getAccountID());
					psInsertProfile.setInt(4, data.getPlatform());
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

			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".updatePlayerData() : Error occurred -> " + e.getLocalizedMessage());
		} finally {
			ResourceManager.releaseResources(rs);
			ResourceManager.releaseResources(psSelect, psInsert, psInsertProfile, psUpdate, psUpdateData);
		}
	}

	public static void updateWeeklyPlayerData(Connection conn, Collection<PlayerData> players, LocalDate sundayDate) throws SQLException {
		PreparedStatement psSelect = null;
		PreparedStatement psUpdate = null;
		PreparedStatement psInsert = null;

		ResultSet rs = null;
		try {
			psSelect = conn.prepareStatement("SELECT WEAPON_KILLS, KILLED_BY FROM WEEKLY_DATA WHERE UID = ? AND WEEK_DATE = ? AND GAME_MODE = ? AND ELO = ? AND PLATFORM = ?");
			psUpdate = conn.prepareStatement("UPDATE WEEKLY_DATA SET KILLS = KILLS + ?, DEATHS = DEATHS + ?, MECHANICS = MECHANICS + ?, CAPTURES = CAPTURES + ?, ROUNDS = ROUNDS + ?, WEAPON_KILLS = ?, KILLED_BY = ?, TOTAL_TIME = TOTAL_TIME + ? WHERE UID = ? AND WEEK_DATE = ? AND GAME_MODE = ? AND ELO = ? AND PLATFORM = ?");

			for (PlayerData data : players) {
				if (data.hasData()) {
					boolean alreadyHasData = true;
					Map<String, Integer> wepKills = null;
					Map<String, KillerData> killedBy = null;
					int platform = data.getPlatform();

					psSelect.setString(1, data.getUID());
					psSelect.setObject(2, sundayDate);
					psSelect.setInt(3, data.getGameMode().getId());
					psSelect.setInt(4, data.getEloRating().getCode());
					psSelect.setInt(5, platform);
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
						psUpdate.setInt(13, platform);
						result = psUpdate.executeUpdate();
						if (result != 1) {
							Log.warn(LOG_ID + ".updateWeeklyPlayerData() : Failed to update weekly data for player=" + data.getPlayerName() + " (" + data.getUID() + ")");
						}
					} else {
						// In this case, need to add them to DB
						if (psInsert == null) {
							psInsert = conn.prepareStatement("INSERT INTO WEEKLY_DATA (WEEK_DATE,UID,GAME_MODE,ELO,KILLS,DEATHS,MECHANICS,CAPTURES,ROUNDS,WEAPON_KILLS,KILLED_BY,TOTAL_TIME,PLATFORM) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)");
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
						psInsert.setInt(13, platform);
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

	private static int updateWeeklyDataReferences(Connection conn, String oldUID, String newUID) throws SQLException {
		PreparedStatement ps = null;
		PreparedStatement psUpdate = null;
		ResultSet rs = null;
		int numUpdated = 0;

		try {
			psUpdate = conn.prepareStatement("UPDATE WEEKLY_DATA SET KILLED_BY = ? WHERE UID=? AND WEEK_DATE=? AND GAME_MODE = ? AND ELO = ?");
			ps = conn.prepareStatement("SELECT UID,WEEK_DATE,GAME_MODE,ELO,KILLED_BY FROM WEEKLY_DATA WHERE KILLED_BY LIKE ?");
			ps.setString(1, "%\"" + oldUID + "\"%");
			rs = ps.executeQuery();
			while (rs.next()) {
				String weekUID = rs.getString("UID");
				int gameMode = rs.getInt("GAME_MODE");
				int elo = rs.getInt("ELO");
				LocalDate weekDate = DateUtil.getWeekDate(rs.getString("WEEK_DATE"));
				Map<String, KillerData> killedBy = DBUtil.parseDBMap(rs.getString("KILLED_BY"), String.class, KillerData.class);
				KillerData killerData = killedBy.get(oldUID);
				if (killedBy.containsKey(newUID)) {
					killerData.combine(killedBy.get(newUID));
				}
				killedBy.put(newUID, killerData);
				killedBy.remove(oldUID);

				psUpdate.setString(1, DBUtil.createDBMap(killedBy));
				psUpdate.setString(2, weekUID);
				psUpdate.setObject(3, weekDate);
				psUpdate.setInt(4, gameMode);
				psUpdate.setInt(5, elo);
				numUpdated += psUpdate.executeUpdate();
			}

		} finally {
			ResourceManager.releaseResources(ps, psUpdate);
			ResourceManager.releaseResources(rs);
		}
		return numUpdated;
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
						psInsertInfo = conn.prepareStatement("INSERT INTO WEAPON_INFO (WEAPON, WEAPON_REAL, WEAPON_TYPE) VALUES (?,?,?)");
					}
					psInsert.setString(1, weapon);
					psInsert.setInt(2, weaponKills.get(weapon));
					result = psInsert.executeUpdate();

					WeaponData wepData = WarframeItemCache.singleton().getItemInfo(weapon);
					psInsertInfo.setString(1, weapon);
					psInsertInfo.setString(2, wepData == null ? null : wepData.getRealName());
					psInsertInfo.setString(3, wepData == null || wepData.getType() == null ? WeaponType.UNKNOWN.name() : wepData.getType().name());
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

	public static SortedMap<String, WeaponType> findUnmappedItems() {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		SortedMap<String, WeaponType> items = new TreeMap<String, WeaponType>();

		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("SELECT WEAPON,WEAPON_TYPE FROM WEAPON_INFO WHERE WEAPON_REAL IS NULL OR WEAPON_REAL = WEAPON || '_' OR WEAPON_TYPE IS NULL OR WEAPON_TYPE='UNKNOWN'"); // NOTE: This query will NOT work with MySql as MySql requires concat() function instead!!!
			rs = ps.executeQuery();
			while (rs.next()) {
				items.put(rs.getString("WEAPON"), WeaponType.valueOf(rs.getString("WEAPON_TYPE")));
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".findUnmappedItems() : Error occurred -> " + e);
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}

		return items;
	}

	public static boolean updateItemName(String item, String itemName, WeaponType type) {
		Connection conn = null;
		PreparedStatement ps = null;
		boolean mapped = false;

		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("UPDATE WEAPON_INFO SET WEAPON_REAL = ?, WEAPON_TYPE=? WHERE WEAPON = ?");
			ps.setString(1, itemName);
			ps.setString(2, type.name());
			ps.setString(3, item);
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
				playerData.setPlatform(rs.getInt("PLATFORM"));
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
			ps = conn.prepareStatement("SELECT * FROM PLAYER_PROFILE WHERE LOWER(NAME) LIKE LOWER(?)");
			ps.setString(1, name);
			rs = ps.executeQuery();
			while (rs.next()) {
				PlayerData playerData = new PlayerData();
				playerData.setPlayerName(rs.getString("NAME"));
				playerData.setUID(rs.getString("UID"));
				playerData.setPlatform(rs.getInt("PLATFORM"));
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
