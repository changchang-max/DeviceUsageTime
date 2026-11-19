-- ============================================================
-- 初始化建表脚本 — 设备使用时间追踪系统
--
-- 由 docker compose 挂载到 MySQL 容器的 /docker-entrypoint-initdb.d/，
-- 仅在数据卷为空（首次启动）时自动执行。
--
-- 表结构与后端实体 / Mapper 严格对应：
--   user_auth           <- UserAuthEntity / UserAuthMapper.xml
--   daily_statistics    <- DailyStatisticsEntity / HistoryDataMapper.xml
--   app_usage_records   <- AppUsageRecordEntity / HistoryDataMapper.xml
--   app_window_titles   <- AppWindowTitleEntity / HistoryDataMapper.xml
--   data_date_index     <- DataDateIndexEntity / HistoryDataMapper.xml
--
-- 注意：HistoryDataMapper.xml 的 upsert 使用 MySQL 8.0.19+ 的
--       "INSERT ... AS new ON DUPLICATE KEY UPDATE new.col" 语法，
--       因此必须使用 MySQL 8.0.19 及以上版本。
-- ============================================================

SET NAMES utf8mb4;

USE deviceusagetime;

-- ------------------------------------------------------------
-- 用户认证表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_auth (
    user_email        VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'User email (global unique identifier)',
    user_password     VARCHAR(255) NOT NULL COMMENT 'BCrypt hashed password',
    user_key          VARCHAR(64) DEFAULT NULL COMMENT 'Invite code for client data upload',
    upload_credential VARCHAR(32) DEFAULT NULL COMMENT 'Upload credential: alternative identifier for data upload without login',
    user_name         VARCHAR(50) DEFAULT NULL COMMENT 'Display name',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    last_login_at     DATETIME DEFAULT NULL COMMENT 'Last login time',
    PRIMARY KEY (user_email),
    UNIQUE KEY uk_user_auth_key (user_key),
    UNIQUE KEY uk_user_auth_upload_credential (upload_credential)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'User authentication';

-- ------------------------------------------------------------
-- 每日统计表（键盘/鼠标/使用时长）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS daily_statistics (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_email        VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Owner email',
    stat_date         DATE NOT NULL COMMENT 'Statistics date',
    keyboard_count    BIGINT DEFAULT 0 COMMENT 'Keyboard hit count',
    mouse_click_count BIGINT DEFAULT 0 COMMENT 'Mouse click count',
    mouse_distance    DECIMAL(20, 4) DEFAULT 0.0000 COMMENT 'Mouse move distance (m)',
    scroll_distance   DECIMAL(20, 4) DEFAULT 0.0000 COMMENT 'Scroll distance',
    total_duration    BIGINT DEFAULT 0 COMMENT 'Device usage duration (s)',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_statistics_user_date (user_email, stat_date),
    KEY idx_daily_statistics_date (stat_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'Daily input and duration statistics';

-- ------------------------------------------------------------
-- 应用使用记录表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_usage_records (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_email        VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Owner email',
    stat_date         DATE NOT NULL COMMENT 'Statistics date',
    app_name          VARCHAR(255) NOT NULL COMMENT 'Application name',
    total_duration    BIGINT DEFAULT 0 COMMENT 'App total duration that day (s)',
    sessions          BIGINT DEFAULT 0 COMMENT 'Session count',
    last_window_title VARCHAR(1024) DEFAULT NULL COMMENT 'Last foreground window title',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_usage_records_user_date_app (user_email, stat_date, app_name),
    KEY idx_app_usage_records_user_date (user_email, stat_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'Per-application usage records';

-- ------------------------------------------------------------
-- 应用窗口标题表（窗口标题明细分，依赖 app_usage_records）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_window_titles (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    record_id    BIGINT UNSIGNED NOT NULL COMMENT 'Related app_usage_records id',
    window_title VARCHAR(1024) NOT NULL COMMENT 'Window title',
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_window_titles_record_title (record_id, window_title(255)),
    KEY idx_app_window_titles_record (record_id),
    CONSTRAINT fk_app_window_titles_record
        FOREIGN KEY (record_id) REFERENCES app_usage_records (id)
            ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'Window titles per application record';

-- ------------------------------------------------------------
-- 有数据的日期索引表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS data_date_index (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_email       VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Owner email',
    stat_date        DATE NOT NULL COMMENT 'Date that has data',
    last_active_app  VARCHAR(255) DEFAULT NULL COMMENT 'Last foreground app of that day',
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_date_index_user_date (user_email, stat_date),
    KEY idx_data_date_index_date (stat_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'Index of dates that have data';