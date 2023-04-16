package wfDataManager.client.util.data;

import wfDataManager.client.task.BanFetcherTask;
import wfDataManager.client.task.BanProcessorTask;
import wfDataManager.client.task.RetryDataTask;
import wfDataManager.client.task.LogProcessorTask;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataManager.client.util.ClientTaskUtil;
import wfDataModel.model.logging.Log;
import wfDataModel.model.util.data.TaskManager;

/**
 * Util class for handling the starting and stopping of client-related tasks. <br>
 * Use of this is facilitated by the {@link ClientTaskUtil} class
 * 
 * @author MatNova
 *
 */
public class ClientTaskManager extends TaskManager {

	@Override
	protected TaskInfo getTask(String taskName) {
		TaskInfo taskInfo = null;

		long delay = 0;
		long period = 0;
		Runnable task = null;

		if (ClientTaskUtil.TASK_LOG_PROCESSOR.equals(taskName)) {
			period = ClientSettingsUtil.getPollInterval();
			delay = ClientSettingsUtil.getPollInterval();
			task = new LogProcessorTask();
		} else if (ClientTaskUtil.TASK_BAN_CHECKER.equals(taskName)) {
			period = ClientSettingsUtil.getBanCheckInterval();
			delay = ClientSettingsUtil.getBanCheckInterval();
			task = new BanProcessorTask();
		} else if (ClientTaskUtil.TASK_BAN_FETCHER.equals(taskName)) {
			period = ClientSettingsUtil.getBanFetchInterval();
			delay = ClientSettingsUtil.getBanFetchInterval();
			task = new BanFetcherTask();
		} else if (ClientTaskUtil.TASK_RETRY_FAILED.equals(taskName)) {
			delay = ClientSettingsUtil.getPollInterval() / 2; // Initial delay is half the log parsing time
			if (delay <= 0) {
				delay = 30; // This will happen during historical log processing, so just default to 30s
			}
			
			// Period for this is based on how long the service timeout is and how many failed counts we'll try at most
			// We want the period to be longer than the longest it'd take if every retry timed out so that there will be no overlap
			// i.e. if service timeout is 10 seconds and we have 10 total retries to make (10 game data),
			// we'd want a period of at least 100 seconds, so that each retry can timeout and we'd not overlap
			// Also add a bit of a buffer time onto it as well
			period = ClientSettingsUtil.getServiceTimeout() * (RetryDataTask.GAME_DATA_RETRY_COUNT) + (ClientSettingsUtil.getServiceTimeout() * 2);
			task = new RetryDataTask();
		} else {
			Log.warn(LOG_ID + ".getTask() : Unknown taskName provided -> " + taskName);
		}

		if (delay != 0 && period != 0 && task != null) {
			taskInfo = new TaskInfo();
			taskInfo.delay = delay;
			taskInfo.period = period;
			taskInfo.task = task;
		}


		return taskInfo;
	}

}
