package wfDataService.service.db.manager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import wfDataModel.model.logging.Log;

/**
 * Manager class for retrieving DB connections and disposing of DB-related objects
 * @author MatNova
 *
 */
public class ResourceManager {

	private static HikariDataSource dataSource = null;
	static {
		try {
			HikariConfig config = new HikariConfig();
			dataSource = new HikariDataSource(config);
		} catch (Throwable e) {
			Log.error(ResourceManager.class.getSimpleName() + "() : Exception -> ", e);
		}
	}

	public static Connection getDBConnection() throws SQLException {
		return dataSource.getConnection();
	}

	public static void releaseResources(Connection conn) {
		releaseResources(conn, null, null);
	}

	public static void releaseResources(PreparedStatement... ps) {
		if (ps != null && ps.length > 0) {
			for (PreparedStatement stmt : ps) {
				releaseResources(null,stmt,null);
			}
		}
	}

	public static void releaseResources(ResultSet... rs) {
		if (rs != null && rs.length > 0) {
			for (ResultSet res : rs) {
				releaseResources(null,null,res);
			}
		}
	}

	public static void releaseResources(PreparedStatement ps) {
		releaseResources(null,ps,null);
	}

	public static void releaseResources(Connection conn, PreparedStatement ps) {
		releaseResources(conn,ps,null);
	}

	public static void releaseResources(PreparedStatement ps, ResultSet rs) {
		releaseResources(null,ps,rs);
	}

	public static void releaseResources(Connection conn, PreparedStatement ps, ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException e) {
				Log.error("ResourceManager.releaseResources() : Error releasing ResultSet -> " + e.getLocalizedMessage());
			}
		}
		if (ps != null) {
			try {
				ps.close();
			} catch (SQLException e) {
				Log.error("ResourceManager.releaseResources() : Error releasing PreparedStatement -> " + e.getLocalizedMessage());
			}
		}
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException e) {
				Log.error("ResourceManager.releaseResources() : Error releasing Connection -> " + e.getLocalizedMessage());
			}
		}
	}

}
