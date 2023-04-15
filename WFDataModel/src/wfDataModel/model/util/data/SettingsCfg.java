package wfDataModel.model.util.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdtools.util.MiscUtil;
import wfDataModel.model.annotation.SettingData;
import wfDataModel.model.logging.Log;

/**
 * Base class to outline and define configs for service and client implementations <br>
 * Contains some config fields that are shared between the service and client. 
 * @author MatNova
 *
 */
public abstract class SettingsCfg {

	private static final String LOG_ID = SettingsCfg.class.getSimpleName();
	private static final char COMMENT_CHAR = '#';
	private static final String SETTING_SEPARATOR = String.valueOf('=');
	private static final String CONFIG_PROPERTY = "wfdata.config";
	private static final String USER_DIR = System.getProperty("user.dir") + File.separator;

	@SettingData(cfgName="cacheDir")
	private String cacheDir = USER_DIR + "cache" + File.separator;
	@SettingData(cfgName="printServerData", wrapper=Boolean.class)
	protected boolean printServerOutput = false;
	@SettingData(cfgName="serverDataFile")
	private String serverOutputFile = getUserDir() + File.separator + "data" + File.separator + "ServerData.json";
	@SettingData(cfgName="customItemsConfig")
	private String customItemsConfig = getUserDir() + File.separator + "cfg" + File.separator + "customItems.json";

	private boolean settingsLoaded;

	protected SettingsCfg() {
	}

	public static <T extends SettingsCfg> T create(Class<T> clazz) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		T cfg = clazz.getConstructor().newInstance();
		cfg.loadSettings();
		return cfg;
	}

	protected void loadSettings() {
		boolean isSuccess = false;
		File settingsFile = new File(!MiscUtil.isEmpty(System.getProperty(CONFIG_PROPERTY)) ? System.getProperty(CONFIG_PROPERTY) : USER_DIR + "cfg" + File.separator + "config.properties");

		if (settingsFile.exists()) {
			try (BufferedReader br = new BufferedReader(new FileReader(settingsFile))) {
				String line = null;
				Map<String, Field> cfgData = getSettings();
				boolean allValid = true;
				while ((line = br.readLine()) != null) {
					if (!MiscUtil.isEmpty(line.trim()) && line.trim().charAt(0) != COMMENT_CHAR) {
						String[] parts = line.split(SETTING_SEPARATOR, 2);
						String cfgName = parts[0].trim();
						if (cfgData.containsKey(cfgName)) {
							Field field = cfgData.get(cfgName);
							SettingData setting = field.getAnnotation(SettingData.class);

							if (parts.length == 2 && !MiscUtil.isEmpty(parts[1])) {
								String value = parts[1].trim();
								if (setting.wrapper().equals(String.class)) {
									field.set(this, value);
								} else {
									Method parseMethod = setting.wrapper().getDeclaredMethod("valueOf", String.class);
									field.set(this, parseMethod.invoke(null, value));
									if (Number.class.isAssignableFrom(setting.wrapper()) && field.get(this) != null) {
										if (setting.minValue() != Double.NaN && setting.minValue() > ((Number)field.get(this)).doubleValue()) {
											field.set(this, setting.minValue());
											Log.warn(LOG_ID + ".loadSettings() : Value for " + cfgName + " was below min allowed, defaulting to min. value=" + value + ", min=" + setting.minValue());
										} else if (setting.maxValue() != Double.NaN && setting.maxValue() < ((Number)field.get(this)).doubleValue()) {
											field.set(this, setting.maxValue());
											Log.warn(LOG_ID + ".loadSettings() : Value for " + cfgName + " was above max allowed, defaulting to max. value=" + value + ", max=" + setting.maxValue());
										}
									}
								}
							} else {
								Log.debug(LOG_ID + ".loadSettings() : Ignoring invalid or unset config line -> " + line);
								if (setting.required()) {
									Log.warn(LOG_ID + ".loadSettings() : Required field " + cfgName + " is not set!");
									allValid = false;
								}
							}
						} else {
							Log.warn(LOG_ID + ".loadSettings() : Ignoring unknown config setting -> " + cfgName);
						}
					}
				}
				isSuccess = allValid;
			} catch (FileNotFoundException e) {
				Log.error(LOG_ID + ".loadSettings() : File not found apparently while reading config -> " + e.getLocalizedMessage());
			} catch (IOException e) {
				Log.error(LOG_ID + ".loadSettings() : IOException while reading config -> " + e.getLocalizedMessage());
			} catch (IllegalArgumentException e) {
				Log.error(LOG_ID + ".loadSettings() : IllegalArgumentException while parsing config -> " + e.getLocalizedMessage());
			} catch (IllegalAccessException e) {
				Log.error(LOG_ID + ".loadSettings() : IllegalAccessException while parsing config -> " + e.getLocalizedMessage());
			} catch (NoSuchMethodException e) {
				Log.error(LOG_ID + ".loadSettings() : NoSuchMethodException while parsing config -> " + e.getLocalizedMessage());
			} catch (SecurityException e) {
				Log.error(LOG_ID + ".loadSettings() : SecurityException while parsing config -> " + e.getLocalizedMessage());
			} catch (InvocationTargetException e) {
				Log.error(LOG_ID + ".loadSettings() : InvocationTargetException while parsing config -> " + e.getLocalizedMessage());
			}
		} else {
			Log.warn(LOG_ID + ".loadSettings() : No config file was found -> " + settingsFile.getAbsolutePath());
		}

		settingsLoaded = isSuccess;
	}

	private Map<String, Field> getSettings() {
		Map<String, Field> fields = new HashMap<String, Field>();
		Class<?> clazz = getClass();
		List<Field> allFields = new ArrayList<Field>(Arrays.asList(clazz.getDeclaredFields()));

		do {
			clazz = clazz.getSuperclass();
			if (clazz != null) {
				allFields.addAll(Arrays.asList(clazz.getDeclaredFields()));
			}
		} while (clazz != null && !clazz.equals(SettingsCfg.class));

		for (Field field : allFields) {
			if (field.isAnnotationPresent(SettingData.class)) {
				SettingData setting = field.getAnnotation(SettingData.class);
				field.setAccessible(true);
				fields.put(setting.cfgName(), field);
			}
		}

		return fields;
	}

	public boolean settingsLoaded() {
		return settingsLoaded;
	}

	public String getCacheDir() {
		return cacheDir;
	}

	public boolean printServerData() {
		return printServerOutput;
	}

	public String getServerDataFile() {
		return serverOutputFile;
	}

	public String getCustomItemsConfig() {
		return customItemsConfig;
	}

	public String getUserDir() {
		return USER_DIR;
	}
}
