package wfDataService.service.processor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import jdtools.logging.Log;
import wfDataModel.service.type.RegionType;
import wfDataService.service.db.manager.ResourceManager;

public class ActivityProcessor {

	private static final String LOG_ID = ActivityProcessor.class.getSimpleName();
	private static final Gson BUILDER = new GsonBuilder().disableHtmlEscaping().create();
	private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("HH");

	public void processWeeklyActivity(List<LocalDate> datesToProcess) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			Map<LocalDate, WeekTrack> tracks = new HashMap<LocalDate, WeekTrack>();
			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("SELECT WEEK_DATE,W.UID,GAME_MODE,ELO,PLATFORM,W.SID,C.REGION FROM WEEKLY_DATA W LEFT JOIN MANAGER_CLIENT C ON C.SID=W.SID ORDER BY WEEK_DATE");
			rs = ps.executeQuery();
			LocalDate curWeek = null;
			while (rs.next()) {
				LocalDate weekDate = rs.getObject("WEEK_DATE", LocalDate.class);
				int region = rs.getInt("REGION");
				int sid = rs.getInt("SID");

				if (!weekDate.equals(curWeek)) {
					if (curWeek != null && datesToProcess.contains(curWeek)) {
						WeekTrack trk = tracks.get(curWeek);
						loadWeeklyActivityData(conn, curWeek, trk);
					}
					curWeek = weekDate;
				}

				parseWeeklyData(rs, tracks, weekDate, region, sid);
				parseWeeklyData(rs, tracks, weekDate, RegionType.WORLD.getCode(), RegionType.WORLD.getCode());
			}

			if (curWeek != null && datesToProcess.contains(curWeek)) {
				WeekTrack trk = tracks.get(curWeek);
				loadWeeklyActivityData(conn, curWeek, trk);
			}

		} catch (Throwable t) {
			Log.error(LOG_ID + ".processWeeklyActivity() : Error while processing -> ", t);
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}
	}

	public void processPlaytimeData() {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			Map<Integer, RegionTrack> tracks = new HashMap<Integer, RegionTrack>();

			conn = ResourceManager.getDBConnection();
			ps = conn.prepareStatement("SET time_zone = 'UTC'");
			ps.executeUpdate();
			ResourceManager.releaseResources(ps);
			
			ps = conn.prepareStatement("SELECT time, count, game_mode, elo, a.sid, c.region FROM activity_data a, manager_client c where a.sid=c.sid");
			rs = ps.executeQuery();
			while (rs.next()) {
				int region = rs.getInt("REGION");
				int sid = rs.getInt("SID");
				parsePlaytimeData(rs, tracks, region, sid);
				parsePlaytimeData(rs, tracks, RegionType.WORLD.getCode(), RegionType.WORLD.getCode());
			}

			loadPlaytimeData(conn, tracks.values());
			
		} catch (Throwable t) {
			Log.error(LOG_ID + ".processPlaytimeData() : Error while processing -> ", t);
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}
	}

	private void loadWeeklyActivityData(Connection conn, LocalDate week, WeekTrack weekTrack) throws SQLException {
		PreparedStatement ps = null;

		try {
			ps = conn.prepareStatement("REPLACE INTO WEEKLY_ACTIVITY (WEEK_DATE,GAME_MODE,ELO,NUM_UNIQUE,NUM_NEW,NUM_UNIQUE_REGION,NUM_NEW_REGION,SID) VALUES (?,?,?,?,?,?,?,?)");
			ps.setObject(1, week);

			for (RegionTrack reg : weekTrack.regions) {
				for (SIDTrack s : reg.sids) {
					for (GameTrack game : s.games) {
						ps.setInt(2, game.gameMode);
						ps.setInt(3, game.elo);
						ps.setString(4, BUILDER.toJson(reg.getNumUniqueSID(s.sid, game.gameMode, game.elo)));
						ps.setString(5, BUILDER.toJson(reg.getNumNewSID(s.sid, game.gameMode, game.elo)));
						ps.setString(6, BUILDER.toJson(reg.getNumUniqueRegion(s.sid, game.gameMode, game.elo)));
						ps.setString(7, BUILDER.toJson(reg.getNumNewRegion(s.sid, game.gameMode, game.elo)));
						ps.setInt(8, s.sid);
						if (ps.executeUpdate() == 0) {
							Log.warn(LOG_ID, ".loadWeeklyActivityData() : Did not load entry for week " + week + ", region " + reg.rid + ", sid " + s.sid + ", game " + game.gameMode + ", elo " + game.elo);
						}
					}
				}
			}
		} finally {
			ResourceManager.releaseResources(ps);
		}
	}

	private void loadPlaytimeData(Connection conn, Collection<RegionTrack> regions) throws SQLException {
		PreparedStatement ps = null;

		try {
			ps = conn.prepareStatement("REPLACE INTO CONSOLIDATED_ACTIVITY_DATA (GAME_MODE,ELO,COUNT,NUM_TIMES,HOUR,SID) VALUES (?,?,?,?,?,?)");

			for (RegionTrack reg : regions) {
				for (SIDTrack s : reg.sids) {
					for (GameTrack game : s.games) {
						for (String hour : game.playtimes.keySet()) {
							ps.setInt(1, game.gameMode);
							ps.setInt(2, game.elo);
							ps.setInt(3, game.playtimes.get(hour).totalCount);
							ps.setInt(4, game.playtimes.get(hour).numTimes);
							ps.setInt(5, Integer.parseInt(hour));
							ps.setInt(6, s.sid);
							if (ps.executeUpdate() == 0) {
								Log.warn(LOG_ID, ".loadPlaytimeData() : Did not load entry for region " + reg.rid + ", sid " + s.sid + ", game " + game.gameMode + ", elo " + game.elo + ", hour " + hour);
							}
						}
					}
				}
			}
		} finally {
			ResourceManager.releaseResources(ps);
		}
	}

	private void parsePlaytimeData(ResultSet rs, Map<Integer, RegionTrack> tracks, int region, int sid) throws SQLException {
		int gameMode = rs.getInt("GAME_MODE");
		int elo = rs.getInt("ELO");
		int count = rs.getInt("COUNT");
		LocalDateTime ldt = rs.getObject("TIME", LocalDateTime.class);//.atZone(ZoneOffset.UTC);
		String hour = HOUR_FORMATTER.format(ldt);

		RegionTrack regTrack = tracks.computeIfAbsent(region, k -> new RegionTrack(region));
		SIDTrack sidTrack = regTrack.getSIDTrack(sid);
		GameTrack gameTrack = sidTrack.getGameTrack(gameMode, elo);
		gameTrack.playtimes.computeIfAbsent(hour, k -> new PlaytimeTrack()).addCount(count);
	}

	private void parseWeeklyData(ResultSet rs, Map<LocalDate, WeekTrack> tracks, LocalDate weekDate, int region, int sid) throws SQLException {
		int gameMode = rs.getInt("GAME_MODE");
		int elo = rs.getInt("ELO");
		String uid = rs.getString("UID");
		int platform = rs.getInt("PLATFORM");

		WeekTrack weekTrack = tracks.computeIfAbsent(weekDate, k -> new WeekTrack());
		RegionTrack regTrack = weekTrack.getRegionTrack(region);
		SIDTrack sidTrack = regTrack.getSIDTrack(sid);
		GameTrack gameTrack = sidTrack.getGameTrack(gameMode, elo);

		if (!gameTrack.plats.containsKey(uid)) {
			boolean isNew = true;
			boolean isUnique = true;
			boolean isRegionNew = true;
			boolean isRegionUnique = true;
			for (LocalDate wDate : tracks.keySet()) {
				WeekTrack wTrack = tracks.get(wDate);
				if (wTrack.hasPlayer(uid, region, sid, gameMode, elo)) {
					isNew = false;
					isRegionNew = false;
					if (wDate.equals(weekDate)) {
						isRegionUnique = false;
						isUnique = false;
					}
					break;
				} else if (wTrack.hasPlayer(uid, region, null, gameMode, elo)) {
					isRegionNew = false;
					if (wDate.equals(weekDate)) {
						isRegionUnique = false;
					}
				}
			}

			isUnique = isUnique && (isNew || !gameTrack.plats.containsKey(uid));
			isRegionUnique = isRegionUnique && (isRegionNew || !weekTrack.hasPlayer(uid, region, null, gameMode, elo));

			PlatformTrack plat = new PlatformTrack();
			plat.platform = platform;
			plat.isNew = isNew;
			plat.isUnique = isUnique;
			plat.isRegionUnique= isRegionUnique;
			plat.isRegionNew = isRegionNew;
			gameTrack.plats.put(uid, plat);
		}
	}

	private class WeekTrack {
		public List<RegionTrack> regions = new ArrayList<RegionTrack>();

		public RegionTrack getRegionTrack(int rid) {
			RegionTrack track = null;

			for (RegionTrack trk : regions) {
				if (trk.rid == rid) {
					track = trk;
					break;
				}
			}

			if (track == null) {
				track = new RegionTrack(rid);
				regions.add(track);
			}

			return track;
		}

		public boolean hasPlayer(String uid, Integer region, Integer sid, Integer gameMode, Integer elo) {
			boolean hasPlayer = false;
			for (RegionTrack reg : regions) {
				if (region != null && reg.rid != region) {
					continue;
				}
				for (SIDTrack strack : reg.sids) {
					if (sid != null && strack.sid != sid) {
						continue;
					}

					for (GameTrack game : strack.games) {
						if (gameMode != null && elo != null && (game.gameMode != gameMode || game.elo != elo)) {
							continue;
						}

						if (game.plats.containsKey(uid)) {
							hasPlayer = true;
							break;
						}
					}
					if (hasPlayer) {
						break;
					}
				}
				if (hasPlayer) {
					break;
				}
			}
			return hasPlayer;
		}

	}

	private class RegionTrack {
		public int rid;
		public List<SIDTrack> sids = new ArrayList<SIDTrack>();

		public RegionTrack(int rid) {
			this.rid = rid;
		}

		public SIDTrack getSIDTrack(int sid) {
			SIDTrack track = null;

			for (SIDTrack trk : sids) {
				if (trk.sid == sid) {
					track = trk;
					break;
				}
			}

			if (track == null) {
				track = new SIDTrack();
				track.sid = sid;
				sids.add(track);
			}

			return track;
		}

		public Map<Integer, Integer> getNumUniqueRegion(int sid, int gameMode, int elo) {
			Map<Integer, Integer> counts = new HashMap<Integer, Integer>(2, 1.0f);
			for (SIDTrack s : sids) {
				if (s.sid == sid) {
					counts = s.getNumUnique(gameMode, elo, true);
				}
			}
			return counts;
		}

		public Map<Integer, Integer> getNumNewRegion(int sid, int gameMode, int elo) {
			Map<Integer, Integer> counts = new HashMap<Integer, Integer>(2, 1.0f);
			for (SIDTrack s : sids) {
				if (s.sid == sid) {
					counts = s.getNumNew(gameMode, elo, true);
				}			
			}
			return counts;
		}

		public Map<Integer, Integer> getNumUniqueSID(int sid, int gameMode, int elo) {
			Map<Integer, Integer> counts = new HashMap<Integer, Integer>(2, 1.0f);

			for (SIDTrack s : sids) {
				if (s.sid == sid) {
					counts = s.getNumUnique(gameMode, elo, false);
				}
			}
			return counts;
		}

		public Map<Integer, Integer> getNumNewSID(int sid, int gameMode, int elo) {
			Map<Integer, Integer> counts = new HashMap<Integer, Integer>(2, 1.0f);

			for (SIDTrack s : sids) {
				if (s.sid == sid) {
					counts = s.getNumNew(gameMode, elo, false);
				}
			}
			return counts;
		}

	}

	private class SIDTrack {
		public int sid;
		public List<GameTrack> games = new ArrayList<GameTrack>();

		public GameTrack getGameTrack(int gameMode, int elo) {
			GameTrack track = null;

			for (GameTrack trk : games) {
				if (trk.gameMode == gameMode && trk.elo == elo) {
					track = trk;
					break;
				}
			}

			if (track == null) {
				track = new GameTrack();
				track.gameMode = gameMode;
				track.elo = elo;
				games.add(track);
			}

			return track;
		}

		public Map<Integer, Integer> getNumUnique(int gameMode, int elo, boolean isRegion) {
			Map<Integer, Integer> counts = new HashMap<Integer, Integer>(4, 1.0f);
			for (GameTrack game : games) {
				if (game.gameMode == gameMode && game.elo == elo) {
					for (PlatformTrack plat : game.plats.values()) {
						if ((!isRegion && plat.isUnique) || (isRegion && plat.isRegionUnique)) {
							counts.compute(plat.platform, (k,v) -> v == null ? 1 : v + 1);
						}
					}
				}
			}
			return counts;
		}

		public Map<Integer, Integer> getNumNew(int gameMode, int elo, boolean isRegion) {
			Map<Integer, Integer> counts = new HashMap<Integer, Integer>(4, 1.0f);
			for (GameTrack game : games) {
				if (game.gameMode == gameMode && game.elo == elo) {
					for (PlatformTrack plat : game.plats.values()) {
						if ((!isRegion && plat.isNew) || (isRegion && plat.isRegionNew)) {
							counts.compute(plat.platform, (k,v) -> v == null ? 1 : v + 1);
						}
					}
				}
			}
			return counts;
		}
	}

	private class GameTrack {
		public int gameMode;
		public int elo;
		public Map<String, PlatformTrack> plats = new HashMap<String, PlatformTrack>(2, 1.0f); // uid, platform
		public Map<String, PlaytimeTrack> playtimes = new HashMap<String, PlaytimeTrack>(1, 1.0f);
	}

	private class PlatformTrack {
		public int platform;
		public boolean isUnique;
		public boolean isNew;
		public boolean isRegionUnique;
		public boolean isRegionNew;
	}

	private class PlaytimeTrack {
		public int totalCount;
		public int numTimes;

		public void addCount(int count) {
			this.totalCount += count;
			++numTimes;
		}
	}

}
