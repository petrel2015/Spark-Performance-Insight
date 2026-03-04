-- Gold Applications (Serving layer)
CREATE TABLE IF NOT EXISTS gold_applications (
    app_id VARCHAR PRIMARY KEY,
    app_name VARCHAR,
    user_name VARCHAR,
    start_time BIGINT,
    end_time BIGINT,
    duration BIGINT,
    spark_version VARCHAR,
    status VARCHAR,
    data_quality_status VARCHAR DEFAULT 'GOOD',
    data_quality_note TEXT,
    parsing_status VARCHAR DEFAULT 'READY',
    parsing_progress VARCHAR,
    parsing_progress_value DOUBLE DEFAULT 0.0,
    parsing_start_time BIGINT,
    parsing_end_time BIGINT,
    source_file_metadata JSON,
    performance_score INTEGER,
    diagnosis_info JSON,
    llm_report TEXT,
    llm_start_time BIGINT,
    llm_end_time BIGINT,
    total_log_size BIGINT DEFAULT 0,
    total_tasks BIGINT DEFAULT 0,
    failed_tasks BIGINT DEFAULT 0,
    total_input_bytes BIGINT DEFAULT 0,
    total_shuffle_read_bytes BIGINT DEFAULT 0,
    compression_format VARCHAR,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 扫描到的待处理日志记录
CREATE TABLE IF NOT EXISTS sys_event_log_scans (
    id VARCHAR PRIMARY KEY,
    app_id VARCHAR,
    file_paths JSON,
    total_size BIGINT,
    previous_status VARCHAR,
    detected_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Gold 环境配置
CREATE TABLE IF NOT EXISTS gold_environment_configs (
    id VARCHAR PRIMARY KEY,
    app_id VARCHAR,
    param_key VARCHAR,
    param_value VARCHAR,
    category VARCHAR
);

-- Gold Executors
CREATE TABLE IF NOT EXISTS gold_executors (
    id VARCHAR PRIMARY KEY,
    app_id VARCHAR,
    executor_id VARCHAR,
    host VARCHAR,
    add_time BIGINT,
    remove_time BIGINT,
    total_cores BIGINT,
    memory BIGINT,
    is_active BOOLEAN DEFAULT TRUE,
    rdd_blocks BIGINT DEFAULT 0,
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
    resource_profile_id BIGINT DEFAULT 0,
    active_tasks BIGINT DEFAULT 0,
    failed_tasks BIGINT DEFAULT 0,
    completed_tasks BIGINT DEFAULT 0,
    total_tasks BIGINT DEFAULT 0,
    task_time_ms BIGINT DEFAULT 0,
    gc_time_ms BIGINT DEFAULT 0,
    input_bytes BIGINT DEFAULT 0,
    shuffle_read_bytes BIGINT DEFAULT 0,
    shuffle_write_bytes BIGINT DEFAULT 0,
    exec_loss_reason TEXT,
    avg_task_duration_ms DOUBLE,
    cpu_utilization_ratio DOUBLE,
    max_peak_memory BIGINT
);

-- Gold Jobs
CREATE TABLE IF NOT EXISTS gold_jobs (
    id VARCHAR PRIMARY KEY,
    app_id VARCHAR,
    job_id BIGINT,
    submission_time BIGINT,
    completion_time BIGINT,
    duration BIGINT DEFAULT 0,
    status VARCHAR,
    num_stages BIGINT,
    num_tasks BIGINT,
    stage_ids TEXT,
    description TEXT,
    job_group VARCHAR,
    num_completed_stages BIGINT DEFAULT 0,
    num_failed_stages BIGINT DEFAULT 0,
    num_skipped_stages BIGINT DEFAULT 0,
    num_completed_tasks BIGINT DEFAULT 0,
    num_failed_tasks BIGINT DEFAULT 0,
    num_active_tasks BIGINT DEFAULT 0,
    num_skipped_tasks BIGINT DEFAULT 0,
    sql_execution_id BIGINT,
    performance_score DOUBLE DEFAULT 0.0,
    tasks_duration_sum BIGINT DEFAULT 0
);

-- Gold Stages
CREATE TABLE IF NOT EXISTS gold_stages (
    id VARCHAR PRIMARY KEY,
    app_id VARCHAR,
    stage_id BIGINT,
    job_id BIGINT,
    attempt_id BIGINT,
    stage_name VARCHAR,
    num_tasks BIGINT,
    num_completed_tasks BIGINT DEFAULT 0,
    num_failed_tasks BIGINT DEFAULT 0,
    submission_time BIGINT,
    completion_time BIGINT,
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
    skew_ratio DOUBLE DEFAULT 1.0,
    gc_ratio DOUBLE DEFAULT 0.0,
    parent_stage_ids TEXT,
    rdd_info TEXT,
    locality_summary TEXT,
    diagnosis_info TEXT,
    performance_score DOUBLE DEFAULT 0.0,
    score_skew DOUBLE,
    score_gc DOUBLE,
    score_locality DOUBLE,
    score_shuffle_write DOUBLE,
    score_shuffle_read DOUBLE,
    score_io DOUBLE,
    score_ser DOUBLE,
    score_result DOUBLE,
    score_delay DOUBLE,
    score_spill DOUBLE
);

-- Gold Tasks
CREATE TABLE IF NOT EXISTS gold_tasks (
    id VARCHAR PRIMARY KEY,
    app_id VARCHAR,
    stage_id BIGINT,
    attempt_id BIGINT DEFAULT 0,
    task_id BIGINT,
    task_index BIGINT,
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

-- 其他业务支持表
CREATE TABLE IF NOT EXISTS gold_stage_statistics (id VARCHAR PRIMARY KEY, app_id VARCHAR, stage_id BIGINT, attempt_id BIGINT, metric_name VARCHAR, min_value BIGINT, p25 BIGINT, p50 BIGINT, p75 BIGINT, p95 BIGINT, max_value BIGINT);
CREATE TABLE IF NOT EXISTS gold_sql_executions (id VARCHAR PRIMARY KEY, app_id VARCHAR, execution_id BIGINT, description TEXT, details TEXT, physical_plan TEXT, plan_info TEXT, start_time BIGINT, end_time BIGINT, duration BIGINT DEFAULT 0, status VARCHAR, performance_score DOUBLE DEFAULT 0.0);
CREATE TABLE IF NOT EXISTS gold_storage_rdds (id VARCHAR PRIMARY KEY, app_id VARCHAR, rdd_id BIGINT, name VARCHAR, storage_level VARCHAR, num_partitions BIGINT, num_cached_partitions BIGINT, memory_size BIGINT DEFAULT 0, disk_size BIGINT DEFAULT 0);
CREATE TABLE IF NOT EXISTS gold_storage_blocks (id VARCHAR PRIMARY KEY, app_id VARCHAR, rdd_id BIGINT, block_name VARCHAR, storage_level VARCHAR, memory_size BIGINT DEFAULT 0, disk_size BIGINT DEFAULT 0, executor_id VARCHAR, host VARCHAR);
CREATE TABLE IF NOT EXISTS sys_application_logs (id VARCHAR PRIMARY KEY, app_id VARCHAR, event_type VARCHAR, event_name VARCHAR, details TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);

-- Pipeline Progress Tracking for Incremental Retry
CREATE TABLE IF NOT EXISTS sys_pipeline_progress (
    app_id VARCHAR,
    phase VARCHAR, -- SILVER_TRANSFORM, GOLD_AGGR
    task_type VARCHAR, -- STAGE, JOB, APP_METADATA, etc.
    task_id VARCHAR, -- e.g., "stage_0_0" or "job_1"
    status VARCHAR, -- COMPLETED, FAILED
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (app_id, phase, task_type, task_id)
);

-- Application Parsing Queue
CREATE TABLE IF NOT EXISTS sys_parsing_queue (
    id UUID DEFAULT uuid() PRIMARY KEY,
    app_id VARCHAR NOT NULL,
    type VARCHAR NOT NULL, -- FULL, BRONZE_TO_GOLD, SILVER_TO_GOLD
    status VARCHAR DEFAULT 'QUEUED', -- QUEUED, RUNNING
    submit_time BIGINT,
    start_time BIGINT,
    end_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_sys_parsing_queue_status ON sys_parsing_queue(status, submit_time);

-- ==========================================
-- BRONZE LAYER
-- ==========================================
CREATE TABLE IF NOT EXISTS bronze_event_application_start (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS bronze_event_application_end (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS bronze_event_job_start (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS bronze_event_job_end (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS bronze_event_stage_submitted (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS bronze_event_stage_completed (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS bronze_event_task_start (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS bronze_event_task_end (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX IF NOT EXISTS idx_bronze_task_end_app ON bronze_event_task_end(app_id);
CREATE TABLE IF NOT EXISTS bronze_event_executor_added (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS bronze_event_executor_removed (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS bronze_event_block_manager_added (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS bronze_event_sql_execution_start (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS bronze_event_sql_execution_end (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS bronze_event_environment_update (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS bronze_event_log_start (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS bronze_event_block_updated (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS bronze_event_unpersist_rdd (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS bronze_event_unknown (id UUID DEFAULT uuid(), app_id VARCHAR, file_name VARCHAR, event_name VARCHAR, raw_json JSON, ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);

-- ==========================================
-- SILVER LAYER
-- ==========================================
CREATE TABLE IF NOT EXISTS silver_jobs (app_id VARCHAR, job_id BIGINT, submission_time BIGINT, completion_time BIGINT, duration_ms BIGINT, status VARCHAR, num_stages BIGINT, stage_ids JSON, description TEXT, sql_execution_id BIGINT);
CREATE INDEX IF NOT EXISTS idx_silver_jobs_app_job ON silver_jobs(app_id, job_id);

CREATE TABLE IF NOT EXISTS silver_stages (app_id VARCHAR, stage_id BIGINT, attempt_id BIGINT, job_id BIGINT, name VARCHAR, num_tasks BIGINT, status VARCHAR, submission_time BIGINT, completion_time BIGINT, duration_ms BIGINT, input_bytes BIGINT DEFAULT 0, shuffle_read_bytes BIGINT DEFAULT 0, parent_ids JSON, rdd_info TEXT);
CREATE INDEX IF NOT EXISTS idx_silver_stages_app_stage ON silver_stages(app_id, stage_id, attempt_id);

CREATE TABLE IF NOT EXISTS silver_tasks (app_id VARCHAR, task_id BIGINT, stage_id BIGINT, stage_attempt_id BIGINT, executor_id VARCHAR, host VARCHAR, index BIGINT, attempt_number BIGINT, launch_time BIGINT, finish_time BIGINT, duration_ms BIGINT, status VARCHAR, locality VARCHAR, speculative BOOLEAN, executor_run_time BIGINT, executor_cpu_time BIGINT, gc_time BIGINT, input_bytes BIGINT, output_bytes BIGINT, shuffle_read_bytes BIGINT, shuffle_fetch_wait_time BIGINT, shuffle_write_bytes BIGINT, shuffle_write_time BIGINT, memory_bytes_spilled BIGINT, disk_bytes_spilled BIGINT, peak_execution_memory BIGINT, executor_deserialize_time BIGINT, result_serialization_time BIGINT, getting_result_time BIGINT, scheduler_delay BIGINT);
CREATE INDEX IF NOT EXISTS idx_silver_tasks_app_task ON silver_tasks(app_id, task_id);

CREATE TABLE IF NOT EXISTS silver_executors (app_id VARCHAR, executor_id VARCHAR, host VARCHAR, total_cores BIGINT, add_time BIGINT, remove_time BIGINT, remove_reason TEXT);
CREATE INDEX IF NOT EXISTS idx_silver_executors_app_exec ON silver_executors(app_id, executor_id);

CREATE TABLE IF NOT EXISTS silver_sql_executions (app_id VARCHAR, execution_id BIGINT, description TEXT, details TEXT, physical_plan TEXT, plan_info TEXT, start_time BIGINT, end_time BIGINT, duration_ms BIGINT, status VARCHAR);
CREATE INDEX IF NOT EXISTS idx_silver_sql_app_exec ON silver_sql_executions(app_id, execution_id);

CREATE TABLE IF NOT EXISTS silver_environment_configs (app_id VARCHAR, param_key VARCHAR, param_value VARCHAR, category VARCHAR);
CREATE INDEX IF NOT EXISTS idx_silver_env_app ON silver_environment_configs(app_id);

CREATE TABLE IF NOT EXISTS silver_storage_blocks (
    app_id VARCHAR,
    block_id VARCHAR,
    rdd_id BIGINT,
    name VARCHAR,
    storage_level VARCHAR,
    memory_size BIGINT,
    disk_size BIGINT,
    executor_id VARCHAR,
    host VARCHAR,
    status VARCHAR, -- UPDATED, DELETED
    event_time BIGINT
);
CREATE INDEX IF NOT EXISTS idx_silver_storage_app ON silver_storage_blocks(app_id, block_id);

CREATE TABLE IF NOT EXISTS silver_rdd_info (
    app_id VARCHAR,
    rdd_id BIGINT,
    name VARCHAR,
    storage_level VARCHAR,
    num_partitions BIGINT,
    callsite TEXT,
    scope TEXT
);
CREATE INDEX IF NOT EXISTS idx_silver_rdd_app ON silver_rdd_info(app_id, rdd_id);
