package wfDataService.service.main;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jdtools.codes.ExitCode;
import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataService.service.db.manager.ResourceManager;
import wfDataService.service.processor.ActivityProcessor;

public class WeeklyActivityBackfill {

	private static final String LOG_ID = WeeklyActivityBackfill.class.getSimpleName();

	public static void main(String[] args) {
		ExitCode rc = ExitCode.SUCCESS;

		try {
			Log.info(LOG_ID, "() : Begin processing");
			new WeeklyActivityBackfill().process();
		} catch (Exception e) {
			Log.error(LOG_ID + "() -> ", e);
		} finally {
			Log.info(LOG_ID, "() : Done processing");
			System.exit(rc.getCode());
		}

	}

	private void process() throws SQLException {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		List<LocalDate> dates = new ArrayList<LocalDate>();
		
		try {
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("SELECT DISTINCT WEEK_DATE FROM WEEKLY_DATA ORDER BY WEEK_DATE");
			rs = ps.executeQuery();
			while (rs.next()) {
				dates.add(rs.getObject("WEEK_DATE", LocalDate.class));
			}
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}
		
		if (MiscUtil.isEmpty(dates)) {
			Log.warn(LOG_ID, ".process() : No dates found to load, will do nothing");
		} else {
			Log.info(LOG_ID, ".process() : Will load data for " + dates.size(), " dates");
			new ActivityProcessor().processWeeklyActivity(dates);
		}
	}
}
