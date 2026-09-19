CREATE SCHEMA IF NOT EXISTS quartz;

-- quartz.qrtz_* 保持 Quartz 官方 PostgreSQL JDBC JobStore 表结构，包括表间外键。
CREATE TABLE quartz.qrtz_job_details (
    sched_name VARCHAR(120) NOT NULL,
    job_name VARCHAR(200) NOT NULL,
    job_group VARCHAR(200) NOT NULL,
    description VARCHAR(250),
    job_class_name VARCHAR(250) NOT NULL,
    is_durable BOOLEAN NOT NULL,
    is_nonconcurrent BOOLEAN NOT NULL,
    is_update_data BOOLEAN NOT NULL,
    requests_recovery BOOLEAN NOT NULL,
    job_data BYTEA,
    PRIMARY KEY (sched_name, job_name, job_group)
);

CREATE TABLE quartz.qrtz_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    job_name VARCHAR(200) NOT NULL,
    job_group VARCHAR(200) NOT NULL,
    description VARCHAR(250),
    next_fire_time BIGINT,
    prev_fire_time BIGINT,
    priority INTEGER,
    trigger_state VARCHAR(16) NOT NULL,
    trigger_type VARCHAR(8) NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT,
    calendar_name VARCHAR(200),
    misfire_instr SMALLINT,
    job_data BYTEA,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, job_name, job_group)
        REFERENCES quartz.qrtz_job_details (sched_name, job_name, job_group)
);

CREATE TABLE quartz.qrtz_simple_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    repeat_count BIGINT NOT NULL,
    repeat_interval BIGINT NOT NULL,
    times_triggered BIGINT NOT NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES quartz.qrtz_triggers (sched_name, trigger_name, trigger_group)
);

CREATE TABLE quartz.qrtz_cron_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    cron_expression VARCHAR(120) NOT NULL,
    time_zone_id VARCHAR(80),
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES quartz.qrtz_triggers (sched_name, trigger_name, trigger_group)
);

CREATE TABLE quartz.qrtz_simprop_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    str_prop_1 VARCHAR(512),
    str_prop_2 VARCHAR(512),
    str_prop_3 VARCHAR(512),
    int_prop_1 INTEGER,
    int_prop_2 INTEGER,
    long_prop_1 BIGINT,
    long_prop_2 BIGINT,
    dec_prop_1 NUMERIC(13, 4),
    dec_prop_2 NUMERIC(13, 4),
    bool_prop_1 BOOLEAN,
    bool_prop_2 BOOLEAN,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES quartz.qrtz_triggers (sched_name, trigger_name, trigger_group)
);

CREATE TABLE quartz.qrtz_blob_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    blob_data BYTEA,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES quartz.qrtz_triggers (sched_name, trigger_name, trigger_group)
);

CREATE TABLE quartz.qrtz_calendars (
    sched_name VARCHAR(120) NOT NULL,
    calendar_name VARCHAR(200) NOT NULL,
    calendar BYTEA NOT NULL,
    PRIMARY KEY (sched_name, calendar_name)
);

CREATE TABLE quartz.qrtz_paused_trigger_grps (
    sched_name VARCHAR(120) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    PRIMARY KEY (sched_name, trigger_group)
);

CREATE TABLE quartz.qrtz_fired_triggers (
    sched_name VARCHAR(120) NOT NULL,
    entry_id VARCHAR(95) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    instance_name VARCHAR(200) NOT NULL,
    fired_time BIGINT NOT NULL,
    sched_time BIGINT NOT NULL,
    priority INTEGER NOT NULL,
    state VARCHAR(16) NOT NULL,
    job_name VARCHAR(200),
    job_group VARCHAR(200),
    is_nonconcurrent BOOLEAN,
    requests_recovery BOOLEAN,
    PRIMARY KEY (sched_name, entry_id)
);

CREATE TABLE quartz.qrtz_scheduler_state (
    sched_name VARCHAR(120) NOT NULL,
    instance_name VARCHAR(200) NOT NULL,
    last_checkin_time BIGINT NOT NULL,
    checkin_interval BIGINT NOT NULL,
    PRIMARY KEY (sched_name, instance_name)
);

CREATE TABLE quartz.qrtz_locks (
    sched_name VARCHAR(120) NOT NULL,
    lock_name VARCHAR(40) NOT NULL,
    PRIMARY KEY (sched_name, lock_name)
);

CREATE INDEX idx_qrtz_j_req_recovery
    ON quartz.qrtz_job_details (sched_name, requests_recovery);
CREATE INDEX idx_qrtz_j_grp
    ON quartz.qrtz_job_details (sched_name, job_group);
CREATE INDEX idx_qrtz_t_j
    ON quartz.qrtz_triggers (sched_name, job_name, job_group);
CREATE INDEX idx_qrtz_t_jg
    ON quartz.qrtz_triggers (sched_name, job_group);
CREATE INDEX idx_qrtz_t_c
    ON quartz.qrtz_triggers (sched_name, calendar_name);
CREATE INDEX idx_qrtz_t_g
    ON quartz.qrtz_triggers (sched_name, trigger_group);
CREATE INDEX idx_qrtz_t_state
    ON quartz.qrtz_triggers (sched_name, trigger_state);
CREATE INDEX idx_qrtz_t_n_state
    ON quartz.qrtz_triggers (sched_name, trigger_name, trigger_group, trigger_state);
CREATE INDEX idx_qrtz_t_n_g_state
    ON quartz.qrtz_triggers (sched_name, trigger_group, trigger_state);
CREATE INDEX idx_qrtz_t_next_fire_time
    ON quartz.qrtz_triggers (sched_name, next_fire_time);
CREATE INDEX idx_qrtz_t_nft_st
    ON quartz.qrtz_triggers (sched_name, trigger_state, next_fire_time);
CREATE INDEX idx_qrtz_t_nft_misfire
    ON quartz.qrtz_triggers (sched_name, misfire_instr, next_fire_time);
CREATE INDEX idx_qrtz_t_nft_st_misfire
    ON quartz.qrtz_triggers (sched_name, misfire_instr, next_fire_time, trigger_state);
CREATE INDEX idx_qrtz_t_nft_st_misfire_grp
    ON quartz.qrtz_triggers (
        sched_name, misfire_instr, next_fire_time, trigger_group, trigger_state);
CREATE INDEX idx_qrtz_ft_trig_inst_name
    ON quartz.qrtz_fired_triggers (sched_name, instance_name);
CREATE INDEX idx_qrtz_ft_inst_job_req_rcvry
    ON quartz.qrtz_fired_triggers (sched_name, instance_name, requests_recovery);
CREATE INDEX idx_qrtz_ft_j_g
    ON quartz.qrtz_fired_triggers (sched_name, job_name, job_group);
CREATE INDEX idx_qrtz_ft_jg
    ON quartz.qrtz_fired_triggers (sched_name, job_group);
CREATE INDEX idx_qrtz_ft_t_g
    ON quartz.qrtz_fired_triggers (sched_name, trigger_name, trigger_group);
CREATE INDEX idx_qrtz_ft_tg
    ON quartz.qrtz_fired_triggers (sched_name, trigger_group);

COMMENT ON SCHEMA quartz IS '保存 Quartz JDBC JobStore 的集群调度状态';

COMMENT ON TABLE quartz.qrtz_job_details IS '保存 Quartz JDBC JobStore 的 JobDetail';
COMMENT ON COLUMN quartz.qrtz_job_details.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_job_details.job_name IS 'JobDetail 在组内的名称';
COMMENT ON COLUMN quartz.qrtz_job_details.job_group IS 'JobDetail 所属组名';
COMMENT ON COLUMN quartz.qrtz_job_details.description IS 'JobDetail 可选说明';
COMMENT ON COLUMN quartz.qrtz_job_details.job_class_name IS '执行 Job 的 Java 类名';
COMMENT ON COLUMN quartz.qrtz_job_details.is_durable IS '没有 Trigger 时是否保留 JobDetail';
COMMENT ON COLUMN quartz.qrtz_job_details.is_nonconcurrent IS '同一 JobDetail 是否禁止并发执行';
COMMENT ON COLUMN quartz.qrtz_job_details.is_update_data IS '执行完成后是否保存修改后的 JobDataMap';
COMMENT ON COLUMN quartz.qrtz_job_details.requests_recovery IS 'Scheduler 故障恢复后是否重新执行该 Job';
COMMENT ON COLUMN quartz.qrtz_job_details.job_data IS 'Quartz 序列化的 JobDataMap';

COMMENT ON TABLE quartz.qrtz_triggers IS '保存 Quartz Trigger 的公共字段和当前调度状态';
COMMENT ON COLUMN quartz.qrtz_triggers.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_triggers.trigger_name IS 'Trigger 在组内的名称';
COMMENT ON COLUMN quartz.qrtz_triggers.trigger_group IS 'Trigger 所属组名';
COMMENT ON COLUMN quartz.qrtz_triggers.job_name IS 'Trigger 关联的 JobDetail 名称';
COMMENT ON COLUMN quartz.qrtz_triggers.job_group IS 'Trigger 关联的 JobDetail 组名';
COMMENT ON COLUMN quartz.qrtz_triggers.description IS 'Trigger 可选说明';
COMMENT ON COLUMN quartz.qrtz_triggers.next_fire_time IS '下一次触发的 Unix 毫秒时间';
COMMENT ON COLUMN quartz.qrtz_triggers.prev_fire_time IS '上一次触发的 Unix 毫秒时间';
COMMENT ON COLUMN quartz.qrtz_triggers.priority IS '同一时刻多个 Trigger 的执行优先级';
COMMENT ON COLUMN quartz.qrtz_triggers.trigger_state IS 'Quartz JobStore 使用的 Trigger 状态';
COMMENT ON COLUMN quartz.qrtz_triggers.trigger_type IS 'SIMPLE、CRON、BLOB 或 CAL_INT 等 Trigger 类型';
COMMENT ON COLUMN quartz.qrtz_triggers.start_time IS 'Trigger 生效的 Unix 毫秒时间';
COMMENT ON COLUMN quartz.qrtz_triggers.end_time IS 'Trigger 停止生效的可选 Unix 毫秒时间';
COMMENT ON COLUMN quartz.qrtz_triggers.calendar_name IS 'Trigger 使用的可选 Quartz Calendar 名称';
COMMENT ON COLUMN quartz.qrtz_triggers.misfire_instr IS 'Quartz 定义的 misfire 处理指令';
COMMENT ON COLUMN quartz.qrtz_triggers.job_data IS 'Trigger 自己附加的序列化 JobDataMap';

COMMENT ON TABLE quartz.qrtz_simple_triggers IS '保存 SimpleTrigger 的重复次数和固定间隔';
COMMENT ON COLUMN quartz.qrtz_simple_triggers.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_simple_triggers.trigger_name IS '对应 Trigger 名称';
COMMENT ON COLUMN quartz.qrtz_simple_triggers.trigger_group IS '对应 Trigger 组名';
COMMENT ON COLUMN quartz.qrtz_simple_triggers.repeat_count IS '首次执行后允许重复的次数';
COMMENT ON COLUMN quartz.qrtz_simple_triggers.repeat_interval IS '相邻两次触发之间的毫秒数';
COMMENT ON COLUMN quartz.qrtz_simple_triggers.times_triggered IS 'SimpleTrigger 已经触发的次数';

COMMENT ON TABLE quartz.qrtz_cron_triggers IS '保存 CronTrigger 表达式和解释时区';
COMMENT ON COLUMN quartz.qrtz_cron_triggers.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_cron_triggers.trigger_name IS '对应 Trigger 名称';
COMMENT ON COLUMN quartz.qrtz_cron_triggers.trigger_group IS '对应 Trigger 组名';
COMMENT ON COLUMN quartz.qrtz_cron_triggers.cron_expression IS 'Quartz Cron 表达式';
COMMENT ON COLUMN quartz.qrtz_cron_triggers.time_zone_id IS '解释 Cron 表达式的可选时区标识';

COMMENT ON TABLE quartz.qrtz_simprop_triggers IS '保存 CalendarIntervalTrigger 和 DailyTimeIntervalTrigger 的类型化属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.trigger_name IS '对应 Trigger 名称';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.trigger_group IS '对应 Trigger 组名';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.str_prop_1 IS 'Quartz TriggerPersistenceDelegate 定义的第一个字符串属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.str_prop_2 IS 'Quartz TriggerPersistenceDelegate 定义的第二个字符串属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.str_prop_3 IS 'Quartz TriggerPersistenceDelegate 定义的第三个字符串属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.int_prop_1 IS 'Quartz TriggerPersistenceDelegate 定义的第一个整数属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.int_prop_2 IS 'Quartz TriggerPersistenceDelegate 定义的第二个整数属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.long_prop_1 IS 'Quartz TriggerPersistenceDelegate 定义的第一个长整数属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.long_prop_2 IS 'Quartz TriggerPersistenceDelegate 定义的第二个长整数属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.dec_prop_1 IS 'Quartz TriggerPersistenceDelegate 定义的第一个十进制属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.dec_prop_2 IS 'Quartz TriggerPersistenceDelegate 定义的第二个十进制属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.bool_prop_1 IS 'Quartz TriggerPersistenceDelegate 定义的第一个布尔属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.bool_prop_2 IS 'Quartz TriggerPersistenceDelegate 定义的第二个布尔属性';

COMMENT ON TABLE quartz.qrtz_blob_triggers IS '保存无法映射到类型化列的序列化 Trigger 数据';
COMMENT ON COLUMN quartz.qrtz_blob_triggers.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_blob_triggers.trigger_name IS '对应 Trigger 名称';
COMMENT ON COLUMN quartz.qrtz_blob_triggers.trigger_group IS '对应 Trigger 组名';
COMMENT ON COLUMN quartz.qrtz_blob_triggers.blob_data IS 'Quartz 序列化的 Trigger 属性';

COMMENT ON TABLE quartz.qrtz_calendars IS '保存 Quartz Calendar 名称和序列化定义';
COMMENT ON COLUMN quartz.qrtz_calendars.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_calendars.calendar_name IS 'Scheduler 内唯一的 Calendar 名称';
COMMENT ON COLUMN quartz.qrtz_calendars.calendar IS 'Quartz 序列化的 Calendar 定义';

COMMENT ON TABLE quartz.qrtz_paused_trigger_grps IS '记录 Quartz Scheduler 中整体暂停的 Trigger 组';
COMMENT ON COLUMN quartz.qrtz_paused_trigger_grps.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_paused_trigger_grps.trigger_group IS '当前整体暂停的 Trigger 组名';

COMMENT ON TABLE quartz.qrtz_fired_triggers IS '保存 Quartz 集群已经领取或正在执行的 Trigger 实例';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.entry_id IS '本次 Trigger 执行实例标识';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.trigger_name IS '被触发的 Trigger 名称';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.trigger_group IS '被触发的 Trigger 组名';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.instance_name IS '领取 Trigger 的 Scheduler 实例名';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.fired_time IS '实际触发的 Unix 毫秒时间';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.sched_time IS '原定触发的 Unix 毫秒时间';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.priority IS '本次 Trigger 执行优先级';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.state IS '本次 Trigger 执行的 JobStore 状态';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.job_name IS '本次执行关联的可选 JobDetail 名称';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.job_group IS '本次执行关联的可选 JobDetail 组名';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.is_nonconcurrent IS '本次 JobDetail 是否禁止并发执行';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.requests_recovery IS 'Scheduler 故障后本次执行是否请求恢复';

COMMENT ON TABLE quartz.qrtz_scheduler_state IS '保存 Quartz 集群各 Scheduler 实例的最近一次心跳';
COMMENT ON COLUMN quartz.qrtz_scheduler_state.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_scheduler_state.instance_name IS '集群内唯一的 Scheduler 实例名';
COMMENT ON COLUMN quartz.qrtz_scheduler_state.last_checkin_time IS '实例最近心跳的 Unix 毫秒时间';
COMMENT ON COLUMN quartz.qrtz_scheduler_state.checkin_interval IS '实例声明的心跳间隔毫秒数';

COMMENT ON TABLE quartz.qrtz_locks IS '保存 Quartz JDBC JobStore 使用的逻辑锁名称';
COMMENT ON COLUMN quartz.qrtz_locks.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_locks.lock_name IS 'Quartz JobStore 竞争的逻辑锁名称';
