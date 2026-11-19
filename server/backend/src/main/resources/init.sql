-- ============================================================
-- 建表脚本 — 设备使用时间追踪系统
--
-- 由 docker compose / docker run 挂载到 MySQL 容器的
-- /docker-entrypoint-initdb.d/，仅在数据卷为空（首次启动）时执行。
--
-- 结构来源：本机 MySQL 8.0.43 实际库 deviceusagetime 的 mysqldump --no-data
--          （含列类型、注释、索引名、外键、排序规则），并补齐下列代码依赖字段：
--          user_auth.upload_credential —— 见 db/migration.sql，
--          后端 UserAuthMapper 的 insert / selectByUploadCredential /
--          updateUploadCredential / revokeUploadCredential 均依赖该列。
--
-- 注意：HistoryDataMapper.xml 的 upsert 使用 MySQL 8.0.19+ 的
--       "INSERT ... AS new ON DUPLICATE KEY UPDATE new.col" 语法，
--       因此 MySQL 版本必须 >= 8.0.19。
-- ============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `deviceusagetime`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `deviceusagetime`;

-- ------------------------------------------------------------
-- 应用使用记录表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `app_usage_records` (
    `id`                bigint                              NOT NULL AUTO_INCREMENT,
    `user_email`        varchar(255)                        NOT NULL COMMENT '用户邮箱',
    `stat_date`         date                                NOT NULL COMMENT '统计日期',
    `app_name`          varchar(255)                        NOT NULL COMMENT '应用名称',
    `total_duration`    int unsigned                        DEFAULT '0' COMMENT '该应用当天总使用时长(秒)',
    `sessions`          int unsigned                        DEFAULT '0' COMMENT '使用次数',
    `last_window_title` varchar(500)                        DEFAULT NULL COMMENT '该应用当天最近一次前台窗口标题(服务端聚合辅助字段)',
    `created_at`        datetime                            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        datetime                            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date_app` (`user_email`, `stat_date`, `app_name`),
    KEY `idx_user_date` (`user_email`, `stat_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '应用使用记录表';

-- ------------------------------------------------------------
-- 应用窗口标题表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `app_window_titles` (
    `id`           bigint       NOT NULL AUTO_INCREMENT,
    `record_id`    bigint       NOT NULL COMMENT '关联的应用使用记录ID',
    `window_title` varchar(500) NOT NULL COMMENT '窗口标题',
    `created_at`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_title` (`record_id`, `window_title`),
    KEY `idx_record_id` (`record_id`),
    CONSTRAINT `fk_app_window_titles_record`
        FOREIGN KEY (`record_id`) REFERENCES `app_usage_records` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '应用窗口标题表';

-- ------------------------------------------------------------
-- 每日统计数据表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `daily_statistics` (
    `id`                bigint       NOT NULL AUTO_INCREMENT,
    `user_email`        varchar(255) NOT NULL COMMENT '用户邮箱',
    `stat_date`         date         NOT NULL COMMENT '统计日期',
    `keyboard_count`    int unsigned DEFAULT '0' COMMENT '键盘敲击总次数',
    `mouse_click_count` int unsigned DEFAULT '0' COMMENT '鼠标点击总次数',
    `mouse_distance`    decimal(10, 2) DEFAULT '0.00' COMMENT '鼠标移动总距离(米)',
    `scroll_distance`   decimal(10, 2) DEFAULT '0.00' COMMENT '滚轮滚动总距离',
    `total_duration`    int unsigned DEFAULT '0' COMMENT '设备总使用时长(秒)',
    `created_at`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_email`, `stat_date`),
    KEY `idx_stat_date` (`stat_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '每日统计数据表';

-- ------------------------------------------------------------
-- 有数据日期索引表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `data_date_index` (
    `id`              bigint       NOT NULL AUTO_INCREMENT,
    `user_email`      varchar(255) NOT NULL COMMENT '用户邮箱',
    `stat_date`       date         NOT NULL COMMENT '有数据的日期',
    `last_active_app` varchar(255) DEFAULT NULL COMMENT '当日最近一次前台应用(服务端聚合辅助字段)',
    `created_at`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_email`, `stat_date`),
    KEY `idx_user_id` (`user_email`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '有数据日期索引表';

-- ------------------------------------------------------------
-- 用户认证表
--
-- upload_credential：客户端免登录上传凭证，对应 db/migration.sql。
-- 本机旧库尚未执行该迁移，此处一并创建，保证与后端 Mapper 一致。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_auth` (
    `user_email`        varchar(255) NOT NULL COMMENT '用户邮箱地址，也是登录凭证',
    `user_password`     varchar(255) NOT NULL COMMENT '存储加密后的用户密码',
    `user_key`          varchar(255) DEFAULT NULL,
    `upload_credential` varchar(32)  DEFAULT NULL COMMENT 'Upload credential: alternative identifier for data upload without login',
    `user_name`         varchar(255) DEFAULT '请输入昵称' COMMENT '存储用户昵称，但不作为登录凭证',
    `created_at`        datetime     NOT NULL COMMENT '账户被创建的时间',
    `last_login_at`     datetime     DEFAULT NULL COMMENT '最后一次登录的时间',
    PRIMARY KEY (`user_email` DESC),
    UNIQUE KEY `uk_user_auth_key` (`user_key`),
    UNIQUE KEY `uk_user_auth_upload_credential` (`upload_credential`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;