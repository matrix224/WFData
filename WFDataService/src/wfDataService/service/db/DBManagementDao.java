package wfDataService.service.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import jdtools.exception.ProcessingException;
import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataModel.model.util.DBUtil;
import wfDataModel.service.type.EloType;
import wfDataModel.service.type.GameMode;
import wfDataService.service.db.manager.ResourceManager;
import wfDataService.service.versioning.BuildVersion;

/**
 * Dao class for managing DB structure and versioning
 * @author MatNova
 *
 */
public final class DBManagementDao {

	private static final String LOG_ID = DBManagementDao.class.getSimpleName();

	public static void upgradeDB() throws ProcessingException {
		Connection conn = null;
		PreparedStatement ps = null;
		PreparedStatement ps2 = null;
		ResultSet rs = null;
		String curDBVer = null;
		String curVer = BuildVersion.getBuildVersion();
		
		try {
			conn = ResourceManager.getDBConnection(false);
			curDBVer = ProcessorVarDao.getVar(conn, ProcessorVarDao.VAR_DB_VERSION);
			if (MiscUtil.isEmpty(curDBVer)) {
				curDBVer = DBUtil.DEFAULT_VER;
			}
			
			// DBVer isn't stored until we hit version 1.1.0
			// If empty, we assume we're upgrading from 1.0.0 to this version and will perform upgrades starting from those onward
			// Otherwise perform any upgrades if DBVer is < curVer
			if (curDBVer.compareTo(curVer) < 0) {
				if ("1.1.0".compareTo(curDBVer) > 0) {
					
					/*
					ps = conn.prepareStatement("ALTER TABLE `player_profile` ADD `banned` TINYINT NULL DEFAULT '0' COMMENT 'Denotes if a player is banned from the game. This flag has to be set manually' AFTER `platform`");
					ps.executeUpdate();
					ResourceManager.releaseResources(ps);

					ps = conn.prepareStatement("ALTER TABLE `weekly_data` ADD `platform` INT NOT NULL DEFAULT '0' BEFORE `sid`");
					ps.executeUpdate();
					ResourceManager.releaseResources(ps);
					
					ps = conn.prepareStatement("SELECT W.UID, W.WEEK_DATE, W.ELO, W.GAME_MODE, W.SID, P.PLATFORM FROM WEEKLY_DATA W, PLAYER_PROFILE P WHERE W.UID=P.UID ORDER BY W.WEEK_DATE");
					ps2 = conn.prepareStatement("UPDATE `weekly_data` SET PLATFORM=? WHERE UID=? AND WEEK_DATE=? AND GAME_MODE=? AND ELO=? AND SID=?");
					rs = ps.executeQuery();
					while (rs.next()) {
						int platform = rs.getInt("PLATFORM");
						
						ps2.setInt(1, platform);
						ps2.setString(2, rs.getString("UID"));
						ps2.setObject(3, rs.getObject("WEEK_DATE", LocalDate.class));
						ps2.setInt(4, rs.getInt("GAME_MODE"));
						ps2.setInt(5, rs.getInt("ELO"));
						ps2.setInt(6, rs.getInt("SID"));
						ps2.executeUpdate();
					}
					ResourceManager.releaseResources(rs);
					ResourceManager.releaseResources(ps, ps2);
					*/
					
					
					Map<LocalDate, WeekTrack> tracks = new HashMap<LocalDate, WeekTrack>();
					
					ps = conn.prepareStatement("SELECT WEEK_DATE,W.UID,GAME_MODE,ELO,PLATFORM,SID,C.REGION FROM WEEKLY_DATA W LEFT JOIN MANAGER_CLIENT C ON C.SID=W.SID ORDER BY WEEK_DATE");
					rs = ps.executeQuery();
					while (rs.next()) {
						LocalDate weekDate = rs.getObject("WEEK_DATE", LocalDate.class);
						int sid = rs.getInt("SID");
						int region = rs.getInt("REGION");
						int gameMode = rs.getInt("GAME_MODE");
						int elo = rs.getInt("ELO");
						String uid = rs.getString("UID");
						int platform = rs.getInt("PLATFORM");
						
						WeekTrack weekTrack = tracks.computeIfAbsent(weekDate, k -> new DBManagementDao().new WeekTrack());
						RegionTrack regTrack = weekTrack.getRegionTrack(region);
						SIDTrack sidTrack = regTrack.getSIDTrack(sid);
						GameTrack gameTrack = sidTrack.getGameTrack(gameMode, elo);
						
						boolean isNew = true;
						
						for (WeekTrack wTrack : tracks.values()) {
							if (wTrack.hasPlayer(uid, region, sid, gameMode, elo)) {
								isNew = false;
								break;
							}
						}
						
						boolean isUnique = isNew || !gameTrack.plats.containsKey(uid);
						
						
						//updateWeeklyActivity(conn, rs.getObject("WEEK_DATE", LocalDate.class), rs.getInt("SID"), rs.getInt("REGION"), rs.getInt("GAME_MODE"), rs.getInt("ELO"), rs.getString("UID"), rs.getString("PLATFORM"));
					}
					
					
				}
				
				// Lastly update DB ver to current ver
				ProcessorVarDao.updateVar(conn, ProcessorVarDao.VAR_DB_VERSION, curVer);
				conn.commit();
				Log.info(LOG_ID + ".upgradeDB() : Successfully upgraded DB from version " + curDBVer + " to " + curVer);
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".upgradeDB() : Exception trying to upgrade DB, will rollback -> ", e);
			try {
				conn.rollback();
			} catch (SQLException e1) {
				Log.error(LOG_ID + ".upgradeDB() : Exception trying to rollback DB changes -> ", e1);
			}
			throw new ProcessingException("DB upgrade failed");
		} finally {
			ResourceManager.releaseResources(rs);
			ResourceManager.releaseResources(ps, ps2);
			ResourceManager.releaseResources(conn);
		}
	}
	
	private static void updateWeeklyActivity(Connection conn, LocalDate latestSunday, int sid, int region, int gameMode, int elo, String uid, String platform) throws SQLException {
		PreparedStatement psExists = null;
		PreparedStatement psUpdate = null;
		ResultSet rs = null;

		int serverNewInc = 0;
		int regionUniInc = 0;
		int regionNewInc = 0;
		int totalServerUniInc = 0;
		int totalRegionUniInc = 0;
		int totalServerNewInc = 0;
		int totalRegionNewInc = 0;
		int result = 0;
		
		try {
			psExists = conn.prepareStatement("SELECT 1 FROM WEEKLY_DATA W LEFT JOIN MANAGER_CLIENT C ON W.SID=C.SID WHERE W.UID=? AND W.GAME_MODE=? AND W.ELO=? AND C.REGION=? AND W.WEEK_DATE < ?");
			psExists.setString(1, uid);
			psExists.setInt(2, gameMode);
			psExists.setInt(3, elo);
			psExists.setInt(4, region);
			psExists.setObject(5, latestSunday);
			rs = psExists.executeQuery();

			if (rs.next()) {
				// If this UID, GameMode, Elo combination has an entry in weekly_Data for this serverClient's region,
				// then we'll check if this particular week has an entry for it. If not, this is a unique player for this region this week
				// Additionally, we need to check if it has one for this serverClient specifically as well to see if it's new for that client in general

				ResourceManager.releaseResources(psExists, rs);

				psExists = conn.prepareStatement("SELECT 1 FROM WEEKLY_DATA W LEFT JOIN MANAGER_CLIENT C ON W.SID=C.SID WHERE W.UID=? AND W.GAME_MODE=? AND W.ELO=? AND C.REGION=? AND W.WEEK_DATE=?");
				psExists.setString(1, uid);
				psExists.setInt(2, gameMode);
				psExists.setInt(3, elo);
				psExists.setInt(4, sid);
				psExists.setObject(5, latestSunday);
				rs = psExists.executeQuery();

				// If this UID, GameMode, Elo, region, week_date combination has an entry in weekly_Data, then this is not a unique player for this week in the region
				// Otherwise this is a unique player for this game mode - elo - region - week combination
				if (!rs.next()) {
					regionUniInc = 1;
				}
				ResourceManager.releaseResources(psExists, rs);


				psExists = conn.prepareStatement("SELECT 1 FROM WEEKLY_DATA WHERE UID=? AND GAME_MODE=? AND ELO=? AND SID=? AND WEEK_DATE <= ?");
				psExists.setString(1, uid);
				psExists.setInt(2, gameMode);
				psExists.setInt(3, elo);
				psExists.setInt(4, sid);
				psExists.setObject(5, latestSunday);
				rs = psExists.executeQuery();

				// If this UID, GameMode, Elo, SID combination has an entry in weekly_Data, then this is not a new player at all
				// Otherwise this is a new player for this game mode - elo - sid combination
				if (!rs.next()) {
					serverNewInc = 1;
				}
				ResourceManager.releaseResources(psExists, rs);

			} else {
				ResourceManager.releaseResources(psExists, rs);

				// If this UID, GameMode, Elo combination has no entries in weekly_Data for this serverClient's region,
				// that means this is a new entry for both that particular serverClient and also that region
				// Nothing else to check
				serverNewInc = 1;
				regionNewInc = 1;
				regionUniInc = 1;
			}


			String platformKey = "$.\"" + platform + "\"";
			psUpdate = conn.prepareStatement("UPDATE WEEKLY_ACTIVITY SET NUM_UNIQUE = JSON_SET(NUM_UNIQUE, ?, COALESCE(JSON_EXTRACT(NUM_UNIQUE, ?), 0) + ?), NUM_NEW = JSON_SET(NUM_NEW, ?, COALESCE(JSON_EXTRACT(NUM_NEW, ?), 0) + ?), NUM_UNIQUE_REGION = JSON_SET(NUM_UNIQUE_REGION, ?, COALESCE(JSON_EXTRACT(NUM_UNIQUE_REGION, ?), 0) + ?), NUM_NEW_REGION = JSON_SET(NUM_NEW_REGION, ?, COALESCE(JSON_EXTRACT(NUM_NEW_REGION, ?), 0) + ?) WHERE WEEK_DATE=? AND GAME_MODE=? AND ELO=? AND SID=?");
			psUpdate.setString(1, platformKey);
			psUpdate.setString(2, platformKey);
			psUpdate.setInt(3, 1); // If we're adding a new weekly entry for this gamemode/elo/sid, then this is always a unique player for that week for that combo. So always add 1
			psUpdate.setString(4, platformKey);
			psUpdate.setString(5, platformKey);
			psUpdate.setInt(6, serverNewInc); 
			psUpdate.setString(7, platformKey);
			psUpdate.setString(8, platformKey);
			psUpdate.setInt(9, regionUniInc); 
			psUpdate.setString(10, platformKey);
			psUpdate.setString(11, platformKey);
			psUpdate.setInt(12, regionNewInc); 
			psUpdate.setObject(13, latestSunday);
			psUpdate.setInt(14, gameMode);
			psUpdate.setInt(15, elo);
			psUpdate.setInt(16, sid);
			result = psUpdate.executeUpdate();

			// If no row was updated, we assume this is a new weekly activity row
			if (result != 1) {
				ResourceManager.releaseResources(psUpdate);

				psUpdate = conn.prepareStatement("INSERT INTO WEEKLY_ACTIVITY (WEEK_DATE, GAME_MODE, ELO, NUM_UNIQUE, NUM_NEW, NUM_UNIQUE_REGION, NUM_NEW_REGION, SID) VALUES (?,?,?,?,?,?,?,?)");
				psUpdate.setObject(1, latestSunday);
				psUpdate.setInt(2, gameMode);
				psUpdate.setInt(3, elo);
				psUpdate.setString(4, getKeyValue(platform, 1)); // If we're adding a new weekly entry for this gamemode/elo/sid, then this is always a unique player for that week for that combo. So always add 1
				psUpdate.setString(5, getKeyValue(platform, serverNewInc));
				psUpdate.setString(6, getKeyValue(platform, regionUniInc));
				psUpdate.setString(7, getKeyValue(platform, regionNewInc));
				psUpdate.setInt(8, sid);
				result = psUpdate.executeUpdate();
				if (result != 1) {
					Log.warn(LOG_ID + ".updateWeeklyPlayerData() : Did not insert weekly activity row for server " + sid);
				}
			}
			
			
			// After inserting the weekly activity for this game mode and player, do the same for the total row now
			psExists = conn.prepareStatement("SELECT 1 FROM WEEKLY_DATA W LEFT JOIN MANAGER_CLIENT C ON W.SID=C.SID WHERE W.UID=? AND C.REGION=? AND W.WEEK_DATE <= ?");
			psExists.setString(1, uid);
			psExists.setInt(2, region);
			psExists.setObject(3, latestSunday);
			rs = psExists.executeQuery();
			
			if (rs.next()) {
				ResourceManager.releaseResources(psExists, rs);

				psExists = conn.prepareStatement("SELECT 1 FROM WEEKLY_DATA W LEFT JOIN MANAGER_CLIENT C ON W.SID=C.SID WHERE W.UID=? AND C.REGION=? AND W.WEEK_DATE=?");
				psExists.setString(1, uid);
				psExists.setInt(2, sid);
				psExists.setObject(3, latestSunday);
				rs = psExists.executeQuery();
				
				// If this UID does not exist for this region and this week,
				// then this counts as a unique player for the total row
				if (!rs.next()) {
					totalRegionUniInc = 1;
				}
				
				ResourceManager.releaseResources(psExists, rs);

				psExists = conn.prepareStatement("SELECT 1 FROM WEEKLY_DATA WHERE UID=? AND SID=?");
				psExists.setString(1, uid);
				psExists.setInt(2, sid);
				rs = psExists.executeQuery();

				// If there's no entry at all for this UID and SID,
				// then this is a new player for the total row
				if (!rs.next()) {
					totalServerNewInc = 1;
				}
				ResourceManager.releaseResources(psExists, rs);
				
				psExists = conn.prepareStatement("SELECT 1 FROM WEEKLY_DATA WHERE UID=? AND SID=? AND WEEK_DATE=?");
				psExists.setString(1, uid);
				psExists.setInt(2, sid);
				psExists.setObject(3, latestSunday);
				rs = psExists.executeQuery();

				// If there's no entry at all for this UID and SID and week,
				// then this is a unique player for the total row
				if (!rs.next()) {
					totalServerUniInc = 1;
				}
				ResourceManager.releaseResources(psExists, rs);

			} else {
				ResourceManager.releaseResources(psExists, rs);

				// This UID was not found in any game mode for this region for any week,
				// so this counts as a new and unique player for the total row
				totalServerUniInc = 1;
				totalServerNewInc = 1;
				totalRegionUniInc = 1;
				totalRegionNewInc = 1;
			}
			
			psUpdate = conn.prepareStatement("UPDATE WEEKLY_ACTIVITY SET NUM_UNIQUE = JSON_SET(NUM_UNIQUE, ?, COALESCE(JSON_EXTRACT(NUM_UNIQUE, ?), 0) + ?), NUM_NEW = JSON_SET(NUM_NEW, ?, COALESCE(JSON_EXTRACT(NUM_NEW, ?), 0) + ?), NUM_UNIQUE_REGION = JSON_SET(NUM_UNIQUE_REGION, ?, COALESCE(JSON_EXTRACT(NUM_UNIQUE_REGION, ?), 0) + ?), NUM_NEW_REGION = JSON_SET(NUM_NEW_REGION, ?, COALESCE(JSON_EXTRACT(NUM_NEW_REGION, ?), 0) + ?) WHERE WEEK_DATE=? AND GAME_MODE=? AND ELO=? AND SID=?");
			psUpdate.setString(1, platformKey);
			psUpdate.setString(2, platformKey);
			psUpdate.setInt(3, totalServerUniInc); 
			psUpdate.setString(4, platformKey);
			psUpdate.setString(5, platformKey);
			psUpdate.setInt(6, totalServerNewInc); 
			psUpdate.setString(7, platformKey);
			psUpdate.setString(8, platformKey);
			psUpdate.setInt(9, totalRegionUniInc); 
			psUpdate.setString(10, platformKey);
			psUpdate.setString(11, platformKey);
			psUpdate.setInt(12, totalRegionNewInc); 
			psUpdate.setObject(13, latestSunday);
			psUpdate.setInt(14, GameMode.TOTAL.getId());
			psUpdate.setInt(15, EloType.TOTAL.getCode());
			psUpdate.setInt(16, sid);
			result = psUpdate.executeUpdate();

			// If no row was updated, we assume this is a new weekly activity row
			if (result != 1) {
				ResourceManager.releaseResources(psUpdate);

				psUpdate = conn.prepareStatement("INSERT INTO WEEKLY_ACTIVITY (WEEK_DATE, GAME_MODE, ELO, NUM_UNIQUE, NUM_NEW, NUM_UNIQUE_REGION, NUM_NEW_REGION, SID) VALUES (?,?,?,?,?,?,?,?)");
				psUpdate.setObject(1, latestSunday);
				psUpdate.setInt(2, GameMode.TOTAL.getId());
				psUpdate.setInt(3, EloType.TOTAL.getCode());
				psUpdate.setString(4, getKeyValue(platform, totalServerUniInc));
				psUpdate.setString(5, getKeyValue(platform, totalServerNewInc));
				psUpdate.setString(6, getKeyValue(platform, totalRegionUniInc));
				psUpdate.setString(7, getKeyValue(platform, totalRegionNewInc));
				psUpdate.setInt(8, sid);
				result = psUpdate.executeUpdate();
				if (result != 1) {
					Log.warn(LOG_ID + ".updateWeeklyPlayerData() : Did not insert total weekly activity row for server " + sid);
				}
			}
			
			
		} finally {
			ResourceManager.releaseResources(psExists, psUpdate);
			ResourceManager.releaseResources(rs);
		}
	}
	
	private class WeekTrack {
		public List<RegionTrack> regions = new ArrayList<RegionTrack>();
		
		public RegionTrack getRegionTrack(int rid) {
			RegionTrack track = null;
			
			for (RegionTrack trk : regions) {
				if (trk.rid == rid) {
					track = trk;
					break;
				}
			}
			
			if (track == null) {
				track = new RegionTrack();
				track.rid = rid;
				regions.add(track);
			}
			
			return track;
		}
		
		public boolean hasPlayer(String uid, Integer region) {
			return hasPlayer(uid, region, null, null, null);
		}
		
		public boolean hasPlayer(String uid, Integer region, Integer sid, Integer gameMode, Integer elo) {
			boolean hasPlayer = false;
			for (RegionTrack reg : regions) {
				if (region != null && reg.rid != region) {
					continue;
				}
				for (SIDTrack strack : reg.sids) {
					if (sid != null && strack.sid != sid) {
						continue;
					}
					
					for (GameTrack game : strack.games) {
						if (gameMode != null && elo != null && (game.gameMode != gameMode || game.elo != elo)) {
							continue;
						}
						
						if (game.plats.containsKey(uid)) {
							hasPlayer = true;
							break;
						}
					}
					if (hasPlayer) {
						break;
					}
				}
				if (hasPlayer) {
					break;
				}
			}
			return hasPlayer;
		}
		
	}
	
	private class RegionTrack {
		public int rid;
		public List<SIDTrack> sids = new ArrayList<SIDTrack>();
		
		public SIDTrack getSIDTrack(int sid) {
			SIDTrack track = null;
			
			for (SIDTrack trk : sids) {
				if (trk.sid == sid) {
					track = trk;
					break;
				}
			}
			
			if (track == null) {
				track = new SIDTrack();
				track.sid = sid;
				sids.add(track);
			}
			
			return track;
		}
		
		
	}
	
	private class SIDTrack {
		public int sid;
		public List<GameTrack> games = new ArrayList<GameTrack>();
		
		public GameTrack getGameTrack(int gameMode, int elo) {
			GameTrack track = null;
			
			for (GameTrack trk : games) {
				if (trk.gameMode == gameMode && trk.elo == elo) {
					track = trk;
					break;
				}
			}
			
			if (track == null) {
				track = new GameTrack();
				track.gameMode = gameMode;
				track.elo = elo;
				games.add(track);
			}
			
			return track;
		}

	}

	private class GameTrack {
		public int gameMode;
		public int elo;
		public Map<String, PlatformTrack> plats = new HashMap<String, PlatformTrack>();
		
		
		
	}
	
	private class PlatformTrack {
		public int platform;
		public boolean isUnique;
		public boolean isNew;
	}
	
	private static String getKeyValue(String key, int value) {
		JsonObject obj = new JsonObject();
		obj.addProperty(key, value);
		return obj.toString();
	}
}
