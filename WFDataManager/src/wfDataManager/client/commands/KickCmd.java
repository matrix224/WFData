package wfDataManager.client.commands;

import jdtools.logging.Log;
import wfDataManager.client.cache.BanManagerCache;
import wfDataManager.client.cache.ServerDataCache;
import wfDataModel.model.commands.BaseCmd;
import wfDataModel.model.data.PlayerData;
import wfDataModel.service.type.BanActionType;

public class KickCmd extends BaseCmd {

	public KickCmd() {
		super(2);
	}

	@Override
	public String getDescription() {
		StringBuilder desc = new StringBuilder("Kick a specified player UID").append("\n");
		desc.append("kick <UID> <?reason> - Kick the specified UID, with an optional reason \n");
		return desc.toString();
	}

	@Override
	public void runCmd(String... args) {
		if (args == null || args.length < 1) {
			Log.warn("Invalid arguments supplied, usage: \n " + getDescription());
		} else {
			String uid = args[0];
			String reason = args.length == 2 ? args[1] : "Kicked";

			PlayerData player = ServerDataCache.singleton().getPlayer(uid);
			if (player == null) {
				Log.warn("Player with UID ", uid, " is not currently playing. Will not kick anyone.");
				return;
			} else {
				if (!BanManagerCache.singleton().createKickBan(player.getPlayerName(), uid, reason)) {
					Log.warn("Already have kick for UID " + uid);
				} else {
					Log.info("Kicked UID " + uid + ", reason=" + reason);
					BanManagerCache.singleton().manageBan(BanManagerCache.singleton().getBanData(uid), BanActionType.ADD, player.getIPAndPort(), reason);
				}
			}
		}
	}
}