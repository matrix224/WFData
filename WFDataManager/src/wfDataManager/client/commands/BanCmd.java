package wfDataManager.client.commands;

import wfDataManager.client.cache.BanManagerCache;
import wfDataManager.client.cache.ServerDataCache;
import wfDataManager.client.db.GameDataDao;
import wfDataModel.model.commands.BaseCmd;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.logging.Log;
import wfDataModel.service.type.BanActionType;

/**
 * Command for adding or removing a permanent ban
 * @author MatNova
 *
 */
public class BanCmd extends BaseCmd {

	public BanCmd() {
		super(3);
	}

	@Override
	public String getDescription() {
		StringBuilder desc = new StringBuilder("Insert or remove a permanent ban for a specified player UID").append("\n");
		desc.append("ban add <UID> <?reason> - Add a permanent ban for the specified UID, with an optional reason \n");
		desc.append("ban remove <UID> - Remove a permanent ban for the specified UID");
		return desc.toString();
	}

	@Override
	public void runCmd(String... args) {
		if (args == null || args.length < 2 || (!args[0].equalsIgnoreCase("add") && !args[0].equalsIgnoreCase("remove"))) {
			Log.warn("Invalid arguments supplied, usage: \n " + getDescription());
		} else {
			boolean add = args[0].equalsIgnoreCase("add");
			String uid = args[1];

			if (add) {
				String reason = args.length == 3 ? args[2] : "Permaban";
				boolean inServer = false;
				PlayerData player = ServerDataCache.singleton().getPlayer(uid);
				if (player == null) {
					player = GameDataDao.getPlayerByUID(uid);
				} else {
					inServer = true;
				}
				if (player == null) {
					Log.warn("Unknown player provided (not currently playing and not in DB), but will insert ban for provided UID -> " + uid);
				} 
				if (!BanManagerCache.singleton().createPermaBan(player != null ? player.getPlayerName() : "Unknown", uid, reason)) {
					Log.warn("Already have permanent ban for UID " + uid);
				} else {
					Log.info("Added permanent ban for UID " + uid + ", reason=" + reason);
					if (inServer) {
						// If this person is currently in a server, get them the hell out
						BanManagerCache.singleton().manageBan(BanManagerCache.singleton().getBanData(uid), BanActionType.ADD, player.getIPAndPort());
					}
				}
			} else {
				if (!BanManagerCache.singleton().removePermaBan(uid)) {
					Log.warn("Did not find any permanent ban for UID " + uid);
				} else {
					Log.info("Removed permanent ban for UID " + uid);
				}
			}
		}
	}

}
