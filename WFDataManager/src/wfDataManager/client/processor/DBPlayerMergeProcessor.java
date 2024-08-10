package wfDataManager.client.processor;

import java.io.IOException;
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
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import jdtools.exception.ProcessingException;
import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataManager.client.db.manager.ResourceManager;
import wfDataModel.model.data.KillerData;
import wfDataModel.model.util.DBUtil;

/**
 * One-time processor intended for merging player data across multiple UIDs into their intended singular UID
 * @author MatNova
 *
 */
public class DBPlayerMergeProcessor {

	private static final String LOG_ID = DBPlayerMergeProcessor.class.getSimpleName();
	
	public void process() throws ProcessingException, JsonSyntaxException, IOException, SQLException {
		Map<String, List<String>> acctsToUIDs = new HashMap<String, List<String>>();
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		
		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("SELECT p.uid,p.aid, max(d.last_seen) FROM player_profile p, player_data d WHERE p.aid in (select distinct t.aid from player_profile t where t.aid is not null group by t.aid having count(t.aid) > 1) and d.uid=p.uid group by p.uid, p.aid order by max(d.last_seen) desc");
			rs = ps.executeQuery();
			while (rs.next()) {
				acctsToUIDs.computeIfAbsent(rs.getString("AID"), k -> new ArrayList<String>()).add(rs.getString("UID"));
			}
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}
		
		if (acctsToUIDs.isEmpty()) {
			Log.info(LOG_ID + ".process() : No duplicate AIDs found");
		} else {
			Log.info(LOG_ID + ".process() : Found " + acctsToUIDs.size(), " distinct AcctIds that had > 1 instance");

			for (String acctId : acctsToUIDs.keySet()) {
				List<String> uids = acctsToUIDs.get(acctId);
				String uid = uids.get(0); // We will arbitrarily pick the first UID of the group as the one that we combine the others into
								
				for (int i = 1; i < uids.size(); i++) {
					String oldUID = uids.get(i);
					Log.info(LOG_ID + ".process() : Merging ", oldUID, " into ", uid);
					
					try {
						conn = ResourceManager.getDBConnection(false);
						mergePlayerData(conn, oldUID, uid);
						mergeWeeklyData(conn, oldUID, uid);
						mergePlayerProfile(conn, acctId, oldUID, uid);
						conn.commit();
					} catch (Exception e) {
						conn.rollback();
						throw e;
					} finally {
						ResourceManager.releaseResources(conn);
					}
				}
				
			}
		}
	}
	
	
	
	/*
	public void process(String file) throws ProcessingException, JsonSyntaxException, IOException, SQLException {
		File processFile = new File(file);
		
		if (processFile.exists() && processFile.isFile()) {
			JsonArray jsonArr = JsonParser.parseString(Files.readString(Path.of(file))).getAsJsonArray();
			Log.info(LOG_ID, ".process() : Found " + jsonArr.size(), " entries to process");
			for (int i = 0; i < jsonArr.size(); i++) {
				JsonObject acctObj = jsonArr.get(i).getAsJsonObject();
				String uid = acctObj.get(JSONField.USER_ID).getAsString();
				String acctId = acctObj.get(JSONField.ACCOUNT_ID).getAsString();
				JsonArray oldUIDs = acctObj.getAsJsonArray(JSONField.OLD);
				
				Log.info(LOG_ID, ".process() : Will merge " + oldUIDs.size(), " old UIDs into ", uid);
				for (int u = 0; u < oldUIDs.size(); u++) {
					String oldUID = oldUIDs.get(u).getAsString();
					Connection conn = null;
					try {
						conn = ResourceManager.getDBConnection(false);
						mergePlayerData(conn, oldUID, uid);
						mergeWeeklyData(conn, oldUID, uid);
						mergePlayerProfile(conn, acctId, oldUID, uid);
						conn.commit();
					} catch (Exception e) {
						conn.rollback();
						throw e;
					} finally {
						ResourceManager.releaseResources(conn);
					}
					
					
				}
			}
			
		} else {
			throw new ProcessingException("Given file is non-existent or not a file: " + file);
		}
	}*/
	
	
	public void mergePlayerData(Connection conn, String oldUID, String uid) throws SQLException, ProcessingException {
		PreparedStatement ps = null;
		PreparedStatement updatePS = null;
		ResultSet rs = null;
		
		try {
			ps = conn.prepareStatement("SELECT * FROM PLAYER_DATA WHERE UID=?");
			ps.setString(1, oldUID);
			rs = ps.executeQuery();
			while (rs.next()) {
				int kills = rs.getInt("KILLS");
				int deaths = rs.getInt("DEATHS");
				int mechanics = rs.getInt("MECHANICS");
				int goals = rs.getInt("GOALS");
				int passes = rs.getInt("PASSES");
				int interceptions = rs.getInt("INTERCEPTIONS");
				int captures = rs.getInt("CAPTURES");
				int rounds = rs.getInt("ROUNDS");
				LocalDate lastSeen = rs.getObject("LAST_SEEN", LocalDate.class);

				updatePS = conn.prepareStatement("UPDATE PLAYER_DATA SET KILLS=KILLS+?, DEATHS=DEATHS+?, MECHANICS=MECHANICS+?, GOALS=GOALS+?, PASSES=PASSES+?, INTERCEPTIONS=INTERCEPTIONS+?, CAPTURES=CAPTURES+?, ROUNDS=ROUNDS+? WHERE UID=?");
				updatePS.setInt(1, kills);
				updatePS.setInt(2, deaths);
				updatePS.setInt(3, mechanics);
				updatePS.setInt(4, goals);
				updatePS.setInt(5, passes);
				updatePS.setInt(6, interceptions);
				updatePS.setInt(7, captures);
				updatePS.setInt(8, rounds);
				updatePS.setString(9, uid);
				int updated = updatePS.executeUpdate();
				
				if (updated == 0) {
					ResourceManager.releaseResources(updatePS);
					updatePS = conn.prepareStatement("INSERT INTO PLAYER_DATA (UID,KILLS,DEATHS,MECHANICS,GOALS,PASSES,INTERCEPTIONS,CAPTURES,ROUNDS,LAST_SEEN) VALUES(?,?,?,?,?,?,?,?,?,?)");
					updatePS.setString(1, uid);
					updatePS.setInt(2, kills);
					updatePS.setInt(3, deaths);
					updatePS.setInt(4, mechanics);
					updatePS.setInt(5, goals);
					updatePS.setInt(6, passes);
					updatePS.setInt(7, interceptions);
					updatePS.setInt(8, captures);
					updatePS.setInt(9, rounds);
					updatePS.setObject(10, lastSeen);
					updated = updatePS.executeUpdate();
					if (updated != 1) {
						throw new ProcessingException("Did not insert new entry for " + uid);
					}
				}
				
				ResourceManager.releaseResources(updatePS);
				
				updatePS = conn.prepareStatement("DELETE FROM PLAYER_DATA WHERE UID=?");
				updatePS.setString(1, oldUID);
				updatePS.executeUpdate();
				
				ResourceManager.releaseResources(updatePS);
			}
			
		} finally {
			ResourceManager.releaseResources(rs);
			ResourceManager.releaseResources(ps, updatePS);
		}
	}
	
	public void mergeWeeklyData(Connection conn, String oldUID, String uid) throws SQLException, ProcessingException {
		PreparedStatement ps = null;
		PreparedStatement updatePS = null;
		PreparedStatement updatePSTwo = null;
		ResultSet rs = null;
		ResultSet rsUpdate = null;
		
		try {
			ps = conn.prepareStatement("SELECT * FROM WEEKLY_DATA WHERE UID=?");
			ps.setString(1, oldUID);
			rs = ps.executeQuery();
			while (rs.next()) {
				LocalDate weekDate = rs.getObject("WEEK_DATE", LocalDate.class);
				int kills = rs.getInt("KILLS");
				int deaths = rs.getInt("DEATHS");
				int mechanics = rs.getInt("MECHANICS");
				int captures = rs.getInt("CAPTURES");
				int rounds = rs.getInt("ROUNDS");
				int gameMode = rs.getInt("GAME_MODE");
				int elo = rs.getInt("ELO");
				String weaponKillsStr = rs.getString("WEAPON_KILLS");
				String killedByStr = rs.getString("KILLED_BY");
				int totalTime = rs.getInt("TOTAL_TIME");
				int platform = rs.getInt("PLATFORM");
				
				updatePS = conn.prepareStatement("SELECT WEAPON_KILLS, KILLED_BY FROM WEEKLY_DATA WHERE UID=? AND WEEK_DATE=? AND GAME_MODE=? AND ELO=? AND PLATFORM=?");
				updatePS.setString(1, uid);
				updatePS.setObject(2, weekDate);
				updatePS.setInt(3, gameMode);
				updatePS.setInt(4, elo);
				updatePS.setInt(5, platform);
				rsUpdate = updatePS.executeQuery();
				
				if (rsUpdate.next()) {
					String uidKilledByStr = rsUpdate.getString("KILLED_BY");
					String uidWeaponKillsStr = rsUpdate.getString("WEAPON_KILLS");
					// If the new and old UIDs both exist for the same sid,week,game,elo combo, then combine old data into new one and delete old one
					Map<String, KillerData> killedBy = DBUtil.parseDBMap(killedByStr, String.class, KillerData.class);
					Map<String, KillerData> uidKilledBy = MiscUtil.isEmpty(uidKilledByStr) ? new HashMap<String, KillerData>() : DBUtil.parseDBMap(uidKilledByStr, String.class, KillerData.class);
					
					Map<String, Integer> weaponKills = DBUtil.parseDBMap(weaponKillsStr, String.class, Integer.class);
					Map<String, Integer> uidWeaponKills = MiscUtil.isEmpty(uidWeaponKillsStr) ? new HashMap<String, Integer>() : DBUtil.parseDBMap(uidWeaponKillsStr, String.class, Integer.class);

					
					if (!MiscUtil.isEmpty(killedBy)) {
						for (String killer : killedBy.keySet()) {
							if (uidKilledBy.containsKey(killer)) {
								uidKilledBy.get(killer).combine(killedBy.get(killer));
							} else {
								uidKilledBy.put(killer, killedBy.get(killer));
							}
						}
					}
					
					if (!MiscUtil.isEmpty(weaponKills)) {
						for (String weapon : weaponKills.keySet()) {
							int wepKills = weaponKills.get(weapon);
							uidWeaponKills.compute(weapon, (k,v) -> v == null ? wepKills : v + wepKills);
						}
					}
					
					ResourceManager.releaseResources(updatePS);
					updatePS = conn.prepareStatement("UPDATE WEEKLY_DATA SET KILLS=KILLS+?, DEATHS=DEATHS+?, MECHANICS=MECHANICS+?, CAPTURES=CAPTURES+?, ROUNDS=ROUNDS+?, WEAPON_KILLS=?, KILLED_BY=?, TOTAL_TIME=TOTAL_TIME+? WHERE UID=? AND WEEK_DATE=? AND GAME_MODE=? AND ELO=? AND PLATFORM=?");
					updatePS.setInt(1, kills);
					updatePS.setInt(2, deaths);
					updatePS.setInt(3, mechanics);
					updatePS.setInt(4, captures);
					updatePS.setInt(5, rounds);
					updatePS.setString(6, DBUtil.createDBMap(uidWeaponKills));
					updatePS.setString(7, DBUtil.createDBMap(uidKilledBy));
					updatePS.setInt(8, totalTime);
					updatePS.setString(9, uid);
					updatePS.setObject(10, weekDate);
					updatePS.setInt(11, gameMode);
					updatePS.setInt(12, elo);
					updatePS.setInt(13, platform);
					if (updatePS.executeUpdate() == 0) {
						throw new ProcessingException("Did not combine weekly row for " + uid + " for week=" + weekDate + ", game=" + gameMode + ", elo=" + elo);
					}
					
					ResourceManager.releaseResources(updatePS);
					updatePS = conn.prepareStatement("DELETE FROM WEEKLY_DATA WHERE UID=? AND WEEK_DATE=? AND GAME_MODE=? AND ELO=? AND PLATFORM=?");
					updatePS.setString(1, oldUID);
					updatePS.setObject(2, weekDate);
					updatePS.setInt(3, gameMode);
					updatePS.setInt(4, elo);
					updatePS.setInt(5, platform);
					if (updatePS.executeUpdate() == 0) {
						throw new ProcessingException("Did not delete weekly row for " + oldUID + " for week=" + weekDate + ", game=" + gameMode + ", elo=" + elo);
					}
				} else {
					// If new UID is not present for this same sid,week,game,elo combo then we can just update the old UID entry to new one for it
					ResourceManager.releaseResources(updatePS);
					updatePS = conn.prepareStatement("UPDATE WEEKLY_DATA SET UID=? WHERE UID=? AND WEEK_DATE=? AND GAME_MODE=? AND ELO=? AND PLATFORM=?");
					updatePS.setString(1, uid);
					updatePS.setString(2, oldUID);
					updatePS.setObject(3, weekDate);
					updatePS.setInt(4, gameMode);
					updatePS.setInt(5, elo);
					updatePS.setInt(6, platform);
					if (updatePS.executeUpdate() == 0) {
						throw new ProcessingException("Did not update weekly row for " + oldUID + " to " + uid + " for week=" + weekDate + ", game=" + gameMode + ", elo=" + elo);
					}
				}
				ResourceManager.releaseResources(rsUpdate);
				ResourceManager.releaseResources(updatePS);

				// After all updates/combinations above, need to then go through every weekly row that references the old UID in their killedBy and update it
				updatePS = conn.prepareStatement("SELECT UID,WEEK_DATE,GAME_MODE,ELO,PLATFORM,KILLED_BY FROM WEEKLY_DATA WHERE KILLED_BY LIKE ?");
				updatePS.setString(1, "%\"" + oldUID + "\"%");
				rsUpdate = updatePS.executeQuery();
				while (rsUpdate.next()) {
					String weekUID = rsUpdate.getString("UID");
					int weekGameMode = rsUpdate.getInt("GAME_MODE");
					int weekElo = rsUpdate.getInt("ELO");
					int uPlatform = rsUpdate.getInt("PLATFORM");
					LocalDate weekWeekDate = rsUpdate.getObject("WEEK_DATE", LocalDate.class);
					Map<String, KillerData> killedBy = DBUtil.parseDBMap(rsUpdate.getString("KILLED_BY"), String.class, KillerData.class);
					KillerData killerData = killedBy.get(oldUID);
					if (killedBy.containsKey(uid)) {
						killerData.combine(killedBy.get(uid));
					}
					killedBy.put(uid, killerData);
					killedBy.remove(oldUID);
					
					updatePSTwo = conn.prepareStatement("UPDATE WEEKLY_DATA SET KILLED_BY = ? WHERE UID=? AND WEEK_DATE=? AND GAME_MODE = ? AND ELO = ? AND PLATFORM=?");
					updatePSTwo.setString(1, DBUtil.createDBMap(killedBy));
					updatePSTwo.setString(2, weekUID);
					updatePSTwo.setObject(3, weekWeekDate);
					updatePSTwo.setInt(4, weekGameMode);
					updatePSTwo.setInt(5, weekElo);
					updatePSTwo.setInt(6, uPlatform);
					if (updatePSTwo.executeUpdate() == 0) {
						throw new ProcessingException("Did not update weekly reference row for " + weekUID + " for week=" + weekWeekDate + ", game=" + weekGameMode + ", elo=" + weekElo);
					}
					ResourceManager.releaseResources(updatePSTwo);
				}
				
				ResourceManager.releaseResources(rsUpdate);
				ResourceManager.releaseResources(updatePS);
			}
			
		} finally {
			ResourceManager.releaseResources(rs, rsUpdate);
			ResourceManager.releaseResources(ps, updatePS, updatePSTwo);
		}
	}
	
	public void mergePlayerProfile(Connection conn, String acctID, String oldUID, String uid) throws SQLException, ProcessingException {
		PreparedStatement ps = null;
		PreparedStatement psUpdate = null;
		ResultSet rs = null;
		ResultSet rsUpdate = null;
		
		try {
			ps = conn.prepareStatement("SELECT NAME FROM PLAYER_PROFILE WHERE UID=?");
			ps.setString(1, oldUID);
			rs = ps.executeQuery();
			if (rs.next()) {
				String name = rs.getString("NAME");
				psUpdate = conn.prepareStatement("SELECT PAST_NAMES, PAST_UIDS FROM PLAYER_PROFILE WHERE UID=?");
				psUpdate.setString(1, uid);
				rsUpdate = psUpdate.executeQuery();
				if (rsUpdate.next()) {
					String pastNamesStr = rsUpdate.getString("PAST_NAMES");
					String pastUIDsStr = rsUpdate.getString("PAST_UIDS");
					Set<String> pastNamesArr = !MiscUtil.isEmpty(pastNamesStr) ? new Gson().fromJson(pastNamesStr, new TypeToken<HashSet<String>>(){}.getType()) : new HashSet<String>();
					Set<String> pastUIDsArr = !MiscUtil.isEmpty(pastUIDsStr) ? new Gson().fromJson(pastUIDsStr, new TypeToken<HashSet<String>>(){}.getType()) : new HashSet<String>();

					pastNamesArr.add(name);
					pastUIDsArr.add(oldUID);
					
					ResourceManager.releaseResources(psUpdate);
					psUpdate = conn.prepareStatement("UPDATE PLAYER_PROFILE SET PAST_NAMES=?, PAST_UIDS=?, AID=? WHERE UID=?");
					psUpdate.setString(1, DBUtil.createDBCollection(pastNamesArr));
					psUpdate.setString(2, DBUtil.createDBCollection(pastUIDsArr));
					psUpdate.setString(3, acctID);
					psUpdate.setString(4, uid);
					if (psUpdate.executeUpdate() == 0) {
						throw new ProcessingException("Did not update past names or uids for player " + uid);
					}
				}
				
				ResourceManager.releaseResources(psUpdate);
				psUpdate = conn.prepareStatement("DELETE FROM PLAYER_PROFILE WHERE UID=?");
				psUpdate.setString(1, oldUID);
				if (psUpdate.executeUpdate() == 0) {
					throw new ProcessingException("Did not delete old uid " + oldUID);
				}
			}
		} finally {
			ResourceManager.releaseResources(rs, rsUpdate);
			ResourceManager.releaseResources(ps, psUpdate);
		}
	}
	
}
