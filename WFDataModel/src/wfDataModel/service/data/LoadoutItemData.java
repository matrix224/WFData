package wfDataModel.service.data;

/**
 * Defines information about a specific item that's part of a banned loadout config
 * @author MatNova
 *
 */
public class LoadoutItemData {
	
	private String itemName;
	private double amount; // The amount that this item contributes to a ban threshold count
	
	public LoadoutItemData(String itemName, double amount) {
		this.itemName = itemName;
		this.amount = amount;
	}
	
	public String getItemName() {
		return itemName;
	}
	
	public double getAmount() {
		return amount;
	}
}
