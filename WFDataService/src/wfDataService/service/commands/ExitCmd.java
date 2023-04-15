package wfDataService.service.commands;

import wfDataModel.model.commands.BaseCmd;
import wfDataService.service.main.WFDataService;

/**
 * Command to exit the program gracefully.
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
		WFDataService.shouldExit = true;
	}

}
