CREATE OR REPLACE FUNCTION sca4dj_set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION sca4dj_set_updated_at() IS '在包含 updated_at 的可变记录更新时写入数据库当前时间';

CREATE TABLE sys_setting (
    owner_type VARCHAR(128) NOT NULL,
    owner_id VARCHAR(160) NOT NULL,
    setting_key VARCHAR(160) NOT NULL,
    schema_version INTEGER NOT NULL,
    has_value BOOLEAN NOT NULL DEFAULT TRUE,
    value_text TEXT,
    value_key_id VARCHAR(128),
    value_ciphertext BYTEA,
    encryption_context_version SMALLINT,
    synchronized_from_environment BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_type, owner_id, setting_key)
);

COMMENT ON TABLE sys_setting IS '按 owner 保存 Java Setting 声明的系统设置、个人偏好和后端逻辑设置';
COMMENT ON COLUMN sys_setting.owner_type IS '设置归属者使用的稳定类型；SYSTEM 表示 Subject.SYSTEM';
COMMENT ON COLUMN sys_setting.owner_id IS 'owner_type 内唯一的设置归属者标识';
COMMENT ON COLUMN sys_setting.setting_key IS 'Java Setting Bean 声明的全局稳定键';
COMMENT ON COLUMN sys_setting.schema_version IS '当前值使用的结构版本，必须与 Java Setting 定义一致';
COMMENT ON COLUMN sys_setting.has_value IS '是否保存 owner 显式覆盖值；false 表示保留版本的默认值 tombstone';
COMMENT ON COLUMN sys_setting.value_text IS '非敏感设置的编码文本';
COMMENT ON COLUMN sys_setting.value_key_id IS '敏感设置使用的 SecretKeyring 密钥标识';
COMMENT ON COLUMN sys_setting.value_ciphertext IS '敏感设置的密文';
COMMENT ON COLUMN sys_setting.encryption_context_version IS '密文绑定 owner 和设置键时使用的加密用途版本';
COMMENT ON COLUMN sys_setting.synchronized_from_environment IS 'SystemSetting 是否由指定初始化节点在每次启动时同步并禁止界面修改';
COMMENT ON COLUMN sys_setting.version IS '设置值更新使用的乐观并发版本';
COMMENT ON COLUMN sys_setting.created_at IS '设置创建时间';
COMMENT ON COLUMN sys_setting.updated_at IS '设置最近更新时间，由数据库触发器维护';

CREATE TABLE sys_dictionary (
    dictionary_key VARCHAR(160) PRIMARY KEY,
    label VARCHAR(160) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_dictionary_option (
    dictionary_key VARCHAR(160) NOT NULL,
    option_value VARCHAR(160) NOT NULL,
    label VARCHAR(160) NOT NULL,
    i18n JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (dictionary_key, option_value)
);

CREATE INDEX idx_sys_dictionary_option_order
    ON sys_dictionary_option (dictionary_key, sort_order, option_value);

CREATE UNIQUE INDEX uk_sys_dictionary_option_value_ci
    ON sys_dictionary_option (dictionary_key, UPPER(option_value));

COMMENT ON TABLE sys_dictionary IS '只保存数据库创建的动态字典，不保存 Java Dictionary 枚举';
COMMENT ON COLUMN sys_dictionary.dictionary_key IS '动态字典的全局稳定键';
COMMENT ON COLUMN sys_dictionary.label IS '动态字典的默认展示名称';
COMMENT ON COLUMN sys_dictionary.enabled IS '动态字典是否允许查询和维护选项';
COMMENT ON COLUMN sys_dictionary.sort_order IS '动态字典列表的升序排列值';
COMMENT ON COLUMN sys_dictionary.version IS '动态字典更新使用的乐观并发版本';
COMMENT ON COLUMN sys_dictionary.created_at IS '动态字典创建时间';
COMMENT ON COLUMN sys_dictionary.updated_at IS '动态字典最近更新时间，由数据库触发器维护';
COMMENT ON TABLE sys_dictionary_option IS '保存动态字典选项和可扩展代码字典的数据库选项';
COMMENT ON COLUMN sys_dictionary_option.dictionary_key IS '动态字典键或允许扩展的 Java Dictionary 键；不使用外键是因为代码字典不写入 sys_dictionary';
COMMENT ON COLUMN sys_dictionary_option.option_value IS '字典内唯一且供代码和接口使用的选项值';
COMMENT ON COLUMN sys_dictionary_option.label IS '选项缺少目标 Locale 翻译时使用的默认展示名称';
COMMENT ON COLUMN sys_dictionary_option.i18n IS '按 BCP 47 Locale 和 label 字段保存的多语言展示名称';
COMMENT ON COLUMN sys_dictionary_option.enabled IS '选项是否允许用于新的业务数据';
COMMENT ON COLUMN sys_dictionary_option.sort_order IS '同一字典内的升序排列值';
COMMENT ON COLUMN sys_dictionary_option.version IS '选项更新使用的乐观并发版本';
COMMENT ON COLUMN sys_dictionary_option.created_at IS '选项创建时间';
COMMENT ON COLUMN sys_dictionary_option.updated_at IS '选项最近更新时间，由数据库触发器维护';

CREATE TRIGGER set_sys_setting_updated_at
    BEFORE UPDATE ON sys_setting
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_sys_dictionary_updated_at
    BEFORE UPDATE ON sys_dictionary
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_sys_dictionary_option_updated_at
    BEFORE UPDATE ON sys_dictionary_option
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();
