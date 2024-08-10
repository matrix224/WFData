package wfDataService.service.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.gson.JsonParser;

import jdtools.logging.Log;
import jdtools.util.MiscUtil;
import wfDataModel.service.type.RegionType;
import wfDataService.service.data.ServerClientData;
import wfDataService.service.db.manager.ResourceManager;

/**
 * Dao to support management of server client data in the DB.
 * @author MatNova
 *
 */
public class ServerClientDao {

	private static final String LOG_ID = ServerClientDao.class.getSimpleName();
	
	public static List<ServerClientData> fetchClientData() {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		List<ServerClientData> data = new ArrayList<ServerClientData>();
		
		try {
		 	conn = ResourceManager.getDBConnection();
		 	ps = conn.prepareStatement("SELECT * FROM MANAGER_CLIENT");
		 	rs = ps.executeQuery();
		 	while (rs.next()) {
		 		ServerClientData clientData = new ServerClientData(rs.getInt("SID"), rs.getString("DISPLAY_NAME"));
		 		String clientProps = rs.getString("PROPERTIES");
		 		clientData.setLastBanPollTime(rs.getLong("LAST_BAN_POLL"));
		 		clientData.setRegion(RegionType.codeToType(rs.getInt("REGION")));
		 		clientData.setValidated(rs.getInt("VALIDATED") == 1);
		 		if (!MiscUtil.isEmpty(clientProps)) {
		 			clientData.setServerClientProperties(JsonParser.parseString(clientProps).getAsJsonObject());
		 		}
		 		String nameOverride = rs.getString("NAME_OVERRIDE");
		 		if (!MiscUtil.isEmpty(nameOverride)) {
		 			clientData.setNameOverridden(true);
		 			clientData.setDisplayName(nameOverride);
		 		}
		 		data.add(clientData);
		 	}
		} catch (Exception e) {
			Log.error(LOG_ID + ".fetchClientData() : Exception occurred -> ", e);
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}
		
		return data;
	}
	
	public static void updateClientData(ServerClientData data) {
		Connection conn = null;
		PreparedStatement ps = null;
		
		try {
		 	conn = ResourceManager.getDBConnection();
		 	ps = conn.prepareStatement("UPDATE MANAGER_CLIENT SET DISPLAY_NAME = ?, REGION=?, LAST_BAN_POLL=?, VALIDATED=?, PROPERTIES=? WHERE SID=?");
		 	ps.setString(1, data.getDisplayName());
		 	ps.setInt(2, data.getRegion().getCode());
		 	ps.setLong(3, data.getLastBanPollTime());
		 	ps.setInt(4, data.isValidated() ? 1 : 0);
		 	ps.setString(5, data.getServerClientProperties() != null ? data.getServerClientProperties().toString() : null);
		 	ps.setInt(6, data.getServerClientID());
		 	int updated = ps.executeUpdate();
		 	if (updated == 0) {
		 		ResourceManager.releaseResources(ps);
			 	ps = conn.prepareStatement("INSERT INTO MANAGER_CLIENT (SID, DISPLAY_NAME, REGION, LAST_BAN_POLL, VALIDATED, PROPERTIES) VALUES (?,?,?,?,?,?)");
			 	ps.setInt(1, data.getServerClientID());
			 	ps.setString(2, data.getDisplayName());
			 	ps.setInt(3, data.getRegion().getCode());
			 	ps.setLong(4, data.getLastBanPollTime());
			 	ps.setInt(5, data.isValidated() ? 1 : 0);
			 	ps.setString(6, data.getServerClientProperties() != null ? data.getServerClientProperties().toString() : null);
			 	ps.executeUpdate();
		 	}
		} catch (Exception e) {
			Log.error(LOG_ID + ".updateClientData() : Exception occurred -> ", e);
		} finally {
			ResourceManager.releaseResources(conn, ps);
		}
	}
	
	public static void updateClientKey(int sid, String key) {
		Connection conn = null;
		PreparedStatement ps = null;
		
		try {
		 	conn = ResourceManager.getDBConnection();
		 	ps = conn.prepareStatement("UPDATE MANAGER_CLIENT SET SYM_KEY=? WHERE SID=?");
		 	ps.setString(1, key);
		 	ps.setInt(2, sid);
		 	ps.executeUpdate();
		} catch (Exception e) {
			Log.error(LOG_ID + ".updateClientRSA() : Exception occurred -> ", e);
		} finally {
			ResourceManager.releaseResources(conn, ps);
		}
	}
	
	public static byte[] getSymKey(int sid) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		byte[] rsa = null;
		
		try {
		 	conn = ResourceManager.getDBConnection();
		 	ps = conn.prepareStatement("SELECT SYM_KEY FROM MANAGER_CLIENT WHERE SID=?");
		 	ps.setInt(1, sid);
		 	rs = ps.executeQuery();
		 	if (rs.next()) {
		 		rsa = Base64.getDecoder().decode(rs.getString("SYM_KEY"));
		 	}
		} catch (Exception e) {
			Log.error(LOG_ID + ".getRSA() : Exception occurred -> ", e);
		} finally {
			ResourceManager.releaseResources(conn, ps, rs);
		}
		
		return rsa;
	}
}
