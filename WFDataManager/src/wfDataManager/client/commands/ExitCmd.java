package wfDataManager.client.commands;

import wfDataManager.client.main.WFDataManager;
import wfDataModel.model.commands.BaseCmd;

/**
 * Command for exiting the program
 * @author MatNova
 *
 */
public class ExitCmd extends BaseCmd {

	public ExitCmd() {
		super(0);
	}

	@Override
	public String getDescription() {
		StringBuilder desc = new StringBuilder("Exits the program").append("\n");
		desc.append("exit - Exits the program");
		return desc.toString();
	}

	@Override
	public void runCmd(String... args) {
		WFDataManager.shouldExit = true;
	}

}
