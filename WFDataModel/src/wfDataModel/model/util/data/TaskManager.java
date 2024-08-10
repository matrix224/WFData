package wfDataModel.model.util.data;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;

/**
 * Base implementation for task managers to be used for executing specific tasks in the service and client
 * @author MatNova
 *
 */
public abstract class TaskManager {

	protected final String LOG_ID = getClass().getSimpleName();
	private Map<String, ScheduledThreadPoolExecutor> executors = new HashMap<String, ScheduledThreadPoolExecutor>(8);

	public void addTask(String taskName) {
		if (alreadyHasTask(taskName)) {
			Log.warn(LOG_ID + ".addTask() : Already have task set for " + taskName + ". Will do nothing.");
		} else {
			TaskInfo taskInfo = getTask(taskName);

			if (taskInfo != null && taskInfo.delay != 0 && taskInfo.period != 0 && taskInfo.task != null) {
				ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
				scheduler.setRemoveOnCancelPolicy(true);
				scheduler.scheduleAtFixedRate(taskInfo.task, taskInfo.delay, taskInfo.period, TimeUnit.SECONDS);
				executors.put(taskName, scheduler);
				Log.debug(LOG_ID + ".addTask() : Added task for " + taskName);
			} else {
				Log.warn(LOG_ID + ".addTask() : Could not create parameters for taskName " + taskName + ", will not add");
			}
		}
	}

	protected boolean alreadyHasTask(String taskName) {
		return executors.containsKey(taskName);
	}

	public void stopTask(String taskName ) {
		if (!alreadyHasTask(taskName)) {
			Log.warn(LOG_ID + ".stopTask() : Task ", taskName, " is not running, will not stop anything");
		} else {
			executors.get(taskName).shutdownNow();
			Log.debug(LOG_ID + ".stopTask() : Stopped task for " + taskName);
		}
	}
	
	public void stopTasks() {
		if (!MiscUtil.isEmpty(executors)) {
			Log.info(LOG_ID + ".stopTasks() : Shutting down " + executors.size() + " tasks...");
			for (String task : executors.keySet()) {
				ScheduledExecutorService executor = executors.get(task);
				executor.shutdown();
				try {
					if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
						Log.warn(LOG_ID + ".stopTasks() : Task for " + task + " did not shut down within one minute, forcing...");
						executor.shutdownNow();
					}
				} catch (InterruptedException e) {
					Log.error(LOG_ID + ".stopTasks() : Error stopping task " + task + " -> ", e);
				}
				Log.debug(LOG_ID + ".stopTasks() : Stopped task for " + task);
			}
			Log.info(LOG_ID + ".stopTasks() : Done");
		}
	}

	protected abstract TaskInfo getTask(String taskName);

	protected class TaskInfo {
		public long delay;
		public long period;
		public Runnable task;
		
		public TaskInfo() {
		}
	}

}
