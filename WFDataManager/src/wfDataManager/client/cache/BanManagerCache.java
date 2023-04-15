package wfDataManager.client.cache;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jdtools.util.MiscUtil;
import jdtools.util.NumberUtil;
import wfDataManager.client.db.BanDao;
import wfDataManager.client.util.ClientSettingsUtil;
import wfDataManager.client.util.RequestUtil;
import wfDataModel.model.data.PlayerData;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.logging.Log;
import wfDataModel.model.util.NetworkUtil;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.data.BanData;
import wfDataModel.service.data.BanSpec;
import wfDataModel.service.data.LoadoutData;
import wfDataModel.service.data.LoadoutItemData;
import wfDataModel.service.type.BanActionType;
import wfDataModel.service.type.BanDirectionType;
import wfDataModel.service.type.EloType;
import wfDataModel.service.type.GameMode;
import wfDataModel.service.type.BanProtocolType;

/**
 * Cache to store and handle the management of user bans. <br>
 * This is where bans are managed (addition and removal), whether it be manually or automatically (i.e. weapon bans, shared bans).
 * 
 * @author MatNova
 */
public class BanManagerCache {

	private static final String LOG_ID = BanManagerCache.class.getSimpleName();

	private static BanManagerCache singleton;
	private double offenseThreshold = 10; // The threshold at which point a combination of items triggers a ban or a strike
	private int strikeThreshold = 3; // Number of strikes for an unknown player to receive before being banned and marked for instant bans in the future
	private Map<Integer, LoadoutData> loadouts = new HashMap<Integer, LoadoutData>(); // loadout ID -> loadout
	private Map<String, BanData> bannedTracker = new HashMap<String, BanData>(); // UID -> data. Tracks data pertaining to players and their bans (either currently banned, or has committed offenses before)

	public static synchronized BanManagerCache singleton() {
		if (singleton == null) {
			singleton = new BanManagerCache();
		}
		return singleton;
	}

	public void prepareCache() {
		File bannedItemsCfg = new File(ClientSettingsUtil.getBannedItemsConfig());
		if (bannedItemsCfg.exists()) {
			try (BufferedReader br = new BufferedReader(new FileReader(bannedItemsCfg))) {
				String cfgStr = "";
				String line = null;
				while ((line = br.readLine()) != null) {
					cfgStr += line;
				}
				JsonObject cfgObject = JsonParser.parseString(cfgStr).getAsJsonObject();
				if (cfgObject.has(JSONField.OFFENSE_THRESHOLD)) {
					offenseThreshold = cfgObject.get(JSONField.OFFENSE_THRESHOLD).getAsDouble();
				}
				if (cfgObject.has(JSONField.STRIKE_THRESHOLD)) {
					strikeThreshold = cfgObject.get(JSONField.STRIKE_THRESHOLD).getAsInt();
				}
				if (cfgObject.has(JSONField.LOADOUTS)) {
					JsonArray loadoutArr = cfgObject.getAsJsonArray(JSONField.LOADOUTS);
					loadoutArr.forEach(loadout -> {
						JsonObject loadoutObj = loadout.getAsJsonObject();
						String loadoutName = loadoutObj.get(JSONField.LOADOUT_NAME).getAsString();
						JsonArray loadoutItems = loadoutObj.getAsJsonArray(JSONField.ITEMS);
						final LoadoutData loadoutData = new LoadoutData(loadoutName);

						loadoutItems.forEach(item -> {
							JsonObject itemObj = item.getAsJsonObject();
							String itemName = itemObj.get(JSONField.ITEM_NAME).getAsString();
							double itemAmount = NumberUtil.clamp(itemObj.get(JSONField.ITEM_AMOUNT).getAsDouble(), 0.01, 0.99);
							LoadoutItemData itemData = new LoadoutItemData(itemName, itemAmount);
							loadoutData.addLoadoutItem(itemData);
						});

						if (loadoutObj.has(JSONField.ELO)) {
							loadoutData.setElo(EloType.valueOf(loadoutObj.get(JSONField.ELO).getAsString()));
						}

						if (loadoutObj.has(JSONField.GAME_MODES)) {
							JsonArray gameModes = loadoutObj.getAsJsonArray(JSONField.GAME_MODES);
							gameModes.forEach(gameMode -> {
								loadoutData.addGameMode(GameMode.valueOf(gameMode.getAsString()));
							});
						}

						loadouts.put(loadoutData.getLoadoutID(), loadoutData);

					});
				}
			} catch (Exception e) {
				Log.error(LOG_ID + ".prepareCache() : Exception occurred -> ", e);
			}

			Log.info(LOG_ID + ".prepareCache() : Banned loadouts: " + loadouts.size() + ", offenseThreshold: " + offenseThreshold + ", strikeThreshold: " + strikeThreshold);
			bannedTracker.putAll(BanDao.getBanData());
		} else {
			Log.warn(LOG_ID + ".prepareCache() : Banned items config does not exist, no items will be marked as banned -> " + bannedItemsCfg.getAbsolutePath());
		}
	}

	public Collection<BanData> getBanData() {
		return bannedTracker.values();
	}

	public BanData getBanData(String uid) {
		return bannedTracker.get(uid);
	}

	private BanData getOrCreateBanData(String playerName, String uid) {
		synchronized (bannedTracker) {
			BanData data = getBanData(uid);
			if (data == null) {
				data = createBanData(playerName, uid);			
			}
			return data;
		}
	}

	private BanData createBanData(String playerName, String uid) {
		synchronized (bannedTracker) {
			BanData data = new BanData(playerName, uid);
			bannedTracker.put(uid, data);	
			return data;
		}
	}

	public boolean createPermaBan(String playerName, String uid, String reason) {
		BanData data = getOrCreateBanData(playerName, uid);
		if (data.isPermanent()) {
			return false; // If we already have a permaban for them, return false
		}
		BanSpec spec = data.addOrGetBanSpec(BanData.PERM_BAN_IP);
		spec.setBanReason(MiscUtil.isEmpty(reason) ? "Permaban" : reason);
		spec.setBanTime(System.currentTimeMillis());
		BanDao.updateBan(data, BanData.PERM_BAN_IP, BanActionType.ADD);
		return true;
	}

	public boolean removePermaBan(String uid) {
		BanData data = getBanData(uid);
		if (data == null || !data.isPermanent()) {
			return false;
		}
		data.removeBanSpec(BanData.PERM_BAN_IP);
		BanDao.updateBan(data, BanData.PERM_BAN_IP, BanActionType.REMOVE);
		return true;
	}

	public boolean isPermaBanned(String uid) {
		return getBanData(uid) != null && getBanData(uid).isPermanent();
	}

	public boolean isCurrentlyBanned(String uid) {
		synchronized (bannedTracker) {
			BanData data = getBanData(uid);
			if (data != null) {
				for (BanSpec spec : data.getBanSpecs()) {
					if (spec.getBanTime() != null && !spec.isExpired(ClientSettingsUtil.getBanTime(spec.isPrimary()))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public void checkAutoBannable(PlayerData player, ServerData server) {
		boolean bannable = false;
		BanData banData = getBanData(player.getUID());
		// If this person is already banned for this IP, nothing to do here
		// How the hell did they even get here then?
		if (banData != null && (MiscUtil.isEmpty(player.getIPAndPort()) || banData.getBanTime(player.getIPAndPort()) != null)) {
			if (MiscUtil.isEmpty(player.getIPAndPort())) {
				Log.warn(LOG_ID + ".checkAutoBannable() : Player is missing an IP, will not perform any check -> player=" + player.getPlayerName() + ", connHandle=" + player.getConnHandle());
			}
			return;
		}

		Set<String> itemNames = player.getWeaponKills().keySet();
		for (LoadoutData loadout : loadouts.values()) {
			double loadoutAmt = 0;
			String loadoutName = loadout.getLoadoutName();
			int loadoutID = loadout.getLoadoutID();
			for (LoadoutItemData item : loadout.getLoadoutItems()) {
				if (itemNames.contains(item.getItemName()) && loadout.isForElo(player.getEloRating()) && loadout.isForGameMode(player.getGameMode()) && server.getPlayerItemKillCount(player.getUID(), item.getItemName()) > 0) {
					loadoutAmt += Math.pow(item.getAmount(), -(server.getPlayerItemKillCount(player.getUID(), item.getItemName()) * player.getWeaponKills().get(item.getItemName()))); // c^(-tx), c = item amount, t = total kills with item in current session, x = kills with item during parsing period
				}
			}
			if (loadoutAmt >= offenseThreshold) {
				if (banData == null) {
					banData = createBanData(player.getPlayerName(), player.getUID());
				}

				if (!banData.getOffensiveLoadouts().contains(loadoutID)) {
					banData.addStrike(loadoutName);
					if (banData.getStrikes(loadoutName) >= strikeThreshold) {
						banData.addOffensiveLoadout(loadoutID);
						Log.info(LOG_ID + ".checkAutoBannable() : Player exceeded strikes and is bannable with score of " + loadoutAmt + " for loadout '" + loadoutName + "'" + ", player=" + banData.getPlayerName() + ", offenseThreshold=" + offenseThreshold + ", strikes=" + banData.getStrikes(loadout.getLoadoutName()) + ", strikeThreshold=" + strikeThreshold);
						bannable = true;
					}
				} else {
					bannable = true;
					Log.info(LOG_ID + ".checkAutoBannable() : Repeat offender with score of " + loadoutAmt + " for loadout '" + loadoutName + "'" + ", player=" + banData.getPlayerName() + ", offenseThreshold=" + offenseThreshold);
				}
			}

			if (bannable) {
				if (MiscUtil.isEmpty(player.getIPAndPort())) {
					Log.warn(LOG_ID + ".checkAutoBannable() : Wanted to ban but no IP was parsed for player, will not do anything -> player=" + player.getPlayerName() + ", uid=" + player.getUID());
				} else {
					BanSpec spec = banData.addOrGetBanSpec(player.getIPAndPort());
					spec.setBanReason(loadoutName);
					spec.setLoadoutID(loadoutID);

					manageBan(banData, BanActionType.ADD, player.getIPAndPort(), banData.getBanReason(player.getIPAndPort()), server.getServerPort());
				}
				break;
			}
		}
	}

	public synchronized void manageBan(BanData data, BanActionType action, String ipAndPort) {		
		String reason = data.getBanReason(ipAndPort);
		if (MiscUtil.isEmpty(reason) && BanActionType.ADD.equals(action)) {
			reason = "Unknown";
			Log.warn(LOG_ID + ".manageBan() : Reason not supplied for adding new ban, defaulting to 'Unknown' for player " + data.getPlayerName() + " (" + data.getUID() + ")");
		}
		manageBan(data, action, ipAndPort, reason, 0);
	}

	public synchronized void manageBan(BanData data, BanActionType action, String ipAndPort, String reason, int serverPort) {		
		boolean successfullyRan = false;
		String[] parts = ipAndPort.split(":", 2);
		String ip = parts[0];
		String port = parts[1]; // Note that if this isn't a proxy IP, it will always have port 0 since we don't care about it
		boolean isProxy = Integer.valueOf(port) > 0;
		boolean alreadyBanned = getBanData(data.getUID()) != null &&  getBanData(data.getUID()).getBanTime(ipAndPort) != null; // If we already have this UID and IP/port in our system
		
		// Don't allow LAN IPs to be banned (e.g. in case someone shares a fake one to the service)
		if (NetworkUtil.isPrivateIP(ip)) {
			Log.warn(LOG_ID + ".manageBan() : Ignoring ban with local IP -> ip=" + ip + ", uid=" + data.getUID());
			return;
		}
		
		if (BanActionType.ADD.equals(action)) {
			if (alreadyBanned) {
				// If they're already banned, we assume they have a firewall entry already
				// There's no harm in double-adding them, but also no reason to
				successfullyRan = true;
			} else if (isProxy) {
				// If this is a proxy, we ban directly on the port they connected on to avoid interfering with any legit proxy players				
				// If this was a self-made ban (i.e. not reported by someone else), then firewall them on the given port
				// Note we create 4 ban entries here, two for IN (tcp/udp) and two for OUT (tcp/udp) due to port being part of it and protocol has to be specified as TCP or UDP when using port
				// For the servers, just banning UDP works and you can get away with just 2 bans. But there's no harm in blocking TCP either
				if (MiscUtil.isEmpty(data.getReportedBy(ipAndPort))) {
					successfullyRan = runCommands(createBanCmd(action, BanDirectionType.IN, BanProtocolType.TCP, data, ipAndPort), createBanCmd(action, BanDirectionType.IN, BanProtocolType.UDP, data, ipAndPort), createBanCmd(action, BanDirectionType.OUT, BanProtocolType.TCP, data, ipAndPort), createBanCmd(action, BanDirectionType.OUT, BanProtocolType.UDP, data, ipAndPort));
				} else {
					// If this is a proxy and was reported by someone else, we'll track the ban but not add any firewall rule
					// There's no point in firewalling them in this case since the reported port is specific to the reporting user's instance
					successfullyRan = true;
				}
			} else {
				// Otherwise, for non-proxy, just ban their IP on all ports
				successfullyRan = runCommands(createBanCmd(action, BanDirectionType.IN, BanProtocolType.ANY, data, ipAndPort), createBanCmd(action, BanDirectionType.OUT, BanProtocolType.ANY, data, ipAndPort));
			}
		} else {
			// If this is not a proxy, or this was not reported by someone else, we should have a ban entry
			if (!data.isProxy(ipAndPort) || MiscUtil.isEmpty(data.getReportedBy(ipAndPort))) {
				// If this is a proxy, need to remove 4 bans (two for IN (tcp/udp) and two for OUT (tcp/udp)) due to port being part of it
				if (data.isProxy(ipAndPort)) {
					successfullyRan = runCommands(createBanCmd(action, BanDirectionType.IN, BanProtocolType.TCP, data, ipAndPort), createBanCmd(action, BanDirectionType.IN, BanProtocolType.UDP, data, ipAndPort), createBanCmd(action, BanDirectionType.OUT, BanProtocolType.TCP, data, ipAndPort), createBanCmd(action, BanDirectionType.OUT, BanProtocolType.UDP, data, ipAndPort));
				} else {
					successfullyRan = runCommands(createBanCmd(action, BanDirectionType.IN, BanProtocolType.ANY, data, ipAndPort), createBanCmd(action, BanDirectionType.OUT, BanProtocolType.ANY, data, ipAndPort));
				}
			} else {
				// If this was a proxy reported by someone else, just mark successful; we did not make any firewall entry for them
				successfullyRan = true;
			}
		}

		if (successfullyRan) {
			if (BanActionType.ADD.equals(action)) {
				BanSpec spec = data.addOrGetBanSpec(ipAndPort);

				// If this ban was reported by someone else, add the spec into our own tracked bans
				// This will create a new BanData entry for us if needed
				if (!MiscUtil.isEmpty(data.getReportedBy(ipAndPort))) {
					BanData banCopy = getOrCreateBanData(data.getPlayerName(), data.getUID());					
					BanSpec specCopy = banCopy.addOrGetBanSpec(ipAndPort);
					specCopy.setBanTime(spec.getBanTime());
					specCopy.setReportedBy(spec.getReportedBy());
					if (!alreadyBanned) {
						specCopy.setLoadoutID(spec.getLoadoutID());
						specCopy.setPrimary(spec.isPrimary());
						specCopy.setIsProxy(spec.isProxy());
					}
				} else {
					spec.setBanTime(System.currentTimeMillis());
					if (!alreadyBanned) {
						spec.setBanReason(reason);
						spec.setIsProxy(isProxy);
					}
					// Note that if this player is permanently banned, we will not share data with the service
					// It is up to individual server owners to decide whether or not they want to permaban someone
					// We only share primary bans with the service as any secondary ones will just be handled on each client's end
					// once the primary ban is in place
					if (!alreadyBanned && ClientSettingsUtil.enableBanSharing() && !data.isPermanent() && data.isPrimary(ipAndPort) && MiscUtil.isEmpty(data.getReportedBy(ipAndPort))) {
						BanData dataCopy = new BanData(data.getPlayerName(), data.getUID());
						dataCopy.addBanSpec(data.getBanSpec(ipAndPort));
						JsonObject dataObj = new JsonObject();
						dataObj.add(JSONField.BANS, new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJsonTree(dataCopy));
						RequestUtil.sendAddBanRequest(dataObj);
					}
				}
				
				if (alreadyBanned) {
					Log.info(LOG_ID + ".manageBan() : Updating existing ban time: uid=" + data.getUID() + ", ip=" + ipAndPort + ", reason=" + data.getBanReason(ipAndPort) + ", primary=" + data.isPrimary(ipAndPort) + ", proxy=" + data.isProxy(ipAndPort) + (!MiscUtil.isEmpty(data.getReportedBy(ipAndPort)) ? ", reported by " + data.getReportedBy(ipAndPort) : ""));
				} else {
					Log.info(LOG_ID + ".manageBan() : Adding new ban: uid=" + data.getUID() + ", ip=" + ipAndPort + ", reason=" + data.getBanReason(ipAndPort) + ", primary=" + data.isPrimary(ipAndPort) + ", proxy=" + data.isProxy(ipAndPort) + (!MiscUtil.isEmpty(data.getReportedBy(ipAndPort)) ? ", reported by " + data.getReportedBy(ipAndPort) : ""));
				}
			} else {
				Log.info(LOG_ID + ".manageBan() : Removing ban: uid=" + data.getUID() + ", ip=" + ipAndPort + ", reason=" + data.getBanReason(ipAndPort) + ", primary=" + data.isPrimary(ipAndPort) + ", proxy=" + data.isProxy(ipAndPort));
				data.removeBanSpec(ipAndPort);
			}
			
			// Update this ban in our DB
			BanDao.updateBan(data, ipAndPort, action);
			
		} else {
			Log.warn(LOG_ID + ".manageBan() : Firewall command(s) did not run properly, nothing will be updated about ban -> uid=" + data.getUID() + ", ip=" + ip);
		}
	}

	private String[] createBanCmd(BanActionType action, BanDirectionType dir, BanProtocolType protocol, BanData data, String ipAndPort) {
		String[] cmd = null;
		String banKey = data.getBanKey(ipAndPort, dir, protocol);
		if (BanActionType.ADD.equals(action)) {
			String[] parts = ipAndPort.split(":", 2);
			String ip = parts[0];
			String portStr = parts[1]; // Note that if this isn't a proxy IP, it will always have port 0 since we don't care about it
			int port = Integer.valueOf(portStr);

			if (port > 0) {
				// If a non-zero port is specified, we have to also specify protocol field as TCP or UDP for the remoteport field to work
				cmd = new String[]{"netsh", "advfirewall", "firewall", "add", "rule", "name=" + banKey, "dir=" + dir.getCode(), "action=block", "remoteip=" + ip, "remoteport=" + portStr, "protocol=" + protocol.getCode()};
			} else {
				cmd = new String[]{"netsh", "advfirewall", "firewall", "add", "rule", "name=" + banKey, "dir=" + dir.getCode(), "action=block", "remoteip=" + ip};
			}

		} else {
			cmd = new String[] {"netsh", "advfirewall", "firewall", "delete", "rule", "name=" + banKey};
		}
		return cmd;
	}

	private boolean runCommands(String[]... cmds) {
		boolean allSuccess = false;
		int i = 0;
		do {
			allSuccess = runCommand(cmds[i++]);
		} while (allSuccess && i < cmds.length);

		return allSuccess;
	}

	public boolean isBannedLoadout(int loadoutID) {
		return loadouts.containsKey(loadoutID);
	}

	private boolean runCommand(String... programAndArgs) {
		boolean isSuccess = true;
		ProcessBuilder builder = new ProcessBuilder(programAndArgs);
		Process p = null;

		try {
			p = builder.start();
			if (p.waitFor(10, TimeUnit.SECONDS)) {
				try (BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
					if (stdError != null && stdError.ready()) {
						Log.warn(LOG_ID + ".runCommand() : StdError present -> " + stdError.lines().collect(Collectors.joining()));
						isSuccess = false;
					}
				} catch (Throwable t) {
					Log.error(LOG_ID + ".runCommand() : Exception reading error stream -> " + t.getLocalizedMessage());
					isSuccess = false;
				}

				if (isSuccess) {
					try (BufferedReader stdOut = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
						if (stdOut != null && stdOut.ready()) {
							String stdOutStr = stdOut.lines().collect(Collectors.joining());
							if (stdOutStr.contains("elevation") || stdOutStr.contains("not valid")) {
								Log.warn(LOG_ID + ".runCommand() : Command most likely failed, output= -> " + stdOutStr);
								isSuccess = false;
							}
						}
					} catch (Throwable t) {
						Log.error(LOG_ID + ".runCommand() : Exception reading out stream -> " + t.getLocalizedMessage());
						isSuccess = false;
					}
				}

			} else {
				isSuccess = false;
				p.destroy();
				Log.warn(LOG_ID + ".runCommand() : Command timed out");
			}
		} catch (Throwable t) {
			isSuccess = false;
			Log.error(LOG_ID + ".runCommand() : Exception running command -> " + t.getLocalizedMessage());
		}
		return isSuccess;
	}
}
