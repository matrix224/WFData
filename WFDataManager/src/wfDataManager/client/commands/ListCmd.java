package wfDataManager.client.commands;

import wfDataManager.client.cache.BanManagerCache;
import wfDataManager.client.cache.ServerDataCache;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.model.commands.BaseCmd;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.logging.Log;
import wfDataModel.service.data.BanData;
import wfDataModel.service.data.BanSpec;
import wfDataModel.service.type.EloType;
import wfDataModel.service.type.GameMode;

/**
 * Command for listing certain information (e.g. current bans, players, servers)
 * @author MatNova
 *
 */
public class ListCmd extends BaseCmd {

	public ListCmd() {
		super(1);
	}

	@Override
	public String getDescription() {
		StringBuilder desc = new StringBuilder("Lists information about current bans, or currently parsed servers or players").append("\n");
		desc.append("list bans - Lists information about all currently banned players \n");
		desc.append("list players - Lists information about all currently parsed players \n");
		desc.append("list servers - Lists information about all currently parsed servers");
		return desc.toString();
	}

	@Override
	public void runCmd(String... args) {
		if (args == null || args.length == 0 || (!args[0].equalsIgnoreCase("players") && !args[0].equalsIgnoreCase("servers") && !args[0].equals("bans"))) {
			Log.warn("Invalid arguments supplied, usage: \n " + getDescription());
		} else if (args[0].equals("bans")) { 
			StringBuilder resp = new StringBuilder("Current Bans: ").append("\n");
			for (BanData data : BanManagerCache.singleton().getBanData()) {
				for (BanSpec spec : data.getBanSpecs()) {
					if (spec.getBanTime() != null) {
						resp.append(data.getPlayerName()).append(" (").append(data.getUID()).append(")").append(" - reason = ").append(spec.getBanReason()).append(" - ");
						if (spec.isPermanent()) {
							resp.append("expires never");
						} else {
							resp.append("expires ").append(Math.abs(System.currentTimeMillis() - spec.getExpirationTime(ClientSettingsUtil.getBanTime(spec.isPrimary()))) / 1000).append("s");
						}
						resp.append("\n");
					}
				}
			}
			Log.info(resp.toString());
		} else {
			boolean isPlayers = args[0].equalsIgnoreCase("players");
			StringBuilder resp = new StringBuilder(isPlayers ? "Current Parsed Players" : "Current Server Info:").append("\n");
			for (ServerData server : ServerDataCache.singleton().getAllServerData()) {
				if (isPlayers) {
					for (PlayerData player : server.getParsedPlayers()) {
						resp.append(player.toString()).append("\n");
					}
				} else {
					if (server.getGameModeId() == -1) {
						resp.append(server.getId()).append(" - ").append("No info, maybe still loading").append("\n");
					} else {
						resp.append(server.getId() + " - " + server.getConnectedPlayers().size() + "/" + server.getMaxPlayers()).append(" ").append(GameMode.idToType(server.getGameModeId())).append(" ").append(EloType.codeToType(server.getEloRating())).append(" numRepeat=").append(server.getNumRepeats()).append(" numMiss=").append(server.getNumMiss()).append("\n");
					}
				}
			}
			Log.info(resp.toString());
		}
	}

}
