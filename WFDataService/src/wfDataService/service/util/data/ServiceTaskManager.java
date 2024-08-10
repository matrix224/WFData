package wfDataService.service.util.data;

import jdtools.logging.Log;
import wfDataModel.model.util.data.TaskManager;
import wfDataService.service.task.ServerStatusTask;
import wfDataService.service.util.ServiceSettingsUtil;
import wfDataService.service.util.ServiceTaskUtil;

/**
 * Manager class for handling the creation of requested tasks.
 * @author MatNova
 *
 */
public class ServiceTaskManager extends TaskManager {

	@Override
	protected TaskInfo getTask(String taskName) {
		TaskInfo taskInfo = null;

		long delay = 0;
		long period = 0;
		Runnable task = null;

		if (ServiceTaskUtil.TASK_SERVER_STATUS.equals(taskName)) {
			period = ServiceSettingsUtil.getServerStatusUpdateInterval();
			delay = ServiceSettingsUtil.getServerStatusUpdateInterval();
			task = new ServerStatusTask();
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
