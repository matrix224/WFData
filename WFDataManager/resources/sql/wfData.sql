PRAGMA synchronous = OFF;
PRAGMA journal_mode = MEMORY;
BEGIN TRANSACTION;

CREATE TABLE IF NOT EXISTS `activity_data` (
  `game_mode` integer NOT NULL
,  `elo` integer NOT NULL
,  `time` datetime NOT NULL
,  `count` integer NOT NULL DEFAULT '0'
);
CREATE TABLE IF NOT EXISTS `daily_weapon_data` (
  `date` date NOT NULL
,  `kills` integer NOT NULL
,  `weapon` varchar(64) NOT NULL
,  PRIMARY KEY (`date`,`weapon`)
);
CREATE TABLE IF NOT EXISTS `player_data` (
  `uid` varchar(64) NOT NULL
,  `kills` integer NOT NULL
,  `deaths` integer NOT NULL
,  `mechanics` integer NOT NULL
,  `goals` integer NOT NULL DEFAULT '0'
,  `passes` integer NOT NULL DEFAULT '0'
,  `interceptions` integer NOT NULL DEFAULT '0'
,  `captures` integer NOT NULL
,  `rounds` integer NOT NULL DEFAULT '0'
,  `last_seen` date DEFAULT NULL
,  PRIMARY KEY (`uid`)
);
CREATE TABLE IF NOT EXISTS `process_vars` (
  `var_name` varchar(32) NOT NULL
,  `var_value` varchar(64) NOT NULL
,  PRIMARY KEY (`var_name`)
);
CREATE TABLE IF NOT EXISTS `weapon_data` (
  `weapon` varchar(64) NOT NULL
,  `kills` integer NOT NULL
,  PRIMARY KEY (`weapon`)
);
CREATE TABLE IF NOT EXISTS `weapon_info` (
  `weapon` varchar(64) NOT NULL
,  `weapon_real` varchar(64) DEFAULT NULL
,  PRIMARY KEY (`weapon`)
);
CREATE TABLE IF NOT EXISTS `weekly_data` (
  `week_date` date NOT NULL
,  `uid` varchar(64) NOT NULL
,  `kills` integer NOT NULL
,  `deaths` integer NOT NULL
,  `mechanics` integer NOT NULL
,  `captures` integer NOT NULL
,  `rounds` integer NOT NULL
,  `wins` integer NOT NULL DEFAULT '0'
,  `game_mode` integer NOT NULL
,  `elo` integer NOT NULL
,  `weapon_kills` varchar(4096) DEFAULT NULL
,  `killed_by` varchar(16384) DEFAULT NULL
,  `total_time` integer NOT NULL DEFAULT '0'
,  PRIMARY KEY (`elo`,`week_date`,`uid`,`game_mode`)
);
CREATE TABLE IF NOT EXISTS `player_profile` (
  `name` varchar(64) NOT NULL
,  `uid` varchar(64) NOT NULL
,  `past_names` varchar(4096) DEFAULT NULL
,  `platform` integer NOT NULL DEFAULT 0
,  PRIMARY KEY (`uid`)
);
CREATE TABLE IF NOT EXISTS `current_bans` (
  `uid` varchar(64) NOT NULL,
  `when_banned` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `ban_key` varchar(256) NOT NULL,
  `reason` varchar(256) NOT NULL,
  `loadout_id` int(11) NOT NULL DEFAULT 0,
  `ip` varchar(32) NOT NULL,
  `is_primary` int(1) NOT NULL DEFAULT '0',
  `is_proxy` int(1) NOT NULL DEFAULT '0',
   PRIMARY KEY (`uid`,`ban_key`,`is_primary`,`loadout_id`)
);
CREATE TABLE IF NOT EXISTS `marked_players` (
  `uid` varchar(64) NOT NULL,
  `loadouts` varchar(4096) NOT NULL,
  PRIMARY KEY (`uid`)
);
CREATE TABLE IF NOT EXISTS `server_data` (
  `id` varchar(8) NOT NULL,
  `data` varchar(4096) NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE IF NOT EXISTS `retry_data` (
  `id` int(11) NOT NULL,
  `data` varchar(8192) NOT NULL,
  PRIMARY KEY (`id`)
);
CREATE TABLE IF NOT EXISTS `log_history` (
  `id` varchar(12) NOT NULL,
  `log_time` int(20) NOT NULL,
  PRIMARY KEY (`id`,`log_time`)
);
DROP INDEX IF EXISTS "idx_activity_data";
CREATE INDEX "idx_activity_data" ON "activity_data" (`game_mode`,`elo`,`time`);
END TRANSACTION;
