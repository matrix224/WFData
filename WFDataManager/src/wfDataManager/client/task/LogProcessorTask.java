package wfDataManager.client.task;

import jdtools.logging.Log;
import wfDataManager.client.processor.logging.BaseLogProcessor;
import wfDataManager.client.processor.logging.HistoricalLogProcessor;
import wfDataManager.client.processor.logging.NormalLogProcessor;
import wfDataManager.client.processor.logging.TestLogProcessor;
import wfDataManager.client.type.ProcessModeType;
import wfDataManager.client.util.ClientSettingsUtil;

/**
 * Task for periodically processing the log files
 * @author MatNova
 *
 */
public class LogProcessorTask implements Runnable {

	private static BaseLogProcessor processor = ProcessModeType.NORMAL.equals(ClientSettingsUtil.getProcessMode()) ? new NormalLogProcessor()
			: ProcessModeType.TEST.equals(ClientSettingsUtil.getProcessMode()) ? new TestLogProcessor() : new HistoricalLogProcessor();

	@Override
	public void run() {
		try {
			processor.processLogs();
		} catch (Throwable t) {
			Log.error(LogProcessorTask.class.getSimpleName() + "() : Exception while processing -> ", t);
		}
	}

}
