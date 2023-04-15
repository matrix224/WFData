package wfDataService.service.commands;

import jdtools.util.NumberUtil;
import wfDataModel.model.commands.BaseCmd;
import wfDataModel.model.logging.Log;
import wfDataService.service.cache.ServerClientCache;

/**
 * Command to enable a specified client that is registered with the service.
 * @author MatNova
 *
 */
public class EnableCmd extends BaseCmd {

	public EnableCmd() {
		super(1);
	}

	@Override
	public String getDescription() {
		StringBuilder desc = new StringBuilder("Enable a specific server client").append("\n");
		desc.append("enable <serverId> - Enables the server client with the given ID. ID must be numeric");
		return desc.toString();
	}

	@Override
	public void runCmd(String... args) {
		if (args == null || args.length < 1) {
			Log.warn("Invalid arguments supplied, usage: \n " + getDescription());
		} else {
			String serverIdStr = args[0];
			if (NumberUtil.isNumeric(serverIdStr)) {
				int serverId = Integer.valueOf(serverIdStr);
				ServerClientCache.singleton().toggleValidation(serverId, true);
			} else {
				Log.warn("Invalid arguments supplied, usage: \n " + getDescription());
			}
		}
	}
	
}
