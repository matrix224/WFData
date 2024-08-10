package wfDataManager.client.parser.logging;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataManager.client.type.ParseResultType;
import wfDataModel.model.data.ServerData;
import wfDataModel.model.util.NetworkUtil;

/**
 * Parser class for handling introduction requests, which are used to derive an accountID for players. <br>
 * Mapping accountIDs to players is a very painstaking process that has no easy way. 
 * Introduction messages can be received in any order, they can be mapped to one of DE's proxies, a LAN IP, or the player's actual IP.
 * They can also have the same accountID mixed across the different IPs if multiple people join at once. <br>
 * This parser attempts to make the best effort it can to try and untangle them and assign probabilities of "best guess" mapping
 * based on several factors, such as what is the most recent IP -> accountID mapping and if it is a proxy IP, or a relayed IP, etc. <br>
 * There is no guarantee that it will map things successfully, but it should be very accurate for what it does map.
 * @author MatNova
 *
 */
public class IntroductionRequestParser extends BaseLogParser {

	private static final String INTRO_DIRECT = "IT_DIRECT";
	private Matcher NEW_REQUEST_PATTERN;
	private Matcher SQUAD_PEER_PATTERN;
	private Matcher RELAY_PATTERN;
	private Matcher INTRO_REQUEST_PATTERN;


	@Override
	protected List<Matcher> initMatchers() {
		//INTRO_REQUEST_PATTERN = Pattern.compile(".*Received .* introduction request from (.*) task .* \\((\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+)\\)$").matcher("");
		//PING_REQUEST_PATTERN = Pattern.compile(".*Received PING_REQUEST from (\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+)$").matcher("");
		INTRO_REQUEST_PATTERN = Pattern.compile(".*Received .* introduction request from (.*) task .* \\((\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+)\\)$").matcher("");
		NEW_REQUEST_PATTERN = Pattern.compile(".*New request from (.*): (\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+), intro type: (.*), proxy: (\\d{1}),.*").matcher("");
		SQUAD_PEER_PATTERN = Pattern.compile(".*New squad peer added (\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+)$").matcher("");
		RELAY_PATTERN = Pattern.compile(".*Received introduction request from (\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+) \\(reply to (\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+)\\)$").matcher("");
		return Arrays.asList(NEW_REQUEST_PATTERN, INTRO_REQUEST_PATTERN, SQUAD_PEER_PATTERN, RELAY_PATTERN);
	}

	@Override
	public ParseResultType parse(ServerData serverData, long offset, long lastLogTime) throws ParseException {
		// Requests that come in via this pattern are treated as "primary" connections,
		// since they provide more information about the connection and tend to not overlap with other connections
		// i.e. we don't typically see the same accountID shared across different IPs for these messages, unless it's from DE's proxy
		if (NEW_REQUEST_PATTERN.matches()) {
			String acctId = NEW_REQUEST_PATTERN.group(1);
			String ipAndPort = NEW_REQUEST_PATTERN.group(2);
			String introType = NEW_REQUEST_PATTERN.group(3);
			String proxyInd = NEW_REQUEST_PATTERN.group(4);
			String relayIPPort = serverData.getRelayIPMapping(ipAndPort);
			boolean isProxy = "1".equals(proxyInd);
			boolean isRealDirect = !isProxy && INTRO_DIRECT.equals(introType);

			// If this acctId is already mapped, then it's no longer a candidate
			if (!serverData.hasAccountIDMapping(ipAndPort, lastLogTime)) {
				serverData.addAccountIDCandidate(ipAndPort, acctId, lastLogTime, true, isRealDirect);
			}
			if (!INTRO_DIRECT.equals(introType) && !MiscUtil.isEmpty(relayIPPort) && !serverData.hasAccountIDMapping(relayIPPort, lastLogTime)) {
				serverData.addAccountIDCandidate(relayIPPort, acctId, lastLogTime, true, isRealDirect);
			}

		} else if (INTRO_REQUEST_PATTERN.matches()) {
			// Requests from this pattern are treated as "secondary" connections, which are weighted the lowest in terms of acctId mapping candidates
			// These tend to be all over the place and can have the same acctId mapped to multiple IPs frequently
			// They also can be interconnected with relay requests, in which case we attempt to reverse map it based on the relay
			String acctId = INTRO_REQUEST_PATTERN.group(1);
			String ipAndPort = INTRO_REQUEST_PATTERN.group(2);

			// If this acctId is already mapped, then it's no longer a candidate
			if (!serverData.hasAccountIDMapping(ipAndPort, lastLogTime)) {
				serverData.addAccountIDCandidate(ipAndPort, acctId, lastLogTime, false, false);
				// If this IP is the mapped value of a relay IP, then we'll check what acctId is mapped to the relayed IP
				// If there is an acct and it matches the acct provided here, then we will consider
				// this IP as a primary candidate for the acct because this IP was the last one referenced by the relay IP,
				// meaning there's less chance of multiple IPs getting mixed across eachother in the log messages
				String relayIPAndPort = serverData.getRelayReverseIPMapping(ipAndPort);
				if (!MiscUtil.isEmpty(relayIPAndPort)) {
					String relayAcct = serverData.getAccountIDCandidate(relayIPAndPort, lastLogTime);
					if (!MiscUtil.isEmpty(relayAcct) && relayAcct.equals(acctId)) {
						serverData.addAccountIDCandidate(ipAndPort, acctId, lastLogTime, true, false);
					}
				}

			}
		} else if (SQUAD_PEER_PATTERN.matches()) {
			String ipAndPort = SQUAD_PEER_PATTERN.group(1);
			// At this point, this is when a player has joined the squad officially, and so we have their IP that they've been logged as
			// We'll check for the best candidate for that IP at this point and assign the acctId to this IP officially, which will be assigned
			// to the player later on (during connection parsing)

			// If this acctId is already mapped, nothing to do here
			if (!serverData.hasAccountIDMapping(ipAndPort, lastLogTime)) {
				String acctId = serverData.getAccountIDCandidate(ipAndPort, lastLogTime);
				if (MiscUtil.isEmpty(acctId)) {
					Log.warn(LOG_ID + ".parse() : Could not find acctId candidate for " + ipAndPort);
				} else {
					serverData.addAccountIDMapping(ipAndPort, acctId, lastLogTime, true);
					serverData.removeAccountIDCandidate(acctId);
				}
			}

		} else if (RELAY_PATTERN.matches()) {
			// Requests from this pattern are when the server is receiving requests from DE's proxies in response to a request from a person's IP
			// Essentially DE's proxy is sending the introduction request to us on behalf of a person
			// When this happens, we map the relay IP to the IP it was sent on behalf of
			String relayIP = RELAY_PATTERN.group(1);
			String ipAndPort = RELAY_PATTERN.group(2);

			/*
			String currentRelayedIPPort = serverData.getRelayIPMapping(relayIP);
			if (!MiscUtil.isEmpty(currentRelayedIPPort) && !serverData.hasAccountIDMapping(ipAndPort, lastLogTime) && !serverData.hasAccountIDMapping(currentRelayedIPPort, lastLogTime)) {
				String acctId = serverData.getAccountIDCandidate(currentRelayedIPPort);
				if (!MiscUtil.isEmpty(acctId)) {
					serverData.addAccountIDCandidate(ipAndPort, acctId, true);
					Log.info("ADDING CANDIDATE " + acctId + " TO " + ipAndPort + " FROM RELAY " + relayIP + ", CUR RELAY=" + currentRelayedIPPort);
				}
			}*/


			// If the relay IP is currently mapped to an IP, and the relay IP has an acctId candidate mapped to it,
			// then we check all of the IP and port combos for the acctId that the relayIP is mapped to.
			// If any of the mappings have the same IP as this one but a different port, we consider that a conflict of interest and do not want to remap it
			// This is the case when multiple people from the same household start to join at once, which can lead to a flurry of log messages that make it even more annoying
			// to accurately map IPs to accts
			// If the relayIP is not currently mapped to anything, then we just map it to this IP
			String currentRelayedIPPort = serverData.getRelayIPMapping(relayIP);
			if (!MiscUtil.isEmpty(currentRelayedIPPort)) {
				String[] ipParts = ipAndPort.split(":");
				boolean hasConflict = false;

				String acctId = serverData.getAccountIDCandidate(relayIP, lastLogTime);
				if (!MiscUtil.isEmpty(acctId)) {
					List<String> ipAndPorts = serverData.getIPAndPortsForAccountIDCandidates(acctId, lastLogTime);
					for (String acctIPPort : ipAndPorts) {
						String[] acctIPPortParts = acctIPPort.split(":");
						if (acctIPPortParts[0].equals(ipParts[0]) && !acctIPPortParts[1].equals(ipParts[1])) {
							hasConflict = true;
							break;
						}
					}
				}

				// Remove any currently mapped candidates that fall under this relayIP to avoid any mismatches with previously mapped ones
				serverData.removeAccountIDCandidates(relayIP);

				// If there is no conflict in mapping, then we'll just map this relay to its associated IP
				// Otherwise if there is a conflict and the current mapping is a private IP, we'll just remap it 
				// to this IP since we can consider a private IP to essentially be an invalid mapping. Otherwise
				// we'll remove the mapping for this relay IP so the next one gets mapped to it
				if (!hasConflict) {
					//Log.info("RELAY map relay=" + relayIP + ", ipAndPort=" + ipAndPort + ", current=" + currentRelayedIPPort);
					serverData.setRelayIPMapping(relayIP, ipAndPort);
				} else {
					Log.info("CONFLICT , relay=" + relayIP + ", ipAndPort=" + ipAndPort + ", current=" + currentRelayedIPPort);
					//serverData.removeAccountIDCandidates(relayIP);
					serverData.setRelayIPMapping(relayIP, NetworkUtil.isPrivateIP(currentRelayedIPPort) ? ipAndPort : null);
				}


			} else {
				serverData.setRelayIPMapping(relayIP, ipAndPort);
			}

		}
		return ParseResultType.OK;
	}

}
