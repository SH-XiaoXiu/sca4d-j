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

INSERT INTO notification_template
    (id, notification_code, content, enabled, created_at, updated_at, version, deleted)
SELECT template.id,
       template.notification_code,
       localized.content,
       template.enabled,
       template.created_at,
       template.updated_at,
       template.version,
       template.deleted
FROM notification_email_template template
CROSS JOIN LATERAL (
    SELECT jsonb_object_agg(
        locale.key,
        jsonb_build_object(
            'title', jsonb_build_object(
                'type', 'doc',
                'content', jsonb_build_array(jsonb_build_object(
                    'type', 'paragraph',
                    'content', jsonb_build_array(jsonb_build_object(
                        'type', 'templateText',
                        'attrs', jsonb_build_object('source', locale.value ->> 'subject')))))),
            'body', jsonb_build_object(
                'type', 'doc',
                'content', jsonb_build_array(
                    CASE
                        WHEN COALESCE(locale.value ->> 'html', '') <> ''
                            THEN jsonb_build_object(
                                'type', 'richHtml',
                                'attrs', jsonb_build_object('source', locale.value ->> 'html'))
                        ELSE jsonb_build_object(
                            'type', 'paragraph',
                            'content', jsonb_build_array(jsonb_build_object(
                                'type', 'templateText',
                                'attrs', jsonb_build_object('source', locale.value ->> 'text'))))
                    END)))) AS content
    FROM jsonb_each(template.i18n) locale
) localized;

DROP TABLE notification_email_template;

COMMENT ON TABLE notification_template IS '保存平台无关的多语言 Tiptap JSON 通知模板';
COMMENT ON COLUMN notification_template.notification_code IS 'NotificationType 声明的稳定通知编码';
COMMENT ON COLUMN notification_template.content IS 'Locale 到 title/body ProseMirror 文档的 JSON object';
COMMENT ON COLUMN notification_template.enabled IS '模板是否参与实际渠道渲染';

CREATE TRIGGER set_notification_template_updated_at
    BEFORE UPDATE ON notification_template
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

CREATE TRIGGER set_notification_sms_provider_updated_at
    BEFORE UPDATE ON notification_sms_provider
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();
CREATE TRIGGER set_notification_sms_binding_updated_at
    BEFORE UPDATE ON notification_sms_binding
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();
