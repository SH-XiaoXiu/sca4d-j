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
COMMENT ON COLUMN notification_sms_provider.id IS 'SMS Provider 全局标识';
COMMENT ON COLUMN notification_sms_provider.code IS 'Provider 在应用内唯一的稳定编码';
COMMENT ON COLUMN notification_sms_provider.provider_type IS '短信厂商适配器类型编码';
COMMENT ON COLUMN notification_sms_provider.credential_ref IS '部署配置中 Provider 凭据集合的稳定引用';
COMMENT ON COLUMN notification_sms_provider.priority IS '同一路由选择 Provider 时使用的优先级，数值越小越优先';
COMMENT ON COLUMN notification_sms_provider.enabled IS 'Provider 是否参与短信投递';
COMMENT ON COLUMN notification_sms_provider.created_at IS 'SMS Provider 创建时间';
COMMENT ON COLUMN notification_sms_provider.updated_at IS 'SMS Provider 最近更新时间，由数据库触发器维护';
COMMENT ON COLUMN notification_sms_provider.version IS 'SMS Provider 并发修改使用的乐观版本';
COMMENT ON COLUMN notification_sms_provider.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

COMMENT ON COLUMN notification_sms_binding.id IS '短信模板绑定全局标识';
COMMENT ON COLUMN notification_sms_binding.provider_id IS '关联的 SMS Provider 标识';
COMMENT ON COLUMN notification_sms_binding.notification_code IS '对应统一通知类型的稳定编码';
COMMENT ON COLUMN notification_sms_binding.route IS '具体应用声明的 SMS 路由编码，公共模块不预设地区分组';
COMMENT ON COLUMN notification_sms_binding.language_tag IS '模板使用的 BCP 47 Locale';
COMMENT ON COLUMN notification_sms_binding.sign_name IS '短信厂商要求的签名名称';
COMMENT ON COLUMN notification_sms_binding.provider_template_code IS '短信厂商审核通过的模板编码';
COMMENT ON TABLE notification_sms_binding IS '把统一通知变量绑定到 Provider 已审核模板，不保存业务模板正文';
COMMENT ON COLUMN notification_sms_binding.parameter_mapping IS 'Provider 参数名到统一通知变量名的 JSON object';
COMMENT ON COLUMN notification_sms_binding.enabled IS '短信模板绑定是否参与投递';
COMMENT ON COLUMN notification_sms_binding.created_at IS '短信模板绑定创建时间';
COMMENT ON COLUMN notification_sms_binding.updated_at IS '短信模板绑定最近更新时间，由数据库触发器维护';
COMMENT ON COLUMN notification_sms_binding.version IS '短信模板绑定并发修改使用的乐观版本';
COMMENT ON COLUMN notification_sms_binding.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

COMMENT ON TABLE notification_sms_delivery IS '记录一个原始通知消息在各 SMS Provider 的 API 提交结果';
COMMENT ON COLUMN notification_sms_delivery.id IS '短信投递记录全局标识';
COMMENT ON COLUMN notification_sms_delivery.message_id IS '原始 NotificationMessage 标识';
COMMENT ON COLUMN notification_sms_delivery.provider_id IS '实际提交使用的 SMS Provider 标识';
COMMENT ON COLUMN notification_sms_delivery.route IS '本次投递使用的应用短信路由编码';
COMMENT ON COLUMN notification_sms_delivery.recipient_hash IS '收件手机号的不可逆摘要，用于幂等和审计关联';
COMMENT ON COLUMN notification_sms_delivery.recipient_masked IS '收件手机号的脱敏展示值';
COMMENT ON COLUMN notification_sms_delivery.status IS '短信提交、成功或失败状态';
COMMENT ON COLUMN notification_sms_delivery.request_id IS '短信厂商请求标识';
COMMENT ON COLUMN notification_sms_delivery.biz_id IS '短信厂商业务流水标识';
COMMENT ON COLUMN notification_sms_delivery.failure_code IS '短信提交失败时的公开错误码';
COMMENT ON COLUMN notification_sms_delivery.created_at IS '短信投递记录创建时间';
COMMENT ON COLUMN notification_sms_delivery.completed_at IS '短信投递进入最终状态的时间';

CREATE TRIGGER set_notification_sms_provider_updated_at
    BEFORE UPDATE ON notification_sms_provider
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();
CREATE TRIGGER set_notification_sms_binding_updated_at
    BEFORE UPDATE ON notification_sms_binding
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();
