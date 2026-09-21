CREATE TABLE notification_email_template (
    id BIGINT PRIMARY KEY,
    notification_code VARCHAR(128) NOT NULL,
    locales JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_notification_email_template_code
    ON notification_email_template (notification_code) WHERE deleted = 0;

COMMENT ON TABLE notification_email_template IS '保存 Email 渠道使用的多语言主题、纯文本和 HTML 源码模板';
COMMENT ON COLUMN notification_email_template.locales IS 'Locale 到 subject、text、html 源码的 JSON object';
COMMENT ON COLUMN notification_email_template.version IS '模板并发修改使用的乐观版本';
COMMENT ON COLUMN notification_email_template.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

CREATE TRIGGER set_notification_email_template_updated_at
    BEFORE UPDATE ON notification_email_template
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TABLE notification_email_sender (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    host VARCHAR(253) NOT NULL,
    port INTEGER NOT NULL,
    security VARCHAR(16) NOT NULL,
    username VARCHAR(320),
    password_key_id VARCHAR(128),
    password_ciphertext BYTEA,
    from_address VARCHAR(320) NOT NULL,
    from_name VARCHAR(256),
    reply_to VARCHAR(320),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_notification_email_sender_name
    ON notification_email_sender (name) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_notification_email_sender_enabled
    ON notification_email_sender (enabled) WHERE enabled = TRUE AND deleted = 0;

COMMENT ON TABLE notification_email_sender IS '保存 Email 通知使用的 SMTP 配置和加密密码';
COMMENT ON COLUMN notification_email_sender.id IS 'Email Sender 全局标识';
COMMENT ON COLUMN notification_email_sender.name IS '管理端识别 Sender 使用的唯一名称';
COMMENT ON COLUMN notification_email_sender.host IS 'SMTP 服务器主机名';
COMMENT ON COLUMN notification_email_sender.port IS 'SMTP 服务器 TCP 端口';
COMMENT ON COLUMN notification_email_sender.security IS 'SMTP 连接不加密、升级 STARTTLS 或直接使用 TLS';
COMMENT ON COLUMN notification_email_sender.username IS 'SMTP 认证用户名，不认证时为空';
COMMENT ON COLUMN notification_email_sender.password_key_id IS '加密 SMTP 密码使用的 SecretKeyring 密钥标识';
COMMENT ON COLUMN notification_email_sender.password_ciphertext IS 'SecretKeyring 生成的完整密文，不向管理接口返回';
COMMENT ON COLUMN notification_email_sender.from_address IS '邮件 From Header 使用的邮箱地址';
COMMENT ON COLUMN notification_email_sender.from_name IS '邮件 From Header 使用的可选展示名称';
COMMENT ON COLUMN notification_email_sender.reply_to IS '邮件 Reply-To Header 使用的可选邮箱地址';
COMMENT ON COLUMN notification_email_sender.enabled IS '是否为当前唯一参与投递的 SMTP 配置';
COMMENT ON COLUMN notification_email_sender.created_at IS 'Sender 创建时间';
COMMENT ON COLUMN notification_email_sender.updated_at IS 'Sender 最近更新时间，由数据库触发器维护';
COMMENT ON COLUMN notification_email_sender.version IS 'Sender 并发修改使用的乐观版本';
COMMENT ON COLUMN notification_email_sender.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

CREATE TRIGGER set_notification_email_sender_updated_at
    BEFORE UPDATE ON notification_email_sender
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();
