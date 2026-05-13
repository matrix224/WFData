package wfDataService.service.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataModel.model.data.ActivityData;
import wfDataModel.model.util.DateUtil;
import wfDataModel.service.data.BanData;
import wfDataModel.service.data.BanSpec;
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
			ps.setInt(5, server.getServerClientID());
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
	
	public static void addBanLog(ServerClientData server, BanData banData) {
		Connection conn = null;
		PreparedStatement ps = null;
		BanSpec spec = banData.getBanSpecs().stream().findFirst().get(); // We assume this BanData came from a client, and it's just one spec per user

		if (spec == null) {
			Log.warn(LOG_ID + ".addBanLog() : Did not update ban log data for server " + server.getDisplayName() + ", uid " + banData.getUID(), " because no spec was present");
			return;
		} else if (MiscUtil.isEmpty(spec.getBannedItem())) {
			return; // Temporary until all clients update
		}
		LocalDate dailyDate = DateUtil.getDate(spec.getBanTime(), ZoneId.of(spec.getZoneId())).toLocalDate();

		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("INSERT INTO BAN_LOG (BAN_DATE, UID, PLATFORM, WEAPON, GAME_MODE, ELO, BAN_COUNT, SID) VALUES (?,?,?,?,?,?,1,?) ON DUPLICATE KEY UPDATE BAN_COUNT = BAN_COUNT + 1");
			ps.setObject(1, dailyDate);
			ps.setString(2, banData.getUID());
			ps.setInt(3, spec.getPlatform().getCode());
			ps.setString(4, spec.getBannedItem());
			ps.setInt(5, spec.getGameMode().getId());
			ps.setInt(6, spec.getEloRating().getCode());
			ps.setInt(7, server.getServerClientID());
			int result = ps.executeUpdate();
			if (result == 0) {
				Log.warn(LOG_ID + ".addBanLog() : Did not update ban log data for server " + server.getDisplayName() + ", uid " + banData.getUID());
			}
		} catch (Exception e) {
			Log.error(LOG_ID + ".addBanLog() : Error occurred -> ", e);
		} finally {
			ResourceManager.releaseResources(conn, ps);
		}
	}

}
