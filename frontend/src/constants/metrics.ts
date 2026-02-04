export interface MetricDef {
    key: string;
    label: string;
    type: 'time' | 'bytes' | 'nanos' | 'composite' | 'number';
    // 对于复合指标（如 Size / Records），定义其包含的基础字段
    subKeys?: string[];
}

export const AVAILABLE_METRICS: MetricDef[] = [
    {key: 'task_deserialization_time', label: 'Task Deserialization Time', type: 'time'},
    {key: 'duration', label: 'Duration', type: 'time'},
    {key: 'gc_time', label: 'GC Time', type: 'time'},
    {key: 'result_serialization_time', label: 'Result Serialization Time', type: 'time'},
    {key: 'getting_result_time', label: 'Getting Result Time', type: 'time'},
    {key: 'scheduler_delay', label: 'Scheduler Delay', type: 'time'},
    {key: 'peak_execution_memory', label: 'Peak Execution Memory', type: 'bytes'},
    {key: 'memory_spill', label: 'Spill (memory)', type: 'bytes'},
    {key: 'disk_spill', label: 'Spill (disk)', type: 'bytes'},
    {key: 'input', label: 'Input Size / Records', type: 'composite', subKeys: ['input_bytes', 'input_records']},
    {key: 'output', label: 'Output Size / Records', type: 'composite', subKeys: ['output_bytes', 'output_records']},
    {
        key: 'shuffle_read',
        label: 'Shuffle Read Size / Records',
        type: 'composite',
        subKeys: ['shuffle_read_bytes', 'shuffle_read_records']
    },
    {
        key: 'shuffle_write', label: 'Shuffle Write Size / Records',
        type: 'composite',
        subKeys: ['shuffle_write_bytes', 'shuffle_write_records']
    },
    {key: 'shuffle_write_time', label: 'Shuffle Write Time', type: 'nanos'}
];

export const DEFAULT_METRICS = AVAILABLE_METRICS.map(m => m.key);
