CREATE TABLE notification_template (
    id BIGINT PRIMARY KEY,
    notification_code VARCHAR(128) NOT NULL,
    content JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_notification_template_code
    ON notification_template (notification_code) WHERE deleted = 0;

COMMENT ON TABLE notification_template IS '保存平台无关的多语言 Tiptap JSON 通知模板';
COMMENT ON COLUMN notification_template.notification_code IS 'NotificationType 声明的稳定通知编码';
COMMENT ON COLUMN notification_template.content IS 'Locale 到 title/body ProseMirror 文档的 JSON object';
COMMENT ON COLUMN notification_template.enabled IS '模板是否参与实际渠道渲染';
COMMENT ON COLUMN notification_template.version IS '模板并发修改使用的乐观版本';
COMMENT ON COLUMN notification_template.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

CREATE TRIGGER set_notification_template_updated_at
    BEFORE UPDATE ON notification_template
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();
