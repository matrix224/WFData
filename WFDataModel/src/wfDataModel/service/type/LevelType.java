package wfDataModel.service.type;

import java.util.Arrays;
import java.util.List;

import jdtools.logging.Log;

/**
 * Denotes and maps all of the levels that are available within Conclave.
 * @author MatNova
 *
 */
public enum LevelType {
	ARBITERS_ARENA("TennoBallArbiters"),
	BUNKERS("DMFort"),
	CANYON_SETTLEMENT("GrineerSettlementDuel01"),
	CEPHALON_CITADEL("DMCephalon"),
	CEPHALON_SPIRES("CTFCephalonSpire"),
	COMPOUND("DMForestCompound"),
	CORE("DMCrpCore"),
	CORPUS_SHIP("DMCorpusShip"),
	DERELICT_CHAMBERS("CTFOrokinDerelictSmall", "CTFOrokinDerelict"),
	DOCKING_BAY("DMGrnOcean"),
	FORGOTTEN_HALLS("CTFOrokinMoonHalls"),
	FREIGHT_LINE("DMCorpusShipWarehouse"),
	GAS_WORKS("CTFGasCityRemaster", "CTFCorpusGas"),
	INFESTED_FRIGATE("CTFInfestedCorpus"),
	LUA_RUINS("DMOroMoon"),
	NAVIGATION_ARRAY("GrineerGalleonDuel01"),
	NEW_CORE("DMCorpusShipCoreRemaster"),
	OROKIN_ARENA("TennoBallMoon"),
	OUTPOST("CTFOutpostCliffPort"),
	PERRIN_SEQUENCE_ARENA("TennoBallPerrinSequence"),
	SHIPYARDS("DMShipyards"),
	SHRINE("CTFOrokin"),
	STEEL_MERIDIAN_ARENA("TennoBallSettlement"),
	UNKNOWN("");
	
	private static final String LOG_ID = LevelType.class.getSimpleName();
	private List<String> internalLevels;
	
	private LevelType(String... internalLevels) {
		if (internalLevels != null && internalLevels.length > 0) {
			this.internalLevels = Arrays.asList(internalLevels);
		} else {
			this.internalLevels = Arrays.asList("");
		}
	}
	
	public static LevelType internalToType(String internalLevel) {
		LevelType type = UNKNOWN;
		
		for (LevelType t : values() ) {
			if (t.internalLevels.contains(internalLevel)) {
				type = t;
				break;
			}
		}
		
		if (UNKNOWN.equals(type)) {
			Log.warn(LOG_ID, ".internalToType() : Unknown level mapping for -> ", internalLevel);
		}
		
		return type;
	}
}
