CREATE TABLE open_client (
    client_id VARCHAR(128) PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE open_client_operation (
    client_id VARCHAR(128) NOT NULL,
    operation VARCHAR(160) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (client_id, operation)
);

CREATE INDEX idx_open_client_operation_operation
    ON open_client_operation (operation, client_id);

COMMENT ON TABLE open_client IS '保存第三方 Open Client 与普通 APPLICATION Account 的绑定';
COMMENT ON COLUMN open_client.client_id IS '第三方请求和 OAuth Client Credentials 使用的稳定 Client ID';
COMMENT ON COLUMN open_client.account_id IS 'Client 对应的 IAM APPLICATION Account 标识';
COMMENT ON COLUMN open_client.display_name IS '管理端显示的 Client 名称';
COMMENT ON COLUMN open_client.status IS 'ACTIVE 允许认证和调用，DISABLED 拒绝新的 Open 请求';
COMMENT ON COLUMN open_client.created_at IS 'Open Client 创建时间';
COMMENT ON COLUMN open_client.updated_at IS 'Open Client 最近更新时间，由数据库触发器维护';
COMMENT ON TABLE open_client_operation IS '保存 Client 已显式开放的 Operation 子集';
COMMENT ON COLUMN open_client_operation.client_id IS '已开放 Operation 的 Open Client 标识';
COMMENT ON COLUMN open_client_operation.operation IS 'OperationCatalog 中已经注册的 ACTION 编码';
COMMENT ON COLUMN open_client_operation.created_at IS 'Client Operation 关系创建时间';

CREATE TRIGGER set_open_client_updated_at
    BEFORE UPDATE ON open_client
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();
