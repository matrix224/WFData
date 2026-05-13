package wfDataManager.client.task;

import java.util.ArrayList;
import java.util.List;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataManager.client.cache.AllocatorCache;
import wfDataManager.client.cache.ServerDataCache;
import wfDataManager.client.data.AllocatorGameData;
import wfDataManager.client.util.AllocatorUtil;
import wfDataModel.model.data.ServerData;
import wfDataModel.service.type.AllocatorType;

/**
 * Task that handles the allocation of servers (e.g. starting / stopping them as needed)
 * @author MatNova
 *
 */
public class AllocatorTask implements Runnable {

	private static final String LOG_ID = AllocatorTask.class.getSimpleName();

	@Override
	public void run() {
		try {
			List<AllocatorGameData> runningInstances = AllocatorUtil.getRunningInstances();
			int maxExpected = AllocatorCache.singleton().getMaxExpectedServers();

			// There's no running instances, so we'll go through and launch everything we can
			/*if (MiscUtil.isEmpty(runningInstances)) {
				int instanceId = 1;
				// TODO: if update available, try update. otherwise start what's needed

				// First launch any fixed servers
				List<AllocatorGameData> fixedServers = AllocatorCache.singleton().getLaunchableServers(AllocatorType.FIXED);
				if (!MiscUtil.isEmpty(fixedServers)) {
					instanceId = launchServers(fixedServers, instanceId);
				}

				// If we still have more possible servers to launch, then launch any of the balanced ones
				if (instanceId <= maxExpected) {
					List<AllocatorGameData> balancedServers = AllocatorCache.singleton().getLaunchableServers(AllocatorType.BALANCED);
					if (!MiscUtil.isEmpty(balancedServers)) {
						instanceId = launchServers(balancedServers, instanceId);
					}
				}
			} else {*/
			boolean anyLoading = false;
			int totalRunning = MiscUtil.isEmpty(runningInstances) ? 0 : runningInstances.size();
			List<Integer> runningIDs = new ArrayList<Integer>();
			if (!MiscUtil.isEmpty(runningInstances)) {
				for (AllocatorGameData instance : runningInstances) {
					if (instance.getInstanceId() == null || instance.getGameMode() == null) {
						anyLoading = true;
					} else if (instance.getInstanceId() != null) {
						runningIDs.add(instance.getInstanceId());
					}
				}
			}
			
			// If anything is still loading, do nothing
			if (anyLoading) {
				return;
			}

			List<AllocatorGameData> fixedServers = AllocatorCache.singleton().getLaunchableServers(AllocatorType.FIXED);
			List<AllocatorGameData> balancedServers = AllocatorCache.singleton().getLaunchableServers(AllocatorType.BALANCED);
			if (!MiscUtil.isEmpty(fixedServers)) {
				for (AllocatorGameData fixedServer : fixedServers) {
					int runningCount = 0;
					if (!MiscUtil.isEmpty(runningInstances)) {
						for (AllocatorGameData instance : runningInstances) {
							if (instance.getInstanceId() != null && fixedServer.getGameMode().equals(instance.getGameMode()) && fixedServer.getElo().equals(instance.getElo())) {
								++runningCount;
							}
						}
					}

					// If there's less than desired number of servers of this type running, and no servers are currently still loading,
					// then we'll put however many are needed up by filling any empty spots, or killing off balanced ones
					if (runningCount < fixedServer.getMinCount()) {
						// If the number of total servers currently running is less than the max servers that can be running,
						// then just fill any empty spaces with the fixed server first
						if (totalRunning < maxExpected) {
							for (int i = 0; i < Math.min(fixedServer.getMinCount() - runningCount, maxExpected - totalRunning); i++) {
								int instanceId = 0;
								for (int n = 1; n <= maxExpected; n++) {
									if (!runningIDs.contains(n)) {
										instanceId = n;
										runningIDs.add(n);
										break;
									}
								}
								Thread.sleep(3000);
								AllocatorUtil.launchServer(fixedServer, instanceId);
								++runningCount;
								++totalRunning;
							}
						}

						// If there's still more fixed servers to run, and any of the current running instances are balanced servers,
						// then we will kill any empty ones off to start the fixed one
						if (runningCount < fixedServer.getMinCount() && !MiscUtil.isEmpty(balancedServers)) {
							for (AllocatorGameData balancedServer : balancedServers) {
								for (AllocatorGameData instance : runningInstances) {
									if (instance.getInstanceId() != null && balancedServer.getGameMode().equals(instance.getGameMode()) && balancedServer.getElo().equals(instance.getElo())) {
										ServerData serverData = ServerDataCache.singleton().getServerData(instance.getInstanceId().toString());
										if (MiscUtil.isEmpty(serverData.getConnectedPlayers())) {
											Log.info(LOG_ID, "() : Killing server " + instance.getInstanceId() + " (" + instance.getGameMode() + " " + instance.getElo() + ") to launch fixed count server " + fixedServer.getGameMode() + " " + fixedServer.getElo());
											AllocatorUtil.killServer(instance);
											Thread.sleep(3000);
											AllocatorUtil.launchServer(fixedServer, instance.getInstanceId());
											++runningCount;
											//++totalRunning;
											if (runningCount == fixedServer.getMinCount()) {
												break;
											}
										}
									}
								}
							}
						}
					}
				}
			}

			if (!MiscUtil.isEmpty(balancedServers)) {
				if (totalRunning < maxExpected) {
					// If there's any balanced servers set up and we have empty slots, then launch some
					for (AllocatorGameData balancedServer : balancedServers) {
						if (!MiscUtil.isEmpty(runningInstances)) {
							for (AllocatorGameData instance : runningInstances) {
								if (instance.getInstanceId() != null && balancedServer.getGameMode().equals(instance.getGameMode()) && balancedServer.getElo().equals(instance.getElo())) {

								}
							}
						}
					}
				} else {
					// Otherwise if there's any balanced servers but we're full, then see if we can / should kill off any other balanced servers

				}
			}




			//}
		} catch (Throwable t) {
			Log.error(LOG_ID + "(): Error while processing -> ", t);
		}
	}

	private int launchServers(List<AllocatorGameData> servers, int startInstance) throws InterruptedException {
		int instanceId = startInstance;
		for (AllocatorGameData server : servers) {
			for (int i = 0; i < Math.max(1, server.getMinCount()); ++i) {
				AllocatorUtil.launchServer(server, instanceId);
				Thread.sleep(3000);
				++instanceId;
				if (instanceId > AllocatorCache.singleton().getMaxExpectedServers()) {
					break;
				}
			}
		}

		return instanceId;

	}

}
