package wfDataManager.client.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataManager.client.db.manager.ResourceManager;
import wfDataModel.service.data.BanData;
import wfDataModel.service.data.BanSpec;
import wfDataModel.service.type.BanActionType;

/**
 * Dao class for managing ban data in the DB
 * @author MatNova
 *
 */
public final class BanDao {

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
			ps = conn.prepareStatement("SELECT MP.UID AS UID, COALESCE(D.NAME, 'Unknown') AS PLAYER_NAME, MP.LOADOUTS AS LOADOUTS, CB.WHEN_BANNED AS WHEN_BANNED, CB.LOADOUT_ID AS LOADOUT_ID, CB.REASON AS REASON, CB.IP AS IP, CB.IS_PRIMARY AS IS_PRIMARY, CB.IS_PROXY AS IS_PROXY FROM MARKED_PLAYERS MP LEFT OUTER JOIN CURRENT_BANS CB ON CB.UID=MP.UID LEFT OUTER JOIN PLAYER_PROFILE D ON D.UID=MP.UID GROUP BY MP.UID, CB.IP");
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

	public static void updateBanDataReferences(Connection connectionIn, String oldUID, String newUID) {
		Connection conn = null;
		PreparedStatement ps = null;

		try {
			conn = connectionIn == null ? ResourceManager.getDBConnection() : connectionIn;
			ps = conn.prepareStatement("UPDATE CURRENT_BANS SET UID=? WHERE UID=?");
			ps.setString(1, newUID);
			ps.setString(2, oldUID);
			int result = ps.executeUpdate();
			Log.info(LOG_ID + ".updateBanDataReferences() : Updated " + result + " current ban references for old UID " + oldUID);

			ResourceManager.releaseResources(ps);

			ps = conn.prepareStatement("UPDATE MARKED_PLAYERS SET UID=? WHERE UID=?");
			ps.setString(1, newUID);
			ps.setString(2, oldUID);
			result = ps.executeUpdate();
			Log.info(LOG_ID + ".updateBanDataReferences() : Updated " + result + " current marked player references for old UID " + oldUID);

		} catch (Exception e) {
			Log.error(LOG_ID + ".updateBanDataReferences() : Exception updating ban data -> " + e.getLocalizedMessage());
		} finally {
			if (connectionIn == null) {
				ResourceManager.releaseResources(conn, ps);
			} else {
				ResourceManager.releaseResources(ps);
			}
		}
	}

	public static void mergeMarkedPlayers(Connection connectionIn, String oldUID, String newUID) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		String oldLoadoutsStr = null;
		String newLoadoutsStr = null;

		try {
			conn = connectionIn == null ? ResourceManager.getDBConnection() : connectionIn;
			ps = conn.prepareStatement("SELECT UID,LOADOUTS FROM MARKED_PLAYERS WHERE UID=? OR UID=?");
			ps.setString(1, oldUID);
			ps.setString(2, newUID);
			rs = ps.executeQuery();
			while (rs.next()) {
				String uid = rs.getString("UID");
				if (oldUID.equals(uid)) {
					oldLoadoutsStr = rs.getString("LOADOUTS");
				} else {
					newLoadoutsStr = rs.getString("LOADOUTS");
				}
			}

			ResourceManager.releaseResources(ps, rs);

			if (!MiscUtil.isEmpty(oldLoadoutsStr)) {
				// If there is an entry for the old UID and an entry for the new UID, then we will merge them together
				// If there is an entry for the old UID but not one for the new UID, then update old UID reference to new UID reference
				// Otherwise if there is not an entry for the old UID, nothing to do here
				if (!MiscUtil.isEmpty(newLoadoutsStr)) {
					JsonObject oldLoadoutsObj = JsonParser.parseString(oldLoadoutsStr).getAsJsonObject();
					JsonArray oldLoadoutsArr = oldLoadoutsObj.getAsJsonArray("loadouts");
					JsonObject newLoadoutsObj = JsonParser.parseString(newLoadoutsStr).getAsJsonObject();
					JsonArray newLoadoutsArr = newLoadoutsObj.getAsJsonArray("loadouts");
					Gson gson = new GsonBuilder().disableHtmlEscaping().create();
					Set<Long> loadoutValues = new HashSet<>();
					for (JsonElement e : oldLoadoutsArr) {
						loadoutValues.add(e.getAsLong());
					}
					for (JsonElement e : newLoadoutsArr) {
						loadoutValues.add(e.getAsLong());
					}

					newLoadoutsObj.add("loadouts", gson.toJsonTree(loadoutValues));
					
					ps = conn.prepareStatement("UPDATE MARKED_PLAYERS SET LOADOUTS=? WHERE UID=?");
					ps.setString(1, newLoadoutsObj.toString());
					ps.setString(2, newUID);
					int result = ps.executeUpdate();
					Log.info(LOG_ID + ".mergeMarkedPlayers() : Updated " + result + " marked player references for new UID " + newUID);
					ResourceManager.releaseResources(ps);

					ps = conn.prepareStatement("DELETE FROM MARKED_PLAYERS WHERE UID=?");
					ps.setString(1, oldUID);
					result = ps.executeUpdate();
					Log.info(LOG_ID + ".mergeMarkedPlayers() : Removed " + result + " marked player references for old UID " + oldUID);
				} else {
					ps = conn.prepareStatement("UPDATE MARKED_PLAYERS SET UID=? WHERE UID=?");
					ps.setString(1, newUID);
					ps.setString(2, oldUID);
					int result = ps.executeUpdate();
					Log.info(LOG_ID + ".mergeMarkedPlayers() : Updated " + result + " marked player references from old UID " + oldUID + " to new UID " + newUID);
				}
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".mergeMarkedPlayers() : Exception updating ban data -> " + e.getLocalizedMessage());
		} finally {
			if (connectionIn == null) {
				ResourceManager.releaseResources(conn, ps, rs);
			} else {
				ResourceManager.releaseResources(ps, rs);
			}
		}
	}

}
