package wfDataManager.client.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import wfDataManager.client.db.manager.ResourceManager;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.logging.Log;

/**
 * Dao class for managing server status data
 * @author MatNova
 *
 */
public class ServerDao {

	private static final String LOG_ID = ServerDao.class.getSimpleName();

	public static ServerData getServerData(String logId, boolean createIfMissing) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		ServerData serverData = null;

		try {
			conn = ResourceManager.getDBConnection(); 
			ps = conn.prepareStatement("SELECT DATA FROM SERVER_DATA WHERE ID=?");
			ps.setString(1, logId);
			rs = ps.executeQuery();
			if (rs.next()) {
				JsonObject serverDataObj = JsonParser.parseString(rs.getString("DATA")).getAsJsonObject();
				if (serverDataObj != null) {
					serverData = new ServerData(logId, serverDataObj);
				} else {
					Log.warn(LOG_ID + ".getServerData() : Server data could not be parsed for ID " + logId);
				}
			}

			if (createIfMissing && serverData == null) {
				serverData = new ServerData(logId, -1);
				ResourceManager.releaseResources(ps,rs);
				ps = conn.prepareStatement("INSERT INTO SERVER_DATA (ID, DATA) VALUES(?,?)");
				ps.setString(1, logId);
				ps.setString(2, new JsonObject().toString());
				int result = ps.executeUpdate();
				if (result == 1) {
					Log.info(LOG_ID + ".getServerData() : Added entry for logId " + logId);
					serverData.setLogPosition(0);
				} else {
					Log.warn(LOG_ID + ".getServerData() : Failed to add entry for logId " + logId);
				}
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".getServerData() : Error occurred -> ", e);
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}
		return serverData;
	}

	public static void updateServerData(Connection conn, List<ServerData> serverDatas) {
		PreparedStatement ps = null;

		try {
			ps = conn.prepareStatement("UPDATE SERVER_DATA SET DATA=? WHERE ID=?");
			for (ServerData serverData : serverDatas) {
				ps.setString(1, serverData.getServerDataDB());
				ps.setString(2, serverData.getId());
				int result = ps.executeUpdate();
				if (result != 1) {
					Log.warn(LOG_ID + ".updateServerData() : Did not update server data for id " + serverData.getId());
				}
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".updateServerData() : Error occurred -> ", e);
		} finally {
			ResourceManager.releaseResources(ps);
		}
	}
	
	public static void addProcessedLog(ServerData serverData) {
		Connection conn = null;
		PreparedStatement ps = null;
		int logId = serverData.getNumericId();
		long logTime = serverData.getTimeStats().getStartTimeEpoch();
		String serverDir = serverData.getCurrentLaunchDir();
		String id = String.valueOf(serverDir.hashCode() + logId);
		
		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("INSERT INTO LOG_HISTORY (ID,LOG_TIME) VALUES (?,?)");
			ps.setString(1, id);
			ps.setLong(2, logTime);
			int result = ps.executeUpdate();
			if (result != 1) {
				Log.warn(LOG_ID + ".addProcessedLog() : Did not mark log time for logId " + logId + " and value " + logTime);
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".addProcessedLog() : Error occurred -> ", e);
		} finally {
			ResourceManager.releaseResources(conn, ps);
		}
	}
	
	public static boolean hasLogBeenProcessed(ServerData serverData) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		boolean hasData = false;
		int logId = serverData.getNumericId();
		long logTime = serverData.getTimeStats().getStartTimeEpoch();
		String serverDir = serverData.getCurrentLaunchDir();
		String id = String.valueOf(serverDir.hashCode() + logId);

		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("SELECT LOG_TIME FROM LOG_HISTORY WHERE ID=? AND LOG_TIME = ?");
			ps.setString(1, id);
			ps.setLong(2, logTime);
			rs = ps.executeQuery();
			hasData = rs.next();
		} catch (Exception e) {
			Log.error(LOG_ID + ".addProcessedLog() : Error occurred -> ", e);
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}
		return hasData;
	}
}
