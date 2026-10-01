CREATE TABLE notification_inbox_template (
    id BIGINT PRIMARY KEY,
    notification_code VARCHAR(128) NOT NULL,
    content JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_notification_inbox_template_code
    ON notification_inbox_template (notification_code) WHERE deleted = 0;

COMMENT ON TABLE notification_inbox_template IS '保存 Inbox 渠道使用的多语言 Tiptap JSON 模板';
COMMENT ON COLUMN notification_inbox_template.notification_code IS 'NotificationType 声明的稳定通知编码';
COMMENT ON COLUMN notification_inbox_template.content IS 'Locale 到 title/body ProseMirror 文档的 JSON object';
COMMENT ON COLUMN notification_inbox_template.enabled IS '模板是否参与 Inbox 投递';
COMMENT ON COLUMN notification_inbox_template.version IS '模板并发修改使用的乐观版本';
COMMENT ON COLUMN notification_inbox_template.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

CREATE TRIGGER set_notification_inbox_template_updated_at
    BEFORE UPDATE ON notification_inbox_template
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TABLE notification_inbox_message (
    id BIGINT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    notification_code VARCHAR(128) NOT NULL,
    language_tag VARCHAR(64) NOT NULL,
    title VARCHAR(998) NOT NULL,
    text_content TEXT NOT NULL,
    html_content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_notification_inbox_message_delivery
    ON notification_inbox_message (message_id, account_id);
CREATE INDEX idx_notification_inbox_message_account_page
    ON notification_inbox_message (account_id, id DESC);
CREATE INDEX idx_notification_inbox_message_unread
    ON notification_inbox_message (account_id, id DESC) WHERE read_at IS NULL;

COMMENT ON TABLE notification_inbox_message IS '保存发送给 Account 且不会随模板变化的站内信快照';
COMMENT ON COLUMN notification_inbox_message.message_id IS '原始 NotificationMessage 标识，用于消费幂等';
COMMENT ON COLUMN notification_inbox_message.account_id IS '站内信所属 Account 标识';
COMMENT ON COLUMN notification_inbox_message.notification_code IS '产生站内信的 NotificationType 编码';
COMMENT ON COLUMN notification_inbox_message.language_tag IS '渲染模板使用的 BCP 47 Locale';
COMMENT ON COLUMN notification_inbox_message.read_at IS '首次标记已读时间；为空表示未读';
