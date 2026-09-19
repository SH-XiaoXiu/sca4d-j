ALTER TABLE sys_setting
    ADD COLUMN owner_type VARCHAR(128) NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN owner_id VARCHAR(160) NOT NULL DEFAULT '1',
    ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN has_value BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN encryption_context_version SMALLINT;

UPDATE sys_setting
SET encryption_context_version = 1
WHERE value_ciphertext IS NOT NULL;

UPDATE sys_setting
SET version = version + 1;

ALTER TABLE sys_setting
    ALTER COLUMN owner_type DROP DEFAULT,
    ALTER COLUMN owner_id DROP DEFAULT,
    ALTER COLUMN schema_version DROP DEFAULT,
    ALTER COLUMN version SET DEFAULT 1,
    DROP CONSTRAINT sys_setting_pkey,
    ADD PRIMARY KEY (owner_type, owner_id, setting_key);

INSERT INTO sys_setting (
    owner_type,
    owner_id,
    setting_key,
    schema_version,
    has_value,
    value_text,
    value_key_id,
    value_ciphertext,
    encryption_context_version,
    synchronized_from_environment,
    version,
    created_at,
    updated_at
)
SELECT 'account',
       factors.account_id::TEXT,
       'authentication.account-policy',
       1,
       TRUE,
       jsonb_build_object(
           'loginFactorCount', factors.login_factor_count,
           'sensitiveActionFactorCount', factors.sensitive_action_factor_count,
           'reusableAuthenticationMaximumAgeSeconds', factors.reusable_authentication_maximum_age_seconds,
           'methods', COALESCE(
               jsonb_agg(
                   jsonb_build_object(
                       'methodCode', methods.method_code,
                       'loginStartEnabled', methods.login_start_enabled,
                       'loginAdditionalEnabled', methods.login_additional_enabled,
                       'sensitiveActionEnabled', methods.sensitive_action_enabled
                   ) ORDER BY methods.method_code
               ) FILTER (WHERE methods.account_id IS NOT NULL),
               '[]'::JSONB
           )
       )::TEXT,
       NULL,
       NULL,
       NULL,
       FALSE,
       1,
       factors.created_at,
       GREATEST(factors.updated_at, COALESCE(MAX(methods.created_at), factors.updated_at))
FROM app_account_authentication_settings factors
LEFT JOIN app_account_authentication_method methods
       ON methods.account_id = factors.account_id
GROUP BY factors.account_id,
         factors.login_factor_count,
         factors.sensitive_action_factor_count,
         factors.reusable_authentication_maximum_age_seconds,
         factors.created_at,
         factors.updated_at;

DROP TABLE app_account_authentication_method;
DROP TABLE app_account_authentication_settings;

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
