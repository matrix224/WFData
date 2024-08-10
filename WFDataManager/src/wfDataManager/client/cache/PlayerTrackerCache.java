package wfDataManager.client.cache;

import java.util.ArrayList;
import java.util.List;

import wfDataManager.client.data.PlayerTracker;
import wfDataManager.client.db.PlayerTrackerDao;

/**
 * Cache to manage data related to tracking of specified players for alts
 * @author MatNova
 *
 */
public final class PlayerTrackerCache {

	private static PlayerTrackerCache singleton = null;
	private List<PlayerTracker> trackedPlayers = null;
	
	public static synchronized PlayerTrackerCache singleton() {
		if (singleton == null) {
			singleton = new PlayerTrackerCache();
		}
		return singleton;
	}
	
	private PlayerTrackerCache() {
		loadTrackers();
	}
	
	private void loadTrackers() {
		trackedPlayers = PlayerTrackerDao.getTrackedPlayers();
	}
	
	public void addTracker(PlayerTracker tracker) {
		trackedPlayers.add(tracker);
	}
	
	public void removeTracker(PlayerTracker tracker) {
		trackedPlayers.remove(tracker);
	}
	
	public List<PlayerTracker> getPlayerTrackers() {
		return trackedPlayers;
	}
	
	public PlayerTracker getPlayerTracker(String uid) {
		PlayerTracker tracked = null;
		for (PlayerTracker track : trackedPlayers) {
			if (track.getUID().equals(uid)) {
				tracked = track;
				break;
			}
		}
		return tracked;
	}
	
	public List<PlayerTracker> getPlayerTrackers(String uid, String ip) {
		List<PlayerTracker> tracked = null;
		for (PlayerTracker track : trackedPlayers) {
			if (track.getUID().equals(uid) || track.getKnownAlts().containsKey(uid) || track.getKnownIPs().contains(ip)) {
				if (tracked == null) {
					tracked = new ArrayList<PlayerTracker>();
				}
				tracked.add(track);
			}
		}
		return tracked;
	}
	
	public void updatePlayerTracker(String oldUID, String newUID) {
		for (PlayerTracker tracker : getPlayerTrackers()) {
			if (tracker.getUID().equals(oldUID)) {
				PlayerTrackerDao.updatePlayerTracker(tracker, newUID); // Pass in newUID to remap tracker's UID in DB
				tracker.setUID(newUID); // Update in object after updating DB
			} else if (tracker.getKnownAlts().containsKey(oldUID)) {
				tracker.addKnownAlt(newUID, tracker.getKnownAlts().get(oldUID));
				tracker.removeKnownAlt(oldUID);
				PlayerTrackerDao.updatePlayerTracker(tracker); // Note don't pass newUID into method here since we're updating alt references, not tracker UID itself
			}
		}
		
	}
}
