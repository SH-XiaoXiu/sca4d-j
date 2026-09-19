UPDATE iot_event
SET data_json = (data_json - 'failure') || jsonb_build_object(
        'failureCode',
        CASE btrim(data_json ->> 'failure')
            WHEN '命令重试次数已耗尽' THEN 'COMMAND_RETRY_EXHAUSTED'
            WHEN '状态同步策略只适用于 desired' THEN 'RETRY_POLICY_MISMATCH'
            WHEN '设备报告执行失败' THEN 'DEVICE_REPORTED_FAILURE'
            WHEN '策略不允许重试' THEN 'RETRY_DISABLED'
            WHEN 'ACK_REJECTED' THEN 'DEVICE_REPORTED_FAILURE'
            ELSE CASE
                WHEN btrim(data_json ->> 'failure') ~ '^[0-9]{4}$'
                        OR btrim(data_json ->> 'failure') ~ '^[A-Z][A-Z0-9_]{0,127}$'
                    THEN btrim(data_json ->> 'failure')
                ELSE 'LEGACY_FAILURE'
            END
        END)
WHERE event_code = '$schedule.execution'
  AND data_json ? 'failure';
