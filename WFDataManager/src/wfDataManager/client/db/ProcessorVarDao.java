package wfDataManager.client.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

import jdtools.logging.Log;
import wfDataManager.client.db.manager.ResourceManager;

/**
 * Dao class for managing certain process related variables
 * @author MatNova
 *
 */
public final class ProcessorVarDao {

	public static final String VAR_NRS_BUILD = "NRS_ALERT_BUILD";
	public static final String VAR_SYM_KEY = "SKEY";
	public static final String VAR_DB_VERSION = "DBVER";
	
	private static final String LOG_ID = ProcessorVarDao.class.getSimpleName();
	
	public static String getVar(String varName) {
		Connection conn = null;
		String varValue = null;
		try {
			conn = ResourceManager.getDBConnection();
			varValue = getVar(conn, varName);
		} catch (Exception e) {
			Log.error(LOG_ID + ".getVar() : Error occurred -> " + e.getLocalizedMessage());
		} finally {
			ResourceManager.releaseResources(conn);
		}
		return varValue;
	}
	
	public static String getVar(Connection conn, String varName) {
		PreparedStatement ps = null;
		ResultSet rs = null;
		String varValue = null;

		try {
			ps = conn.prepareStatement("SELECT VAR_VALUE FROM PROCESS_VARS WHERE VAR_NAME=?");
			ps.setString(1, varName);
			rs = ps.executeQuery();
			if (rs.next()) {
				varValue = rs.getString("VAR_VALUE");
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".getVar() : Error occurred -> " + e.getLocalizedMessage());
		} finally {
			ResourceManager.releaseResources(ps, rs);
		}
		return varValue;
	}
	
	public static void updateVar(String varName, String varValue) {
		Connection conn = null;
		try {
			conn = ResourceManager.getDBConnection();
			updateVar(conn, varName, varValue);
		} catch (Exception e) {
			Log.error(LOG_ID + ".updateVar() : Error occurred -> " + e.getLocalizedMessage());
		} finally {
			ResourceManager.releaseResources(conn);
		}
	}

	public static void updateVar(Connection conn, String varName, String varValue) {
		PreparedStatement ps = null;

		try {
			ps = conn.prepareStatement("REPLACE INTO PROCESS_VARS (VAR_NAME, VAR_VALUE) VALUES (?, ?)");
			ps.setString(1, varName);
			ps.setString(2, varValue);
			int result = ps.executeUpdate();
			
			if (result != 1) {
				Log.warn(LOG_ID + ".updateVar() : Did not update var " + varName + ", result = " + result);
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".updateVar() : Error occurred -> " + e.getLocalizedMessage());
		} finally {
			ResourceManager.releaseResources(ps);
		}
	}

	public static byte[] getSymKey() {
		return Base64.getDecoder().decode(getVar(VAR_SYM_KEY));
	}
}

