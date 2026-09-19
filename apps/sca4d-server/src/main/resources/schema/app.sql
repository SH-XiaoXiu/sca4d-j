CREATE TABLE platform_user_profile (
    principal_id BIGINT PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    email VARCHAR(320),
    phone_number VARCHAR(32),
    avatar_file_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE platform_user_profile IS '记录示例 App 的 Platform 用户资料';
COMMENT ON COLUMN platform_user_profile.principal_id IS '资料所属 IAM Principal 标识';
COMMENT ON COLUMN platform_user_profile.display_name IS '示例 App 展示的用户名称';
COMMENT ON COLUMN platform_user_profile.email IS '可选联系邮箱';
COMMENT ON COLUMN platform_user_profile.phone_number IS '可选规范 E.164 手机号';
COMMENT ON COLUMN platform_user_profile.avatar_file_id IS '当前绑定的头像文件标识';
COMMENT ON COLUMN platform_user_profile.created_at IS '资料创建时间';
COMMENT ON COLUMN platform_user_profile.updated_at IS '资料最近更新时间';
COMMENT ON COLUMN platform_user_profile.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

CREATE INDEX idx_platform_user_profile_avatar_file
    ON platform_user_profile (avatar_file_id)
    WHERE avatar_file_id IS NOT NULL;

CREATE TABLE app_iot_product_profile (
    product_id BIGINT PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE app_iot_device_profile (
    device_id BIGINT PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE app_iot_device_creation_request (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_key_id VARCHAR(128),
    response_ciphertext BYTEA,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_iot_device_creation_expiry
    ON app_iot_device_creation_request (expires_at);

COMMENT ON TABLE app_iot_product_profile IS '保存 Sca4d Server 对 IoT Product 的启停状态';
COMMENT ON COLUMN app_iot_product_profile.product_id IS '状态所属 IoT Product 标识';
COMMENT ON COLUMN app_iot_product_profile.status IS 'Sca4d Server 是否允许该 Product 继续创建或管理 Device';
COMMENT ON COLUMN app_iot_product_profile.created_at IS 'Product 应用资料创建时间';
COMMENT ON COLUMN app_iot_product_profile.updated_at IS 'Product 启停状态最近更新时间，由数据库触发器维护';

COMMENT ON TABLE app_iot_device_profile IS '保存 Sca4d Server 对 IoT Device 的启停状态';
COMMENT ON COLUMN app_iot_device_profile.device_id IS '状态所属 IoT Device 标识';
COMMENT ON COLUMN app_iot_device_profile.status IS 'Sca4d Server 是否允许该 Device 认证和执行业务操作';
COMMENT ON COLUMN app_iot_device_profile.created_at IS 'Device 应用资料创建时间';
COMMENT ON COLUMN app_iot_device_profile.updated_at IS 'Device 启停状态最近更新时间，由数据库触发器维护';

COMMENT ON TABLE app_iot_device_creation_request IS '在十分钟窗口内保存加密的 Device 创建幂等结果';
COMMENT ON COLUMN app_iot_device_creation_request.idempotency_key IS '调用方在创建重试中保持不变的幂等键';
COMMENT ON COLUMN app_iot_device_creation_request.request_hash IS '规范化创建请求的 SHA-256 小写十六进制摘要';
COMMENT ON COLUMN app_iot_device_creation_request.status IS '创建请求当前为 PENDING 或已经保存响应的 COMPLETE';
COMMENT ON COLUMN app_iot_device_creation_request.response_key_id IS '加密创建响应使用的 SecretKeyring 密钥标识';
COMMENT ON COLUMN app_iot_device_creation_request.response_ciphertext IS '只允许在幂等窗口内重放的加密创建响应';
COMMENT ON COLUMN app_iot_device_creation_request.expires_at IS '幂等键和加密响应停止复用的时间';
COMMENT ON COLUMN app_iot_device_creation_request.created_at IS '首次登记 Device 创建请求的时间';
COMMENT ON COLUMN app_iot_device_creation_request.updated_at IS '请求状态最近更新时间，由数据库触发器维护';

CREATE TRIGGER set_platform_user_profile_updated_at
    BEFORE UPDATE ON platform_user_profile
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_app_iot_product_profile_updated_at
    BEFORE UPDATE ON app_iot_product_profile
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_app_iot_device_profile_updated_at
    BEFORE UPDATE ON app_iot_device_profile
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_app_iot_device_creation_request_updated_at
    BEFORE UPDATE ON app_iot_device_creation_request
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();
