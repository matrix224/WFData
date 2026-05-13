package wfDataService.service.main;

import jdtools.codes.ExitCode;
import jdtools.logging.Log;
import wfDataService.service.processor.ActivityProcessor;

public class ConsolidatedActivityBackfill {

	private static final String LOG_ID = ConsolidatedActivityBackfill.class.getSimpleName();

	public static void main(String[] args) {
		ExitCode rc = ExitCode.SUCCESS;

		try {
			Log.info(LOG_ID, "() : Begin processing");
			new ActivityProcessor().processPlaytimeData();
		} catch (Exception e) {
			Log.error(LOG_ID + "() -> ", e);
		} finally {
			Log.info(LOG_ID, "() : Done processing");
			System.exit(rc.getCode());
		}
	}
}
