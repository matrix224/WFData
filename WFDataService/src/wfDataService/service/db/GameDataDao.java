package wfDataService.service.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import jdtools.exception.ProcessingException;
import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataModel.model.data.DBPlayerData;
import wfDataModel.model.data.KillerData;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.data.WeaponData;
import wfDataModel.model.util.DBUtil;
import wfDataModel.model.util.DateUtil;
import wfDataModel.service.type.GameDataType;
import wfDataModel.service.type.GameMode;
import wfDataModel.service.type.PlatformType;
import wfDataModel.service.type.WeaponType;
import wfDataService.service.cache.BanDataCache;
import wfDataService.service.cache.WarframeItemCache;
import wfDataService.service.data.ServerClientData;
import wfDataService.service.db.manager.ResourceManager;
import wfDataService.service.processor.DBPlayerMergeProcessor;

/**
 * Dao that supports storage of game-related data (e.g player profiles / stats, weapon data, etc) in the DB
 * @author MatNova
 *
 */
public class GameDataDao {

	private static final String LOG_ID = GameDataDao.class.getSimpleName();

	public static boolean updateGameData(ServerClientData serverClient, ServerData server, long time, String zoneId, GameDataType dataType) {
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
		LocalDate latestSunday = DateUtil.getLatestSunday(time, !MiscUtil.isEmpty(zoneId) ? ZoneId.of(zoneId) : ZoneOffset.of(server.getTimeStats().getZoneId())).toLocalDate();
		LocalDate dailyDate = DateUtil.getDate(time, !MiscUtil.isEmpty(zoneId) ? ZoneId.of(zoneId) : ZoneOffset.of(server.getTimeStats().getZoneId())).toLocalDate();
		Map<String, Integer> weaponData = new HashMap<String, Integer>(); // Weapon -> kills. Accumulated for all players in given server
		boolean success = true;

		try {
			conn = ResourceManager.getDBConnection(false);

			for (PlayerData data : server.getParsedPlayers()) {
				List<DBPlayerData> dbData = null;
				boolean sidExists = false;
				int result = -1;

				if (MiscUtil.isEmpty(data.getPlayerName()) || MiscUtil.isEmpty(data.getUID())) {
					Log.warn(LOG_ID + ".updateGameData() : Ignoring player with empty name or UID -> name=" + data.getPlayerName() + ", uid=" + data.getUID());
					continue;
				}

				// First check if we have this player by UID
				// Added this much further down the line, so (at least at first) don't expect to match on this
				ps = conn.prepareStatement("SELECT P.NAME AS PLAYER, P.PAST_NAMES, P.PAST_UIDS, P.UID, P.AID, P.PLATFORM, D.SID AS SID FROM PLAYER_PROFILE P LEFT JOIN PLAYER_DATA D ON D.UID=P.UID AND D.SID=? WHERE P.UID=? OR (P.AID IS NOT NULL AND P.AID=?)");
				ps.setInt(1, serverClient.getServerClientID());
				ps.setString(2, data.getUID());
				ps.setString(3, data.getAccountID());
				rs = ps.executeQuery();

				while (rs.next()) {
					DBPlayerData dbPlayer = new DBPlayerData();
					dbPlayer.setCurDBName(rs.getString("PLAYER"));
					dbPlayer.setCurDBUID(rs.getString("UID"));
					dbPlayer.setCurDBAID(rs.getString("AID"));
					dbPlayer.setCurPlatform(rs.getInt("PLATFORM"));
					dbPlayer.setPastNames(rs.getString("PAST_NAMES"));
					dbPlayer.setPastUIDs(rs.getString("PAST_UIDS"));
					sidExists = !MiscUtil.isEmpty(rs.getString("SID"));
					if (dbData == null) {
						dbData = new ArrayList<DBPlayerData>();
					}
					dbData.add(dbPlayer);
				}

				// Release these for now
				ResourceManager.releaseResources(ps,rs);


				if (!MiscUtil.isEmpty(dbData)) {

					if (dbData.size() > 1) {
						Log.info(LOG_ID, ".updateGameData() : Will attempt to merge ", data.getUID(), " into at least " + (dbData.size() - 1), " other players");
						DBPlayerMergeProcessor merger = new DBPlayerMergeProcessor();
						for (DBPlayerData dbPlayer : dbData) {
							if (!dbPlayer.getCurDBUID().equals(data.getUID())) {
								// If their platforms are different or they're PSN/IOS/Android with the same name, will consider okay to merge
								// For IOS, this was added 10/31/25 because UID format changed for them at some point
								// Android added 3/1/2026 because UID may change depending on device
								// Otherwise will consider an issue for manual review
								if (dbPlayer.getCurPlatform() != data.getPlatform() ||  ((PlatformType.PSN.getCode() == dbPlayer.getCurPlatform() || PlatformType.IOS.getCode() == dbPlayer.getCurPlatform() || PlatformType.ANDROID.getCode() == dbPlayer.getCurPlatform()) && (dbPlayer.getCurDBAID().equals(data.getAccountID()) || dbPlayer.getCurDBName().equals(data.getPlayerName())))) {
									merger.mergePlayerData(conn, dbPlayer.getCurDBUID(), data.getUID());
									merger.mergeWeeklyData(conn, dbPlayer.getCurDBUID(), data.getUID());
									merger.mergeBanLogs(conn, dbPlayer.getCurDBUID(), data.getUID());
									merger.mergePlayerProfile(conn, data.getAccountID(), dbPlayer.getCurDBUID(), data.getUID());
									Log.info(LOG_ID, ".updateGameData() : Merged players -> dbUID=" + dbPlayer.getCurDBUID(), ", dbPlatform=" + dbPlayer.getCurPlatform() + ", dbAID=", dbPlayer.getCurDBAID(), ", dbName=", dbPlayer.getCurDBName(), ", curUID=" + data.getUID(), ", curPlatform=" + data.getPlatform(), ", curAID=", data.getAccountID(), ", curName=", data.getPlayerName());
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
						//  * Ban cache (in case player happened to be banned at some point)
						//  * Ban log (in case player happened to be banned at some point)
						//  * Player data (across all SIDs)
						//  * Weekly data (across all SIDs)
						//  * Weekly data killed by value - requires fetching all rows and parsing out the field for the UID and updating it
						// Otherwise if we're in historical mode, we change the player object's UID to the current DB one so that going forward,
						// any historical data will be attributed to their current UID
						if (!curDBUID.equals(data.getUID())) {

							// STEP 1: Update player profile
							if (GameDataType.HISTORICAL_GAME_DATA.equals(dataType)) {
								ps = conn.prepareStatement("UPDATE PLAYER_PROFILE SET PAST_UIDS = ? WHERE UID=?");
							} else if (GameDataType.GAME_DATA.equals(dataType)) {
								ps = conn.prepareStatement("UPDATE PLAYER_PROFILE SET UID = ?, PAST_UIDS = ? WHERE UID=?");
							}

							Set<String> pastUIDsArr = !MiscUtil.isEmpty(pastUIDs) ? new Gson().fromJson(pastUIDs, new TypeToken<HashSet<String>>(){}.getType()) : new HashSet<String>();

							if (GameDataType.HISTORICAL_GAME_DATA.equals(dataType)) {
								pastUIDsArr.add(data.getUID());
								ps.setString(1, DBUtil.createDBCollection(pastUIDsArr));
								ps.setString(2, curDBUID);
							} else {
								pastUIDsArr.add(curDBUID);
								ps.setString(1, data.getUID());
								ps.setString(2, DBUtil.createDBCollection(pastUIDsArr));
								ps.setString(3, curDBUID);
							}

							result = ps.executeUpdate();

							if (result != 1) {
								Log.warn(LOG_ID + ".updatePlayerData() : Did not update player profile UID for " + data.getPlayerName() + ", result count = " + result);
							} else {
								Log.info(LOG_ID + ".updatePlayerData() : Updated player UID from " + curDBUID + " to " + data.getUID() + " for player " + data.getPlayerName());
							}
							ResourceManager.releaseResources(ps);

							// If Historical mode, update player object's UID to current DB one
							// so that any processing going forward will apply data to current UID
							if (GameDataType.HISTORICAL_GAME_DATA.equals(dataType)) {
								data.setUID(curDBUID);
							} else {
								// Otherwise for game data, then update other tables to reflect new UID
								// STEP 2: Update any ban data
								BanDataCache.singleton().updateBanData(curDBUID, data.getUID());

								ps = conn.prepareStatement("UPDATE BAN_LOG SET UID=? WHERE UID=?");
								ps.setString(1, data.getUID());
								ps.setString(2, curDBUID);
								result = ps.executeUpdate();
								if (result <= 0) {
									Log.warn(LOG_ID + ".updatePlayerData() : Did not update ban log UID for " + data.getPlayerName() + ", result count = " + result);
								} else {
									Log.info(LOG_ID + ".updatePlayerData() : Updated ban log UID from " + curDBUID + " to " + data.getUID() + " for player " + data.getPlayerName());
								}
								ResourceManager.releaseResources(ps);
								
								// STEP 3: Update any player data across ALL SIDs
								ps = conn.prepareStatement("UPDATE PLAYER_DATA SET UID=? WHERE UID=?");
								ps.setString(1, data.getUID());
								ps.setString(2, curDBUID);
								result = ps.executeUpdate();
								if (result <= 0) {
									Log.warn(LOG_ID + ".updatePlayerData() : Did not update player data UID for " + data.getPlayerName() + ", result count = " + result);
								} else {
									Log.info(LOG_ID + ".updatePlayerData() : Updated player data UID from " + curDBUID + " to " + data.getUID() + " for player " + data.getPlayerName());
								}
								ResourceManager.releaseResources(ps);

								// STEP 4: Update weekly player data across ALL SIDs
								ps = conn.prepareStatement("UPDATE WEEKLY_DATA SET UID=? WHERE UID=?");
								ps.setString(1, data.getUID());
								ps.setString(2, curDBUID);
								result = ps.executeUpdate();
								if (result <= 0) {
									Log.warn(LOG_ID + ".updatePlayerData() : Did not update weekly data UID for " + data.getPlayerName() + ", result count = " + result);
								} else {
									Log.info(LOG_ID + ".updatePlayerData() : Updated weekly data UID from " + curDBUID + " to " + data.getUID() + " for player " + data.getPlayerName());
								}
								ResourceManager.releaseResources(ps);

								// STEP 5: Update all weekly data killed_by references across ALL SIDs
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
							ps = conn.prepareStatement("UPDATE PLAYER_PROFILE SET AID=? WHERE UID=?");
							ps.setString(1, data.getAccountID());
							ps.setString(2, data.getUID());
							result = ps.executeUpdate();
							if (result <= 0) {
								Log.warn(LOG_ID + ".updatePlayerData() : Did not update player data AcctID for " + data.getUID() + ", result count = " + result);
							} else {
								Log.info(LOG_ID + ".updatePlayerData() : Updated player data AcctID from " + curDBAID + " to " + data.getAccountID() + " for player " + data.getUID());
							}
							ResourceManager.releaseResources(ps);
						}

						if (!curDBName.equals(data.getPlayerName())) {
							Set<String> pastNamesArr = !MiscUtil.isEmpty(pastNames) ? new Gson().fromJson(pastNames, new TypeToken<HashSet<String>>(){}.getType()) : new HashSet<String>();

							if (GameDataType.HISTORICAL_GAME_DATA.equals(dataType)) {
								pastNamesArr.add(data.getPlayerName());
								ps = conn.prepareStatement("UPDATE PLAYER_PROFILE SET PAST_NAMES = ? WHERE UID=?");
								ps.setString(1, new Gson().toJson(pastNamesArr));
								ps.setString(2, data.getUID());
							} else {
								pastNamesArr.add(curDBName);
								ps = conn.prepareStatement("UPDATE PLAYER_PROFILE SET NAME = ?, PAST_NAMES = ? WHERE UID=?");
								ps.setString(1, data.getPlayerName());
								ps.setString(2, new Gson().toJson(pastNamesArr));
								ps.setString(3, data.getUID());
							}

							result = ps.executeUpdate();
							if (result != 1) {
								Log.warn(LOG_ID + ".updateGameData() : Did not update player profile name for " + data.getPlayerName() + ", result count = " + result);
							} else if (GameDataType.GAME_DATA.equals(dataType)) {
								Log.info(LOG_ID + ".updateGameData() : Updated player name from " + curDBName + " to " + data.getPlayerName() + " for ID " + data.getUID());
							}
							ResourceManager.releaseResources(ps);
						}

						// Ignore if this is for Historical since we don't want to set their platform to an older one
						if (curPlatform != data.getPlatform() && GameDataType.GAME_DATA.equals(dataType)) {
							ps = conn.prepareStatement("UPDATE PLAYER_PROFILE SET PLATFORM = ? WHERE UID = ?");
							ps.setInt(1, data.getPlatform());
							ps.setString(2, data.getUID());
							result = ps.executeUpdate();
							if (result != 1) {
								Log.warn(LOG_ID + ".updatePlayerData() : Did not update player profile platform for " + data.getPlayerName() + ", result count = " + result);
							} else {
								Log.info(LOG_ID + ".updatePlayerData() : Updated player platform from " + curPlatform + " to " + data.getPlatform() + " for ID " + data.getUID());
							}
							ResourceManager.releaseResources(ps);
						}
					}
					// If we updated a UID from an old one to a new one and this SID did not exist before,
					// check if the SID exists now after the update (i.e. an entry existed for the SID for the old UID, but not the new one.
					// now after updating the old UID to new one, check if an entry exists again)
					if (!sidExists) {
						ps = conn.prepareStatement("SELECT 1 FROM PLAYER_DATA WHERE UID=? AND SID=?");
						ps.setString(1, data.getUID());
						ps.setInt(2, serverClient.getServerClientID());
						rs = ps.executeQuery();
						sidExists = rs.next();
						ResourceManager.releaseResources(ps, rs);
					}
				} else {
					ps = conn.prepareStatement("INSERT INTO PLAYER_PROFILE (NAME, UID, AID, PLATFORM) VALUES (?,?,?,?)");
					ps.setString(1, data.getPlayerName());
					ps.setString(2, data.getUID());
					ps.setString(3, data.getAccountID());
					ps.setInt(4, data.getPlatform());

					result = ps.executeUpdate();
					if (result != 1) {
						Log.warn(LOG_ID, ".updateGameData() : Did not insert player profile for server ", serverClient.getDisplayName(), ", player=", data.getPlayerName(), ", uid=" + data.getUID(), ", aid=", data.getUID(),", result count = " + result);
					}
				}


				ResourceManager.releaseResources(ps);

				if (sidExists) {
					// If we had data here, then we matched on existing player and SID, so update stats and player name for that UID
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
					ps.setInt(10, serverClient.getServerClientID());
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
					ps.setInt(10, serverClient.getServerClientID());
				}

				result = ps.executeUpdate();
				if (result != 1) {
					Log.warn(LOG_ID + ".updateGameData() : Did not update player data for server " + serverClient.getDisplayName() + ", player=" + data.getPlayerName() + ", result count = " + result);
				}

				ResourceManager.releaseResources(ps);

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

			// All data is treated as one transaction here, so if any part of it fails, we consider the whole thing as failed and roll it back
			// This is why we explicitly commit here after all data has been processed, as auto commit is false
			conn.commit();
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

			ps = conn.prepareStatement("SELECT WEAPON_KILLS, KILLED_BY FROM WEEKLY_DATA WHERE UID = ? AND WEEK_DATE = ? AND GAME_MODE = ? AND ELO = ? AND PLATFORM = ? AND SID = ?");
			ps.setString(1, data.getUID());
			ps.setObject(2, latestSunday);
			ps.setInt(3, server.getGameModeId());
			ps.setInt(4, server.getEloRating());
			ps.setInt(5, data.getPlatform());
			ps.setInt(6, serverClient.getServerClientID());
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
				ps = conn.prepareStatement("UPDATE WEEKLY_DATA SET KILLS = KILLS + ?, DEATHS = DEATHS + ?, MECHANICS = MECHANICS + ?, CAPTURES = CAPTURES + ?, ROUNDS = ROUNDS + ?, WEAPON_KILLS = ?, KILLED_BY = ?, TOTAL_TIME = TOTAL_TIME + ? WHERE UID = ? AND WEEK_DATE = ? AND GAME_MODE = ? AND ELO = ? AND PLATFORM = ? AND SID = ?");
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
				ps.setInt(13, data.getPlatform());
				ps.setInt(14, serverClient.getServerClientID());
				result = ps.executeUpdate();
				if (result != 1) {
					Log.warn(LOG_ID + ".updateWeeklyPlayerData() : Failed to update weekly data for server " + serverClient.getDisplayName() + ", player=" + data.getPlayerName() + " (" + data.getUID() + ")");
				}
			} else {
				// In this case, need to add them to DB
				ps = conn.prepareStatement("INSERT INTO WEEKLY_DATA (WEEK_DATE,UID,GAME_MODE,ELO,KILLS,DEATHS,MECHANICS,CAPTURES,ROUNDS,WEAPON_KILLS,KILLED_BY,TOTAL_TIME,PLATFORM,SID) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
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
				ps.setInt(13, data.getPlatform());
				ps.setInt(14, serverClient.getServerClientID());
				result = ps.executeUpdate();

				if (result != 1) {
					Log.warn(LOG_ID + ".updateWeeklyPlayerData() : Did not insert weekly data for server " + serverClient.getDisplayName() + ", player=" + data.getPlayerName() + " (" + data.getUID() + ")");
				}
			}

		} finally {
			ResourceManager.releaseResources(ps, rs);
		}
	}

	private static int updateWeeklyDataReferences(Connection conn, String oldUID, String newUID) throws SQLException {
		PreparedStatement ps = null;
		PreparedStatement psUpdate = null;
		ResultSet rs = null;
		int numUpdated = 0;

		try {
			psUpdate = conn.prepareStatement("UPDATE WEEKLY_DATA SET KILLED_BY = ? WHERE UID=? AND SID=? AND WEEK_DATE=? AND GAME_MODE = ? AND ELO = ? AND PLATFORM=?");
			ps = conn.prepareStatement("SELECT UID,SID,PLATFORM,WEEK_DATE,GAME_MODE,ELO,KILLED_BY FROM WEEKLY_DATA WHERE KILLED_BY LIKE ?");
			ps.setString(1, "%\"" + oldUID + "\"%");
			rs = ps.executeQuery();
			while (rs.next()) {
				String weekUID = rs.getString("UID");
				int sid = rs.getInt("SID");
				int gameMode = rs.getInt("GAME_MODE");
				int elo = rs.getInt("ELO");
				int platform = rs.getInt("PLATFORM");
				LocalDate weekDate = rs.getObject("WEEK_DATE", LocalDate.class);
				Map<String, KillerData> killedBy = DBUtil.parseDBMap(rs.getString("KILLED_BY"), String.class, KillerData.class);
				KillerData killerData = killedBy.get(oldUID);
				if (killedBy.containsKey(newUID)) {
					killerData.combine(killedBy.get(newUID));
				}
				killedBy.put(newUID, killerData);
				killedBy.remove(oldUID);

				psUpdate.setString(1, DBUtil.createDBMap(killedBy));
				psUpdate.setString(2, weekUID);
				psUpdate.setInt(3, sid);
				psUpdate.setObject(4, weekDate);
				psUpdate.setInt(5, gameMode);
				psUpdate.setInt(6, elo);
				psUpdate.setInt(7, platform);
				numUpdated += psUpdate.executeUpdate();
			}

		} finally {
			ResourceManager.releaseResources(ps, psUpdate);
			ResourceManager.releaseResources(rs);
		}
		return numUpdated;
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
					psInsert.setInt(3, server.getServerClientID());

					result = psInsert.executeUpdate();

					if (psInsertInfo == null) {
						// If no WEAPON_INFO entry, then add that here as well
						// Note we do REPLACE INTO in case another request happens to be doing this at the same time, so we'll just delete then insert
						psInsertInfo = conn.prepareStatement("REPLACE INTO WEAPON_INFO (WEAPON, WEAPON_REAL, WEAPON_TYPE) VALUES (?,?,?)");
					}
					WeaponData wepData = WarframeItemCache.singleton().getItemInfo(weapon);
					psInsertInfo.setString(1, weapon);
					psInsertInfo.setString(2, wepData == null ? null : wepData.getRealName());
					psInsertInfo.setString(3, wepData == null || wepData.getType() == null ? WeaponType.UNKNOWN.name() : wepData.getType().name());
					psInsertInfo.executeUpdate();
				} else {
					psUpdate.setInt(1, kills);
					psUpdate.setString(2, weapon);
					psUpdate.setInt(3, server.getServerClientID());
					result = psUpdate.executeUpdate();

					if (result != 1) {
						psInsert.setString(1, weapon);
						psInsert.setInt(2, kills);
						psInsert.setInt(3, server.getServerClientID());
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
				psUpdate.setInt(4, server.getServerClientID());
				int result = psUpdate.executeUpdate();

				if (result != 1) {
					if (psInsert == null) {
						psInsert = conn.prepareStatement("INSERT INTO DAILY_WEAPON_DATA (DATE,WEAPON,KILLS,SID) VALUES(?,?,?,?)");
					}

					psInsert.setObject(1, dailyDate);
					psInsert.setString(2, weapon);
					psInsert.setInt(3, kills);
					psInsert.setInt(4, server.getServerClientID());

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

	public static SortedMap<String, WeaponType> findUnmappedItems() {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		SortedMap<String, WeaponType> items = new TreeMap<String, WeaponType>();

		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("SELECT WEAPON,WEAPON_TYPE FROM WEAPON_INFO WHERE WEAPON_REAL IS NULL OR WEAPON_REAL = CONCAT(WEAPON, '_') OR WEAPON_TYPE IS NULL OR WEAPON_TYPE = 'UNKNOWN'");  // NOTE: This query will NOT work with SQLite (and other DBs) as most others use '||' operator instead!!!
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

	public static boolean updateItem(String item, String itemName, WeaponType type) {
		Connection conn = null;
		PreparedStatement ps = null;
		boolean mapped = false;

		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("REPLACE INTO WEAPON_INFO (WEAPON, WEAPON_REAL, WEAPON_TYPE) VALUES (?,?,?)");
			ps.setString(1, item);
			ps.setString(2, itemName);
			ps.setString(3, type.name());
			mapped = ps.executeUpdate() >= 1;
		} catch (Exception e) {
			Log.error(LOG_ID + ".updateItemName() : Error occurred -> " + e);
		} finally {
			ResourceManager.releaseResources(conn, ps);
		}
		return mapped;
	}

	public static LocalDate getLatestWeeklyDate() {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		LocalDate date = null;

		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("SELECT MAX(WEEK_DATE) AS WEEK_DATE FROM WEEKLY_DATA");
			rs = ps.executeQuery();
			if (rs.next()) {
				date = rs.getObject("WEEK_DATE", LocalDate.class);
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".getLatestWeeklyDate() : Error occurred -> " + e);

		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}

		return date;
	}
}
