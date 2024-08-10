package wfDataService.service.main;

import java.net.InetSocketAddress;
import java.util.Scanner;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import jdtools.exception.ProcessingException;
import jdtools.logging.Log;
import wfDataModel.model.processor.commands.CommandProcessor;
import wfDataModel.service.type.RequestType;
import wfDataService.service.db.DBManagementDao;
import wfDataService.service.handler.AddBanHandler;
import wfDataService.service.handler.AddDataHandler;
import wfDataService.service.handler.GetBansHandler;
import wfDataService.service.handler.RegisterHandler;
import wfDataService.service.util.ServiceSettingsUtil;
import wfDataService.service.util.ServiceTaskUtil;
import wfDataService.service.versioning.BuildVersion;

/**
 * Main class for running the WF Data Service
 * @author MatNova
 *
 */
public class WFDataService {

	private static final int MAX_SHUTDOWN_DELAY = 10; // Max time in seconds server will wait to shut down
	private static final String LOG_ID = WFDataService.class.getSimpleName();
	
	public static boolean shouldExit = false;
	
	public static void main(String[] args) {
		int rc = 0;
		try {

			if (!ServiceSettingsUtil.settingsLoaded()) {
				throw new ProcessingException("Issue occurred loading settings");
			}
			
			// Perform any DB upgrades that may be necessary before we start any processing
			DBManagementDao.upgradeDB();
			
			HttpServer server = HttpServer.create(new InetSocketAddress(ServiceSettingsUtil.getServicePort()), 0);
			server.createContext(RequestType.REGISTER.getEndPoint(), new RegisterHandler());
			server.createContext(RequestType.ADD_BAN.getEndPoint(), new AddBanHandler());
			server.createContext(RequestType.GET_BANS.getEndPoint(), new GetBansHandler());
			server.createContext(RequestType.ADD_DATA.getEndPoint(), new AddDataHandler());
			server.setExecutor(Executors.newCachedThreadPool());
			server.start();
			Log.info("WFDataService() : Service started, version: " + BuildVersion.getBuildVersion());
			Log.info("WFDataService() : Service listening on port " + ServiceSettingsUtil.getServicePort());
			
			if (ServiceSettingsUtil.trackServerStatus()) {
				ServiceTaskUtil.addTask(ServiceTaskUtil.TASK_SERVER_STATUS);
			}
			
			CommandProcessor cmdProcessor = new CommandProcessor("wfDataService.service.commands");
			try (Scanner scanner = new Scanner(System.in)) {
				while (!shouldExit) {
					String cmd = scanner.nextLine();
					cmdProcessor.processCommand(cmd);
				}
			}
			
			Log.info("WFDataService() : Stopping service in " + MAX_SHUTDOWN_DELAY + " seconds...");

			server.stop(MAX_SHUTDOWN_DELAY);
			Log.info("WFDataService() : Service shut down");
		} catch (Exception e) {
			Log.error("WFDataService() : Exception occurred -> ", e);
			rc = 2;
		} finally {
			ServiceTaskUtil.stopTasks();
			Log.info(LOG_ID + "() : Shutting down with RC " + rc);
			System.exit(rc);
		}
	}
}
