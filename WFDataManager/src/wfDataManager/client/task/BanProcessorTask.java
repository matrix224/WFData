package wfDataManager.client.task;

import java.util.ArrayList;
import java.util.List;

import jdtools.logging.Log;
import wfDataManager.client.cache.BanManagerCache;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.service.data.BanData;
import wfDataModel.service.data.BanSpec;
import wfDataModel.service.type.BanActionType;

/**
 * Task to periodically check for expiration of any current bans
 * @author MatNova
 *
 */
public class BanProcessorTask implements Runnable {

	@Override
	public void run() {
		try {
			for (BanData banData : BanManagerCache.singleton().getBanData()) {
				List<BanSpec> specs = new ArrayList<BanSpec>(banData.getBanSpecs());
				// Check this upfront since we may remove the spec that marks this as a kick before removing
				// the actual IP ban, so need to always know what we're dealing with
				boolean isKick = banData.isKick();
				for (BanSpec spec : specs) {
					if (spec.isExpired(ClientSettingsUtil.getBanTime(spec.isPrimary(), isKick))) {
						BanManagerCache.singleton().manageBan(banData, BanActionType.REMOVE, spec.getIP());
					}
				}
			}
		} catch (Throwable t) {
			Log.error(BanProcessorTask.class.getSimpleName() + "() : Exception while processing -> ", t);
		}
	}

}
