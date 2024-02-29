create database mpc;
use mpc;
CREATE TABLE `parent_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT 'id',
  `status` int NOT NULL,
  `type` varchar(100) DEFAULT NULL,
  `create_at` datetime NOT NULL,
  `update_at` datetime NOT NULL,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  `params` longtext,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
CREATE TABLE `children_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '[]',
  `parent_task_id` varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT 'id',
  `sub_id` int NOT NULL,
  `task_index` int NOT NULL,
  `pod_num` int DEFAULT NULL COMMENT 'pod',
  `status` int NOT NULL,
  `task_type` varchar(100) DEFAULT NULL,
  `create_at` datetime NOT NULL,
  `update_at` datetime NOT NULL,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  `message` text,
  `result` longtext,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

use mpc;
CREATE TABLE `data_block_meta_l` (
  `block_id` varchar(300) NOT NULL DEFAULT '',
  `dfs_data_block_dir` varchar(500) NOT NULL DEFAULT '',
  `partition_id` bigint DEFAULT '0',
  `file_version` bigint DEFAULT '0',
  `start_time` bigint DEFAULT NULL,
  `end_time` bigint DEFAULT NULL,
  `example_ids` bigint DEFAULT '0',
  `leader_start_index` bigint DEFAULT '0',
  `leader_end_index` bigint DEFAULT '0',
  `follower_start_index` bigint DEFAULT '0',
  `follower_end_index` bigint DEFAULT '0',
  `data_block_index` bigint DEFAULT '0',
  `create_time` bigint DEFAULT NULL,
  `update_time` bigint DEFAULT NULL,
  `create_status` int DEFAULT '2',
  `consumed_status` int DEFAULT NULL,
  `follower_restart_index` bigint DEFAULT '0',
  `data_source_name` varchar(300) DEFAULT NULL,
  PRIMARY KEY (`block_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

use mpc;
CREATE TABLE `job_task_stub` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `parent_task_id` varchar(100) DEFAULT NULL COMMENT '父任务id',
  `pre_job_json` longtext DEFAULT NULL COMMENT '任务详情json',
  `job_target` varchar(30) DEFAULT NULL COMMENT '任务执行端',
  `job_distributor_sign` varchar(500) DEFAULT NULL COMMENT '任务发起端签名MD5withRSA',
  `job_executor_sign` varchar(500) DEFAULT NULL COMMENT '任务执行端签名MD5withRSA',
  `job_distributor_cert` varchar(2000) DEFAULT NULL COMMENT '任务发起端证书内容',
  `job_executor_cert` varchar(2000) DEFAULT NULL COMMENT '任务执行端证书内容',
  `is_local` tinyint(4) NOT NULL COMMENT '任务来源，1=自身发起，0=外部发起',
  `create_at` datetime NOT NULL,
  `update_at` datetime NOT NULL,
  `is_deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE TABLE `cert_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `cert_content` varchar(2000) DEFAULT NULL COMMENT '证书内容',
  `public_exponent` varchar(1000) DEFAULT NULL COMMENT '公钥指数，ACES加密',
  `private_exponent` varchar(1000) DEFAULT NULL COMMENT '私钥指数，ACES加密',
  `modulus` varchar(1000) DEFAULT NULL COMMENT '模数，ACES加密',
  `is_root` tinyint(4) NOT NULL COMMENT '是否根证书，1是，0否',
  `create_at` datetime NOT NULL,
  `update_at` datetime NOT NULL,
  `is_deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE TABLE `auth_info` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `domain` varchar(200) DEFAULT NULL COMMENT 'target',
  `cert_type` varchar(200) DEFAULT NULL COMMENT ' ROOT  AUTH   WORKER',
  `cert` varchar(1000) DEFAULT NULL,
  `pub_key` varchar(1000) DEFAULT NULL,
  `pri_key` varchar(1000) DEFAULT NULL,
  `status` varchar(200) DEFAULT NULL COMMENT ' SUBMIT  PASS  REJECT',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UNIQUE_DOMAIN_CERT_TYPE` (`domain`,`cert_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
