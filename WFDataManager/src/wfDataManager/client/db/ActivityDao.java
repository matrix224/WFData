package wfDataManager.client.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;

import wfDataManager.client.db.manager.ResourceManager;
import wfDataModel.model.data.ActivityData;
import wfDataModel.model.logging.Log;

/**
 * Dao class for adding activity data to the DB
 * @author MatNova
 *
 */
public class ActivityDao {
	private static final String LOG_ID = ActivityDao.class.getSimpleName();

	public static boolean addActivityData(Collection<ActivityData> activities, int gameMode, int elo) {
		Connection conn = null;
		PreparedStatement ps = null;
		boolean isSuccess = true;


		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("INSERT INTO ACTIVITY_DATA (GAME_MODE,ELO,TIME,COUNT) VALUES (?,?,?,?)");

			for (ActivityData activity : activities) {
				Instant activityTime = Instant.ofEpochMilli(activity.getTimestamp());
				ps.setInt(1, gameMode);
				ps.setInt(2, elo);
				ps.setObject(3, activityTime.atOffset(ZoneOffset.UTC)); // Note we store all activity data in UTC time
				ps.setInt(4, activity.getPlayerCount());
				int result = ps.executeUpdate();
				if (result != 1) {
					Log.warn(LOG_ID + ".addActivityData() : Did not update server activity data for gameMode " + gameMode + " elo " + elo);
				}
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
