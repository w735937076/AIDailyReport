-- DailyReport MySQL Schema

-- 表1: git_commit_log (原始提交记录)
CREATE TABLE IF NOT EXISTS git_commit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    repo_name VARCHAR(100) NOT NULL,
    commit_id VARCHAR(64) NOT NULL,
    author VARCHAR(100) NOT NULL,
    author_email VARCHAR(200),
    commit_time DATETIME NOT NULL,
    message TEXT,
    diff_text LONGTEXT,
    parsed TINYINT(1) DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_repo_commit (repo_name, commit_id)
);

CREATE INDEX idx_commit_time ON git_commit_log(commit_time);
CREATE INDEX idx_parsed ON git_commit_log(parsed);

-- 表2: work_item (结构化任务)
CREATE TABLE IF NOT EXISTS work_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    commit_id VARCHAR(64),
    repo_name VARCHAR(100),
    work_date DATE NOT NULL,
    type VARCHAR(20) NOT NULL,
    module VARCHAR(50),
    plan TEXT,
    practice TEXT,
    estimate TEXT,
    progress TEXT,
    confidence DECIMAL(3,2),
    ai_raw_response LONGTEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_work_date ON work_item(work_date);
CREATE INDEX idx_type ON work_item(type);
CREATE INDEX idx_repo ON work_item(repo_name);

-- 表3: daily_report (日报)
CREATE TABLE IF NOT EXISTS daily_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_date DATE NOT NULL UNIQUE,
    content TEXT,
    tomorrow_plan TEXT,
    raw_data_json TEXT,
    excel_path VARCHAR(500),
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_report_date ON daily_report(report_date);

-- 表4: period_report (周期报告: 周报/月报)
CREATE TABLE IF NOT EXISTS period_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(10) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    content TEXT,
    raw_data_json TEXT,
    excel_path VARCHAR(500),
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_type_start (type, start_date)
);

CREATE INDEX idx_period ON period_report(type, start_date);
