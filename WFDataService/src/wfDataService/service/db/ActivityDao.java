package wfDataService.service.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.ZoneOffset;

import wfDataModel.model.data.ActivityData;
import wfDataModel.model.logging.Log;
import wfDataService.service.data.ServerClientData;
import wfDataService.service.db.manager.ResourceManager;

/**
 * Dao that supports the storage of activity-related data in the DB.
 * 
 * @author MatNova
 *
 */
public class ActivityDao {

	private static final String LOG_ID = ActivityDao.class.getSimpleName();

	public static boolean addActivityData(ServerClientData server, ActivityData activity, int gameMode, int elo) {
		Connection conn = null;
		PreparedStatement ps = null;
		boolean isSuccess = true;
		
		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("INSERT INTO ACTIVITY_DATA (GAME_MODE,ELO,TIME,COUNT,SID) VALUES (?,?,?,?,?)");
			ps.setInt(1, gameMode);
			ps.setInt(2, elo);
			ps.setObject(3, Instant.ofEpochMilli(activity.getTimestamp()).atOffset(ZoneOffset.UTC));
			ps.setInt(4, activity.getPlayerCount());
			ps.setInt(5, server.getServerID());
			int result = ps.executeUpdate();
			if (result != 1) {
				Log.warn(LOG_ID + ".addActivityData() : Did not update server activity data for server " + server.getDisplayName() + ", gameMode " + gameMode + " elo " + elo);
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".addActivityData() : Error occurred -> ", e);
			isSuccess = false;
		} finally {
			ResourceManager.releaseResources(conn, ps);
		}
		return isSuccess;
	}

}
