CREATE TABLE audit_record (
    id BIGINT PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL,
    subject_id BIGINT NOT NULL,
    account_id BIGINT,
    principal_id BIGINT NOT NULL,
    operation VARCHAR(128) NOT NULL,
    target_type VARCHAR(128),
    target_id VARCHAR(128),
    result VARCHAR(32) NOT NULL,
    payload_type VARCHAR(128),
    payload_version INTEGER,
    payload_json JSONB,
    http_method VARCHAR(16),
    http_path VARCHAR(2048),
    client_ip VARCHAR(128),
    user_agent VARCHAR(512),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_record_request ON audit_record (request_id);
CREATE INDEX idx_audit_record_account_time ON audit_record (account_id, occurred_at DESC, id DESC)
    WHERE account_id IS NOT NULL;
CREATE INDEX idx_audit_record_principal_time ON audit_record (principal_id, occurred_at DESC, id DESC);
CREATE INDEX idx_audit_record_operation_time ON audit_record (operation, occurred_at DESC, id DESC);
CREATE INDEX idx_audit_record_target_time
    ON audit_record (target_type, target_id, occurred_at DESC, id DESC)
    WHERE target_type IS NOT NULL;

COMMENT ON TABLE audit_record IS '保存操作发生时的身份、目标 Resource、结果和已脱敏 payload';
COMMENT ON COLUMN audit_record.id IS '审计记录全局标识';
COMMENT ON COLUMN audit_record.request_id IS '产生审计记录的请求或调用标识';
COMMENT ON COLUMN audit_record.subject_id IS '操作发生时的 Subject 标识快照，不关联 IAM 外键';
COMMENT ON COLUMN audit_record.account_id IS '操作发生时可选的 Account 标识快照，不关联 IAM 外键';
COMMENT ON COLUMN audit_record.principal_id IS '操作发生时的 Principal 标识快照，不关联 IAM 外键';
COMMENT ON COLUMN audit_record.operation IS 'AuditRecord 中稳定的 Operation 标识';
COMMENT ON COLUMN audit_record.target_type IS '可选目标 Resource 类型';
COMMENT ON COLUMN audit_record.target_id IS '可选目标 Resource 标识';
COMMENT ON COLUMN audit_record.result IS '操作最终成功或失败的稳定结果编码';
COMMENT ON COLUMN audit_record.payload_type IS '审计 payload 使用的 Raw 或 Codec 类型编码';
COMMENT ON COLUMN audit_record.payload_version IS '对应 payload_type 的正整数编码版本';
COMMENT ON COLUMN audit_record.payload_json IS 'Raw 或 Codec 编码后完成脱敏和长度限制的 JSON';
COMMENT ON COLUMN audit_record.http_method IS '可选 HTTP 方法快照';
COMMENT ON COLUMN audit_record.http_path IS '可选且不含查询参数的 HTTP 请求路径快照';
COMMENT ON COLUMN audit_record.client_ip IS '可选直连客户端地址，不读取转发 Header';
COMMENT ON COLUMN audit_record.user_agent IS '可选且完成控制字符清理和长度限制的 User-Agent';
COMMENT ON COLUMN audit_record.occurred_at IS '被审计操作产生最终结果的时间';
