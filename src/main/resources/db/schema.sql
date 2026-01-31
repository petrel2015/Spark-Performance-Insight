-- Application 元数据
CREATE TABLE IF NOT EXISTS applications (
    app_id VARCHAR PRIMARY KEY,
    app_name VARCHAR,
    user_name VARCHAR,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration BIGINT,
    spark_version VARCHAR,
    status VARCHAR
);

-- 环境配置
CREATE TABLE IF NOT EXISTS environment_configs (
    id VARCHAR PRIMARY KEY,
    app_id VARCHAR,
    param_key VARCHAR,
    param_value VARCHAR,
    category VARCHAR
);

-- Executor 信息
CREATE TABLE IF NOT EXISTS executors (
    id VARCHAR PRIMARY KEY,
    app_id VARCHAR,
    executor_id VARCHAR,
    host VARCHAR,
    total_cores INT,
    memory BIGINT,
    is_active BOOLEAN DEFAULT TRUE
);

-- Job 详情
CREATE TABLE IF NOT EXISTS jobs (
    id VARCHAR PRIMARY KEY,
    app_id VARCHAR,
    job_id INT,
    submission_time TIMESTAMP,
    completion_time TIMESTAMP,
    status VARCHAR,
    num_stages INT,
    num_tasks INT,
    stage_ids TEXT -- 存储 Stage ID 列表，逗号分隔
);

-- Stage 详情
CREATE TABLE IF NOT EXISTS stages (
    id VARCHAR PRIMARY KEY,
    app_id VARCHAR,
    stage_id INT,
    attempt_id INT,
    stage_name VARCHAR,
    num_tasks INT,
    submission_time TIMESTAMP,
    completion_time TIMESTAMP,
    input_bytes BIGINT DEFAULT 0,
    output_bytes BIGINT DEFAULT 0,
    shuffle_read_bytes BIGINT DEFAULT 0,
    shuffle_write_bytes BIGINT DEFAULT 0,
    gc_time_sum BIGINT DEFAULT 0,
    duration_p50 BIGINT DEFAULT 0,
    duration_p75 BIGINT DEFAULT 0,
    duration_p95 BIGINT DEFAULT 0,
    duration_p99 BIGINT DEFAULT 0,
    is_skewed BOOLEAN DEFAULT FALSE
);

-- Task 细节
CREATE TABLE IF NOT EXISTS tasks (
    id VARCHAR PRIMARY KEY,
    app_id VARCHAR,
    stage_id INT,
    task_id BIGINT,
    executor_id VARCHAR,
    host VARCHAR,
    duration BIGINT,
    gc_time BIGINT,
    scheduler_delay BIGINT,
    getting_result_time BIGINT,
    peak_execution_memory BIGINT,
    input_bytes BIGINT DEFAULT 0,
    memory_bytes_spilled BIGINT DEFAULT 0,
    disk_bytes_spilled BIGINT DEFAULT 0,
    shuffle_read_bytes BIGINT,
    shuffle_remote_read BIGINT,
    speculative BOOLEAN,
    status VARCHAR
);

-- 诊断建议表
CREATE TABLE IF NOT EXISTS diagnosis_reports (
    id INTEGER PRIMARY KEY,
    app_id VARCHAR,
    diag_type VARCHAR,
    severity VARCHAR,
    target_stage_id INT,
    summary_text TEXT,
    suggestion TEXT
);

-- Stage 统计指标 (仿 Spark UI Summary Metrics)
CREATE TABLE IF NOT EXISTS stage_statistics (
    id VARCHAR PRIMARY KEY, -- 组合键: app_id:stage_id:attempt_id:metric_name
    app_id VARCHAR,
    stage_id INT,
    attempt_id INT,
    metric_name VARCHAR, -- duration, gc_time, input_bytes, shuffle_read_bytes, shuffle_write_bytes ...
    min_value BIGINT,
    p25 BIGINT,
    p50 BIGINT,
    p75 BIGINT,
    p95 BIGINT,
    max_value BIGINT
);
