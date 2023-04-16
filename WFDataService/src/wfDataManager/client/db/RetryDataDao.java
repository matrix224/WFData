package wfDataManager.client.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import wfDataManager.client.db.manager.ResourceManager;
import wfDataModel.model.logging.Log;

public class RetryDataDao {
	private static final String LOG_ID = RetryDataDao.class.getSimpleName();

	public static void addRetryData(String dataStr) {
		Connection conn = null;
		PreparedStatement ps = null;

		try {
			conn = ResourceManager.getDBConnection(); 
			ps = conn.prepareStatement("INSERT INTO RETRY_DATA (ID, DATA) VALUES (?,?)");
			ps.setInt(1, (dataStr + String.valueOf(System.currentTimeMillis())).hashCode());
			ps.setString(2, dataStr);
			ps.executeUpdate();
		} catch (Exception e) {
			Log.error(LOG_ID + ".addRetryData() : Error occurred -> " + e.getLocalizedMessage());
		} finally {
			ResourceManager.releaseResources(conn, ps);
		}
	}

	public static void deleteRetryData(int dataId) {
		Connection conn = null;
		PreparedStatement ps = null;

		try {
			conn = ResourceManager.getDBConnection(); 
			ps = conn.prepareStatement("DELETE FROM RETRY_DATA WHERE ID=?");
			ps.setInt(1, dataId);
			ps.executeUpdate();
		} catch (Exception e) {
			Log.error(LOG_ID + ".deleteRetryData() : Error occurred -> " + e.getLocalizedMessage());
		} finally {
			ResourceManager.releaseResources(conn, ps);
		}
	}

	public static Map<Integer, String> getRetryData(int limit) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		Map<Integer, String> data = new HashMap<Integer, String>();

		try {
			conn = ResourceManager.getDBConnection(); 
			ps = conn.prepareStatement("SELECT ID, DATA FROM RETRY_DATA LIMIT ?");
			ps.setInt(1, limit);
			rs = ps.executeQuery();
			while (rs.next()) {
				data.put(rs.getInt("ID"), rs.getString("DATA"));
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".getRetryData() : Error occurred -> " + e.getLocalizedMessage());
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}

		return data;
	}
}
