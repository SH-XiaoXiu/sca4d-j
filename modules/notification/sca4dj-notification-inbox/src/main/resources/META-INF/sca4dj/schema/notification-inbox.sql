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
COMMENT ON COLUMN notification_inbox_message.message_id IS '原始 notification.requested 消息标识，用于消费幂等';
COMMENT ON COLUMN notification_inbox_message.account_id IS '站内信所属 Account 标识';
COMMENT ON COLUMN notification_inbox_message.notification_code IS '产生站内信的 NotificationType 编码';
COMMENT ON COLUMN notification_inbox_message.language_tag IS '渲染模板使用的 BCP 47 Locale';
COMMENT ON COLUMN notification_inbox_message.read_at IS '首次标记已读时间；为空表示未读';
