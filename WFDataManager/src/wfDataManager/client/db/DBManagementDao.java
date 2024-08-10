package wfDataManager.client.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jdtools.exception.ProcessingException;
import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataManager.client.db.manager.ResourceManager;
import wfDataManager.client.versioning.BuildVersion;
import wfDataModel.model.util.DBUtil;

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
					ps = conn.prepareStatement("CREATE TABLE IF NOT EXISTS `tracked_players` ("
							+ "  `uid` varchar(64) NOT NULL,"
							+ "  `known_ips` varchar(2048),"
							+ "  `known_alts` varchar(4096),"
							+ "  PRIMARY KEY (`uid`)"
							+ ")");
					ps.executeUpdate();
					ResourceManager.releaseResources(ps);

					ps = conn.prepareStatement("ALTER TABLE `player_profile` ADD COLUMN `aid` varchar(64) DEFAULT NULL");
					ps.executeUpdate();
					ResourceManager.releaseResources(ps);

					ps = conn.prepareStatement("ALTER TABLE `player_profile` ADD COLUMN `past_uids` varchar(4096) DEFAULT NULL");
					ps.executeUpdate();
					ResourceManager.releaseResources(ps);

					ps = conn.prepareStatement("CREATE TABLE IF NOT EXISTS `weekly_data_copy` ("
							+ "  `week_date` date NOT NULL"
							+ ",  `uid` varchar(64) NOT NULL"
							+ ",  `kills` integer NOT NULL"
							+ ",  `deaths` integer NOT NULL"
							+ ",  `mechanics` integer NOT NULL"
							+ ",  `captures` integer NOT NULL"
							+ ",  `rounds` integer NOT NULL"
							+ ",  `game_mode` integer NOT NULL"
							+ ",  `elo` integer NOT NULL"
							+ ",  `weapon_kills` varchar(4096) DEFAULT NULL"
							+ ",  `killed_by` varchar(16384) DEFAULT NULL"
							+ ",  `total_time` varchar(128) NOT NULL"
							+ ",  `platform` integer NOT NULL"
							+ ",  PRIMARY KEY (`elo`,`week_date`,`uid`,`game_mode`,`platform`)"
							+ ")");
					ps.executeUpdate();
					ResourceManager.releaseResources(ps);

					ps = conn.prepareStatement("INSERT INTO `weekly_data_copy` (week_date, uid, kills, deaths, mechanics, captures, rounds, game_mode, elo, weapon_kills, killed_by, total_time, platform) SELECT W.WEEK_DATE, W.UID, W.KILLS, W.DEATHS, W.MECHANICS, W.CAPTURES, W.ROUNDS, W.GAME_MODE, W.ELO, W.WEAPON_KILLS, W.KILLED_BY, W.TOTAL_TIME, P.PLATFORM FROM WEEKLY_DATA W, PLAYER_PROFILE P WHERE W.UID=P.UID");
					ps.executeUpdate();
					ResourceManager.releaseResources(ps);

					ps = conn.prepareStatement("DROP TABLE `weekly_data`");
					ps.executeUpdate();
					ResourceManager.releaseResources(ps);

					ps = conn.prepareStatement("ALTER TABLE `weekly_data_copy` RENAME TO `weekly_data`");
					ps.executeUpdate();
					ResourceManager.releaseResources(ps);

				}
				
				
				if ("1.1.1".compareTo(curDBVer) > 0) {
					ps = conn.prepareStatement("ALTER TABLE `weapon_info` ADD COLUMN `weapon_type` varchar(16) DEFAULT 'UNKNOWN'");
					ps.executeUpdate();
					ResourceManager.releaseResources(ps);
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
			ResourceManager.releaseResources(ps);
			ResourceManager.releaseResources(conn);
		}
	}	
}
