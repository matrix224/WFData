package wfDataManager.client.util;

import wfDataManager.client.util.data.ClientTaskManager;
import wfDataModel.model.exception.ProcessingException;

/**
 * Utility class to facilitate the starting and stopping of client-specific tasks. <br>
 * The current possible tasks are:
 * <blockquote><pre>
 *  - {@link #TASK_BAN_CHECKER} > Task for periodically checking existing bans for expiration
 *  - {@link #TASK_BAN_FETCHER} > Task for periodically checking the service for any new bans
 *  - {@link #TASK_LOG_PROCESSOR} > Task for periodically processing the logs
 *  - {@link #TASK_RETRY_FAILED} > Task for periodically retrying any failed service data requests 
 * </pre></blockquote>
 * @author MatNova
 *
 */
public final class ClientTaskUtil {

	private static ClientTaskManager singleton;
	
	public static final String TASK_LOG_PROCESSOR = "Log Processor";
	public static final String TASK_BAN_CHECKER = "Ban Checker";
	public static final String TASK_BAN_FETCHER = "Ban Fetcher";
	public static final String TASK_RETRY_FAILED = "Failed Request Retry";
	
	private static synchronized ClientTaskManager singleton() {
		if (singleton == null) {
			singleton = new ClientTaskManager();
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
