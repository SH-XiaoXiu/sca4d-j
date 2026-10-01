-- 发布时必须先停止全部旧实例；保留旧 Redis 与 MQ 数据，不能混用两套物理资源名称。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM messaging_outbox WHERE status <> 'SENT') THEN
        RAISE EXCEPTION '迁移名称前必须由旧版本发送完 Outbox 的未完成消息，并排空旧 RabbitMQ 队列';
    END IF;
    IF EXISTS (
        SELECT 1 FROM iam_principal
        GROUP BY upper(replace(replace(principal_type, '/', '.'), ':', '.'))
        HAVING count(DISTINCT principal_type) > 1
    ) THEN
        RAISE EXCEPTION 'Principal 类型归一化后重名，请人工确认类型关系并迁移，禁止自动合并';
    END IF;
    IF EXISTS (
        SELECT 1 FROM iam_principal
        WHERE upper(replace(replace(principal_type, '/', '.'), ':', '.'))
            !~ '^[A-Z0-9_-]+(\.[A-Z0-9_-]+)*$'
    ) THEN
        RAISE EXCEPTION 'Principal 类型包含新 Code 规则不接受的字符，请人工迁移后重试';
    END IF;
    IF EXISTS (
        SELECT 1 FROM job.job_run
        WHERE upper(replace(runtime_namespace, '-', '_')) !~ '^[A-Z0-9_]+$'
    ) THEN
        RAISE EXCEPTION 'Job 的旧 App 名无法归一化，请先人工迁移为合法 App 名';
    END IF;
    IF EXISTS (
        SELECT 1 FROM job.job_run
        GROUP BY upper(replace(runtime_namespace, '-', '_'))
        HAVING count(DISTINCT runtime_namespace) > 1
    ) THEN
        RAISE EXCEPTION '多个旧 App 名归一化后重名，请人工迁移资源归属后重试';
    END IF;
    IF EXISTS (
        SELECT 1 FROM iot_product_access_method
        GROUP BY product_id, upper(replace(replace(method_code, '/', ':'), '.', ':')) HAVING count(*) > 1
    ) OR EXISTS (
        SELECT 1 FROM iot_device_access_method
        GROUP BY device_id, upper(replace(replace(method_code, '/', ':'), '.', ':')) HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Device 接入方式归一化后重名，请人工确认并迁移，禁止自动合并';
    END IF;
END
$$;

UPDATE iam_principal SET principal_type = upper(replace(replace(principal_type, '/', '.'), ':', '.'));
UPDATE job.job_run SET runtime_namespace = upper(replace(runtime_namespace, '-', '_'));
ALTER TABLE job.job_run RENAME COLUMN runtime_namespace TO app_name;
COMMENT ON COLUMN job.job_run.app_name IS '全局 App 隔离名称，同一 App 的所有实例共享';
UPDATE iot_product_access_method SET method_code = upper(replace(replace(method_code, '/', ':'), '.', ':'));
UPDATE iot_device_access_method SET method_code = upper(replace(replace(method_code, '/', ':'), '.', ':'));

-- Quartz 保留官方外键。事务内临时延迟检查，全部表修改成功后立即验证并恢复原有属性。
CREATE TEMP TABLE sca4dj_quartz_names (old_name TEXT PRIMARY KEY, new_name TEXT NOT NULL UNIQUE) ON COMMIT DROP;
CREATE TEMP TABLE sca4dj_quartz_constraints (
    relation REGCLASS NOT NULL, constraint_name TEXT NOT NULL,
    was_deferrable BOOLEAN NOT NULL, was_deferred BOOLEAN NOT NULL
) ON COMMIT DROP;

DO $$
DECLARE
    table_name TEXT;
    constraint_row RECORD;
    names TEXT[] := ARRAY['qrtz_job_details', 'qrtz_triggers', 'qrtz_simple_triggers',
        'qrtz_cron_triggers', 'qrtz_simprop_triggers', 'qrtz_blob_triggers', 'qrtz_calendars',
        'qrtz_paused_trigger_grps', 'qrtz_fired_triggers', 'qrtz_scheduler_state', 'qrtz_locks'];
BEGIN
    FOREACH table_name IN ARRAY names LOOP
        EXECUTE format(
            'INSERT INTO sca4dj_quartz_names SELECT DISTINCT sched_name,
                upper(replace(left(sched_name, length(sched_name) - length('':sca4dj:scheduling:quartz'')), ''-'', ''_''))
                    || '':SCHEDULING:QUARTZ'' FROM quartz.%I
                WHERE sched_name LIKE ''%%:sca4dj:scheduling:quartz'' ON CONFLICT (old_name) DO NOTHING', table_name);
    END LOOP;
    IF EXISTS (SELECT 1 FROM sca4dj_quartz_names WHERE new_name !~ '^[A-Z0-9_]+:SCHEDULING:QUARTZ$' OR length(new_name) > 120) THEN
        RAISE EXCEPTION 'Quartz 的旧 App 名无法归一化或超过长度限制，请人工迁移后重试';
    END IF;
    FOREACH table_name IN ARRAY names LOOP
        EXECUTE format('SELECT EXISTS (SELECT 1 FROM quartz.%I current_name
            JOIN sca4dj_quartz_names names ON names.new_name = current_name.sched_name)', table_name)
            INTO STRICT constraint_row;
        IF constraint_row.exists THEN
            RAISE EXCEPTION 'Quartz 目标名称已经存在，请人工确认资源归属，禁止自动合并';
        END IF;
    END LOOP;
    INSERT INTO sca4dj_quartz_constraints
    SELECT conrelid, conname, condeferrable, condeferred FROM pg_constraint
    WHERE contype = 'f' AND connamespace = 'quartz'::regnamespace;
    FOR constraint_row IN SELECT * FROM sca4dj_quartz_constraints LOOP
        EXECUTE format('ALTER TABLE %s ALTER CONSTRAINT %I DEFERRABLE INITIALLY DEFERRED',
            constraint_row.relation, constraint_row.constraint_name);
    END LOOP;
    SET CONSTRAINTS ALL DEFERRED;
    FOREACH table_name IN ARRAY names LOOP
        EXECUTE format('UPDATE quartz.%I target SET sched_name = names.new_name
            FROM sca4dj_quartz_names names WHERE target.sched_name = names.old_name', table_name);
    END LOOP;
    SET CONSTRAINTS ALL IMMEDIATE;
    FOR constraint_row IN SELECT * FROM sca4dj_quartz_constraints LOOP
        EXECUTE format('ALTER TABLE %s ALTER CONSTRAINT %I %s', constraint_row.relation,
            constraint_row.constraint_name,
            CASE WHEN NOT constraint_row.was_deferrable THEN 'NOT DEFERRABLE'
                 WHEN constraint_row.was_deferred THEN 'DEFERRABLE INITIALLY DEFERRED'
                 ELSE 'DEFERRABLE INITIALLY IMMEDIATE' END);
    END LOOP;
EXCEPTION WHEN unique_violation THEN
    RAISE EXCEPTION 'Quartz 名称归一化后重名，请人工确认并迁移资源归属，禁止自动合并';
END
$$;
