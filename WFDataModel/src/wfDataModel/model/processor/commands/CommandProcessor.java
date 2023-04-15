package wfDataModel.model.processor.commands;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import jdtools.util.MiscUtil;
import wfDataModel.model.commands.BaseCmd;
import wfDataModel.model.logging.Log;

/**
 * Processor for loading and handling the execution of any defined commands
 * @author MatNova
 *
 */
public final class CommandProcessor {
	private static final String LOG_ID = CommandProcessor.class.getSimpleName();
	private Map<String, Class<? extends BaseCmd>> commands = new HashMap<String, Class<? extends BaseCmd>>();

	public CommandProcessor(String cmdPackage) {
		loadCommands(cmdPackage);
	}

	private void loadCommands(String cmdPackage) {
		try {
			List<Class<? extends BaseCmd>> cmdClasses = getClassesInPackage(cmdPackage);
			if (!MiscUtil.isEmpty(cmdClasses)) {
				for (Class<? extends BaseCmd> cmd : cmdClasses) {
					String cmdName = cmd.getSimpleName().toLowerCase().endsWith("cmd") ? cmd.getSimpleName().substring(0, cmd.getSimpleName().length() - 3) : cmd.getSimpleName();
					commands.put(cmdName.toLowerCase(), cmd);
				}
			} else {
				Log.warn(LOG_ID + ".loadCommands() : No commands found for package " + cmdPackage);
			}

		} catch (Exception e) {
			Log.error(LOG_ID + ".loadCommands() : Exception trying to load commands -> ", e);
		}
	}

	public void processCommand(String cmdAndArgs) {
		if (!MiscUtil.isEmpty(cmdAndArgs)) {
			String[] parts = cmdAndArgs.split(" ", 2);
			String cmd = parts[0].toLowerCase();
			if (commands.containsKey(cmd)) {
				try {
					BaseCmd cmdClass = commands.get(cmd).getDeclaredConstructor().newInstance();
					String[] args = parts.length > 1 ? parts[1].split(" ", cmdClass.getMaxParams()) : null;
					cmdClass.runCmd(args);
				} catch (Throwable t) {
					Log.error(LOG_ID + ".processCommand() : Exception processing command '" + cmd + "' -> ", t);
				}

			} else if (cmd.equalsIgnoreCase("help")) {
				if (parts.length == 1) {
					String cmdsStr = "";
					List<String> cmds = new ArrayList<String>(commands.keySet());
					cmds.add("help");
					Collections.sort(cmds);
					for (String c : cmds) {
						cmdsStr += c + "\n";
					}
					Log.info("Available commands: \n" + cmdsStr);
				} else {
					String helpCmd = parts[1].toLowerCase();
					if (commands.containsKey(helpCmd)) {
						try {
							BaseCmd cmdClass = commands.get(helpCmd).getDeclaredConstructor().newInstance();
							Log.info(cmdClass.getDescription());
						} catch (Throwable t) {
							Log.error(LOG_ID + ".processCommand() : Exception processing command '" + cmd + "' -> ", t);
						}
					} else {
						Log.warn(LOG_ID + ".processCommand() : Unknown command for help -> "+ cmd);
					}
				}

			} else {
				Log.warn(LOG_ID + ".processCommand() : Unknown command -> "+ cmd);
			}
		}
	}

	// Adapted from https://stackoverflow.com/questions/28678026/how-can-i-get-all-class-files-in-a-specific-package-in-java
	@SuppressWarnings("unchecked")
	public static final List<Class<? extends BaseCmd>> getClassesInPackage(String packageName) {
		String path = packageName.replaceAll("\\.", "/");
		List<Class<? extends BaseCmd>> classes = new ArrayList<>();
		String[] classPathEntries = System.getProperty("java.class.path").split(
				System.getProperty("path.separator")
				);

		String name;
		for (String classpathEntry : classPathEntries) {
			if (classpathEntry.endsWith(".jar")) {
				File jar = new File(classpathEntry);
				try (JarInputStream is = new JarInputStream(new FileInputStream(jar));) {
					JarEntry entry;
					while((entry = is.getNextJarEntry()) != null) {
						name = entry.getName();
						if (name.endsWith(".class") && name.contains(path)) {
							String classPath = name.substring(0, entry.getName().length() - 6);
							classPath = classPath.replaceAll("[\\|/]", ".");
							Class<?> clz = Class.forName(classPath);
							if (BaseCmd.class.isAssignableFrom(clz)) {
								classes.add((Class<? extends BaseCmd>) clz);
							} 
						}
					}
				} catch (Exception ex) {
					Log.error(LOG_ID + ".getClassesInPackage() : Error occurred -> ", ex);
				}
			} else {
				try {
					File base = new File(classpathEntry + File.separatorChar + path);
					for (File file : base.listFiles()) {
						name = file.getName();
						if (name.endsWith(".class")) {
							name = name.substring(0, name.length() - 6);
							Class<?> clz = Class.forName((packageName + "." + name));
							if (BaseCmd.class.isAssignableFrom(clz)) {
								classes.add((Class<? extends BaseCmd>) clz);
							} 
						}
					}
				} catch (Exception ex) {
					Log.error(LOG_ID + ".getClassesInPackage() : Error occurred -> ", ex);
				}
			}
		}

		return classes;
	}
}
