package wfDataService.service.task;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataModel.model.util.DateUtil;
import wfDataService.service.db.GameDataDao;
import wfDataService.service.db.ProcessorVarDao;
import wfDataService.service.processor.ActivityProcessor;

public class ActivityTask implements Runnable {

	private static final String LOG_ID = ActivityTask.class.getSimpleName();
	
	@Override
	public void run() {
		Log.info(LOG_ID, "() : Beginning activity processing...");
		try {
			ActivityProcessor processor = new ActivityProcessor();
			String latestProcessed = ProcessorVarDao.getVar(ProcessorVarDao.VAR_WEEKLY_DATE);
			LocalDate latestWeekly = GameDataDao.getLatestWeeklyDate();
			
			if (latestWeekly == null) {
				Log.warn(LOG_ID, "() : Could not get latest weekly date, will do nothing for weekly...");
			} else {
				LocalDate latestProcessedDate = MiscUtil.isEmpty(latestProcessed) ? null : DateUtil.getWeekDate(latestProcessed);
				List<LocalDate> dates = new ArrayList<LocalDate>();
				if (latestProcessedDate == null || latestProcessedDate.equals(latestWeekly)) {
					dates.add(latestWeekly);
				} else if (latestProcessedDate.isBefore(latestWeekly)) {
					LocalDate tmp = latestProcessedDate;
					do {
						dates.add(tmp);
						tmp = tmp.plusDays(7);
					} while (!tmp.isAfter(latestWeekly));
				} else {
					Log.warn(LOG_ID, "() : Last processed weekly ", latestProcessed, " is after latest available weekly ", latestWeekly.toString(), " ? Will do nothing...");
				}
				
				if (!MiscUtil.isEmpty(dates)) {
					Log.info(LOG_ID, "() : Will process for " + dates.size(), " date(s)");
					processor.processWeeklyActivity(dates);
					ProcessorVarDao.updateVar(ProcessorVarDao.VAR_WEEKLY_DATE, DateUtil.getWeekDate(latestWeekly));
				}
			}
			
			// Always will process playtime data
			processor.processPlaytimeData();
		} catch (Throwable t) {
			Log.error(LOG_ID + "() : Error while processing -> ", t);
		}
		Log.info(LOG_ID, "() : Finished activity processing");
	}

}
