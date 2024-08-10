package wfDataService.service.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneOffset;

import jdtools.logging.Log;
import wfDataModel.service.type.GameDataType;
import wfDataService.service.db.manager.ResourceManager;

public final class TransactionDao {

	private static final String LOG_ID = TransactionDao.class.getSimpleName();

	public static void markTransaction(String tid, int sid, GameDataType type) {
		Connection conn = null;
		PreparedStatement ps = null;

		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("INSERT INTO SERVICE_TRANSACTIONS (TID,SID,TYPE,TIME) VALUES (?,?,?,?)");
			ps.setString(1, tid);
			ps.setInt(2, sid);
			ps.setString(3, type.name());
			ps.setObject(4, Instant.ofEpochMilli(System.currentTimeMillis()).atOffset(ZoneOffset.UTC));
			if (ps.executeUpdate() != 1) {
				Log.warn(LOG_ID + ".markTransaction() : Did not insert row, tid=", tid, ", sid=" + sid, ", type=", type.name());
			}
		} catch (Exception e) { 
			Log.error(LOG_ID + ".markTransaction() : Error trying to mark transaction: tid=" + tid + ", sid=" + sid + ", type=" + type.name() + " -> ", e);
		} finally {
			ResourceManager.releaseResources(conn, ps);
		}
	}

	public static boolean alreadyProcessed(String tid, int sid, GameDataType type) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		boolean exists = false;
		
		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("SELECT SID FROM SERVICE_TRANSACTIONS WHERE TID=? AND SID=? AND TYPE=?");
			ps.setString(1, tid);
			ps.setInt(2, sid);
			ps.setString(3, type.name());
			rs = ps.executeQuery();
			exists = rs.next();
		} catch (Exception e) { 
			Log.error(LOG_ID + ".alreadyProcessed() : Error trying to check transaction: tid=" + tid + ", sid=" + sid + ", type=" + type.name() + " -> ", e);
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}
		return exists;
	}
	
}
