package wfDataManager.client.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataManager.client.data.PlayerTracker;
import wfDataManager.client.db.manager.ResourceManager;

/**
 * Dao class for managing tracked player data in the DB
 * @author MatNova
 *
 */
public final class PlayerTrackerDao {

	private static final String LOG_ID = PlayerTrackerDao.class.getSimpleName();

	public static List<PlayerTracker> getTrackedPlayers() {
		Connection conn = null;
		PreparedStatement ps = null;
		PreparedStatement psFetch = null;
		ResultSet rs = null;
		ResultSet rsFetch = null;
		List<PlayerTracker> tracked = new ArrayList<PlayerTracker>();

		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("SELECT t.*, p.NAME FROM TRACKED_PLAYERS t LEFT JOIN PLAYER_PROFILE p ON p.UID=t.UID");
			rs = ps.executeQuery();
			while (rs.next()) {
				String uid = rs.getString("UID");
				String knownIps = rs.getString("KNOWN_IPS");
				String knownAlts = rs.getString("KNOWN_ALTS");
				String playerName = rs.getString("NAME");
				Set<String> knownIpsArr = !MiscUtil.isEmpty(knownIps) ? new Gson().fromJson(knownIps, new TypeToken<HashSet<String>>(){}.getType()) : new HashSet<String>();
				Map<String, String> knownAltsMap = !MiscUtil.isEmpty(knownAlts) ? new Gson().fromJson(knownAlts, new TypeToken<HashMap<String, String>>(){}.getType()) : new HashMap<String, String>();
				Set<String> knownAltsArr = new HashSet<String>(knownAltsMap.keySet());
				
				psFetch = conn.prepareStatement("SELECT NAME FROM PLAYER_PROFILE WHERE UID=?");
				for (String altUid : knownAltsArr) {
					psFetch.setString(1, altUid);
					rsFetch = psFetch.executeQuery();
					if (rsFetch.next()) {
						knownAltsMap.put(altUid, rsFetch.getString("NAME"));
					} else {
						knownAltsMap.put(altUid, "Unknown");
					}
					ResourceManager.releaseResources(rsFetch);
				}
				
				tracked.add(new PlayerTracker(uid, playerName, knownIpsArr, knownAltsMap));
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".getTrackedPlayers() : Exception trying to get tracked players -> ", e);
		} finally {
			ResourceManager.releaseResources(psFetch);
			ResourceManager.releaseResources(conn, ps, rs);
		}

		return tracked;
	}

	public static void addPlayerTracker(PlayerTracker tracker) {
		Connection conn = null;
		PreparedStatement ps = null;

		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("INSERT INTO TRACKED_PLAYERS (UID, KNOWN_IPS, KNOWN_ALTS) VALUES (?,?,?)");
			ps.setString(1, tracker.getUID());
			ps.setString(2, new Gson().toJson(tracker.getKnownIPs()));
			ps.setString(3, new Gson().toJson(tracker.getKnownAlts()));
			int added = ps.executeUpdate();
			if (added != 1) {
				Log.warn(LOG_ID + ".addPlayerTracker() : Did not add tracked player to DB? uid=" + tracker.getUID());
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".addPlayerTracker() : Exception trying to add tracked player -> ", e);
		} finally {
			ResourceManager.releaseResources(conn, ps);
		}
	}

	public static void updatePlayerTracker(PlayerTracker tracker) {
		updatePlayerTracker(tracker, null);
	}
	
	public static void updatePlayerTracker(PlayerTracker tracker, String newUID) {
		Connection conn = null;
		PreparedStatement ps = null;
		boolean remapUID = !MiscUtil.isEmpty(newUID);
		String sql = !remapUID ? "UPDATE TRACKED_PLAYERS SET KNOWN_IPS=?, KNOWN_ALTS=? WHERE UID=?" : "UPDATE TRACKED_PLAYERS SET KNOWN_IPS=?, KNOWN_ALTS=?, UID=? WHERE UID=?";
			
		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement(sql);
			ps.setString(1, new Gson().toJson(tracker.getKnownIPs()));
			ps.setString(2, new Gson().toJson(tracker.getKnownAlts()));
			if (remapUID) {
				ps.setString(3, newUID);
				ps.setString(4, tracker.getUID());
			} else {
				ps.setString(3, tracker.getUID());
			}
			int updated = ps.executeUpdate();
			if (updated != 1) {
				Log.warn(LOG_ID + ".updatePlayerTracker() : Did not update tracked player in DB? uid=" + tracker.getUID());
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".updatePlayerTracker() : Exception trying to update tracked player -> ", e);
		} finally {
			ResourceManager.releaseResources(conn, ps);
		}
	}
	
	public static void removePlayerTracker(PlayerTracker tracker) {
		Connection conn = null;
		PreparedStatement ps = null;

		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("DELETE FROM TRACKED_PLAYERS WHERE UID=?");
			ps.setString(1, tracker.getUID());
			int removed = ps.executeUpdate();
			if (removed != 1) {
				Log.warn(LOG_ID + ".removePlayerTracker() : Did not remove tracked player in DB? uid=" + tracker.getUID());
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".removePlayerTracker() : Exception trying to remove tracked player -> ", e);
		} finally {
			ResourceManager.releaseResources(conn, ps);
		}
	}
}
