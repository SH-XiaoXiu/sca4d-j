ALTER TABLE iam_system_identity
    DROP CONSTRAINT IF EXISTS iam_system_identity_subject_id_check;

COMMENT ON COLUMN iam_system_identity.subject_id IS '整个业务系统唯一的 SYSTEM Subject；ID 由应用配置确定并由启动事务校验';
