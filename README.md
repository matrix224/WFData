

# WF Data
The WFData repository encompasses the projects required for both parsing and sending Dedicated Server data, and also hosting the service to receive said data.
There are three main components of this repository:

 1. WFDataManager
 2. WFDataService
 3. WFDataModel
---
### Index
* [WFDataManager](#wfdatamanager)
  * [Requirements](#manager-requirements)
  * [Execution](#manager-execution)
  * [Config Files](#manager-config-files)
* [WFDataService](#wfdataservice)
  * [Requirements](#service-requirements)
  * [Execution](#service-execution)
  * [Config Files](#service-config-files)
* [WFDataModel](#wfdatamodel)

---
## WFDataManager
This is the client program that will be ran to parse Dedicated Server data on the local machine, and if specified, send the data to an instance of the WFDataService which is / can be hosted elsewhere. 

As a quick synopsis, this program functions by polling a specified directory for server log files at a specified interval. During each polling session, the logs will be parsed - picking up where the last polling session left off - and any applicable data will be tracked and processed as per your designated settings.
This program **does not** touch the server executables or any other of DE's data in any way. This simply reads the log files, which are presented in plain text, and builds the data from them.

----

### Manager Requirements
```
 * Java 1.8+
 * JDTools (custom jar)
 * WFDataModel
 * gson 2.9.0
 * log4j-api 2.19.0
 * log4j-core 2.19.0
 * lzma-java 1.3
 * slf4j-api 1.7.30
 * slf4j-simple 1.7.30
 * sqlite-jdbc 3.8.7
 * HikariCP 4.0.3
```
In addition, it should be noted that if you plan to use the banning features of the parsser (i.e. item banning, perm banning, etc), then the manager must be run with administrator privileges. This is due to the fact that it is inserting/removing entries from the firewall when managing bans, which will require admin to do properly.

----
### Manager Execution
In the release zip file, there is a folder for the WFDataManager. This folder contains everything that is needed to run the WFDataManager. There are certain config settings that should be set up and changed as needed for your preference. These can be seen further down in the next section.

As part of the included setup, there is a .bat file that is used to run the WFDataManager. If none of the folder structures are changed and the location of this .bat is left where it is relative to those folders, then nothing in there should need to be changed except (potentially) one thing.
The WFDataManager itself only takes one argument, and that is "mode". The two valid options to provide are `NORMAL` and `HISTORICAL`. 
For `NORMAL` mode, this is just going to parse the specified normal log directory and store/send data as configured. This is the mode for regular, everyday use.
For `HISTORICAL` mode, this is used when you have old log files and want to provide them for processing and storage. This will read the specified historical log dir, and will also have a few special caveats such as not enacting any bans, or updating server status information. In addition, the polling time for logs will be overridden in this mode so that processing can go faster, since these logs are already written and completed. 

There are two main, non-mutually-exclusive options that can happen with the data that is parsed from this program:

 1. The data can be stored locally in a database
 2. The data can be sent to an instance of the WFDataService

If the first option is enabled, then it's exactly as described: any data parsed from this will be stored in a local database. By default, this comes with and is configured to use a local SQLite file. This can be configured and changed as needed to meet your needs, though it may require extra work.
If the second option is enabled, then this will send any parsed data to a specified instance of the WFDataService. The service will accept and aggregate this data, so that it can be combined and displayed in a centralized place (e.g. a website) alongside data for other contributing servers.

By default, neither of these options are enabled. So when configuring this, make sure to enable and configure them up as desired.

----

### Manager Config Files
There are several config files that come with this. Some of them are not handled directly by the project (e.g log4j), while others are explicitly used by the project.
 For the non-direct ones, a brief description will be given.
  For any that are explicitly for this project, a more in-depth description will be given.

#### log4j2.properties
This is the config file for logging settings, used by the log4j library. 
By default:

 - Will roll log over every day, and gzip the previous day's log file
 - Will automatically delete any log files older than 30 days
 - Verbosity is debug, which prints out a good amount of information about what is being parsed
 - Log file location is ./logs
For any other information and config details, please see log4j's documentation.

#### db.properties
This is the config file for HikariCP, a database pooling manager. This is very simple by default, and just points to the SQLite DB file in the ./data directory
For any other information and config details, please see HikariCP's documentation

#### config.properties
This is explicit to the WFDataManager program. This defines all of the properties that may be used and configured for the program. The following is a breakdown of all the properties, what they do, and how they interact with one another. Any field denoted with an * (asterisk) is required to be set.

&#128292;``serverLogsDir* (String)``
The directory that server log files are stored in. This is where the program will pull logs from for its parsing. The files it looks for in here will be determined as log files if they match the regex pattern specified in the ``serverLogPattern`` setting.
If you have multiple directories that you would like to read at once, then you can specify multiple here by delimiting them with a ; (semi-colon)

&#128292;`` historicalLogsDir (String)``
The directory that historical (i.e. old) server log files are stored in. This is where the program will pull logs from for its historical data parsing. The files it looks for in here will be determined as log files if they match the regex pattern specified in the ``historicalLogPattern`` setting.  
If you have multiple directories that you would like to read at once, then you can specify multiple here by delimiting them with a ; (semi-colon)
This is required only if you are running the program with Historical mode toggled on.

&#128292;``serverLogPattern (String)``
The regex pattern that is used to denote what a typical log file looks like.
This should have at most one capture group, where it captures a set of digits to denote the server's ID. If no ID can be derived from the name or no capture group is specified, then it will use the default value of 99 for the server ID.
e.g. DedicatedServer1.log => 1,  DedicatedServer.log => 99

&#128292;`` historicalLogPattern (String)``
The regex pattern used to denote what a historical log file looks like, to be parsed for loading historical data. This should have at most one capture group, where it captures a set of digits to denote the server's ID. If no ID can be derived from the name or no capture group is specified, then it will use the default value of 99 for the server ID.
e.g. DedicatedServer1.log372117618.log => 1,  DedicatedServer.log72861612.log => 99

&#9989;`` cleanServerProfiles (Boolean)``
If set to true, this will delete the profile folder that is created by a server when it starts up. If the profile folder the server is using is not empty, then this will not delete it. This is not expected in most cases currently. 
This may also not be needed anymore after Maciej addressed the issue, but there is no harm in having it enabled.

&#128292;`` displayName (String)``
This is the friendly name that the WFDataService will recognize you as. This may be displayed to other people (e.g. on a website), so make sure it's sensible and friendly! The WFDataService has the ability to override your chosen name if needed.
If you are using the data service at all, this is required to be set.

&#128292;``region (String)``
This is the region that your server is being run from. This will be used for data aggregation in the WFDataService if you have enabled sending data to them in any way.
Valid values are: ASIA, EUROPE, NORTH_AMERICA, OCEANIA, RUSSIA, SOUTH_AMERICA. If not set, will be considered UNKNOWN.
This is not required to be set if using the data service, but is highly recommended.

&#9989;`` persist (Boolean)``
If set to true, then any parsed data will be stored in the local database.
Note that even if this is false, some data will still be stored in the database (e.g. service connection keys, server parsing states)

&#128292;`` serviceHost (String)``
The hostname / IP for the WFDataService that you will send data to if enabled. 

&#128290;``servicePort (Integer)``
The port for the WFDataService that you will send data to if enabled.

&#128290;``serviceTimeout (Integer)``
The timeout, specified in seconds, that will be applied to any connections to the WFDataService.
Min value: 1
Max value: 60

&#9989;``enableDataSharing (Boolean)``
If set to true, then any parsed data (player kills, weapon data, server status, etc) will be sent to your specified WFDataService for data aggregation.
This requires ``displayName``, ``serviceHost``, and ``servicePort`` to be set.

&#9989;`` enableAlerts (Boolean)``
> NOT IMPLEMENTED YET  

&#128292;`` cacheDir (String)``
The directory to store item cache JSON files in. If not set, will default to [current directory]/cache

&#128292;``customItemsConfig (String)``
The JSON config for custom items that the parser should recognize. This is not required, but prevents it from doing a double-parse on items it might not know (e.g. DamageTrigger). If not specified, will use [current directory]/cfg/customItems.json

&#128290;`` pollInterval (Integer)``
The interval, specified in seconds, in which log files will be parsed for new data.
Min value: 10

&#128290;`` jamThreshold (Integer)``
*This currently will not do anything functional until ``enableAlerts`` is implemented*
Specifies the number of times the server has to be polled without any new data to assume it is jammed.
e.g. if the ``jamThreshold`` is 5 and a server's log is read 5 times without any new data being read, it will assume it is jammed.
Note that this should be something sensible in relation to the ``pollInterval``. It's possible for the server to legitimately not print any new data for several minutes if nothing is happening, so this shouldn't be too low to avoid any false detections. A value <= 0 will disable jam alerts

&#128290;``logCheckInterval (Integer)``
Specifies the number of times parsing will occur before refreshing and checking for any new log files in your specified ``serverLogsDir``. This is not applicaple for historical mode, as it does not check for any new log files after the first run.
e.g. if set to 4, then every 4 runs this will check ``serverLogsDir`` for any log files it didn't know about yet, or remove any that may have disappeared

&#9989;`` printServerData (Boolean)``
If set to true, then a JSON file containing current server data will be printed during parsing. The file it prints to is defined via the ``serverDataFile`` value

&#9989;``serverDataFile (Boolean)``
If ``printServerData`` is true, this is the file data will be printed to. If no value is specified, will print to [current directory]/data/ServerData.json

&#9989;``enableBanning (Boolean)``
> NOTE: This only works for Windows currently! 

If true, will allow for players to be banned for using items outlined in the ``bannedItemsConfig`` file. When a player is banned, they will be kicked from the server and any subsequent rejoins will be met with an automatic kick as well, up until ``banTime`` seconds are up. For any IP that we determine is one of DE's proxies, it will specifically ban the IP on the player's current remote port as to not affect any legit players who may be using that same proxy to join the server. Otherwise, if their IP is not determined to be a DE proxy, it will ban their IP with no specific port.
The methodology of how item bans work is explained in detail in the ``bannedItems.json`` cfg section.

&#9989;``enableBanSharing (Boolean)``
If true and ``enableBanning`` is true, then this will share any bans with the WFDataService to allow other servers to use this ban as well (e.g. you ban someone, server B will ban them as well for their own specified amount of time). Additionally, other servers' bans will be used on your machine (e.g. server B bans someone, your server will ban them as well for your specified amount of time)

&#9989;`` enforceSharedBanLoadouts (Boolean)``
If true, and ``enableBanSharing`` (and all of its prerequisites) is true, then this will ensure any other server's bans will only be applied to you if it's for an exact loadout that you also have banned.
e.g. if another server bans the Rubico but you do not, then their Rubico bans will not be applied to you
It is recommended to keep this on in case anyone else decides to send rogue ban requests.

&#128292;``bannedItemsConfig (String)``
Specifies the JSON config file that outlines banned items / loadouts. If no value is specified, will look for [current directory]/cfg/bannedItems.json

&#128290;``banTime (Integer)``
Specifies the time in seconds that players with offensive loadouts will be banned and repeatedly kicked for (+ up to ``banCheckInterval`` additional seconds). If you would prefer to have it be treated as just a kick instead of a ban, it's recommended to make this a much shorter value (e.g. 10) so that once a player is kicked, they'll be able to rejoin with no consequences sooner.

&#128290;`` secondaryBanTime (Integer)``
Specifies time in seconds that any bans applied to people who are already banned will last for (+ up to ``banCheckInterval`` additional seconds)
e.g. if Player A was banned and rejoins before ``banTime`` elapses, this is the time that the ban we create to kick them again will last for
There's little to no point in making this any longer than 15 or so seconds, since the point of this is just to kick them while their ban is in effect. In addition, if they're rejoining, they're most likely using a proxy (DE's or otherwise). 

&#128290;`` banCheckInterval (Integer)``
Specifies the interval in seconds that bans will be checked for expiration
Min value: 1

&#128290;``banFetchInterval (Integer)``
Specifies the interval in seconds that new bans will be fetched from the service if ``enableBanSharing`` is true
Min value: 15

#### customItems.json
This is explicit to the WFDataManager program. This specifies item mappings that you want to denote because they are not going to be found by the item cache. For example, "DamageTrigger" is a common 'item' that comes up in parsing a lot as it kills players. However this is not an item that will be recognized in DE's item schemas. 
When parsing for player kills is performed, it will halt parsing and re-parse the last line the next time around if it encounters an item that is not recognized in the cache. On the second parse, it will take what it gets and add a temporary mapping to the item cache for it. This was originally implemented to avoid the last line being cut off mid-read, but that problem had been resolved later on by stopping parsing at the last line. Nonetheless, the logic still remained and as such, this config can allow you to specify items you know are okay to avoid this 'double parse' scenario.
The format is simply a JSON object with key:value string entries, where the key is the item name and the value is the display name

    {
	"DamageTrigger":"DamageTrigger"
	}

#### bannedItems.json
This is specific to the WFDataManager program. This file specifies items that are bannable for being used, and specifies the threshold for which they should trigger a ban. Additionally, certain elos and gamemodes can be specified as well to limit the scope of certain bans.

Item banning works via a points system, where each kill within a parsing period garners a certain amount of points. Once the total amount of points exceeds a threshold, a ban is created for the user.
There is a system of leniency that can be used to try and let players talk to the offending user / give them a fair chance to stop, in case they are truly unaware about the items. This is called the strike system, and it is straight forward: a number of strikes is specified, and each time the offense threshold is reached, a strike is put in place. Once the strike threshold is reached, the ban will be put in place and any future infractions for that same item will have no strikes applied; it will be an immediate ban once the offense threshold is reached.

Each item specified in the banned loadouts has its own itemAmount, which is a value between 0.01 and 0.99. The lower the value, the less lenient it is (quicker it will ban someone) while the higher the value, the more lenient it is (takes longer to ban someone)

The formula for calculating the points is as follows
points = c^(-tx) 
c = item scale (itemAmount)
t = total kills with item in current game (i.e. total kills they've gotten with it during their time being connected to the server)
x = kills with item during parsing period (i.e. each interval it parses the logs)

So lets run through an example, where we have a banned loadout for the Wolf Sledge. In this example, we have an offenseThreshold of 10, a strikeThreshold of 3, and an itemAmount of 0.1 (so very strict). The log is getting parsed every 30 seconds. For this example, it will be a completely new person who doesn't know about the sledge, and is going crazy with it.
The person gets one kill, so c = 0.1, t=1, and x = 1:  points = 0.1  ^ (-1*1) => 10. That is >= our offenseThreshold, and they get 1 strike.
30 seconds pass and it parses the log again. They have killed with it two more times during that period. So now c = 0.1, t = 3, x = 2. points = 0.1 ^ (-3*2) => 1 million (obviously excessive, but that's what we want). That is strike 2 now.
30 seconds pass and it parses the log again. They did not kill anyone with it during this time, so nothing happens; no calculations needed.
30 seconds pass and it parses the log again. They have gotten one more kill during this period. So now c = 0.1, t = 4, x = 1. points = 0.1 ^ (-4*1) => 10000. Well over our threshold of 10, and that is also now strike 3. 
They would now be banned, and would be noted for future infractions so that if they do come back and use it again, there will be no strikes. The first time they exceed the offenseThreshold, they will be banned.

Here is an example of what this config file may look like for the example above:

    {
	"offenseThreshold":10,
	"strikeThreshold":3,
	"loadouts":[
		{
			"loadoutName":"Wolf Sledge",
			"items":[
				{
					"itemName":"ThrowingHammer",
					"itemAmount":0.1
				}
			],
			"elo":"NON_RC"
		}
	  ]
	}
&#128290;`` offenseThreshold (Integer)``
This is the number of points required to elicit a strike or ban. The points formula outlined above can be played with to find a good number for your needs.

&#128290;``strikeThreshold (Integer)``
This is the number of times a first-time offender can exceed the offenseThreshold before being banned. Once they have run out of strikes, any future infractions will be an immediate ban.

``loadouts (JSON array)``
The loadout objects that specify banned loadout parameters

&#128292;`` loadoutName (String)``
The friendly name for the loadout

``items (JSON array)``
Specifies the items that are part of this loadout. Note that if multiple items are present, all of them will contribute to the point calculation.

&#128292;`` itemName (String)``
The item definition name (i.e.  DE's internal name) for the item you want to ban

&#128290;`` itemAmount (Double)``
The scaling factor for this item, between 0.01 and 0.99. The lower the number, the less leniency and the higher the number, the more leniency.

&#128292;`` elo (String)``
One of "RC" or "NON_RC". This allows you to specify what elo mode the loadout should apply to. If this field is not present, it will be applied to any elo.

`` gameMode (JSON array)``
Array of strings. Can contain any of CTF, TA, FFA, LUNARO, CTF_VAR, TA_VAR, FFA_VAR, VT. This allows you to specify what gamemode the loadout should apply to. If this field is not present, it will be applied to any gamemode.

----
## WFDataService
This is the service program that will be ran to parse Dedicated Server data received from instances of the WFDataManager. 
The data received by this service will be stored as per the specified DB config, and any necessary aggregation of data will be performed by the service as well.

----
### Service Requirements
```
 * Java 1.8+
 * JDTools (custom jar)
 * WFDataModel
 * gson 2.9.0
 * log4j-api 2.19.0
 * log4j-core 2.19.0
 * lzma-java 1.3
 * slf4j-api 1.7.30
 * slf4j-simple 1.7.30
 * mysql-connector-java 8.0.31
 * HikariCP 4.0.3
```
----
### Service Execution
In the release zip file, there is a folder for the WFDataService. This folder contains everything that is needed to run the WFDataService. There are certain config settings that should be set up and changed as needed for your preference. These can be seen further down in the next section.

As part of the included setup, there is a .bat file that is used to run the WFDataService. If none of the folder structures are changed and the location of this .bat is left where it is relative to those folders, then nothing in there should need to be changed by default.

There are no separate modes or program args for the WFDataService.

----
### Service Config Files
There are several config files that come with this. Some of them are not handled directly by the project (e.g log4j), while others are explicitly used by the project.
 For the non-direct ones, a brief description will be given.
 For any that are explicitly for this project, a more in-depth description will be given.

#### log4j2.properties
This is the config file for logging settings, used by the log4j library. 
By default:

 - Will roll log over every day, and gzip the previous day's log file
 - Will automatically delete any log files older than 30 days
 - Verbosity is debug, which prints out a good amount of information about what is being parsed
 - Log file location is ./logs
For any other information and config details, please see log4j's documentation.

#### db.properties
This is the config file for HikariCP, a database pooling manager. This is very simple by default, and just points to a MySQL DB. By default, it points to one running on your localhost.
For any other information and config details, please see HikariCP's documentation

#### config.properties
This is explicit to the WFDataService program. This defines all of the properties that may be used and configured for the program. The following is a breakdown of all the properties, what they do, and how they interact with one another. Any field denoted with an * (asterisk) is required to be set.

&#128290;`` servicePort* (Integer)``
Specifies the port that the WFDataService will listen for connections on

&#9989;``allowAutoRegistration (Boolean)``
 If false, then any clients who register with the service must be enabled with the "enable" command. Otherwise if true, any clients who register will be automatically enabled. Clients must be enabled before they can send/receive any data from the service.

&#128292;`` cacheDir (String)``
The directory to store item cache JSON files in. If not set, will default to [current directory]/cache

&#128292;``customItemsConfig (String)``
The JSON config for custom items that the parser should recognize. This is not required, but allows received items to be mapped if they're not known (e.g. DamageTrigger). If not specified, will use [current directory]/cfg/customItems.json
For more information on this, please see config above on [custom items](#customitems.json)

&#9989;``printServerData (Boolean)``
If set, will periodically print out a summary of all the currently registered client's server statuses

&#128292;``serverDataFile (String)``
Denotes what file server status data should be printed to if printServerData is true. If no value is specified, will print to [current directory]/data/ServerStatus.json

&#128290;`` serverStatusUpdateInterval (Integer)``
Specifies the interval (in seconds) that the server status file will be updated if printServerData is true.


----
## WFDataModel
This is the intermediary jar that provides logic and data models that are common between the WFDataManager and WFDataService programs. There is nothing here to be executed or configured.
