-- Web app settings tables (does not alter core report tables)

CREATE TABLE IF NOT EXISTS app_git_project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    local_path VARCHAR(500),
    remote_url VARCHAR(500),
    branch VARCHAR(100) DEFAULT 'main',
    enabled TINYINT(1) DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_name (name)
);

CREATE TABLE IF NOT EXISTS app_ai_config (
    id BIGINT PRIMARY KEY,
    primary_api_key VARCHAR(500),
    primary_base_url VARCHAR(500) NOT NULL DEFAULT 'https://api.openai.com/v1',
    primary_model VARCHAR(100) NOT NULL DEFAULT 'gpt-4o-mini',
    primary_connect_timeout_ms INT DEFAULT 5000,
    primary_read_timeout_ms INT DEFAULT 60000,
    max_retries INT DEFAULT 3,
    retry_delay_ms INT DEFAULT 1000,
    failure_threshold INT DEFAULT 2,
    cooldown_ms BIGINT DEFAULT 120000,
    fallback_enabled TINYINT(1) DEFAULT 0,
    fallback_api_key VARCHAR(500),
    fallback_base_url VARCHAR(500),
    fallback_model VARCHAR(100),
    fallback_connect_timeout_ms INT DEFAULT 5000,
    fallback_read_timeout_ms INT DEFAULT 60000,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT IGNORE INTO app_ai_config (id) VALUES (1);

CREATE TABLE IF NOT EXISTS app_scheduler_config (
    id BIGINT PRIMARY KEY,
    enabled TINYINT(1) DEFAULT 1,
    cron VARCHAR(50) NOT NULL DEFAULT '0 0 18 * * ?',
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Shanghai',
    git_filter_username VARCHAR(100),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT IGNORE INTO app_scheduler_config (id, enabled, cron, timezone) VALUES (1, 1, '0 0 18 * * ?', 'Asia/Shanghai');

CREATE TABLE IF NOT EXISTS app_setting (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value TEXT
);
