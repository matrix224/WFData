package wfDataManager.client.commands;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataManager.client.cache.PlayerTrackerCache;
import wfDataManager.client.cache.ServerDataCache;
import wfDataManager.client.data.PlayerTracker;
import wfDataManager.client.db.PlayerTrackerDao;
import wfDataModel.model.commands.BaseCmd;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.ServerData;

/**
 * Command for managing player trackers
 * @author MatNova
 *
 */
public class TrackerCmd extends BaseCmd {

	public TrackerCmd() {
		super(2);
	}

	@Override
	public String getDescription() {
		StringBuilder desc = new StringBuilder("Allows the addition, removal, or viewing of players marked for alt-tracking").append("\n");
		desc.append("tracker add <UID> - Adds the specified UID to the players to be tracked \n");
		desc.append("tracker remove <UID> - Removes the specified UID from the players to be tracked \n");
		desc.append("tracker view <UID> - Lists tracking information about the specified UID \n");
		desc.append("tracker list - Lists information about all currently tracked players");
		return desc.toString();
	}

	@Override
	public void runCmd(String... args) {
		if (args == null || args.length == 0 || (!args[0].equalsIgnoreCase("add") && !args[0].equalsIgnoreCase("remove") && !args[0].equals("view") &&  !args[0].equals("list"))) {
			Log.warn("Invalid arguments supplied, usage: \n " + getDescription());
		} else if (args[0].equals("add") || args[0].equals("remove") || args[0].equals("view")) { 
			if (args.length < 2) {
				Log.warn("Invalid arguments supplied, usage: \n " + getDescription());
			} else {
				boolean isAdd = args[0].equals("add");
				String uid = args[1];
				PlayerTracker tracker = PlayerTrackerCache.singleton().getPlayerTracker(uid);
				if (tracker != null && isAdd) {
					Log.warn("Tracker entry already exists for " + uid);
				} else if (tracker == null && !isAdd) {
					Log.warn("Tracker entry not found for " + uid);
				} else if (isAdd) {
					tracker = new PlayerTracker(uid);
					for (ServerData server : ServerDataCache.singleton().getAllServerData()) {
						for (PlayerData player : server.getParsedPlayers()) {
							String ip = !MiscUtil.isEmpty(player.getIPAndPort()) ? player.getIPAndPort().split(":")[0] : "";
							if (player.getUID().equals(uid) && !MiscUtil.isEmpty(ip) && !server.isProxyIP(ip)) {
								tracker.addKnownIP(ip);
							}
						}
						// If an IP was found, go through players again and see if anyone else with that IP is found and mark them as alts
						if (!MiscUtil.isEmpty(tracker.getKnownIPs())) {
							for (PlayerData player : server.getParsedPlayers()) {
								String ip = !MiscUtil.isEmpty(player.getIPAndPort()) ? player.getIPAndPort().split(":")[0] : "";
								if (!player.getUID().equals(uid) && !MiscUtil.isEmpty(ip) && tracker.getKnownIPs().contains(ip)) {
									tracker.addKnownAlt(player.getUID(), player.getPlayerName());
								}
							}
						}
					}
					PlayerTrackerCache.singleton().addTracker(tracker);
					PlayerTrackerDao.addPlayerTracker(tracker);
					Log.info("Added tracker for " + uid);
				} else {
					if (args[0].equals("remove")) {
						PlayerTrackerCache.singleton().removeTracker(tracker);
						PlayerTrackerDao.removePlayerTracker(tracker);
						Log.info("Removed tracker for " + uid);
					} else {
						Log.info(tracker.toString());
					}
				}
			}
		} else if (args[0].equals("list")) { 
			for (PlayerTracker tracker : PlayerTrackerCache.singleton().getPlayerTrackers()) {
				Log.info(tracker.toString());
			}
		}
	}
}
