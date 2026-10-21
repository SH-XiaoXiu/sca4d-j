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
COMMENT ON COLUMN iam_system_identity.created_at IS '系统身份记录创建时间';

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
