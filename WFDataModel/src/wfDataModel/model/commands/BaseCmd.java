package wfDataModel.model.commands;

/**
 * Base implementation for a command
 * @author MatNova
 *
 */
public abstract class BaseCmd {
	protected int maxParams;
	
	public BaseCmd(int maxParams) {
		this.maxParams = maxParams;
	}
	
	public int getMaxParams() {
		return maxParams;
	}
	
	public abstract void runCmd(String...params);
	public abstract String getDescription();
}
