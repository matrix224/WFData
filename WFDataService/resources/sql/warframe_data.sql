SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `warframe_data`
--

-- --------------------------------------------------------

--
-- Table structure for table `activity_data`
--

DROP TABLE IF EXISTS `activity_data`;
CREATE TABLE IF NOT EXISTS `activity_data` (
  `game_mode` int NOT NULL,
  `elo` int NOT NULL,
  `time` timestamp NOT NULL,
  `count` int NOT NULL DEFAULT '0',
  `sid` int NOT NULL,
  KEY `idx_activity_data` (`game_mode`,`elo`,`time`,`sid`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `daily_weapon_data`
--

DROP TABLE IF EXISTS `daily_weapon_data`;
CREATE TABLE IF NOT EXISTS `daily_weapon_data` (
  `date` date NOT NULL,
  `kills` int NOT NULL,
  `weapon` varchar(64) NOT NULL COMMENT 'Raw weapon name',
  `sid` int NOT NULL,
  PRIMARY KEY (`date`,`weapon`,`sid`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `game_info`
--

DROP TABLE IF EXISTS `game_info`;
CREATE TABLE IF NOT EXISTS `game_info` (
  `game_mode` int NOT NULL,
  `elo` int NOT NULL,
  `game_name` varchar(32) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `manager_client`
--

DROP TABLE IF EXISTS `manager_client`;
CREATE TABLE IF NOT EXISTS `manager_client` (
  `sid` int NOT NULL,
  `display_name` varchar(64) NOT NULL,
  `name_override` varchar(64) DEFAULT NULL,
  `region` int NOT NULL,
  `last_ban_poll` bigint NOT NULL DEFAULT '0',
  `sym_key` varchar(4096) DEFAULT NULL,
  `validated` int NOT NULL DEFAULT '0',
  `properties` varchar(4096) DEFAULT NULL COMMENT 'Extra properties for the client',
  PRIMARY KEY (`sid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `player_data`
--

DROP TABLE IF EXISTS `player_data`;
CREATE TABLE IF NOT EXISTS `player_data` (
  `uid` varchar(64) NOT NULL COMMENT 'Unique ID of player',
  `kills` int NOT NULL,
  `deaths` int NOT NULL,
  `mechanics` int NOT NULL COMMENT 'Items picked up(?)',
  `goals` int NOT NULL DEFAULT '0' COMMENT 'Lunaro only. \r\nFrom 8.11.21 onwards',
  `passes` int NOT NULL DEFAULT '0' COMMENT 'Lunaro only. \r\nFrom 8.11.21 onwards',
  `interceptions` int NOT NULL DEFAULT '0' COMMENT 'Lunaro only. \r\nFrom 8.11.21 onwards',
  `captures` int NOT NULL COMMENT 'For CTF only. Starting 11.13.21',
  `rounds` int NOT NULL DEFAULT '0',
  `last_seen` date DEFAULT NULL,
  `sid` int NOT NULL,
  PRIMARY KEY (`uid`,`sid`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `player_profile`
--

DROP TABLE IF EXISTS `player_profile`;
CREATE TABLE IF NOT EXISTS `player_profile` (
  `name` varchar(64) NOT NULL,
  `uid` varchar(64) NOT NULL,
  `aid` varchar(64) DEFAULT NULL,
  `past_names` varchar(4096) DEFAULT NULL,
  `past_uids` varchar(4096) DEFAULT NULL,
  `platform` int NOT NULL DEFAULT '0',
  `banned` TINYINT NULL DEFAULT '0' COMMENT 'Denotes if a player is banned from the game. This flag has to be set manually',
  PRIMARY KEY (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `process_vars`
--

DROP TABLE IF EXISTS `process_vars`;
CREATE TABLE IF NOT EXISTS `process_vars` (
  `var_name` varchar(32) NOT NULL,
  `var_value` varchar(64) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

INSERT INTO `process_vars` (var_name, var_value) VALUES ('DBVER', '1.1.0');

-- --------------------------------------------------------

--
-- Table structure for table `region_info`
--

DROP TABLE IF EXISTS `region_info`;
CREATE TABLE IF NOT EXISTS `region_info` (
  `code` int NOT NULL,
  `display_name` varchar(32) NOT NULL,
  `display_name_short` varchar(6) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `weapon_data`
--

DROP TABLE IF EXISTS `weapon_data`;
CREATE TABLE IF NOT EXISTS `weapon_data` (
  `weapon` varchar(64) NOT NULL,
  `kills` int NOT NULL,
  `sid` int NOT NULL,
  PRIMARY KEY (`weapon`,`sid`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `weapon_info`
--

DROP TABLE IF EXISTS `weapon_info`;
CREATE TABLE IF NOT EXISTS `weapon_info` (
  `weapon` varchar(64) NOT NULL,
  `weapon_real` varchar(64) DEFAULT NULL COMMENT 'Weapon in-game name',
  PRIMARY KEY (`weapon`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `weekly_data`
--

DROP TABLE IF EXISTS `weekly_data`;
CREATE TABLE IF NOT EXISTS `weekly_data` (
  `week_date` date NOT NULL COMMENT 'The Sunday date of the week',
  `uid` varchar(64) NOT NULL,
  `kills` int NOT NULL,
  `deaths` int NOT NULL,
  `mechanics` int NOT NULL,
  `captures` int NOT NULL COMMENT 'Only applicable for CTF',
  `rounds` int NOT NULL,
  `wins` int NOT NULL DEFAULT '0',
  `game_mode` int NOT NULL COMMENT 'Game mode ID',
  `elo` int NOT NULL COMMENT '0 = RC, 2 = non-RC',
  `weapon_kills` varchar(4096) DEFAULT NULL,
  `killed_by` varchar(49152) DEFAULT NULL,
  `total_time` int NOT NULL DEFAULT '0' COMMENT 'Total time played in seconds',
  `platform` int NOT NULL DEFAULT '0',
  `sid` int NOT NULL,
  PRIMARY KEY (`elo`,`week_date`,`uid`,`game_mode`,`platform`,`sid`) USING BTREE,
  KEY `IDX_WEEKLY_GM_ELO_SID` (`game_mode`,`elo`,`sid`),
  KEY `IDX_WEEKLY_UID` (`uid`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
