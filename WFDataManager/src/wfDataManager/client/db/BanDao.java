package wfDataManager.client.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import wfDataManager.client.db.manager.ResourceManager;
import wfDataModel.model.logging.Log;
import wfDataModel.service.data.BanData;
import wfDataModel.service.data.BanSpec;
import wfDataModel.service.type.BanActionType;

/**
 * Dao class for managing ban data in the DB
 * @author MatNova
 *
 */
public class BanDao {

	private static final String LOG_ID = BanDao.class.getSimpleName();

	public static void updateBan(BanData data, String ip, BanActionType action) {
		Connection conn = null;
		PreparedStatement ps = null;
		
		try {
			// For adding a ban, should only have one for a given IP and type at a time
			// The primary key is: uid, ban_key, loadout_id, is_primary
			String sql = BanActionType.REMOVE.equals(action) ? "DELETE FROM CURRENT_BANS WHERE uid=? AND ip=?" : "REPLACE INTO CURRENT_BANS (UID, WHEN_BANNED, BAN_KEY, REASON, LOADOUT_ID, IP, IS_PRIMARY, IS_PROXY) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement(sql);
			ps.setString(1, data.getUID());
			
			if (BanActionType.ADD.equals(action)) {
				String reason = data.getBanReason(ip);
				if (reason.length() > 256) {
					reason = reason.substring(0, 256);
				}
				ps.setTimestamp(2, new Timestamp(data.getBanTime(ip)));
				ps.setString(3, data.getBanKey(ip));
				ps.setString(4, reason);
				ps.setInt(5, data.getBanLoadoutID(ip));
				ps.setString(6, ip);
				ps.setInt(7, data.isPrimary(ip) ? 1 : 0);
				ps.setInt(8, data.isProxy(ip) ? 1 : 0);
			} else {
				ps.setString(2, ip);
			}
			int updated = ps.executeUpdate();
			if (updated != 1) {
				Log.warn(LOG_ID + ".updateBan() : Updated rows didn't equal 1 for " + action + " of " + data.getPlayerName() + ", updated = " + updated);
			}

			if (BanActionType.ADD.equals(action)) {
				// Need to also update marked players if adding ban
				ResourceManager.releaseResources(ps);
				ps = conn.prepareStatement("REPLACE INTO MARKED_PLAYERS (UID, LOADOUTS) VALUES (?,?)");
				ps.setString(1, data.getUID());
				ps.setString(2, data.getOffensiveLoadoutsForDB());
				ps.executeUpdate();
			}

		} catch (Exception e) {
			Log.error(LOG_ID + ".updateBan() : Exception trying to update ban for player " + data.getPlayerName() + " -> " + e.getLocalizedMessage());
		} finally {
			ResourceManager.releaseResources(conn, ps);
		}
	}

	public static Map<String, BanData> getBanData() {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		Map<String, BanData> data = new HashMap<String, BanData>();

		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("SELECT MP.UID AS UID, D.NAME AS PLAYER_NAME, MP.LOADOUTS AS LOADOUTS, CB.WHEN_BANNED AS WHEN_BANNED, CB.LOADOUT_ID AS LOADOUT_ID, CB.REASON AS REASON, CB.IP AS IP, CB.IS_PRIMARY AS IS_PRIMARY, CB.IS_PROXY AS IS_PROXY FROM MARKED_PLAYERS MP, PLAYER_PROFILE D LEFT OUTER JOIN CURRENT_BANS CB ON CB.UID=D.UID WHERE D.UID=MP.UID GROUP BY CB.UID, CB.IP");
			rs = ps.executeQuery();

			while (rs.next()) {
				String uid = rs.getString("UID");
				String playerName = rs.getString("PLAYER_NAME");
				long whenBanned = rs.getLong("WHEN_BANNED");
				String loadoutsJson = rs.getString("LOADOUTS");

				BanData banData = data.containsKey(uid) ? data.get(uid) : new BanData(playerName, uid);
				if (!data.containsKey(uid)) {
					JsonObject loadoutsObj = JsonParser.parseString(loadoutsJson).getAsJsonObject();
					JsonArray loadoutsArr = loadoutsObj.getAsJsonArray("loadouts");
					loadoutsArr.forEach(loadoutObj -> {
						banData.addOffensiveLoadout(loadoutObj.getAsInt());
					});
					data.put(uid, banData);
				}

				if (whenBanned != 0) {
					int loadout = rs.getInt("LOADOUT_ID");
					String reason = rs.getString("REASON");
					String ip = rs.getString("IP");
					int primary = rs.getInt("IS_PRIMARY");
					int proxy = rs.getInt("IS_PROXY");
					BanSpec spec = banData.addOrGetBanSpec(ip);
					spec.setBanTime(whenBanned);
					spec.setLoadoutID(loadout);
					spec.setBanReason(reason);
					spec.setPrimary(primary == 1);
					spec.setIsProxy(proxy == 1);
				}

			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".getBanData() : Exception fetching ban data -> " + e.getLocalizedMessage());
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}

		return data;
	}
}
