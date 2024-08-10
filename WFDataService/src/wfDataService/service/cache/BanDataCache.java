package wfDataService.service.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdtools.logging.Log;
import wfDataModel.model.util.NetworkUtil;
import wfDataModel.service.data.BanData;
import wfDataModel.service.data.BanSpec;
import wfDataService.service.data.ServerClientData;

/**
 * Cache for storing data about bans that have been supplied by clients.
 * @author MatNova
 *
 */
public class BanDataCache {
	private static final String LOG_ID = BanDataCache.class.getSimpleName();
	private static BanDataCache singleton;
	private Map<String, BanData> banData = new HashMap<String, BanData>(); // UID -> ban data

	public static synchronized BanDataCache singleton() {
		if (singleton == null) {
			singleton = new BanDataCache();
		}
		return singleton;
	}

	public boolean addBanData(ServerClientData reporter, BanData data) {
		int numAdded = 0;
		synchronized(banData) {
			// Create a new BanData for our cache if needed
			BanData cacheData = banData.computeIfAbsent(data.getUID(), k ->  new BanData(data.getPlayerName(), data.getUID()));
			for (BanSpec spec : data.getBanSpecs()) {
				if (NetworkUtil.isPrivateIP(spec.getIP())) {
					Log.warn(LOG_ID + ".addBanData() : Received invalid IP address for ban from " + reporter.getDisplayName() + ", ip=" + spec.getIP() + ". Will not add ban");
					continue;
				}

				BanSpec cacheSpec = cacheData.addOrGetBanSpec(spec.getIP());
				cacheSpec.setBanTime(spec.getBanTime());
				cacheSpec.setLoadoutID(spec.getLoadoutID());
				cacheSpec.setPrimary(spec.isPrimary());
				cacheSpec.setIsProxy(spec.isProxy());
				cacheSpec.setReportedBy(reporter.getDisplayName());
				cacheSpec.setReportingID(reporter.getServerClientID());
				numAdded++;
			}

			if (numAdded > 0) {
				Log.info(LOG_ID + ".addBanData() : Adding new ban for " + data.getPlayerName() + " (" + data.getUID() + ") for " + numAdded + " IP(s), reported by " + reporter.getDisplayName());
			}
		}
		return numAdded > 0;
	}

	public List<BanData> getBansSinceLastCheck(ServerClientData requester) {
		List<BanData> bans = new ArrayList<BanData>();

		for (BanData data : banData.values()) {
			BanData banCopy = null;
			for (BanSpec spec : data.getBanSpecs()) {
				if (spec.getBanTime() != null && spec.getBanTime() > requester.getLastBanPollTime() && spec.getReportingID() != requester.getServerClientID()) {
					if (banCopy == null) {
						banCopy = new BanData(data.getPlayerName(), data.getUID());
					}
					BanSpec specCopy = banCopy.addOrGetBanSpec(spec.getIP());
					specCopy.setBanTime(spec.getBanTime());
					specCopy.setLoadoutID(spec.getLoadoutID());
					specCopy.setPrimary(spec.isPrimary());
					specCopy.setIsProxy(spec.isProxy());
					specCopy.setReportedBy(spec.getReportedBy());
				}
			}

			if (banCopy != null) {
				bans.add(banCopy);
			}
		}

		return bans;
	}

	public void updateBanData(String oldUID, String newUID) {
		if (banData.containsKey(oldUID)) {
			synchronized (banData) {
				BanData data = banData.get(oldUID);
				data.setUID(newUID);
				banData.put(newUID, data);
				banData.remove(oldUID);
			}
		}
	}
}
