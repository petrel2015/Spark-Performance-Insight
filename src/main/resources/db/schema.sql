-- Application 元数据
CREATE TABLE IF NOT EXISTS applications (
    app_id VARCHAR PRIMARY KEY,
    app_name VARCHAR,
    user_name VARCHAR,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration BIGINT,
    spark_version VARCHAR,
    status VARCHAR,
    data_quality_status VARCHAR DEFAULT 'GOOD',
    data_quality_note TEXT,
    parsing_status VARCHAR DEFAULT 'READY',
    parsing_progress VARCHAR
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
    add_time TIMESTAMP,
    remove_time TIMESTAMP,
    total_cores INT,
    memory BIGINT,
    is_active BOOLEAN DEFAULT TRUE,
    rdd_blocks INT DEFAULT 0,
    storage_memory BIGINT DEFAULT 0,
    on_heap_storage_memory BIGINT DEFAULT 0,
    off_heap_storage_memory BIGINT DEFAULT 0,
    peak_jvm_on_heap BIGINT DEFAULT 0,
    peak_jvm_off_heap BIGINT DEFAULT 0,
    peak_execution_on_heap BIGINT DEFAULT 0,
    peak_execution_off_heap BIGINT DEFAULT 0,
    peak_storage_on_heap BIGINT DEFAULT 0,
    peak_storage_off_heap BIGINT DEFAULT 0,
    peak_pool_direct BIGINT DEFAULT 0,
    peak_pool_mapped BIGINT DEFAULT 0,
    disk_used BIGINT DEFAULT 0,
    resources TEXT,
    resource_profile_id INT DEFAULT 0,
    active_tasks INT DEFAULT 0,
    failed_tasks INT DEFAULT 0,
    completed_tasks INT DEFAULT 0,
    total_tasks INT DEFAULT 0,
    task_time_ms BIGINT DEFAULT 0,
    gc_time_ms BIGINT DEFAULT 0,
    input_bytes BIGINT DEFAULT 0,
    shuffle_read_bytes BIGINT DEFAULT 0,
    shuffle_write_bytes BIGINT DEFAULT 0,
    exec_loss_reason TEXT
);

-- Job 详情
CREATE TABLE IF NOT EXISTS jobs (
    id VARCHAR PRIMARY KEY,
    app_id VARCHAR,
    job_id INT,
    submission_time TIMESTAMP,
    completion_time TIMESTAMP,
    duration BIGINT DEFAULT 0,
    status VARCHAR,
    num_stages INT,
    num_tasks INT,
    stage_ids TEXT, -- 存储 Stage ID 列表，逗号分隔
    description TEXT,
    job_group VARCHAR,
    num_completed_stages INT DEFAULT 0,
    num_failed_stages INT DEFAULT 0,
    num_skipped_stages INT DEFAULT 0,
    num_completed_tasks INT DEFAULT 0,
    num_failed_tasks INT DEFAULT 0,
    num_active_tasks INT DEFAULT 0,
    num_skipped_tasks INT DEFAULT 0,
    sql_execution_id BIGINT
);

-- Stage 详情
CREATE TABLE IF NOT EXISTS stages (
    id VARCHAR PRIMARY KEY,
    app_id VARCHAR,
    stage_id INT,
    job_id INT,
    attempt_id INT,
    stage_name VARCHAR,
    num_tasks INT,
    num_completed_tasks INT DEFAULT 0,
    num_failed_tasks INT DEFAULT 0,
    submission_time TIMESTAMP,
    completion_time TIMESTAMP,
    duration BIGINT DEFAULT 0,
    input_bytes BIGINT DEFAULT 0,
    input_records BIGINT DEFAULT 0,
    output_bytes BIGINT DEFAULT 0,
    shuffle_read_bytes BIGINT DEFAULT 0,
    shuffle_read_records BIGINT DEFAULT 0,
    shuffle_write_bytes BIGINT DEFAULT 0,
    shuffle_write_records BIGINT DEFAULT 0,
    output_records BIGINT DEFAULT 0,
    gc_time_sum BIGINT DEFAULT 0,
    tasks_duration_sum BIGINT DEFAULT 0,
    executor_deserialize_time_sum BIGINT DEFAULT 0,
    result_serialization_time_sum BIGINT DEFAULT 0,
    getting_result_time_sum BIGINT DEFAULT 0,
    scheduler_delay_sum BIGINT DEFAULT 0,
    peak_execution_memory_max BIGINT DEFAULT 0,
    peak_execution_memory_sum BIGINT DEFAULT 0,
    memory_bytes_spilled_sum BIGINT DEFAULT 0,
    disk_bytes_spilled_sum BIGINT DEFAULT 0,
    shuffle_write_time_sum BIGINT DEFAULT 0,
    duration_p50 BIGINT DEFAULT 0,
    duration_p75 BIGINT DEFAULT 0,
    duration_p95 BIGINT DEFAULT 0,
    duration_p99 BIGINT DEFAULT 0,
    max_task_duration BIGINT DEFAULT 0,
    status VARCHAR,
    is_skewed BOOLEAN DEFAULT FALSE,
    parent_stage_ids TEXT,
    rdd_info TEXT,
    locality_summary TEXT,
    diagnosis_info TEXT,
    performance_score DOUBLE DEFAULT 0.0
);

-- Task 细节
CREATE TABLE IF NOT EXISTS tasks (
    id VARCHAR PRIMARY KEY,
    app_id VARCHAR,
    stage_id INT,
    attempt_id INT DEFAULT 0,
    task_id BIGINT,
    task_index INT,
    executor_id VARCHAR,
    host VARCHAR,
    launch_time BIGINT,
    finish_time BIGINT,
    duration BIGINT,
    gc_time BIGINT,
    scheduler_delay BIGINT,
    getting_result_time BIGINT,
    executor_deserialize_time BIGINT,
    executor_run_time BIGINT,
    result_serialization_time BIGINT,
    executor_cpu_time BIGINT,
    peak_execution_memory BIGINT,
    input_bytes BIGINT DEFAULT 0,
    input_records BIGINT DEFAULT 0,
    output_bytes BIGINT DEFAULT 0,
    output_records BIGINT DEFAULT 0,
    memory_bytes_spilled BIGINT DEFAULT 0,
    disk_bytes_spilled BIGINT DEFAULT 0,
    shuffle_read_bytes BIGINT,
    shuffle_read_records BIGINT DEFAULT 0,
    shuffle_fetch_wait_time BIGINT,
    shuffle_write_bytes BIGINT DEFAULT 0,
    shuffle_write_time BIGINT DEFAULT 0,
    shuffle_write_records BIGINT DEFAULT 0,
    shuffle_remote_read BIGINT,
    speculative BOOLEAN,
    status VARCHAR,
    locality VARCHAR
);

-- Ensure columns exist for existing databases (DuckDB)
ALTER TABLE stages ADD COLUMN IF NOT EXISTS locality_summary TEXT;
ALTER TABLE stages ADD COLUMN IF NOT EXISTS diagnosis_info TEXT;
ALTER TABLE stages ADD COLUMN IF NOT EXISTS performance_score DOUBLE DEFAULT 0.0;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS sql_execution_id BIGINT;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS locality VARCHAR;

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

-- EventLog 文件解析状态记录
CREATE TABLE IF NOT EXISTS parsed_event_logs (
    file_path VARCHAR PRIMARY KEY,
    last_modified BIGINT,
    file_size BIGINT,
    file_hash VARCHAR,
    parsed_at TIMESTAMP,
    status VARCHAR
);

-- SQL 执行详情
CREATE TABLE IF NOT EXISTS sql_executions (
    id VARCHAR PRIMARY KEY, -- appId:executionId
    app_id VARCHAR,
    execution_id BIGINT,
    description TEXT,
    details TEXT,
    physical_plan TEXT,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration BIGINT DEFAULT 0,
    status VARCHAR -- RUNNING, SUCCEEDED, FAILED
);
