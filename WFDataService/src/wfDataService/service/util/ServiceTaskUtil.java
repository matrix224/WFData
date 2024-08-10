package wfDataService.service.util;

import jdtools.exception.ProcessingException;
import wfDataService.service.util.data.ServiceTaskManager;

/**
 * Util class for starting and stopping tasks for the service.
 * @author MatNova
 *
 */
public final class ServiceTaskUtil {
	public static final String TASK_SERVER_STATUS = "Server Status";
	
	private static ServiceTaskManager singleton;
	
	private static synchronized ServiceTaskManager singleton() {
		if (singleton == null) {
			singleton = new ServiceTaskManager();
		}
		return singleton;
	}
	
	public static void addTask(String taskName) throws ProcessingException {
		singleton().addTask(taskName);
	}
	
	public static void stopTasks() {
		singleton().stopTasks();
	}
}
