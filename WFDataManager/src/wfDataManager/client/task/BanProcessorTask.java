package wfDataManager.client.task;

import java.util.ArrayList;
import java.util.List;

import wfDataManager.client.cache.BanManagerCache;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataModel.model.logging.Log;
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
				for (BanSpec spec : specs) {
					if (spec.isExpired(ClientSettingsUtil.getBanTime(spec.isPrimary()))) {
						BanManagerCache.singleton().manageBan(banData, BanActionType.REMOVE, spec.getIP());
					}
				}
			}
		} catch (Throwable t) {
			Log.error(BanProcessorTask.class.getSimpleName() + "() : Exception while processing -> ", t);
		}
	}

}
