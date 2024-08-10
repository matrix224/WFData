package wfDataService.service.main;

import jdtools.codes.ExitCode;
import jdtools.logging.Log;
import wfDataService.service.processor.DBPlayerMergeProcessor;

public class DBPlayerMerge {
	
	private static final String LOG_ID = DBPlayerMerge.class.getSimpleName();
	
	public static void main(String[] args) {
		ExitCode rc = ExitCode.SUCCESS;
		try {
			Log.info(LOG_ID + "() : Beginning processing");
			new DBPlayerMergeProcessor().process();
		} catch (Throwable t) {
			Log.error(LOG_ID + ".() : Error while processing -> ", t);
			rc = ExitCode.ERROR;
		} finally {
			Log.info(LOG_ID + "() : Done processing");
			System.exit(rc.getCode());
		}
	}
}
