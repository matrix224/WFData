package wfDataModel.model.util;

import java.util.Collection;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import jdtools.util.MiscUtil;

/**
 * Util for handling DB related tasks
 * @author MatNova
 *
 */
public final class DBUtil {

	public static final String DEFAULT_VER = "1.0.0";
	
	/**
	 * Given a Collection, will return it as a String represented in JSON format. <br/>
	 * If the Collection is null, this will return null.
	 * @param input
	 * @return
	 */
	public static String createDBCollection(Collection<?> input) {
		String data = null;
		if (input != null) {
			data = new GsonBuilder().disableHtmlEscaping().create().toJson(input).toString();
		}
		return data;
	}
	
	/**
	 * Given a map, will return it as a String represented in JSON format. <br/>
	 * If the map is null, this will return null.
	 * @param input
	 * @return
	 */
	public static String createDBMap(Map<?, ?> input) {
		String data = null;
		if (input != null) {
			data = new GsonBuilder().disableHtmlEscaping().create().toJson(input).toString();
		}
		return data;
	}
	
	/**
	 * Given an input string, a key type, and a value type, will return it as a map of key K and value V. <br/>
	 * If the input string is null or empty, this will return null.
	 * @param input
	 * @param key
	 * @param val
	 * @return
	 */
	public static <K,V> Map<K, V> parseDBMap(String input, Class<K> key, Class<V> val) {
		Map<K, V> data = null;

		if (!MiscUtil.isEmpty(input)) {
			Gson gson = new Gson();
			data = gson.fromJson(input, TypeToken.getParameterized(Map.class, key, val).getType());
		}
		
		return data;
	}
	
}
