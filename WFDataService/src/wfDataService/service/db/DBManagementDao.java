package wfDataService.service.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jdtools.exception.ProcessingException;
import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataModel.model.util.DBUtil;
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


}
