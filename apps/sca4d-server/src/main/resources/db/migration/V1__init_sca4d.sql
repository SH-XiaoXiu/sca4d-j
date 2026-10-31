-- 此文件由 initFlyway 根据 App 运行模块生成。
-- 生成后由 App 维护，之后的数据库变化请新增 V2、V3 等迁移。

-- 组件：project :core:sca4dj-core-spring
-- 结构：dictionary-settings.sql
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

-- 组件：project :modules:audit:sca4dj-audit
-- 结构：audit.sql
CREATE TABLE audit_record (
    id BIGINT PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL,
    subject_id BIGINT NOT NULL,
    account_id BIGINT,
    principal_id BIGINT NOT NULL,
    operation VARCHAR(128) NOT NULL,
    target_type VARCHAR(128),
    target_id VARCHAR(128),
    result VARCHAR(32) NOT NULL,
    payload_type VARCHAR(128),
    payload_version INTEGER,
    payload_json JSONB,
    http_method VARCHAR(16),
    http_path VARCHAR(2048),
    client_ip VARCHAR(128),
    user_agent VARCHAR(512),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    instance_id VARCHAR(128)
);

CREATE INDEX idx_audit_record_request ON audit_record (request_id);
CREATE INDEX idx_audit_record_account_time ON audit_record (account_id, occurred_at DESC, id DESC)
    WHERE account_id IS NOT NULL;
CREATE INDEX idx_audit_record_principal_time ON audit_record (principal_id, occurred_at DESC, id DESC);
CREATE INDEX idx_audit_record_operation_time ON audit_record (operation, occurred_at DESC, id DESC);
CREATE INDEX idx_audit_record_target_time
    ON audit_record (target_type, target_id, occurred_at DESC, id DESC)
    WHERE target_type IS NOT NULL;

COMMENT ON TABLE audit_record IS '保存操作发生时的身份、目标 Resource、结果和已脱敏 payload';
COMMENT ON COLUMN audit_record.id IS '审计记录全局标识';
COMMENT ON COLUMN audit_record.request_id IS '产生审计记录的请求或调用标识';
COMMENT ON COLUMN audit_record.subject_id IS '操作发生时的 Subject 标识快照，不关联 IAM 外键';
COMMENT ON COLUMN audit_record.account_id IS '操作发生时可选的 Account 标识快照，不关联 IAM 外键';
COMMENT ON COLUMN audit_record.principal_id IS '操作发生时的 Principal 标识快照，不关联 IAM 外键';
COMMENT ON COLUMN audit_record.operation IS 'AuditRecord 中稳定的 Operation 标识';
COMMENT ON COLUMN audit_record.target_type IS '可选目标 Resource 类型';
COMMENT ON COLUMN audit_record.target_id IS '可选目标 Resource 标识';
COMMENT ON COLUMN audit_record.result IS '操作最终成功或失败的稳定结果编码';
COMMENT ON COLUMN audit_record.payload_type IS '审计 payload 使用的 Raw 或 Codec 类型编码';
COMMENT ON COLUMN audit_record.payload_version IS '对应 payload_type 的正整数编码版本';
COMMENT ON COLUMN audit_record.payload_json IS 'Raw 或 Codec 编码后完成脱敏和长度限制的 JSON';
COMMENT ON COLUMN audit_record.http_method IS '可选 HTTP 方法快照';
COMMENT ON COLUMN audit_record.http_path IS '可选且不含查询参数的 HTTP 请求路径快照';
COMMENT ON COLUMN audit_record.client_ip IS '可选直连客户端地址，不读取转发 Header';
COMMENT ON COLUMN audit_record.user_agent IS '可选且完成控制字符清理和长度限制的 User-Agent';
COMMENT ON COLUMN audit_record.occurred_at IS '被审计操作产生最终结果的时间';
COMMENT ON COLUMN audit_record.instance_id IS '实际执行实例标识，不是 IAM 身份；历史记录允许为空';

-- 组件：project :modules:auth:sca4dj-auth-federation
-- 结构：auth-federation.sql
CREATE TABLE auth_federated_provider (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    protocol VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
    login_page_visible BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0,
    unlinked_identity_action VARCHAR(32) NOT NULL DEFAULT 'REJECT',
    auto_link_verified_email BOOLEAN NOT NULL DEFAULT FALSE,
    i18n JSONB NOT NULL,
    icon_reference VARCHAR(512),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_auth_federated_provider_code ON auth_federated_provider (code) WHERE deleted = 0;

CREATE TABLE auth_federated_provider_configuration (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    issuer VARCHAR(512),
    client_id VARCHAR(512),
    client_secret_key_id VARCHAR(128),
    client_secret_ciphertext BYTEA,
    authorization_endpoint VARCHAR(2048),
    token_endpoint VARCHAR(2048),
    userinfo_endpoint VARCHAR(2048),
    jwks_endpoint VARCHAR(2048),
    client_authentication VARCHAR(32) NOT NULL DEFAULT 'CLIENT_SECRET_POST',
    scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
    profile_mapping JSONB NOT NULL DEFAULT '{}'::jsonb,
    authorization_parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_auth_federated_provider_configuration ON auth_federated_provider_configuration (provider_id) WHERE deleted = 0;

CREATE TABLE auth_federated_identity (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    issuer VARCHAR(512),
    subject VARCHAR(512) NOT NULL,
    display_name VARCHAR(256),
    email VARCHAR(320),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    avatar_url VARCHAR(2048),
    version BIGINT NOT NULL DEFAULT 0,
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_auth_federated_identity_subject ON auth_federated_identity (provider_id, COALESCE(issuer, ''), subject) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_auth_federated_identity_account_provider ON auth_federated_identity (provider_id, account_id) WHERE deleted = 0;

COMMENT ON TABLE auth_federated_provider IS 'Platform 范围内可配置的 OIDC/OAuth2 第三方登录 Provider';
COMMENT ON COLUMN auth_federated_provider.id IS '外部登录 Provider 数据库标识';
COMMENT ON COLUMN auth_federated_provider.code IS '配置、路由和接口使用的稳定 Provider 编码';
COMMENT ON COLUMN auth_federated_provider.protocol IS 'Provider 使用 OIDC 还是 OAuth2';
COMMENT ON COLUMN auth_federated_provider.status IS 'Provider 当前是否允许开始外部登录';
COMMENT ON COLUMN auth_federated_provider.login_page_visible IS 'Provider 是否显示在登录页的可选入口中';
COMMENT ON COLUMN auth_federated_provider.display_order IS '登录页 Provider 的升序排列值';
COMMENT ON COLUMN auth_federated_provider.unlinked_identity_action IS '外部身份没有本地绑定时拒绝登录或即时创建 Account';
COMMENT ON COLUMN auth_federated_provider.auto_link_verified_email IS '是否允许用 Provider 已验证邮箱自动关联本地 Account';
COMMENT ON COLUMN auth_federated_provider.i18n IS '按 Locale 保存 Provider 展示名称和说明';
COMMENT ON COLUMN auth_federated_provider.icon_reference IS '可选的 Provider 图标资源引用';
COMMENT ON COLUMN auth_federated_provider.version IS 'Provider 配置和状态并发修改使用的乐观版本';
COMMENT ON COLUMN auth_federated_provider.created_at IS 'Provider 创建时间';
COMMENT ON COLUMN auth_federated_provider.updated_at IS 'Provider 最近更新时间，由数据库触发器维护';
COMMENT ON COLUMN auth_federated_provider.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

COMMENT ON TABLE auth_federated_provider_configuration IS '外部 Provider 协议端点、作用域、映射和加密 Client Secret';
COMMENT ON COLUMN auth_federated_provider_configuration.id IS 'Provider 配置版本数据库标识';
COMMENT ON COLUMN auth_federated_provider_configuration.provider_id IS '配置所属 Provider 标识';
COMMENT ON COLUMN auth_federated_provider_configuration.issuer IS 'OIDC Issuer，OAuth2 Provider 可为空';
COMMENT ON COLUMN auth_federated_provider_configuration.client_id IS '向外部 Provider 登记的 OAuth Client ID';
COMMENT ON COLUMN auth_federated_provider_configuration.client_secret_key_id IS '加密 Client Secret 使用的 SecretKeyring 密钥标识';
COMMENT ON COLUMN auth_federated_provider_configuration.client_secret_ciphertext IS '包含 nonce 和认证标签的 Client Secret 密文';
COMMENT ON COLUMN auth_federated_provider_configuration.authorization_endpoint IS '开始 Authorization Code 流程的端点';
COMMENT ON COLUMN auth_federated_provider_configuration.token_endpoint IS '用授权码换取 Token 的端点';
COMMENT ON COLUMN auth_federated_provider_configuration.userinfo_endpoint IS '可选的用户资料查询端点';
COMMENT ON COLUMN auth_federated_provider_configuration.jwks_endpoint IS '验证 Provider 签名使用的 JWK Set 端点';
COMMENT ON COLUMN auth_federated_provider_configuration.client_authentication IS 'Token 端点接受 Client 凭据的方式';
COMMENT ON COLUMN auth_federated_provider_configuration.scopes IS '发起授权请求时申请的 scope 数组';
COMMENT ON COLUMN auth_federated_provider_configuration.profile_mapping IS '把外部 claims 映射到本地资料字段的 JSON 对象';
COMMENT ON COLUMN auth_federated_provider_configuration.authorization_parameters IS '发起授权请求时追加的参数 JSON 对象';
COMMENT ON COLUMN auth_federated_provider_configuration.created_at IS '该 Provider 配置版本创建时间';
COMMENT ON COLUMN auth_federated_provider_configuration.deleted IS '配置版本失效标记；0 表示当前版本，1 表示历史版本';

COMMENT ON TABLE auth_federated_identity IS '外部身份与本地 Account 的绑定及最近一次资料快照';
COMMENT ON COLUMN auth_federated_identity.id IS '外部身份绑定数据库标识';
COMMENT ON COLUMN auth_federated_identity.provider_id IS '识别该外部身份的 Provider 标识';
COMMENT ON COLUMN auth_federated_identity.account_id IS '外部身份绑定的本地 Account 标识';
COMMENT ON COLUMN auth_federated_identity.issuer IS '产生外部 subject 的 Issuer，Provider 不提供时为空';
COMMENT ON COLUMN auth_federated_identity.subject IS 'Provider 范围内稳定且不透明的外部 subject';
COMMENT ON COLUMN auth_federated_identity.display_name IS '最近一次登录读取的外部展示名称快照';
COMMENT ON COLUMN auth_federated_identity.email IS '最近一次登录读取的外部邮箱快照';
COMMENT ON COLUMN auth_federated_identity.email_verified IS 'Provider 是否声明当前邮箱已经验证';
COMMENT ON COLUMN auth_federated_identity.avatar_url IS '最近一次登录读取的外部头像地址快照';
COMMENT ON COLUMN auth_federated_identity.version IS '资料更新和解绑并发修改使用的乐观版本';
COMMENT ON COLUMN auth_federated_identity.linked_at IS '外部身份首次绑定本地 Account 的时间';
COMMENT ON COLUMN auth_federated_identity.updated_at IS '外部资料快照或绑定状态最近更新时间，由数据库触发器维护';
COMMENT ON COLUMN auth_federated_identity.deleted IS '解绑标记；0 表示当前绑定，1 表示保留的历史绑定';

CREATE TRIGGER set_auth_federated_provider_updated_at
    BEFORE UPDATE ON auth_federated_provider
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_auth_federated_identity_updated_at
    BEFORE UPDATE ON auth_federated_identity
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

-- 组件：project :modules:auth:sca4dj-auth-hmac
-- 结构：auth-hmac.sql
CREATE TABLE auth_hmac_credential (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE,
    key_id VARCHAR(128) NOT NULL,
    ciphertext BYTEA NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE
);

COMMENT ON TABLE auth_hmac_credential IS '保存 Account HMAC-SHA256 认证使用的加密 Key 和生命周期状态';
COMMENT ON COLUMN auth_hmac_credential.id IS 'HMAC Credential 数据库标识';
COMMENT ON COLUMN auth_hmac_credential.account_id IS 'HMAC Credential 所属 Account 标识';
COMMENT ON COLUMN auth_hmac_credential.key_id IS '加密 HMAC Key 使用的 SecretKeyring 密钥标识';
COMMENT ON COLUMN auth_hmac_credential.ciphertext IS '包含 nonce 和认证标签的 HMAC Key 密文';
COMMENT ON COLUMN auth_hmac_credential.status IS 'ACTIVE 可认证，REVOKED 已撤销';
COMMENT ON COLUMN auth_hmac_credential.version IS '创建、轮换、撤销或重新签发时递增的乐观版本';
COMMENT ON COLUMN auth_hmac_credential.created_at IS '首次签发 HMAC Key 的时间';
COMMENT ON COLUMN auth_hmac_credential.updated_at IS '最近轮换、撤销或重新签发时间';
COMMENT ON COLUMN auth_hmac_credential.revoked_at IS '最近撤销时间，ACTIVE 时为空';

CREATE TRIGGER set_auth_hmac_credential_updated_at
    BEFORE UPDATE ON auth_hmac_credential
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

-- 组件：project :modules:auth:sca4dj-auth-passkey
-- 结构：auth-passkey.sql
CREATE TABLE auth_passkey_account (
    account_id BIGINT PRIMARY KEY,
    user_handle BYTEA NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE auth_passkey_credential (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL,
    credential_id BYTEA NOT NULL UNIQUE,
    public_key_cose BYTEA NOT NULL,
    signature_count BIGINT NOT NULL DEFAULT 0,
    transports VARCHAR(255) NOT NULL DEFAULT '',
    backup_eligible BOOLEAN NOT NULL,
    backed_up BOOLEAN NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_auth_passkey_credential_account
    ON auth_passkey_credential (account_id, created_at)
    WHERE deleted = 0;

COMMENT ON TABLE auth_passkey_account IS 'Passkey 模块为每个 Account 保存的稳定 WebAuthn user handle';
COMMENT ON COLUMN auth_passkey_account.account_id IS 'user handle 所属 Account 标识';
COMMENT ON COLUMN auth_passkey_account.user_handle IS '浏览器和 authenticator 使用的不透明随机 user handle';
COMMENT ON COLUMN auth_passkey_account.version IS '并发新增名额和删除最小保留数量使用的乐观版本';
COMMENT ON COLUMN auth_passkey_account.created_at IS 'Passkey Account 扩展创建时间';
COMMENT ON COLUMN auth_passkey_account.updated_at IS 'Passkey Account 扩展最近更新时间，由数据库触发器维护';

COMMENT ON TABLE auth_passkey_credential IS 'Passkey 模块拥有的 WebAuthn Credential 公钥和使用状态';
COMMENT ON COLUMN auth_passkey_credential.id IS 'Passkey Credential 数据库标识';
COMMENT ON COLUMN auth_passkey_credential.account_id IS 'Credential 所属 Account 标识';
COMMENT ON COLUMN auth_passkey_credential.credential_id IS 'authenticator 生成的 WebAuthn credential ID';
COMMENT ON COLUMN auth_passkey_credential.public_key_cose IS 'COSE 编码的 Credential 公钥';
COMMENT ON COLUMN auth_passkey_credential.signature_count IS '最近一次成功 assertion 保存的签名计数器';
COMMENT ON COLUMN auth_passkey_credential.transports IS '按标识排序并以逗号分隔的 authenticator transport';
COMMENT ON COLUMN auth_passkey_credential.backup_eligible IS 'Credential 是否允许同步备份';
COMMENT ON COLUMN auth_passkey_credential.backed_up IS 'Credential 最近一次使用时是否处于已备份状态';
COMMENT ON COLUMN auth_passkey_credential.name IS '用户为 Credential 设置的名称';
COMMENT ON COLUMN auth_passkey_credential.status IS 'Credential 当前状态，ACTIVE 可认证，DISABLED 不参与认证';
COMMENT ON COLUMN auth_passkey_credential.created_at IS 'Credential 创建时间';
COMMENT ON COLUMN auth_passkey_credential.updated_at IS 'Credential 最近更新时间，由数据库触发器维护';
COMMENT ON COLUMN auth_passkey_credential.last_used_at IS 'Credential 最近一次成功 assertion 的时间';
COMMENT ON COLUMN auth_passkey_credential.version IS '认证、删除和并发计数器更新使用的乐观版本';
COMMENT ON COLUMN auth_passkey_credential.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

CREATE TRIGGER set_auth_passkey_account_updated_at
    BEFORE UPDATE ON auth_passkey_account
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_auth_passkey_credential_updated_at
    BEFORE UPDATE ON auth_passkey_credential
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

-- 组件：project :modules:auth:sca4dj-auth-password
-- 结构：auth-password.sql
CREATE TABLE auth_password_credential (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_auth_password_credential_account
    ON auth_password_credential (account_id) WHERE deleted = 0;

CREATE TABLE auth_password_secret (
    credential_id BIGINT PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL
);

COMMENT ON TABLE auth_password_credential IS 'Password KnowledgeFactor 的凭据元数据和生命周期状态';
COMMENT ON COLUMN auth_password_credential.id IS '密码凭据标识';
COMMENT ON COLUMN auth_password_credential.account_id IS '密码凭据所属平台账号标识';
COMMENT ON COLUMN auth_password_credential.status IS '密码凭据当前状态';
COMMENT ON COLUMN auth_password_credential.version IS '密码凭据乐观并发版本';
COMMENT ON COLUMN auth_password_credential.created_at IS '密码凭据创建时间';
COMMENT ON COLUMN auth_password_credential.updated_at IS '密码凭据最近更新时间';
COMMENT ON COLUMN auth_password_credential.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

COMMENT ON TABLE auth_password_secret IS 'Password KnowledgeFactor 的 Argon2id 密码摘要详情；删除凭据时物理清除';
COMMENT ON COLUMN auth_password_secret.credential_id IS '密码凭据标识';
COMMENT ON COLUMN auth_password_secret.password_hash IS 'Argon2id 不可逆密码摘要';

CREATE TRIGGER set_auth_password_credential_updated_at
    BEFORE UPDATE ON auth_password_credential
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

-- 组件：project :modules:auth:sca4dj-auth-shared-secret
-- 结构：auth-shared-secret.sql
CREATE TABLE auth_shared_secret_credential (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE,
    secret_hash VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE
);

COMMENT ON TABLE auth_shared_secret_credential IS '保存 Account 直接 Shared Secret 认证使用的不可逆摘要和生命周期状态';
COMMENT ON COLUMN auth_shared_secret_credential.id IS 'Shared Secret Credential 数据库标识';
COMMENT ON COLUMN auth_shared_secret_credential.account_id IS 'Shared Secret 凭据所属 Account 标识';
COMMENT ON COLUMN auth_shared_secret_credential.secret_hash IS '包含算法参数和盐值的 Argon2id 不可逆摘要';
COMMENT ON COLUMN auth_shared_secret_credential.status IS 'ACTIVE 可认证，REVOKED 已撤销';
COMMENT ON COLUMN auth_shared_secret_credential.version IS '创建、轮换、撤销或重新签发时递增的乐观版本';
COMMENT ON COLUMN auth_shared_secret_credential.created_at IS '首次签发 Shared Secret 的时间';
COMMENT ON COLUMN auth_shared_secret_credential.updated_at IS '最近轮换、撤销或重新签发时间';
COMMENT ON COLUMN auth_shared_secret_credential.revoked_at IS '最近撤销时间，ACTIVE 时为空';

CREATE TRIGGER set_auth_shared_secret_credential_updated_at
    BEFORE UPDATE ON auth_shared_secret_credential
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

-- 组件：project :modules:auth:sca4dj-auth-totp
-- 结构：auth-totp.sql
CREATE TABLE auth_totp_credential (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_accepted_step BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_auth_totp_credential_account
    ON auth_totp_credential (account_id) WHERE deleted = 0;

CREATE TABLE auth_totp_secret (
    credential_id BIGINT PRIMARY KEY,
    secret_key_id VARCHAR(64) NOT NULL,
    secret_ciphertext BYTEA NOT NULL
);

COMMENT ON TABLE auth_totp_credential IS 'TOTP PossessionFactor 的凭据元数据、重放游标和生命周期状态';
COMMENT ON COLUMN auth_totp_credential.id IS 'TOTP 凭据标识';
COMMENT ON COLUMN auth_totp_credential.account_id IS 'TOTP 凭据所属 Account 标识';
COMMENT ON COLUMN auth_totp_credential.status IS 'TOTP 凭据当前状态';
COMMENT ON COLUMN auth_totp_credential.last_accepted_step IS '最近一次接受的 30 秒 Unix 时间步；同一时间步可重复使用';
COMMENT ON COLUMN auth_totp_credential.version IS '替换和删除使用的乐观并发版本';
COMMENT ON COLUMN auth_totp_credential.created_at IS 'TOTP 凭据创建时间';
COMMENT ON COLUMN auth_totp_credential.updated_at IS 'TOTP 凭据最近更新时间';
COMMENT ON COLUMN auth_totp_credential.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

COMMENT ON TABLE auth_totp_secret IS 'TOTP PossessionFactor 的加密共享密钥详情；删除凭据时物理清除';
COMMENT ON COLUMN auth_totp_secret.credential_id IS 'TOTP 凭据标识';
COMMENT ON COLUMN auth_totp_secret.secret_key_id IS 'AES-256-GCM keyring 密钥标识';
COMMENT ON COLUMN auth_totp_secret.secret_ciphertext IS '包含 12-byte nonce 与 GCM tag 的密文';

CREATE TRIGGER set_auth_totp_credential_updated_at
    BEFORE UPDATE ON auth_totp_credential
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

-- 组件：project :modules:auth:sca4dj-auth-x509
-- 结构：auth-x509.sql
CREATE TABLE auth_x509_credential (
    id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    fingerprint_sha256 CHAR(64) NOT NULL UNIQUE,
    certificate_der BYTEA NOT NULL,
    serial_number VARCHAR(128) NOT NULL,
    subject_dn TEXT NOT NULL,
    not_before TIMESTAMP WITH TIME ZONE NOT NULL,
    not_after TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_auth_x509_credential_account
    ON auth_x509_credential (account_id, created_at DESC);

COMMENT ON TABLE auth_x509_credential IS '保存 Account X.509 认证使用的公钥证书和生命周期状态，不保存私钥';
COMMENT ON COLUMN auth_x509_credential.id IS 'X.509 Credential 标识';
COMMENT ON COLUMN auth_x509_credential.account_id IS 'X.509 Credential 所属 Account 标识';
COMMENT ON COLUMN auth_x509_credential.fingerprint_sha256 IS 'DER 证书 SHA-256 小写十六进制指纹';
COMMENT ON COLUMN auth_x509_credential.certificate_der IS '不包含私钥的 X.509 公钥证书 DER';
COMMENT ON COLUMN auth_x509_credential.serial_number IS 'X.509 证书序列号十六进制文本';
COMMENT ON COLUMN auth_x509_credential.subject_dn IS 'X.509 Subject DN';
COMMENT ON COLUMN auth_x509_credential.not_before IS '证书生效时间';
COMMENT ON COLUMN auth_x509_credential.not_after IS '证书失效时间';
COMMENT ON COLUMN auth_x509_credential.status IS 'ACTIVE 可认证，REVOKED 已撤销';
COMMENT ON COLUMN auth_x509_credential.version IS '撤销时递增的乐观版本';
COMMENT ON COLUMN auth_x509_credential.created_at IS 'Credential 登记时间';
COMMENT ON COLUMN auth_x509_credential.updated_at IS 'Credential 最近更新时间';
COMMENT ON COLUMN auth_x509_credential.revoked_at IS '撤销时间，ACTIVE 时为空';

CREATE TRIGGER set_auth_x509_credential_updated_at
    BEFORE UPDATE ON auth_x509_credential
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

-- 组件：project :modules:iam:sca4dj-iam
-- 结构：iam.sql
CREATE TABLE iam_subject (
    id BIGINT PRIMARY KEY,
    subject_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE iam_account (
    id BIGINT PRIMARY KEY,
    subject_id BIGINT NOT NULL,
    realm_key VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_iam_account_subject_realm
    ON iam_account (subject_id, realm_key) WHERE deleted = 0;

CREATE TABLE iam_principal (
    id BIGINT PRIMARY KEY,
    principal_type VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    roles_version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_iam_single_system ON iam_subject(subject_type) WHERE subject_type = 'SYSTEM';

CREATE TABLE iam_system_identity (
    code VARCHAR(128) PRIMARY KEY,
    principal_id BIGINT NOT NULL UNIQUE,
    subject_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (code ~ '^[A-Z0-9_]+(:[A-Z0-9_]+)*$')
);

COMMENT ON TABLE iam_system_identity IS '整个业务系统的稳定领域身份；各实例共享编码和 Principal ID';
COMMENT ON COLUMN iam_system_identity.code IS '代码常量声明的系统身份编码';
COMMENT ON COLUMN iam_system_identity.principal_id IS '配置指定且禁止自动改写的全局 Principal ID';
COMMENT ON COLUMN iam_system_identity.subject_id IS '整个业务系统唯一的 SYSTEM Subject';

CREATE TABLE iam_account_principal (
    account_id BIGINT NOT NULL,
    principal_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, principal_id)
);

CREATE TABLE iam_account_identifier (
    id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    realm_key VARCHAR(128) NOT NULL,
    identifier_type VARCHAR(128) NOT NULL,
    value VARCHAR(320) NOT NULL,
    normalized_value VARCHAR(320) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_iam_identifier_account_type
    ON iam_account_identifier (account_id, identifier_type) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_iam_identifier_realm_value
    ON iam_account_identifier (realm_key, identifier_type, normalized_value) WHERE deleted = 0;

CREATE INDEX idx_iam_account_identifier_account ON iam_account_identifier (account_id, status);
CREATE INDEX idx_iam_account_realm_status ON iam_account (realm_key, status);

CREATE TABLE platform_role (
    id BIGINT PRIMARY KEY,
    code VARCHAR(128) NOT NULL,
    i18n JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_platform_role_code ON platform_role (code);

CREATE TABLE platform_role_permission (
    role_id BIGINT NOT NULL,
    permission_selector VARCHAR(256) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_selector)
);

CREATE TABLE platform_principal_role (
    principal_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    protected_relation BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (principal_id, role_id)
);

CREATE INDEX idx_platform_role_permission_role
    ON platform_role_permission (role_id);
CREATE INDEX idx_platform_principal_role_role
    ON platform_principal_role (role_id, principal_id);

CREATE TABLE platform_acl_entry (
    principal_id BIGINT NOT NULL,
    resource_type VARCHAR(128) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (principal_id, resource_type, resource_id, action)
);
CREATE INDEX idx_platform_acl_entry_resource ON platform_acl_entry (resource_type, resource_id);
CREATE INDEX idx_platform_acl_entry_list ON platform_acl_entry (principal_id, resource_type, action);

COMMENT ON TABLE iam_subject IS '记录完成认证或建立受控调用的具体接入者';
COMMENT ON COLUMN iam_subject.id IS '接入者全局标识';
COMMENT ON COLUMN iam_subject.subject_type IS '接入者类型；SYSTEM 表示整个业务系统，其 ID 由应用配置确定';
COMMENT ON COLUMN iam_subject.status IS '接入者当前状态';
COMMENT ON COLUMN iam_subject.created_at IS '接入者创建时间';
COMMENT ON COLUMN iam_subject.updated_at IS '接入者最近更新时间';
COMMENT ON COLUMN iam_subject.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

COMMENT ON TABLE iam_account IS '记录接入者在认证域内使用的账号';
COMMENT ON COLUMN iam_account.id IS '账号全局标识';
COMMENT ON COLUMN iam_account.subject_id IS '使用该账号完成认证的接入者标识';
COMMENT ON COLUMN iam_account.realm_key IS '账号所属认证域的稳定键';
COMMENT ON COLUMN iam_account.status IS '账号当前状态';
COMMENT ON COLUMN iam_account.created_at IS '账号创建时间';
COMMENT ON COLUMN iam_account.updated_at IS '账号最近更新时间';
COMMENT ON COLUMN iam_account.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

COMMENT ON TABLE iam_principal IS '记录平台接口授权、数据归属和审计使用的业务主体';
COMMENT ON COLUMN iam_principal.id IS '业务主体全局标识';
COMMENT ON COLUMN iam_principal.principal_type IS '与 PrincipalCodec type 一致的稳定判别值';
COMMENT ON COLUMN iam_principal.status IS '业务主体当前状态';
COMMENT ON COLUMN iam_principal.created_at IS '业务主体创建时间';
COMMENT ON COLUMN iam_principal.updated_at IS '业务主体最近更新时间';
COMMENT ON COLUMN iam_principal.roles_version IS 'Principal 角色集合版本';
COMMENT ON COLUMN iam_principal.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

COMMENT ON TABLE iam_account_principal IS '记录账号可以选择的业务主体';
COMMENT ON COLUMN iam_account_principal.account_id IS '账号标识';
COMMENT ON COLUMN iam_account_principal.principal_id IS '业务主体标识';
COMMENT ON COLUMN iam_account_principal.created_at IS '账号与业务主体关系创建时间';

COMMENT ON TABLE iam_account_identifier IS '记录认证域内用于定位账号的登录标识';
COMMENT ON COLUMN iam_account_identifier.id IS '登录标识全局主键';
COMMENT ON COLUMN iam_account_identifier.account_id IS '登录标识所属账号';
COMMENT ON COLUMN iam_account_identifier.realm_key IS '登录标识所属认证域的稳定键';
COMMENT ON COLUMN iam_account_identifier.identifier_type IS '登录标识类型';
COMMENT ON COLUMN iam_account_identifier.value IS '保留用于展示的用户输入值';
COMMENT ON COLUMN iam_account_identifier.normalized_value IS '账号查询和唯一约束使用的规范值';
COMMENT ON COLUMN iam_account_identifier.status IS '登录标识当前状态';
COMMENT ON COLUMN iam_account_identifier.created_at IS '登录标识创建时间';
COMMENT ON COLUMN iam_account_identifier.updated_at IS '登录标识最近更新时间';
COMMENT ON COLUMN iam_account_identifier.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

COMMENT ON TABLE platform_role IS '记录 PlatformPrincipal 可以关联的角色';
COMMENT ON COLUMN platform_role.id IS '角色全局标识';
COMMENT ON COLUMN platform_role.code IS 'Platform 角色中唯一且创建后不可修改的编码';
COMMENT ON COLUMN platform_role.i18n IS '按 Locale 和字段键保存的完整角色多语言文本';
COMMENT ON COLUMN platform_role.status IS '角色当前状态；DISABLED 角色不向 Principal 提供 Operation';
COMMENT ON COLUMN platform_role.built_in IS '是否为 IAM 创建并保护的内置角色';
COMMENT ON COLUMN platform_role.created_at IS '角色创建时间';
COMMENT ON COLUMN platform_role.updated_at IS '角色最近更新时间';
COMMENT ON COLUMN platform_role.version IS '角色名称、状态、权限和删除共享的版本';

COMMENT ON TABLE platform_role_permission IS '记录 Platform 角色保存的具体 ACTION 编码或内部全量选择符';
COMMENT ON COLUMN platform_role_permission.role_id IS 'Platform 角色标识';
COMMENT ON COLUMN platform_role_permission.permission_selector IS '具体 ACTION 编码；星号表示 Catalog 当前全部 ACTION';
COMMENT ON COLUMN platform_role_permission.created_at IS '角色 Operation 关系创建时间';

COMMENT ON TABLE platform_principal_role IS '记录 PlatformPrincipal 与 Platform 角色的关系';
COMMENT ON COLUMN platform_principal_role.principal_id IS 'PlatformPrincipal 标识';
COMMENT ON COLUMN platform_principal_role.role_id IS 'Platform 角色标识';
COMMENT ON COLUMN platform_principal_role.protected_relation IS '是否禁止普通角色替换删除这条关系';
COMMENT ON COLUMN platform_principal_role.created_at IS 'Principal 角色关系创建时间';

COMMENT ON TABLE platform_acl_entry IS '记录 PlatformPrincipal 对具体 Resource 拥有的 ACTION';
COMMENT ON COLUMN platform_acl_entry.principal_id IS '获得权限的 PlatformPrincipal 标识';
COMMENT ON COLUMN platform_acl_entry.resource_type IS '权限目标的稳定 Resource 类型';
COMMENT ON COLUMN platform_acl_entry.resource_id IS '权限目标在对应 Resource 类型内的标识';
COMMENT ON COLUMN platform_acl_entry.action IS '允许执行的具体 ACTION 编码';
COMMENT ON COLUMN platform_acl_entry.created_at IS 'ACL 关系创建时间';

CREATE TRIGGER set_iam_subject_updated_at
    BEFORE UPDATE ON iam_subject
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_iam_account_updated_at
    BEFORE UPDATE ON iam_account
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_iam_principal_updated_at
    BEFORE UPDATE ON iam_principal
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_iam_account_identifier_updated_at
    BEFORE UPDATE ON iam_account_identifier
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_platform_role_updated_at
    BEFORE UPDATE ON platform_role
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

-- 组件：project :modules:iot:sca4dj-iot
-- 结构：iot.sql
CREATE TABLE iot_product (
    id BIGINT PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    i18n JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE iot_product_access_method (
    product_id BIGINT NOT NULL,
    method_code VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id, method_code)
);

CREATE TABLE iot_product_protocol (
    product_id BIGINT NOT NULL,
    protocol_code VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id, protocol_code)
);

CREATE TABLE iot_thing_model (
    product_id BIGINT NOT NULL,
    version INTEGER NOT NULL,
    model_json JSONB NOT NULL,
    content_hash CHAR(64) NOT NULL,
    i18n JSONB NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (product_id, version)
);

CREATE TABLE iot_device (
    id BIGINT PRIMARY KEY,
    product_source VARCHAR(16) NOT NULL,
    product_code VARCHAR(128) NOT NULL,
    product_id BIGINT,
    code VARCHAR(128) NOT NULL,
    serial_number VARCHAR(256),
    display_name VARCHAR(128) NOT NULL,
    remark VARCHAR(4096),
    thing_model_version INTEGER NOT NULL,
    binding_generation BIGINT NOT NULL DEFAULT 1,
    protocol_code VARCHAR(128) NOT NULL,
    connection_protocol VARCHAR(128),
    connection_identifier_type VARCHAR(128),
    connection_identifier_value VARCHAR(512),
    desired_sequence BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_iot_device_product_code_code UNIQUE (product_code, code),
    CONSTRAINT uq_iot_device_product_code_serial UNIQUE (product_code, serial_number),
    CONSTRAINT uq_iot_device_connection_identifier
        UNIQUE (connection_protocol, connection_identifier_type, connection_identifier_value)
);

CREATE TABLE iot_device_access_method (
    device_id BIGINT NOT NULL,
    method_code VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (device_id, method_code)
);

CREATE INDEX idx_iot_device_product ON iot_device (product_id, id);

CREATE TABLE iot_reported_group (
    device_id BIGINT NOT NULL,
    binding_generation BIGINT NOT NULL,
    group_code VARCHAR(128) NOT NULL,
    order_scope VARCHAR(128) NOT NULL,
    order_value VARCHAR(256) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    state_json JSONB NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (device_id, binding_generation, group_code)
);

CREATE TABLE iot_desired_property (
    device_id BIGINT NOT NULL,
    binding_generation BIGINT NOT NULL,
    property_code VARCHAR(128) NOT NULL,
    desired_sequence BIGINT NOT NULL,
    value_json JSONB NOT NULL,
    converged_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (device_id, binding_generation, property_code)
);

CREATE TABLE iot_downlink_message (
    message_id BIGINT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    binding_generation BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    target_code VARCHAR(128) NOT NULL,
    desired_sequence BIGINT,
    schedule_id BIGINT,
    scheduled_at TIMESTAMP WITH TIME ZONE,
    data_json JSONB NOT NULL,
    acknowledgement_policy VARCHAR(128) NOT NULL,
    acknowledgement_timeout_millis BIGINT NOT NULL,
    retry_policy VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    ack_deadline TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_ack_identity_hash CHAR(64),
    last_ack_code VARCHAR(128),
    last_ack_data_json JSONB,
    last_ack_source VARCHAR(32),
    last_ack_source_acknowledgement_id VARCHAR(256),
    last_acknowledged_at TIMESTAMP WITH TIME ZONE,
    last_ack_received_at TIMESTAMP WITH TIME ZONE,
    last_failure_code VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_iot_downlink_device_page
    ON iot_downlink_message (device_id, created_at DESC, message_id DESC);
CREATE INDEX idx_iot_downlink_completed_cleanup
    ON iot_downlink_message (completed_at, message_id)
    WHERE status IN ('SUCCEEDED', 'FAILED', 'SUPERSEDED', 'CANCELLED', 'EXPIRED');

CREATE TABLE iot_device_schedule (
    schedule_id BIGINT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    command_code VARCHAR(128) NOT NULL,
    command_inputs_json JSONB NOT NULL,
    trigger_type VARCHAR(16) NOT NULL,
    execute_at TIMESTAMP WITH TIME ZONE,
    cron_expression VARCHAR(256),
    zone_id VARCHAR(64),
    valid_for_millis BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    next_fire_at TIMESTAMP WITH TIME ZONE,
    last_planned_at TIMESTAMP WITH TIME ZONE,
    last_triggered_at TIMESTAMP WITH TIME ZONE,
    last_execution_status VARCHAR(16),
    last_message_id BIGINT,
    last_failure_code VARCHAR(128),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_iot_device_schedule_page ON iot_device_schedule (device_id, schedule_id DESC);
CREATE INDEX idx_iot_device_schedule_due
    ON iot_device_schedule (next_fire_at, schedule_id) WHERE status = 'ACTIVE';

COMMENT ON TABLE iot_product IS '定义一类数据库 Product 允许采用的认证接入方式和设备协议';
COMMENT ON COLUMN iot_product.id IS '数据库 Product 全局标识';
COMMENT ON COLUMN iot_product.code IS '数据库 Product 的全局稳定编码';
COMMENT ON COLUMN iot_product.i18n IS '按 BCP 47 Locale 保存的 Product 完整多语言展示字段';
COMMENT ON COLUMN iot_product.created_at IS 'Product 创建时间';
COMMENT ON COLUMN iot_product.updated_at IS 'Product 最近更新时间，由数据库触发器维护';

COMMENT ON TABLE iot_product_access_method IS '记录数据库 Product 允许 Device 采用的认证接入方式';
COMMENT ON COLUMN iot_product_access_method.product_id IS '接入方式所属数据库 Product 标识';
COMMENT ON COLUMN iot_product_access_method.method_code IS 'Product 允许的接入方式编码，例如 ANONYMOUS、SESSION 或 ACCOUNT:AUTHENTICATION/SHARED_SECRET';
COMMENT ON COLUMN iot_product_access_method.created_at IS 'Product 接入方式关系创建时间';

COMMENT ON TABLE iot_product_protocol IS '记录数据库 Product 允许 Device 绑定的协议实现';
COMMENT ON COLUMN iot_product_protocol.product_id IS '协议所属数据库 Product 标识';
COMMENT ON COLUMN iot_product_protocol.protocol_code IS 'Product 允许的 DeviceProtocol 编码';
COMMENT ON COLUMN iot_product_protocol.created_at IS 'Product 协议关系创建时间';

COMMENT ON TABLE iot_thing_model IS '保存数据库 Product 下连续发布且不可修改的物模型版本';
COMMENT ON COLUMN iot_thing_model.product_id IS '物模型所属数据库 Product 标识';
COMMENT ON COLUMN iot_thing_model.version IS 'Product 内从 1 开始连续递增的物模型版本';
COMMENT ON COLUMN iot_thing_model.model_json IS '完整物模型定义的 JSON 对象';
COMMENT ON COLUMN iot_thing_model.content_hash IS '规范化物模型 JSON 的 SHA-256 小写十六进制摘要';
COMMENT ON COLUMN iot_thing_model.i18n IS '按 BCP 47 Locale 和稳定展示路径保存的完整动态翻译';
COMMENT ON COLUMN iot_thing_model.published_at IS '该物模型版本完成发布的时间';

COMMENT ON TABLE iot_device IS '保存 DEVICE Subject 对应的公共 IoT Profile 和可选直接连接绑定';
COMMENT ON COLUMN iot_device.id IS 'Device 标识，与对应 DEVICE Subject 标识相同';
COMMENT ON COLUMN iot_device.product_source IS 'Product 定义来源，DATABASE 使用 product_id，CODE 由应用目录解析';
COMMENT ON COLUMN iot_device.product_code IS 'Device 永久保存的 Product 稳定编码';
COMMENT ON COLUMN iot_device.product_id IS '数据库 Product 标识；代码 Product 为空';
COMMENT ON COLUMN iot_device.code IS '同一 Product 内唯一的 Device 编码';
COMMENT ON COLUMN iot_device.serial_number IS '同一 Product 内可选且唯一的设备序列号';
COMMENT ON COLUMN iot_device.display_name IS 'Device 默认展示名称';
COMMENT ON COLUMN iot_device.remark IS 'Device 可选备注';
COMMENT ON COLUMN iot_device.thing_model_version IS 'Device 当前绑定的已发布物模型版本';
COMMENT ON COLUMN iot_device.binding_generation IS '物模型或协议每次重新绑定后递增的版本';
COMMENT ON COLUMN iot_device.protocol_code IS 'Device 当前绑定的 DeviceProtocol 编码';
COMMENT ON COLUMN iot_device.connection_protocol IS 'Device 可选直接连接使用的协议编码';
COMMENT ON COLUMN iot_device.connection_identifier_type IS 'Device 可选直接连接使用的公开身份类型';
COMMENT ON COLUMN iot_device.connection_identifier_value IS '连接协议和身份类型范围内唯一的公开 Device 身份值';
COMMENT ON COLUMN iot_device.desired_sequence IS '当前绑定内最近分配的 desired 递增版本';
COMMENT ON COLUMN iot_device.created_at IS 'Device 创建时间';
COMMENT ON COLUMN iot_device.updated_at IS 'Device 最近更新时间，由数据库触发器维护';

COMMENT ON TABLE iot_device_access_method IS '记录 Device 当前启用的认证接入方式';
COMMENT ON COLUMN iot_device_access_method.device_id IS '接入方式所属 Device 标识';
COMMENT ON COLUMN iot_device_access_method.method_code IS 'Device 当前启用的接入方式编码，例如 ANONYMOUS、SESSION 或 ACCOUNT:AUTHENTICATION/SHARED_SECRET';
COMMENT ON COLUMN iot_device_access_method.created_at IS 'Device 接入方式关系创建时间';

COMMENT ON TABLE iot_reported_group IS '保存每个 reported 字段组最后接受的顺序和值';
COMMENT ON COLUMN iot_reported_group.device_id IS 'reported 状态所属 Device 标识';
COMMENT ON COLUMN iot_reported_group.binding_generation IS '状态所属 Device 绑定版本';
COMMENT ON COLUMN iot_reported_group.group_code IS '物模型中定义的 reported 字段组编码';
COMMENT ON COLUMN iot_reported_group.order_scope IS '解释 order_value 所需的排序范围编码';
COMMENT ON COLUMN iot_reported_group.order_value IS '协议提供且在 order_scope 内可比较的顺序值';
COMMENT ON COLUMN iot_reported_group.content_hash IS '规范化 state_json 的 SHA-256 小写十六进制摘要';
COMMENT ON COLUMN iot_reported_group.state_json IS '该字段组最近一次接受的完整 reported 值';
COMMENT ON COLUMN iot_reported_group.observed_at IS 'Device 观察到该组状态的时间';
COMMENT ON COLUMN iot_reported_group.received_at IS '平台收到该组状态的时间';
COMMENT ON COLUMN iot_reported_group.version IS '每次接受新状态后递增的乐观锁版本';
COMMENT ON COLUMN iot_reported_group.created_at IS '该绑定内字段组首个状态创建时间';
COMMENT ON COLUMN iot_reported_group.updated_at IS '字段组最近接受状态的时间，由数据库触发器维护';

COMMENT ON TABLE iot_desired_property IS '保存平台持续期望的当前属性值和收敛结果';
COMMENT ON COLUMN iot_desired_property.device_id IS 'desired 属性所属 Device 标识';
COMMENT ON COLUMN iot_desired_property.binding_generation IS 'desired 属性所属 Device 绑定版本';
COMMENT ON COLUMN iot_desired_property.property_code IS '物模型中可写属性的稳定编码';
COMMENT ON COLUMN iot_desired_property.desired_sequence IS 'Device 内生成该期望值时分配的递增版本';
COMMENT ON COLUMN iot_desired_property.value_json IS '平台当前持续期望的属性值';
COMMENT ON COLUMN iot_desired_property.converged_at IS 'reported 值确认已达到当前期望值的时间';
COMMENT ON COLUMN iot_desired_property.created_at IS '该绑定内属性首次建立期望值的时间';
COMMENT ON COLUMN iot_desired_property.updated_at IS '期望值或收敛结果最近更新时间，由数据库触发器维护';

COMMENT ON TABLE iot_downlink_message IS '保存 desired 和 command 共用的可靠下行状态机';
COMMENT ON COLUMN iot_downlink_message.message_id IS '下行消息全局标识';
COMMENT ON COLUMN iot_downlink_message.device_id IS '接收下行的 Device 标识';
COMMENT ON COLUMN iot_downlink_message.binding_generation IS '创建消息时的 Device 绑定版本';
COMMENT ON COLUMN iot_downlink_message.kind IS '消息种类，DESIRED 表示期望属性，COMMAND 表示一次性命令';
COMMENT ON COLUMN iot_downlink_message.target_code IS '目标属性或命令的物模型编码';
COMMENT ON COLUMN iot_downlink_message.desired_sequence IS 'DESIRED 消息对应的期望版本，COMMAND 时为空';
COMMENT ON COLUMN iot_downlink_message.schedule_id IS '生成该 command 的设备定时规则标识，即时下行为空';
COMMENT ON COLUMN iot_downlink_message.scheduled_at IS '设备定时规则本次原定触发时间，即时下行为空';
COMMENT ON COLUMN iot_downlink_message.data_json IS '协议编码前的属性值或命令输入';
COMMENT ON COLUMN iot_downlink_message.acknowledgement_policy IS '决定发送后是否及怎样等待 Device ACK 的策略编码';
COMMENT ON COLUMN iot_downlink_message.acknowledgement_timeout_millis IS '创建消息时快照的 ACK 等待时间，单位为毫秒';
COMMENT ON COLUMN iot_downlink_message.retry_policy IS '决定失败和超时后重试时间的策略编码';
COMMENT ON COLUMN iot_downlink_message.status IS '可靠下行状态机的当前状态';
COMMENT ON COLUMN iot_downlink_message.attempt_count IS '已经开始的发送尝试次数';
COMMENT ON COLUMN iot_downlink_message.ack_deadline IS '最近一次发布后等待 Device ACK 的截止时间';
COMMENT ON COLUMN iot_downlink_message.expires_at IS '消息不再允许发送或重试的时间';
COMMENT ON COLUMN iot_downlink_message.last_ack_identity_hash IS '最新 ACK 来源事实标识的 SHA-256，用于忽略当前阶段的重复投递';
COMMENT ON COLUMN iot_downlink_message.last_ack_code IS '协议或 reported 推导出的最新 ACK 阶段编码';
COMMENT ON COLUMN iot_downlink_message.last_ack_data_json IS '最新 ACK 阶段的结构化数据';
COMMENT ON COLUMN iot_downlink_message.last_ack_source IS '最新 ACK 的接入来源编码';
COMMENT ON COLUMN iot_downlink_message.last_ack_source_acknowledgement_id IS '来源提供的最新 ACK 稳定事实标识';
COMMENT ON COLUMN iot_downlink_message.last_acknowledged_at IS '最新 ACK 表示的 Device 处理时间';
COMMENT ON COLUMN iot_downlink_message.last_ack_received_at IS '平台收到最新 ACK 的时间';
COMMENT ON COLUMN iot_downlink_message.last_failure_code IS '最近一次发送失败或停止重试的稳定 code';
COMMENT ON COLUMN iot_downlink_message.created_at IS '下行消息创建时间';
COMMENT ON COLUMN iot_downlink_message.updated_at IS '下行状态最近更新时间，由数据库触发器维护';
COMMENT ON COLUMN iot_downlink_message.completed_at IS '下行进入最终状态的时间';
COMMENT ON COLUMN iot_downlink_message.version IS '下行状态并发修改使用的乐观版本';

COMMENT ON TABLE iot_device_schedule IS '保存到期后生成设备 command 下行的单次或 Cron 规则';
COMMENT ON COLUMN iot_device_schedule.schedule_id IS 'Device 计划全局标识';
COMMENT ON COLUMN iot_device_schedule.device_id IS '执行计划的 Device 标识';
COMMENT ON COLUMN iot_device_schedule.command_code IS '到期时生成的物模型命令编码';
COMMENT ON COLUMN iot_device_schedule.command_inputs_json IS '生成命令下行时使用的输入';
COMMENT ON COLUMN iot_device_schedule.trigger_type IS '单次时间或 Cron 的触发类型';
COMMENT ON COLUMN iot_device_schedule.execute_at IS '单次计划的执行时间，Cron 计划为空';
COMMENT ON COLUMN iot_device_schedule.cron_expression IS 'Cron 计划表达式，单次计划为空';
COMMENT ON COLUMN iot_device_schedule.zone_id IS '解释 Cron 表达式使用的时区标识';
COMMENT ON COLUMN iot_device_schedule.valid_for_millis IS '每次生成命令从计划时间起允许延迟执行的毫秒数';
COMMENT ON COLUMN iot_device_schedule.status IS '计划当前是否可继续触发';
COMMENT ON COLUMN iot_device_schedule.next_fire_at IS '调度器下一次应处理该计划的时间';
COMMENT ON COLUMN iot_device_schedule.last_planned_at IS '最近一次执行结果对应的原定触发时间';
COMMENT ON COLUMN iot_device_schedule.last_triggered_at IS '最近一次执行结果的实际处理时间';
COMMENT ON COLUMN iot_device_schedule.last_execution_status IS '最近一次执行生成下行、过期或失败的结果';
COMMENT ON COLUMN iot_device_schedule.last_message_id IS '最近一次成功生成的下行消息标识';
COMMENT ON COLUMN iot_device_schedule.last_failure_code IS '最近一次业务失败的稳定 code';
COMMENT ON COLUMN iot_device_schedule.version IS '计划并发修改使用的乐观版本';
COMMENT ON COLUMN iot_device_schedule.created_at IS '计划创建时间';
COMMENT ON COLUMN iot_device_schedule.updated_at IS '计划最近更新时间，由数据库触发器维护';

CREATE TRIGGER set_iot_product_updated_at
    BEFORE UPDATE ON iot_product
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_iot_device_updated_at
    BEFORE UPDATE ON iot_device
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_iot_reported_group_updated_at
    BEFORE UPDATE ON iot_reported_group
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_iot_desired_property_updated_at
    BEFORE UPDATE ON iot_desired_property
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_iot_downlink_message_updated_at
    BEFORE UPDATE ON iot_downlink_message
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_iot_device_schedule_updated_at
    BEFORE UPDATE ON iot_device_schedule
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

-- 组件：project :modules:iot:sca4dj-iot-timescale
-- 结构：iot-timescale.sql
-- TimescaleDB 使用独立数据源和独立 Flyway；主应用 V1 不拼接本文件。

-- 组件：project :modules:job:sca4dj-job
-- 结构：job.sql
CREATE SCHEMA IF NOT EXISTS job;

CREATE TABLE job.job_run (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    app_name VARCHAR(128) NOT NULL,
    job_key VARCHAR(128) NOT NULL,
    job_definition_version INTEGER NOT NULL,
    job_kind VARCHAR(16) NOT NULL,
    input_json JSONB,
    output_json JSONB,
    status VARCHAR(16) NOT NULL,
    schedule_key VARCHAR(128),
    scheduled_fire_time TIMESTAMP WITH TIME ZONE,
    error_summary VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (app_name, schedule_key, scheduled_fire_time)
);

ALTER TABLE job.job_run
    ADD COLUMN capability_code VARCHAR(128) NOT NULL DEFAULT 'JOB:WORKER',
    ADD COLUMN system_identity VARCHAR(128) NOT NULL DEFAULT 'MAIN',
    ADD COLUMN work_key VARCHAR(256) NOT NULL DEFAULT 'DEFAULT',
    ADD COLUMN overlap_policy VARCHAR(24) NOT NULL DEFAULT 'ALLOW',
    ADD COLUMN target_instance_id VARCHAR(128),
    ADD COLUMN target_generation_id UUID,
    ADD COLUMN target_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN dispatch_id BIGINT;

ALTER TABLE job.job_run
    DROP CONSTRAINT IF EXISTS job_run_runtime_namespace_schedule_key_scheduled_fire_time_key,
    DROP CONSTRAINT IF EXISTS job_run_app_name_schedule_key_scheduled_fire_time_key;

DROP INDEX IF EXISTS job.job_run_runtime_namespace_schedule_key_scheduled_fire_time_key;
DROP INDEX IF EXISTS job.job_run_app_name_schedule_key_scheduled_fire_time_key;

CREATE UNIQUE INDEX uk_job_run_scheduled_target
    ON job.job_run (
        app_name, schedule_key, scheduled_fire_time,
        COALESCE(target_instance_id, ''))
    WHERE schedule_key IS NOT NULL;

ALTER TABLE job.job_run
    ADD CONSTRAINT ck_job_run_target_generation CHECK (
        target_generation_id IS NULL OR target_instance_id IS NOT NULL);

CREATE INDEX idx_job_run_active_work
    ON job.job_run (app_name, system_identity, job_key, work_key, status, id);

CREATE TABLE job.job_instance (
    app_name VARCHAR(128) NOT NULL,
    instance_id VARCHAR(128) NOT NULL,
    generation_id UUID NOT NULL,
    capabilities_json JSONB NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (app_name, instance_id)
);

CREATE TABLE job.job_dispatch (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    app_name VARCHAR(128) NOT NULL,
    job_key VARCHAR(128) NOT NULL,
    schedule_key VARCHAR(128),
    scheduled_fire_time TIMESTAMP WITH TIME ZONE,
    targets_json JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (app_name, schedule_key, scheduled_fire_time)
);

CREATE TABLE job.job_delivery (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    app_name VARCHAR(128) NOT NULL,
    dispatch_id BIGINT NOT NULL REFERENCES job.job_dispatch(id),
    job_key VARCHAR(128) NOT NULL,
    job_definition_version INTEGER NOT NULL,
    capability_code VARCHAR(128) NOT NULL,
    system_identity VARCHAR(128) NOT NULL,
    work_key VARCHAR(256) NOT NULL,
    overlap_policy VARCHAR(24) NOT NULL,
    input_json JSONB,
    target_instance_id VARCHAR(128) NOT NULL,
    target_generation_id UUID,
    status VARCHAR(16) NOT NULL DEFAULT 'READY',
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    lease_until TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_summary VARCHAR(1000),
    UNIQUE (dispatch_id, target_instance_id)
);

CREATE INDEX idx_job_delivery_claim
    ON job.job_delivery (app_name, target_instance_id, target_generation_id, status, id);

CREATE INDEX idx_job_instance_live
    ON job.job_instance (app_name, expires_at);

CREATE TABLE job.job_run_node (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    run_id BIGINT NOT NULL,
    node_key VARCHAR(128) NOT NULL,
    node_kind VARCHAR(16) NOT NULL,
    input_json JSONB,
    output_json JSONB,
    status VARCHAR(16) NOT NULL,
    parallel_merge SMALLINT NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    retry_delay_millis BIGINT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_owner VARCHAR(128),
    lease_until TIMESTAMP WITH TIME ZONE,
    error_summary VARCHAR(1000),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (run_id, node_key)
);

CREATE INDEX idx_job_run_namespace_status
    ON job.job_run (app_name, status, id);

CREATE INDEX idx_job_run_succeeded_cleanup
    ON job.job_run (app_name, status, completed_at, id);

CREATE INDEX idx_job_run_node_claim
    ON job.job_run_node (status, available_at, lease_until, id);

CREATE TABLE job.job_run_dependency (
    run_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    depends_on_node_id BIGINT NOT NULL,
    dependency_order SMALLINT NOT NULL,
    PRIMARY KEY (node_id, depends_on_node_id),
    UNIQUE (node_id, dependency_order)
);

CREATE INDEX idx_job_run_dependency_source
    ON job.job_run_dependency (depends_on_node_id, node_id);

CREATE TABLE job.job_run_attempt (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    node_id BIGINT NOT NULL,
    attempt_number INTEGER NOT NULL,
    worker_id VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    error_summary VARCHAR(1000),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (node_id, attempt_number)
);

COMMENT ON SCHEMA job IS '保存可恢复 Job run、Pipeline 节点、依赖和尝试事实';
COMMENT ON TABLE job.job_run IS '按运行时命名空间保存一次 DirectJob 或 PipelineJob 的根运行';
COMMENT ON COLUMN job.job_run.id IS 'Job run 数据库标识';
COMMENT ON COLUMN job.job_run.app_name IS '隔离 Job 定义、领取和清理的运行时命名空间';
COMMENT ON COLUMN job.job_run.job_key IS '本次运行采用的稳定 Job 编码';
COMMENT ON COLUMN job.job_run.job_definition_version IS '本次运行采用的 Job 定义版本';
COMMENT ON COLUMN job.job_run.job_kind IS '根 Job 是 DIRECT 还是 PIPELINE';
COMMENT ON COLUMN job.job_run.input_json IS '启动根 Job 时保存的可选输入';
COMMENT ON COLUMN job.job_run.output_json IS '根 Job 成功后保存的可选输出';
COMMENT ON COLUMN job.job_run.status IS '根 Job 当前排队、运行、成功或失败状态';
COMMENT ON COLUMN job.job_run.schedule_key IS '由调度器触发时使用的稳定计划编码';
COMMENT ON COLUMN job.job_run.scheduled_fire_time IS '调度器本次触发对应的原定时间';
COMMENT ON COLUMN job.job_run.error_summary IS '根 Job 失败时保存的受限原因摘要';
COMMENT ON COLUMN job.job_run.created_at IS 'Job run 创建时间';
COMMENT ON COLUMN job.job_run.started_at IS '根 Job 首次进入运行状态的时间';
COMMENT ON COLUMN job.job_run.completed_at IS '根 Job 进入成功或失败最终状态的时间';

COMMENT ON TABLE job.job_run_node IS '保存可独立领取和重试的 DirectJob 或 Pipeline 节点';
COMMENT ON COLUMN job.job_run_node.id IS 'Job run 节点数据库标识';
COMMENT ON COLUMN job.job_run_node.run_id IS '节点所属根 Job run 标识';
COMMENT ON COLUMN job.job_run_node.node_key IS '同一 run 内唯一的节点编码';
COMMENT ON COLUMN job.job_run_node.node_kind IS '节点执行 DirectJob 还是展开嵌套 PipelineJob';
COMMENT ON COLUMN job.job_run_node.input_json IS '节点开始执行时读取的可选输入';
COMMENT ON COLUMN job.job_run_node.output_json IS '节点成功后保存的可选输出';
COMMENT ON COLUMN job.job_run_node.status IS '节点等待依赖、可领取、运行或最终结果状态';
COMMENT ON COLUMN job.job_run_node.parallel_merge IS '节点是否接收全部并行前置节点的有序输出';
COMMENT ON COLUMN job.job_run_node.max_attempts IS '节点允许开始的最大尝试次数';
COMMENT ON COLUMN job.job_run_node.retry_delay_millis IS '失败后再次允许领取前等待的毫秒数';
COMMENT ON COLUMN job.job_run_node.attempt_count IS '节点已经开始的执行尝试次数';
COMMENT ON COLUMN job.job_run_node.available_at IS 'Worker 下一次允许领取节点的时间';
COMMENT ON COLUMN job.job_run_node.lease_owner IS '当前领取节点的 Worker 标识';
COMMENT ON COLUMN job.job_run_node.lease_until IS '当前节点租约截止时间';
COMMENT ON COLUMN job.job_run_node.error_summary IS '节点最近一次失败的受限原因摘要';
COMMENT ON COLUMN job.job_run_node.started_at IS '节点首次开始执行的时间';
COMMENT ON COLUMN job.job_run_node.completed_at IS '节点进入最终状态的时间';

COMMENT ON TABLE job.job_run_dependency IS '保存 PipelineJob 节点之间有序的完成依赖';
COMMENT ON COLUMN job.job_run_dependency.run_id IS '两个节点共同所属的根 Job run 标识';
COMMENT ON COLUMN job.job_run_dependency.node_id IS '等待前置节点完成的目标节点标识';
COMMENT ON COLUMN job.job_run_dependency.depends_on_node_id IS '目标节点依赖的前置节点标识';
COMMENT ON COLUMN job.job_run_dependency.dependency_order IS '前置节点输出传给目标节点时使用的顺序';

COMMENT ON TABLE job.job_run_attempt IS '保存节点每次领取、成功、失败或租约放弃结果';
COMMENT ON COLUMN job.job_run_attempt.id IS '节点执行尝试数据库标识';
COMMENT ON COLUMN job.job_run_attempt.node_id IS '执行尝试所属节点标识';
COMMENT ON COLUMN job.job_run_attempt.attempt_number IS '同一节点内从 1 开始递增的尝试序号';
COMMENT ON COLUMN job.job_run_attempt.worker_id IS '领取并执行本次尝试的 Worker 标识';
COMMENT ON COLUMN job.job_run_attempt.status IS '本次尝试正在运行或最终成功、失败、放弃的状态';
COMMENT ON COLUMN job.job_run_attempt.error_summary IS '本次尝试失败或放弃时保存的受限原因摘要';
COMMENT ON COLUMN job.job_run_attempt.started_at IS 'Worker 开始本次尝试的时间';
COMMENT ON COLUMN job.job_run_attempt.completed_at IS '本次尝试进入最终状态的时间';

-- 组件：project :modules:messaging:sca4dj-messaging
-- 结构：messaging.sql
CREATE TABLE messaging_outbox (
    message_id BIGINT PRIMARY KEY,
    message_code VARCHAR(128) NOT NULL,
    envelope BYTEA NOT NULL,
    status VARCHAR(16) NOT NULL,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    lease_id VARCHAR(64),
    lease_until TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_messaging_outbox_dispatch
    ON messaging_outbox (available_at, message_id)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX idx_messaging_outbox_lease
    ON messaging_outbox (lease_until, message_id)
    WHERE status = 'SENDING';

CREATE INDEX idx_messaging_outbox_sent
    ON messaging_outbox (sent_at, message_id)
    WHERE status = 'SENT';

COMMENT ON TABLE messaging_outbox IS '保存业务事务中登记且尚未由 RabbitMQ 确认发布的消息信封';
COMMENT ON COLUMN messaging_outbox.message_id IS '消息信封全局标识';
COMMENT ON COLUMN messaging_outbox.message_code IS '消费者选择消息类型使用的稳定消息编码';
COMMENT ON COLUMN messaging_outbox.envelope IS '已经校验并序列化的 RabbitMQ 业务消息信封';
COMMENT ON COLUMN messaging_outbox.status IS 'PENDING、SENDING、FAILED 或 SENT，不包含发布端 DEAD 状态';
COMMENT ON COLUMN messaging_outbox.available_at IS '发布 Worker 下一次允许领取的时间';
COMMENT ON COLUMN messaging_outbox.attempt_count IS '已经开始的发布尝试次数';
COMMENT ON COLUMN messaging_outbox.lease_id IS '当前发布租约标识，未领取时为空';
COMMENT ON COLUMN messaging_outbox.lease_until IS '当前发布租约截止时间，未领取时为空';
COMMENT ON COLUMN messaging_outbox.last_error IS '最近一次发布失败的受限摘要';
COMMENT ON COLUMN messaging_outbox.created_at IS '消息信封登记时间';
COMMENT ON COLUMN messaging_outbox.updated_at IS '发布状态最近更新时间，由数据库触发器维护';
COMMENT ON COLUMN messaging_outbox.sent_at IS 'RabbitMQ 确认发布成功的时间';
COMMENT ON COLUMN messaging_outbox.version IS '发布状态并发修改使用的乐观版本';

CREATE TRIGGER set_messaging_outbox_updated_at
    BEFORE UPDATE ON messaging_outbox
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

-- 组件：project :modules:notification:sca4dj-notification-email
-- 结构：notification-email.sql
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

-- 组件：project :modules:notification:sca4dj-notification-inbox
-- 结构：notification-inbox.sql
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

-- 组件：project :modules:notification:sca4dj-notification-sms
-- 结构：notification-sms.sql
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

-- 组件：project :modules:open:sca4dj-open
-- 结构：open.sql
CREATE TABLE open_client (
    client_id VARCHAR(128) PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE open_client_operation (
    client_id VARCHAR(128) NOT NULL,
    operation VARCHAR(160) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (client_id, operation)
);

CREATE INDEX idx_open_client_operation_operation
    ON open_client_operation (operation, client_id);

COMMENT ON TABLE open_client IS '保存第三方 Open Client 与普通 APPLICATION Account 的绑定';
COMMENT ON COLUMN open_client.client_id IS '第三方请求和 OAuth Client Credentials 使用的稳定 Client ID';
COMMENT ON COLUMN open_client.account_id IS 'Client 对应的 IAM APPLICATION Account 标识';
COMMENT ON COLUMN open_client.display_name IS '管理端显示的 Client 名称';
COMMENT ON COLUMN open_client.status IS 'ACTIVE 允许认证和调用，DISABLED 拒绝新的 开放请求';
COMMENT ON TABLE open_client_operation IS '保存 Client 已显式开放的 Operation 子集';
COMMENT ON COLUMN open_client_operation.operation IS 'OperationCatalog 中已经注册的 ACTION 编码';

CREATE TRIGGER set_open_client_updated_at
    BEFORE UPDATE ON open_client
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

-- 组件：project :modules:scheduling:sca4dj-scheduling-quartz
-- 结构：quartz.sql
CREATE SCHEMA IF NOT EXISTS quartz;

-- quartz.qrtz_* 保持 Quartz 官方 PostgreSQL JDBC JobStore 表结构，包括表间外键。
CREATE TABLE quartz.qrtz_job_details (
    sched_name VARCHAR(120) NOT NULL,
    job_name VARCHAR(200) NOT NULL,
    job_group VARCHAR(200) NOT NULL,
    description VARCHAR(250),
    job_class_name VARCHAR(250) NOT NULL,
    is_durable BOOLEAN NOT NULL,
    is_nonconcurrent BOOLEAN NOT NULL,
    is_update_data BOOLEAN NOT NULL,
    requests_recovery BOOLEAN NOT NULL,
    job_data BYTEA,
    PRIMARY KEY (sched_name, job_name, job_group)
);

CREATE TABLE quartz.qrtz_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    job_name VARCHAR(200) NOT NULL,
    job_group VARCHAR(200) NOT NULL,
    description VARCHAR(250),
    next_fire_time BIGINT,
    prev_fire_time BIGINT,
    priority INTEGER,
    trigger_state VARCHAR(16) NOT NULL,
    trigger_type VARCHAR(8) NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT,
    calendar_name VARCHAR(200),
    misfire_instr SMALLINT,
    job_data BYTEA,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, job_name, job_group)
        REFERENCES quartz.qrtz_job_details (sched_name, job_name, job_group)
);

CREATE TABLE quartz.qrtz_simple_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    repeat_count BIGINT NOT NULL,
    repeat_interval BIGINT NOT NULL,
    times_triggered BIGINT NOT NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES quartz.qrtz_triggers (sched_name, trigger_name, trigger_group)
);

CREATE TABLE quartz.qrtz_cron_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    cron_expression VARCHAR(120) NOT NULL,
    time_zone_id VARCHAR(80),
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES quartz.qrtz_triggers (sched_name, trigger_name, trigger_group)
);

CREATE TABLE quartz.qrtz_simprop_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    str_prop_1 VARCHAR(512),
    str_prop_2 VARCHAR(512),
    str_prop_3 VARCHAR(512),
    int_prop_1 INTEGER,
    int_prop_2 INTEGER,
    long_prop_1 BIGINT,
    long_prop_2 BIGINT,
    dec_prop_1 NUMERIC(13, 4),
    dec_prop_2 NUMERIC(13, 4),
    bool_prop_1 BOOLEAN,
    bool_prop_2 BOOLEAN,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES quartz.qrtz_triggers (sched_name, trigger_name, trigger_group)
);

CREATE TABLE quartz.qrtz_blob_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    blob_data BYTEA,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES quartz.qrtz_triggers (sched_name, trigger_name, trigger_group)
);

CREATE TABLE quartz.qrtz_calendars (
    sched_name VARCHAR(120) NOT NULL,
    calendar_name VARCHAR(200) NOT NULL,
    calendar BYTEA NOT NULL,
    PRIMARY KEY (sched_name, calendar_name)
);

CREATE TABLE quartz.qrtz_paused_trigger_grps (
    sched_name VARCHAR(120) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    PRIMARY KEY (sched_name, trigger_group)
);

CREATE TABLE quartz.qrtz_fired_triggers (
    sched_name VARCHAR(120) NOT NULL,
    entry_id VARCHAR(95) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    instance_name VARCHAR(200) NOT NULL,
    fired_time BIGINT NOT NULL,
    sched_time BIGINT NOT NULL,
    priority INTEGER NOT NULL,
    state VARCHAR(16) NOT NULL,
    job_name VARCHAR(200),
    job_group VARCHAR(200),
    is_nonconcurrent BOOLEAN,
    requests_recovery BOOLEAN,
    PRIMARY KEY (sched_name, entry_id)
);

CREATE TABLE quartz.qrtz_scheduler_state (
    sched_name VARCHAR(120) NOT NULL,
    instance_name VARCHAR(200) NOT NULL,
    last_checkin_time BIGINT NOT NULL,
    checkin_interval BIGINT NOT NULL,
    PRIMARY KEY (sched_name, instance_name)
);

CREATE TABLE quartz.qrtz_locks (
    sched_name VARCHAR(120) NOT NULL,
    lock_name VARCHAR(40) NOT NULL,
    PRIMARY KEY (sched_name, lock_name)
);

CREATE INDEX idx_qrtz_j_req_recovery
    ON quartz.qrtz_job_details (sched_name, requests_recovery);
CREATE INDEX idx_qrtz_j_grp
    ON quartz.qrtz_job_details (sched_name, job_group);
CREATE INDEX idx_qrtz_t_j
    ON quartz.qrtz_triggers (sched_name, job_name, job_group);
CREATE INDEX idx_qrtz_t_jg
    ON quartz.qrtz_triggers (sched_name, job_group);
CREATE INDEX idx_qrtz_t_c
    ON quartz.qrtz_triggers (sched_name, calendar_name);
CREATE INDEX idx_qrtz_t_g
    ON quartz.qrtz_triggers (sched_name, trigger_group);
CREATE INDEX idx_qrtz_t_state
    ON quartz.qrtz_triggers (sched_name, trigger_state);
CREATE INDEX idx_qrtz_t_n_state
    ON quartz.qrtz_triggers (sched_name, trigger_name, trigger_group, trigger_state);
CREATE INDEX idx_qrtz_t_n_g_state
    ON quartz.qrtz_triggers (sched_name, trigger_group, trigger_state);
CREATE INDEX idx_qrtz_t_next_fire_time
    ON quartz.qrtz_triggers (sched_name, next_fire_time);
CREATE INDEX idx_qrtz_t_nft_st
    ON quartz.qrtz_triggers (sched_name, trigger_state, next_fire_time);
CREATE INDEX idx_qrtz_t_nft_misfire
    ON quartz.qrtz_triggers (sched_name, misfire_instr, next_fire_time);
CREATE INDEX idx_qrtz_t_nft_st_misfire
    ON quartz.qrtz_triggers (sched_name, misfire_instr, next_fire_time, trigger_state);
CREATE INDEX idx_qrtz_t_nft_st_misfire_grp
    ON quartz.qrtz_triggers (
        sched_name, misfire_instr, next_fire_time, trigger_group, trigger_state);
CREATE INDEX idx_qrtz_ft_trig_inst_name
    ON quartz.qrtz_fired_triggers (sched_name, instance_name);
CREATE INDEX idx_qrtz_ft_inst_job_req_rcvry
    ON quartz.qrtz_fired_triggers (sched_name, instance_name, requests_recovery);
CREATE INDEX idx_qrtz_ft_j_g
    ON quartz.qrtz_fired_triggers (sched_name, job_name, job_group);
CREATE INDEX idx_qrtz_ft_jg
    ON quartz.qrtz_fired_triggers (sched_name, job_group);
CREATE INDEX idx_qrtz_ft_t_g
    ON quartz.qrtz_fired_triggers (sched_name, trigger_name, trigger_group);
CREATE INDEX idx_qrtz_ft_tg
    ON quartz.qrtz_fired_triggers (sched_name, trigger_group);

COMMENT ON SCHEMA quartz IS '保存 Quartz JDBC JobStore 的集群调度状态';

COMMENT ON TABLE quartz.qrtz_job_details IS '保存 Quartz JDBC JobStore 的 JobDetail';
COMMENT ON COLUMN quartz.qrtz_job_details.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_job_details.job_name IS 'JobDetail 在组内的名称';
COMMENT ON COLUMN quartz.qrtz_job_details.job_group IS 'JobDetail 所属组名';
COMMENT ON COLUMN quartz.qrtz_job_details.description IS 'JobDetail 可选说明';
COMMENT ON COLUMN quartz.qrtz_job_details.job_class_name IS '执行 Job 的 Java 类名';
COMMENT ON COLUMN quartz.qrtz_job_details.is_durable IS '没有 Trigger 时是否保留 JobDetail';
COMMENT ON COLUMN quartz.qrtz_job_details.is_nonconcurrent IS '同一 JobDetail 是否禁止并发执行';
COMMENT ON COLUMN quartz.qrtz_job_details.is_update_data IS '执行完成后是否保存修改后的 JobDataMap';
COMMENT ON COLUMN quartz.qrtz_job_details.requests_recovery IS 'Scheduler 故障恢复后是否重新执行该 Job';
COMMENT ON COLUMN quartz.qrtz_job_details.job_data IS 'Quartz 序列化的 JobDataMap';

COMMENT ON TABLE quartz.qrtz_triggers IS '保存 Quartz Trigger 的公共字段和当前调度状态';
COMMENT ON COLUMN quartz.qrtz_triggers.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_triggers.trigger_name IS 'Trigger 在组内的名称';
COMMENT ON COLUMN quartz.qrtz_triggers.trigger_group IS 'Trigger 所属组名';
COMMENT ON COLUMN quartz.qrtz_triggers.job_name IS 'Trigger 关联的 JobDetail 名称';
COMMENT ON COLUMN quartz.qrtz_triggers.job_group IS 'Trigger 关联的 JobDetail 组名';
COMMENT ON COLUMN quartz.qrtz_triggers.description IS 'Trigger 可选说明';
COMMENT ON COLUMN quartz.qrtz_triggers.next_fire_time IS '下一次触发的 Unix 毫秒时间';
COMMENT ON COLUMN quartz.qrtz_triggers.prev_fire_time IS '上一次触发的 Unix 毫秒时间';
COMMENT ON COLUMN quartz.qrtz_triggers.priority IS '同一时刻多个 Trigger 的执行优先级';
COMMENT ON COLUMN quartz.qrtz_triggers.trigger_state IS 'Quartz JobStore 使用的 Trigger 状态';
COMMENT ON COLUMN quartz.qrtz_triggers.trigger_type IS 'SIMPLE、CRON、BLOB 或 CAL_INT 等 Trigger 类型';
COMMENT ON COLUMN quartz.qrtz_triggers.start_time IS 'Trigger 生效的 Unix 毫秒时间';
COMMENT ON COLUMN quartz.qrtz_triggers.end_time IS 'Trigger 停止生效的可选 Unix 毫秒时间';
COMMENT ON COLUMN quartz.qrtz_triggers.calendar_name IS 'Trigger 使用的可选 Quartz Calendar 名称';
COMMENT ON COLUMN quartz.qrtz_triggers.misfire_instr IS 'Quartz 定义的 misfire 处理指令';
COMMENT ON COLUMN quartz.qrtz_triggers.job_data IS 'Trigger 自己附加的序列化 JobDataMap';

COMMENT ON TABLE quartz.qrtz_simple_triggers IS '保存 SimpleTrigger 的重复次数和固定间隔';
COMMENT ON COLUMN quartz.qrtz_simple_triggers.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_simple_triggers.trigger_name IS '对应 Trigger 名称';
COMMENT ON COLUMN quartz.qrtz_simple_triggers.trigger_group IS '对应 Trigger 组名';
COMMENT ON COLUMN quartz.qrtz_simple_triggers.repeat_count IS '首次执行后允许重复的次数';
COMMENT ON COLUMN quartz.qrtz_simple_triggers.repeat_interval IS '相邻两次触发之间的毫秒数';
COMMENT ON COLUMN quartz.qrtz_simple_triggers.times_triggered IS 'SimpleTrigger 已经触发的次数';

COMMENT ON TABLE quartz.qrtz_cron_triggers IS '保存 CronTrigger 表达式和解释时区';
COMMENT ON COLUMN quartz.qrtz_cron_triggers.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_cron_triggers.trigger_name IS '对应 Trigger 名称';
COMMENT ON COLUMN quartz.qrtz_cron_triggers.trigger_group IS '对应 Trigger 组名';
COMMENT ON COLUMN quartz.qrtz_cron_triggers.cron_expression IS 'Quartz Cron 表达式';
COMMENT ON COLUMN quartz.qrtz_cron_triggers.time_zone_id IS '解释 Cron 表达式的可选时区标识';

COMMENT ON TABLE quartz.qrtz_simprop_triggers IS '保存 CalendarIntervalTrigger 和 DailyTimeIntervalTrigger 的类型化属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.trigger_name IS '对应 Trigger 名称';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.trigger_group IS '对应 Trigger 组名';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.str_prop_1 IS 'Quartz TriggerPersistenceDelegate 定义的第一个字符串属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.str_prop_2 IS 'Quartz TriggerPersistenceDelegate 定义的第二个字符串属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.str_prop_3 IS 'Quartz TriggerPersistenceDelegate 定义的第三个字符串属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.int_prop_1 IS 'Quartz TriggerPersistenceDelegate 定义的第一个整数属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.int_prop_2 IS 'Quartz TriggerPersistenceDelegate 定义的第二个整数属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.long_prop_1 IS 'Quartz TriggerPersistenceDelegate 定义的第一个长整数属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.long_prop_2 IS 'Quartz TriggerPersistenceDelegate 定义的第二个长整数属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.dec_prop_1 IS 'Quartz TriggerPersistenceDelegate 定义的第一个十进制属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.dec_prop_2 IS 'Quartz TriggerPersistenceDelegate 定义的第二个十进制属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.bool_prop_1 IS 'Quartz TriggerPersistenceDelegate 定义的第一个布尔属性';
COMMENT ON COLUMN quartz.qrtz_simprop_triggers.bool_prop_2 IS 'Quartz TriggerPersistenceDelegate 定义的第二个布尔属性';

COMMENT ON TABLE quartz.qrtz_blob_triggers IS '保存无法映射到类型化列的序列化 Trigger 数据';
COMMENT ON COLUMN quartz.qrtz_blob_triggers.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_blob_triggers.trigger_name IS '对应 Trigger 名称';
COMMENT ON COLUMN quartz.qrtz_blob_triggers.trigger_group IS '对应 Trigger 组名';
COMMENT ON COLUMN quartz.qrtz_blob_triggers.blob_data IS 'Quartz 序列化的 Trigger 属性';

COMMENT ON TABLE quartz.qrtz_calendars IS '保存 Quartz Calendar 名称和序列化定义';
COMMENT ON COLUMN quartz.qrtz_calendars.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_calendars.calendar_name IS 'Scheduler 内唯一的 Calendar 名称';
COMMENT ON COLUMN quartz.qrtz_calendars.calendar IS 'Quartz 序列化的 Calendar 定义';

COMMENT ON TABLE quartz.qrtz_paused_trigger_grps IS '记录 Quartz Scheduler 中整体暂停的 Trigger 组';
COMMENT ON COLUMN quartz.qrtz_paused_trigger_grps.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_paused_trigger_grps.trigger_group IS '当前整体暂停的 Trigger 组名';

COMMENT ON TABLE quartz.qrtz_fired_triggers IS '保存 Quartz 集群已经领取或正在执行的 Trigger 实例';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.entry_id IS '本次 Trigger 执行实例标识';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.trigger_name IS '被触发的 Trigger 名称';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.trigger_group IS '被触发的 Trigger 组名';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.instance_name IS '领取 Trigger 的 Scheduler 实例名';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.fired_time IS '实际触发的 Unix 毫秒时间';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.sched_time IS '原定触发的 Unix 毫秒时间';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.priority IS '本次 Trigger 执行优先级';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.state IS '本次 Trigger 执行的 JobStore 状态';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.job_name IS '本次执行关联的可选 JobDetail 名称';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.job_group IS '本次执行关联的可选 JobDetail 组名';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.is_nonconcurrent IS '本次 JobDetail 是否禁止并发执行';
COMMENT ON COLUMN quartz.qrtz_fired_triggers.requests_recovery IS 'Scheduler 故障后本次执行是否请求恢复';

COMMENT ON TABLE quartz.qrtz_scheduler_state IS '保存 Quartz 集群各 Scheduler 实例的最近一次心跳';
COMMENT ON COLUMN quartz.qrtz_scheduler_state.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_scheduler_state.instance_name IS '集群内唯一的 Scheduler 实例名';
COMMENT ON COLUMN quartz.qrtz_scheduler_state.last_checkin_time IS '实例最近心跳的 Unix 毫秒时间';
COMMENT ON COLUMN quartz.qrtz_scheduler_state.checkin_interval IS '实例声明的心跳间隔毫秒数';

COMMENT ON TABLE quartz.qrtz_locks IS '保存 Quartz JDBC JobStore 使用的逻辑锁名称';
COMMENT ON COLUMN quartz.qrtz_locks.sched_name IS 'Quartz Scheduler 名称';
COMMENT ON COLUMN quartz.qrtz_locks.lock_name IS 'Quartz JobStore 竞争的逻辑锁名称';

-- 组件：project :modules:storage:sca4dj-storage-file
-- 结构：storage-file.sql
CREATE TABLE storage_file (
    id BIGINT PRIMARY KEY,
    uploader_principal_id BIGINT NOT NULL,
    scene VARCHAR(64) NOT NULL,
    target_resource_type VARCHAR(128) NOT NULL,
    target_resource_id VARCHAR(128) NOT NULL,
    backend_id VARCHAR(64) NOT NULL,
    container_name VARCHAR(128) NOT NULL,
    upload_object_key VARCHAR(512) NOT NULL,
    object_key VARCHAR(512),
    filename VARCHAR(255) NOT NULL,
    declared_content_type VARCHAR(128) NOT NULL,
    declared_size_bytes BIGINT NOT NULL,
    content_type VARCHAR(128),
    size_bytes BIGINT,
    status VARCHAR(32) NOT NULL,
    upload_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ready_expires_at TIMESTAMP WITH TIME ZONE,
    upload_object_cleaned SMALLINT NOT NULL DEFAULT 0,
    delete_attempts INTEGER NOT NULL DEFAULT 0,
    next_delete_at TIMESTAMP WITH TIME ZONE,
    delete_lease_owner VARCHAR(128),
    delete_lease_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (backend_id, container_name, upload_object_key),
    UNIQUE (backend_id, container_name, object_key)
);

CREATE INDEX idx_storage_file_cleanup
    ON storage_file (status, next_delete_at, delete_lease_until);
CREATE INDEX idx_storage_file_upload_cleanup
    ON storage_file (upload_object_cleaned, upload_expires_at);

COMMENT ON TABLE storage_file IS '记录文件上传意图、业务用途和实际存储位置';
COMMENT ON COLUMN storage_file.id IS '文件记录全局标识';
COMMENT ON COLUMN storage_file.uploader_principal_id IS '创建文件的 Principal 标识快照；SYSTEM 使用应用配置的全局 Subject ID';
COMMENT ON COLUMN storage_file.scene IS '文件在目标业务资源中的固定使用场景';
COMMENT ON COLUMN storage_file.target_resource_type IS '文件准备绑定或已经绑定的目标 Resource 类型';
COMMENT ON COLUMN storage_file.target_resource_id IS '文件准备绑定或已经绑定的目标 Resource 标识';
COMMENT ON COLUMN storage_file.backend_id IS '创建文件时选择的稳定存储实现标识';
COMMENT ON COLUMN storage_file.container_name IS '存储实现内保存对象的容器或 Bucket 名称';
COMMENT ON COLUMN storage_file.upload_object_key IS '客户端或服务端上传内容时使用的临时对象键';
COMMENT ON COLUMN storage_file.object_key IS '创建上传记录时预分配、完成后用于读取的正式对象键';
COMMENT ON COLUMN storage_file.filename IS '调用方提交并完成安全清理的原始文件名';
COMMENT ON COLUMN storage_file.declared_content_type IS '创建上传意图时声明的媒体类型';
COMMENT ON COLUMN storage_file.declared_size_bytes IS '创建上传意图时声明的文件字节数';
COMMENT ON COLUMN storage_file.content_type IS '完成上传后由存储实现确认的媒体类型';
COMMENT ON COLUMN storage_file.size_bytes IS '完成上传后由存储实现确认的文件字节数';
COMMENT ON COLUMN storage_file.status IS '文件从待上传到可绑定、已绑定或待清理的当前状态';
COMMENT ON COLUMN storage_file.upload_expires_at IS '临时上传意图和上传对象的过期时间';
COMMENT ON COLUMN storage_file.ready_expires_at IS '未及时绑定的 READY 文件过期时间';
COMMENT ON COLUMN storage_file.upload_object_cleaned IS '临时上传对象是否已经确认删除';
COMMENT ON COLUMN storage_file.delete_attempts IS '正式对象已经开始的删除尝试次数';
COMMENT ON COLUMN storage_file.next_delete_at IS '文件清理 Worker 下一次允许领取的时间';
COMMENT ON COLUMN storage_file.delete_lease_owner IS '当前删除租约的 Worker 标识';
COMMENT ON COLUMN storage_file.delete_lease_until IS '当前删除租约截止时间';
COMMENT ON COLUMN storage_file.created_at IS '文件上传意图创建时间';
COMMENT ON COLUMN storage_file.updated_at IS '文件状态最近更新时间，由数据库触发器维护';

CREATE TRIGGER set_storage_file_updated_at
    BEFORE UPDATE ON storage_file
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

-- App：自有结构
-- 结构：app.sql
CREATE TABLE platform_user_profile (
    principal_id BIGINT PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    email VARCHAR(320),
    phone_number VARCHAR(32),
    avatar_file_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE platform_user_profile IS '记录示例 App 的 Platform 用户资料';
COMMENT ON COLUMN platform_user_profile.principal_id IS '资料所属 IAM Principal 标识';
COMMENT ON COLUMN platform_user_profile.display_name IS '示例 App 展示的用户名称';
COMMENT ON COLUMN platform_user_profile.email IS '可选联系邮箱';
COMMENT ON COLUMN platform_user_profile.phone_number IS '可选规范 E.164 手机号';
COMMENT ON COLUMN platform_user_profile.avatar_file_id IS '当前绑定的头像文件标识';
COMMENT ON COLUMN platform_user_profile.created_at IS '资料创建时间';
COMMENT ON COLUMN platform_user_profile.updated_at IS '资料最近更新时间';
COMMENT ON COLUMN platform_user_profile.deleted IS '逻辑删除标记；0 表示保留，1 表示已删除';

CREATE INDEX idx_platform_user_profile_avatar_file
    ON platform_user_profile (avatar_file_id)
    WHERE avatar_file_id IS NOT NULL;

CREATE TABLE app_iot_product_profile (
    product_id BIGINT PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE app_iot_device_profile (
    device_id BIGINT PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE app_iot_device_creation_request (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_key_id VARCHAR(128),
    response_ciphertext BYTEA,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_iot_device_creation_expiry
    ON app_iot_device_creation_request (expires_at);

COMMENT ON TABLE app_iot_product_profile IS '保存 Sca4d Server 对 IoT Product 的启停状态';
COMMENT ON COLUMN app_iot_product_profile.product_id IS '状态所属 IoT Product 标识';
COMMENT ON COLUMN app_iot_product_profile.status IS 'Sca4d Server 是否允许该 Product 继续创建或管理 Device';
COMMENT ON COLUMN app_iot_product_profile.created_at IS 'Product 应用资料创建时间';
COMMENT ON COLUMN app_iot_product_profile.updated_at IS 'Product 启停状态最近更新时间，由数据库触发器维护';

COMMENT ON TABLE app_iot_device_profile IS '保存 Sca4d Server 对 IoT Device 的启停状态';
COMMENT ON COLUMN app_iot_device_profile.device_id IS '状态所属 IoT Device 标识';
COMMENT ON COLUMN app_iot_device_profile.status IS 'Sca4d Server 是否允许该 Device 认证和执行业务操作';
COMMENT ON COLUMN app_iot_device_profile.created_at IS 'Device 应用资料创建时间';
COMMENT ON COLUMN app_iot_device_profile.updated_at IS 'Device 启停状态最近更新时间，由数据库触发器维护';

COMMENT ON TABLE app_iot_device_creation_request IS '在十分钟窗口内保存加密的 Device 创建幂等结果';
COMMENT ON COLUMN app_iot_device_creation_request.idempotency_key IS '调用方在创建重试中保持不变的幂等键';
COMMENT ON COLUMN app_iot_device_creation_request.request_hash IS '规范化创建请求的 SHA-256 小写十六进制摘要';
COMMENT ON COLUMN app_iot_device_creation_request.status IS '创建请求当前为 PENDING 或已经保存响应的 COMPLETE';
COMMENT ON COLUMN app_iot_device_creation_request.response_key_id IS '加密创建响应使用的 SecretKeyring 密钥标识';
COMMENT ON COLUMN app_iot_device_creation_request.response_ciphertext IS '只允许在幂等窗口内重放的加密创建响应';
COMMENT ON COLUMN app_iot_device_creation_request.expires_at IS '幂等键和加密响应停止复用的时间';
COMMENT ON COLUMN app_iot_device_creation_request.created_at IS '首次登记 Device 创建请求的时间';
COMMENT ON COLUMN app_iot_device_creation_request.updated_at IS '请求状态最近更新时间，由数据库触发器维护';

CREATE TRIGGER set_platform_user_profile_updated_at
    BEFORE UPDATE ON platform_user_profile
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_app_iot_product_profile_updated_at
    BEFORE UPDATE ON app_iot_product_profile
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_app_iot_device_profile_updated_at
    BEFORE UPDATE ON app_iot_device_profile
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();

CREATE TRIGGER set_app_iot_device_creation_request_updated_at
    BEFORE UPDATE ON app_iot_device_creation_request
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();
