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
  `game_mode` int(11) NOT NULL,
  `elo` int(11) NOT NULL,
  `time` timestamp NOT NULL,
  `count` int(5) NOT NULL DEFAULT '0',
  `sid` int(11) NOT NULL,
  KEY `idx_activity_data` (`game_mode`,`elo`,`time`,`sid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `daily_weapon_data`
--

DROP TABLE IF EXISTS `daily_weapon_data`;
CREATE TABLE IF NOT EXISTS `daily_weapon_data` (
  `date` date NOT NULL,
  `kills` int(11) NOT NULL,
  `weapon` varchar(64) NOT NULL COMMENT 'Raw weapon name',
  `sid` int(11) NOT NULL,
  PRIMARY KEY (`date`,`weapon`,`sid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `game_info`
--

DROP TABLE IF EXISTS `game_info`;
CREATE TABLE IF NOT EXISTS `game_info` (
  `game_mode` int(11) NOT NULL,
  `elo` int(11) NOT NULL,
  `game_name` varchar(32) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `manager_client`
--

DROP TABLE IF EXISTS `manager_client`;
CREATE TABLE IF NOT EXISTS `manager_client` (
  `sid` int(11) NOT NULL,
  `display_name` varchar(64) NOT NULL,
  `name_override` varchar(64) DEFAULT NULL,
  `region` int(4) NOT NULL,
  `last_ban_poll` bigint(20) NOT NULL DEFAULT '0',
  `sym_key` varchar(4096) DEFAULT NULL,
  `validated` int(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`sid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `player_data`
--

DROP TABLE IF EXISTS `player_data`;
CREATE TABLE IF NOT EXISTS `player_data` (
  `uid` varchar(64) NOT NULL COMMENT 'Unique ID of player',
  `kills` int(11) NOT NULL,
  `deaths` int(11) NOT NULL,
  `mechanics` int(11) NOT NULL COMMENT 'Items picked up(?)',
  `goals` int(11) NOT NULL DEFAULT '0' COMMENT 'Lunaro only.',
  `passes` int(11) NOT NULL DEFAULT '0' COMMENT 'Lunaro only.',
  `interceptions` int(11) NOT NULL DEFAULT '0' COMMENT 'Lunaro only.',
  `captures` int(11) NOT NULL COMMENT 'For CTF only',
  `rounds` int(11) NOT NULL DEFAULT '0',
  `last_seen` date DEFAULT NULL,
  `sid` int(11) NOT NULL,
  PRIMARY KEY (`uid`,`sid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `player_profile`
--

DROP TABLE IF EXISTS `player_profile`;
CREATE TABLE IF NOT EXISTS `player_profile` (
  `name` varchar(64) NOT NULL,
  `uid` varchar(64) NOT NULL,
  `past_names` varchar(4096) DEFAULT NULL,
  `platform` int(11) NOT NULL DEFAULT '0',
  PRIMARY KEY (`uid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `process_vars`
--

DROP TABLE IF EXISTS `process_vars`;
CREATE TABLE IF NOT EXISTS `process_vars` (
  `var_name` varchar(32) NOT NULL,
  `var_value` varchar(64) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `region_info`
--

DROP TABLE IF EXISTS `region_info`;
CREATE TABLE IF NOT EXISTS `region_info` (
  `code` int(4) NOT NULL,
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
  `kills` int(11) NOT NULL,
  `sid` int(11) NOT NULL,
  PRIMARY KEY (`weapon`,`sid`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `weapon_info`
--

DROP TABLE IF EXISTS `weapon_info`;
CREATE TABLE IF NOT EXISTS `weapon_info` (
  `weapon` varchar(64) NOT NULL,
  `weapon_real` varchar(64) DEFAULT NULL COMMENT 'Weapon in-game name',
  PRIMARY KEY (`weapon`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `weekly_data`
--

DROP TABLE IF EXISTS `weekly_data`;
CREATE TABLE IF NOT EXISTS `weekly_data` (
  `week_date` date NOT NULL COMMENT 'The Sunday date of the week',
  `uid` varchar(64) NOT NULL,
  `kills` int(11) NOT NULL,
  `deaths` int(11) NOT NULL,
  `mechanics` int(11) NOT NULL,
  `captures` int(11) NOT NULL COMMENT 'Only applicable for CTF',
  `rounds` int(11) NOT NULL,
  `wins` int(11) NOT NULL DEFAULT '0',
  `game_mode` int(11) NOT NULL COMMENT 'Game mode ID',
  `elo` int(11) NOT NULL COMMENT '0 = RC, 2 = non-RC',
  `weapon_kills` varchar(4096) DEFAULT NULL,
  `killed_by` varchar(16384) DEFAULT NULL,
  `total_time` int(11) NOT NULL DEFAULT '0' COMMENT 'Total time played in seconds',
  `sid` int(11) NOT NULL,
  PRIMARY KEY (`elo`,`week_date`,`uid`,`game_mode`,`sid`) USING BTREE
) ENGINE=MyISAM DEFAULT CHARSET=latin1;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
