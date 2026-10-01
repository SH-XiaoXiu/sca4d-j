CREATE UNIQUE INDEX uk_iam_single_system ON iam_subject(subject_type) WHERE subject_type = 'SYSTEM';

CREATE TABLE iam_system_identity (
    code VARCHAR(128) PRIMARY KEY,
    principal_id BIGINT NOT NULL UNIQUE,
    subject_id BIGINT NOT NULL CHECK (subject_id = 1),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (code ~ '^[A-Z0-9_]+(:[A-Z0-9_]+)*$')
);

COMMENT ON TABLE iam_system_identity IS '整个业务系统的稳定领域身份；各实例共享编码和 Principal ID';
COMMENT ON COLUMN iam_system_identity.code IS '代码常量声明的系统身份编码';
COMMENT ON COLUMN iam_system_identity.principal_id IS '配置指定且禁止自动改写的全局 Principal ID';
COMMENT ON COLUMN iam_system_identity.subject_id IS '整个业务系统唯一的 SYSTEM Subject';
COMMENT ON COLUMN iam_subject.subject_type IS '接入者类型；SYSTEM 表示整个业务系统，其固定 ID 为 1';

ALTER TABLE audit_record ADD COLUMN instance_id VARCHAR(128);
COMMENT ON COLUMN audit_record.instance_id IS '实际执行实例标识，不是 IAM 身份；历史记录允许为空';
