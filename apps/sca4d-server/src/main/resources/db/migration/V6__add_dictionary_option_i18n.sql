ALTER TABLE sys_dictionary_option
    ADD COLUMN i18n JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN sys_dictionary_option.label IS
    '选项缺少目标 Locale 翻译时使用的默认展示名称';

COMMENT ON COLUMN sys_dictionary_option.i18n IS
    '按 BCP 47 Locale 和 label 字段保存的多语言展示名称';
