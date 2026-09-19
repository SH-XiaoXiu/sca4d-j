CREATE TABLE notification_sms_provider (
    id BIGINT PRIMARY KEY,
    code VARCHAR(128) NOT NULL,
    provider_type VARCHAR(64) NOT NULL,
    credential_ref VARCHAR(128) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_notification_sms_provider_code
    ON notification_sms_provider (code) WHERE deleted = 0;

CREATE TABLE notification_sms_binding (
    id BIGINT PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    notification_code VARCHAR(128) NOT NULL,
    route VARCHAR(64) NOT NULL,
    language_tag VARCHAR(64) NOT NULL,
    sign_name VARCHAR(128) NOT NULL,
    provider_template_code VARCHAR(128) NOT NULL,
    parameter_mapping JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_notification_sms_binding_parameter_mapping_object
        CHECK (jsonb_typeof(parameter_mapping) = 'object')
);

CREATE UNIQUE INDEX uk_notification_sms_binding_route
    ON notification_sms_binding (provider_id, notification_code, route, language_tag)
    WHERE deleted = 0;

CREATE TABLE notification_sms_delivery (
    id BIGINT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    route VARCHAR(64) NOT NULL,
    recipient_hash VARCHAR(64) NOT NULL,
    recipient_masked VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    request_id VARCHAR(128),
    biz_id VARCHAR(128),
    failure_code VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uk_notification_sms_delivery_attempt
    ON notification_sms_delivery (message_id, provider_id);

COMMENT ON TABLE notification_sms_provider IS '保存可由 App 策略选择的 SMS Provider 实例，不保存密钥';
COMMENT ON COLUMN notification_sms_provider.credential_ref IS '部署配置中 Provider 凭据集合的稳定引用';
COMMENT ON COLUMN notification_sms_binding.route IS '具体应用声明的 SMS 路由编码，公共模块不预设地区分组';
COMMENT ON TABLE notification_sms_binding IS '把统一通知变量绑定到 Provider 已审核模板，不保存业务模板正文';
COMMENT ON COLUMN notification_sms_binding.parameter_mapping IS 'Provider 参数名到统一通知变量名的 JSON object';
COMMENT ON TABLE notification_sms_delivery IS '记录一个原始通知消息在各 SMS Provider 的 API 提交结果';

CREATE TRIGGER set_notification_sms_provider_updated_at
    BEFORE UPDATE ON notification_sms_provider
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();
CREATE TRIGGER set_notification_sms_binding_updated_at
    BEFORE UPDATE ON notification_sms_binding
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();
