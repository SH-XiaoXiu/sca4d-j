-- 把已有数据库中的开放 Client 从 OpenAPI 协议命名迁移到平台开放能力命名。
DO $$
BEGIN
    IF to_regclass('openapi_client_operation') IS NOT NULL THEN
        ALTER TABLE openapi_client_operation
            RENAME TO open_client_operation;
    END IF;
    IF to_regclass('openapi_client') IS NOT NULL THEN
        ALTER TABLE openapi_client
            RENAME TO open_client;
    END IF;
    IF to_regclass('idx_openapi_client_operation_operation') IS NOT NULL THEN
        ALTER INDEX idx_openapi_client_operation_operation
            RENAME TO idx_open_client_operation_operation;
    END IF;
    IF to_regclass('open_client') IS NOT NULL
            AND EXISTS (
                SELECT 1
                FROM pg_trigger
                WHERE tgrelid = 'open_client'::regclass
                    AND tgname = 'set_openapi_client_updated_at') THEN
        ALTER TRIGGER set_openapi_client_updated_at
            ON open_client
            RENAME TO set_open_client_updated_at;
    END IF;
END
$$;

UPDATE iam_account
SET realm_key = 'open'
WHERE realm_key = 'openapi';

UPDATE iam_account_identifier
SET realm_key = 'open'
WHERE realm_key = 'openapi';

COMMENT ON TABLE open_client IS '保存第三方 Open Client 与普通 APPLICATION Account 的绑定';
COMMENT ON TABLE open_client_operation IS '保存 Client 已显式开放的 Operation 子集';
