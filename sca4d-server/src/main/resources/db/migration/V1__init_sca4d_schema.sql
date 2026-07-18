-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V1__init_iam_and_system_tables.sql

CREATE TABLE IF NOT EXISTS iam_subject (
    id BIGINT PRIMARY KEY,
    display_name VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam_subject IS '登录身份/自然人主体';
COMMENT ON COLUMN iam_subject.id IS 'Subject 主键';
COMMENT ON COLUMN iam_subject.display_name IS '显示名称';
COMMENT ON COLUMN iam_subject.status IS '身份状态';
COMMENT ON COLUMN iam_subject.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_subject.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_subject.version IS '乐观锁版本号';
COMMENT ON COLUMN iam_subject.deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS iam_account (
    id BIGINT PRIMARY KEY,
    subject_id BIGINT NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam_account IS 'Subject 在一个身份作用域中的登录账号';
COMMENT ON COLUMN iam_account.id IS 'Account 主键';
COMMENT ON COLUMN iam_account.subject_id IS '所属 Subject 主键';
COMMENT ON COLUMN iam_account.scope_type IS '身份作用域类型：GLOBAL 或 TENANT';
COMMENT ON COLUMN iam_account.scope_id IS 'TENANT 作用域的租户主键；GLOBAL 作用域为空';
COMMENT ON COLUMN iam_account.status IS 'Account 登录状态';
COMMENT ON COLUMN iam_account.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_account.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_account.version IS '乐观锁版本号';
COMMENT ON COLUMN iam_account.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_account_global_subject
    ON iam_account (subject_id)
    WHERE scope_type = 'GLOBAL' AND deleted = 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_account_tenant_subject
    ON iam_account (subject_id, scope_id)
    WHERE scope_type = 'TENANT' AND deleted = 0;

CREATE INDEX IF NOT EXISTS idx_iam_account_scope
    ON iam_account (scope_type, scope_id, status, deleted);

CREATE TABLE IF NOT EXISTS iam_account_identifier (
    id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_id BIGINT,
    identifier_type VARCHAR(32) NOT NULL,
    value VARCHAR(128) NOT NULL,
    normalized_value VARCHAR(128) NOT NULL,
    contact_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam_account_identifier IS 'Account 在身份作用域内使用的登录标识';
COMMENT ON COLUMN iam_account_identifier.id IS 'Identifier 主键';
COMMENT ON COLUMN iam_account_identifier.account_id IS '所属 Account 主键';
COMMENT ON COLUMN iam_account_identifier.scope_type IS '复制 Account 的不可变作用域类型，用于登录标识唯一索引';
COMMENT ON COLUMN iam_account_identifier.scope_id IS '复制 Account 的不可变租户作用域主键';
COMMENT ON COLUMN iam_account_identifier.identifier_type IS 'USERNAME 或 EMAIL';
COMMENT ON COLUMN iam_account_identifier.value IS '用户输入和页面展示使用的原值';
COMMENT ON COLUMN iam_account_identifier.normalized_value IS '登录解析和唯一索引使用的 NFKC 规范值';
COMMENT ON COLUMN iam_account_identifier.contact_id IS 'EMAIL Identifier 对应的已验证 Contact；USERNAME 为空';
COMMENT ON COLUMN iam_account_identifier.status IS '登录标识状态';
COMMENT ON COLUMN iam_account_identifier.version IS '乐观锁版本号';
COMMENT ON COLUMN iam_account_identifier.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_account_identifier_account_type
    ON iam_account_identifier (account_id, identifier_type)
    WHERE deleted = 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_account_identifier_global
    ON iam_account_identifier (identifier_type, normalized_value)
    WHERE scope_type = 'GLOBAL' AND deleted = 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_account_identifier_tenant
    ON iam_account_identifier (scope_id, identifier_type, normalized_value)
    WHERE scope_type = 'TENANT' AND deleted = 0;

CREATE INDEX IF NOT EXISTS idx_iam_account_identifier_account
    ON iam_account_identifier (account_id, status, deleted);

CREATE TABLE IF NOT EXISTS iam_contact (
    id BIGINT PRIMARY KEY,
    subject_id BIGINT NOT NULL,
    contact_type VARCHAR(32) NOT NULL,
    value VARCHAR(256) NOT NULL,
    normalized_value VARCHAR(256) NOT NULL,
    primary_contact BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP WITH TIME ZONE,
    verified_by_provider_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam_contact IS 'Subject 的邮箱或电话资料';
COMMENT ON COLUMN iam_contact.id IS 'Contact 主键';
COMMENT ON COLUMN iam_contact.subject_id IS '所属 Subject 主键';
COMMENT ON COLUMN iam_contact.contact_type IS 'EMAIL 或 PHONE';
COMMENT ON COLUMN iam_contact.value IS '用户输入和页面展示使用的原值';
COMMENT ON COLUMN iam_contact.normalized_value IS '查重和关联使用的规范值';
COMMENT ON COLUMN iam_contact.primary_contact IS '是否为该 Subject 当前展示和通知使用的主要 Contact';
COMMENT ON COLUMN iam_contact.verified_at IS '邮箱验证码或可信上游确认的时间；未验证时为空';
COMMENT ON COLUMN iam_contact.verified_by_provider_id IS '确认该 Contact 的 IdentityProvider；邮箱验证码确认为空';
COMMENT ON COLUMN iam_contact.status IS 'Contact 状态';
COMMENT ON COLUMN iam_contact.version IS '乐观锁版本号';
COMMENT ON COLUMN iam_contact.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_contact_subject_value
    ON iam_contact (subject_id, contact_type, normalized_value)
    WHERE deleted = 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_contact_subject_primary_type
    ON iam_contact (subject_id, contact_type)
    WHERE primary_contact = TRUE AND deleted = 0;

CREATE INDEX IF NOT EXISTS idx_iam_contact_verified_email
    ON iam_contact (normalized_value, subject_id)
    WHERE contact_type = 'EMAIL' AND verified_at IS NOT NULL AND status = 'ACTIVE' AND deleted = 0;

CREATE TABLE IF NOT EXISTS iam_credential (
    id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    credential_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam_credential IS '身份登录凭证';
COMMENT ON COLUMN iam_credential.id IS 'Credential 主键';
COMMENT ON COLUMN iam_credential.account_id IS '所属 Account 主键';
COMMENT ON COLUMN iam_credential.credential_type IS '凭证类型';
COMMENT ON COLUMN iam_credential.status IS '凭证状态';
COMMENT ON COLUMN iam_credential.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_credential.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_credential.version IS '乐观锁版本号';
COMMENT ON COLUMN iam_credential.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_credential_account_type
    ON iam_credential (account_id, credential_type)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_iam_credential_account
    ON iam_credential (account_id, deleted);

CREATE TABLE IF NOT EXISTS iam_password (
    credential_id BIGINT PRIMARY KEY,
    password_hash VARCHAR(512) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE iam_password IS 'PASSWORD Credential 的 Argon2id 摘要';
COMMENT ON COLUMN iam_password.credential_id IS '对应 iam_credential 主键';
COMMENT ON COLUMN iam_password.password_hash IS '包含参数和盐的 Argon2id 编码摘要';

CREATE TABLE IF NOT EXISTS iam_tenant (
    id BIGINT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    tenant_type VARCHAR(32) NOT NULL DEFAULT 'DEFAULT',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam_tenant IS '租户/资源归属空间';
COMMENT ON COLUMN iam_tenant.id IS 'Tenant 主键';
COMMENT ON COLUMN iam_tenant.code IS '租户编码';
COMMENT ON COLUMN iam_tenant.name IS '租户名称';
COMMENT ON COLUMN iam_tenant.tenant_type IS '租户类型';
COMMENT ON COLUMN iam_tenant.status IS '租户状态';
COMMENT ON COLUMN iam_tenant.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_tenant.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_tenant.version IS '乐观锁版本号';
COMMENT ON COLUMN iam_tenant.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_tenant_code
    ON iam_tenant (code)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS iam_tenant_membership (
    id BIGINT PRIMARY KEY,
    subject_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam_tenant_membership IS 'Subject 加入 Tenant 的成员关系';
COMMENT ON COLUMN iam_tenant_membership.id IS 'Membership 主键';
COMMENT ON COLUMN iam_tenant_membership.subject_id IS '成员 Subject 主键';
COMMENT ON COLUMN iam_tenant_membership.tenant_id IS '成员所属 Tenant 主键';
COMMENT ON COLUMN iam_tenant_membership.status IS '成员关系状态';
COMMENT ON COLUMN iam_tenant_membership.joined_at IS '成员接受邀请或被管理员加入的时间';
COMMENT ON COLUMN iam_tenant_membership.version IS '乐观锁版本号';
COMMENT ON COLUMN iam_tenant_membership.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_tenant_membership_subject_tenant
    ON iam_tenant_membership (subject_id, tenant_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_iam_tenant_membership_tenant_status
    ON iam_tenant_membership (tenant_id, status, deleted);

CREATE TABLE IF NOT EXISTS iam_principal (
    id BIGINT PRIMARY KEY,
    subject_id BIGINT,
    tenant_id BIGINT,
    membership_id BIGINT,
    principal_type VARCHAR(32) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam_principal IS '统一业务参与主体';
COMMENT ON COLUMN iam_principal.id IS 'Principal 主键';
COMMENT ON COLUMN iam_principal.subject_id IS '关联 Subject 主键';
COMMENT ON COLUMN iam_principal.tenant_id IS '关联 Tenant 主键';
COMMENT ON COLUMN iam_principal.membership_id IS 'TENANT Principal 对应的 Membership 主键';
COMMENT ON COLUMN iam_principal.principal_type IS '主体类型';
COMMENT ON COLUMN iam_principal.display_name IS '主体显示名称';
COMMENT ON COLUMN iam_principal.status IS '主体状态';
COMMENT ON COLUMN iam_principal.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_principal.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_principal.version IS '乐观锁版本号';
COMMENT ON COLUMN iam_principal.deleted IS '逻辑删除标记';

CREATE INDEX IF NOT EXISTS idx_iam_principal_subject
    ON iam_principal (subject_id, deleted);

CREATE INDEX IF NOT EXISTS idx_iam_principal_tenant
    ON iam_principal (tenant_id, deleted);

CREATE INDEX IF NOT EXISTS idx_iam_principal_type
    ON iam_principal (principal_type, deleted);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_principal_subject_type_tenant
    ON iam_principal (
        subject_id,
        principal_type,
        COALESCE(tenant_id, -1)
    )
    WHERE deleted = 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_principal_membership
    ON iam_principal (membership_id)
    WHERE membership_id IS NOT NULL AND deleted = 0;

CREATE TABLE IF NOT EXISTS iam_default_profile (
    principal_id BIGINT PRIMARY KEY,
    avatar_file_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam_default_profile IS 'DEFAULT Principal 的默认资料';
COMMENT ON COLUMN iam_default_profile.principal_id IS 'DEFAULT Principal 主键';
COMMENT ON COLUMN iam_default_profile.avatar_file_id IS 'Subject 默认头像文件主键';
COMMENT ON COLUMN iam_default_profile.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_default_profile.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_default_profile.version IS '乐观锁版本号';
COMMENT ON COLUMN iam_default_profile.deleted IS '逻辑删除标记';

CREATE INDEX IF NOT EXISTS idx_iam_default_profile_avatar
    ON iam_default_profile (avatar_file_id)
    WHERE deleted = 0;

INSERT INTO iam_subject (id, display_name, status)
VALUES (1, 'SYSTEM', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO iam_account (id, subject_id, scope_type, scope_id, status)
VALUES (1, 1, 'GLOBAL', NULL, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO iam_principal (id, subject_id, tenant_id, principal_type, display_name, status)
VALUES (1, 1, NULL, 'SYSTEM', 'SYSTEM', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS iam_permission (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    permission_type VARCHAR(32) NOT NULL DEFAULT 'ACTION',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam_permission IS '权限定义';
COMMENT ON COLUMN iam_permission.id IS 'Permission 主键';
COMMENT ON COLUMN iam_permission.parent_id IS '父权限主键';
COMMENT ON COLUMN iam_permission.code IS '权限编码';
COMMENT ON COLUMN iam_permission.name IS '权限名称';
COMMENT ON COLUMN iam_permission.permission_type IS '权限类型';
COMMENT ON COLUMN iam_permission.sort_order IS '排序值';
COMMENT ON COLUMN iam_permission.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_permission.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_permission.version IS '乐观锁版本号';
COMMENT ON COLUMN iam_permission.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_permission_code
    ON iam_permission (code)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS iam_menu (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT,
    permission_code VARCHAR(128),
    path VARCHAR(255) NOT NULL,
    title VARCHAR(128) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    visible SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam_menu IS '后台菜单定义';
COMMENT ON COLUMN iam_menu.id IS 'Menu 主键';
COMMENT ON COLUMN iam_menu.parent_id IS '父菜单主键';
COMMENT ON COLUMN iam_menu.permission_code IS '关联权限编码';
COMMENT ON COLUMN iam_menu.path IS '前端路由路径';
COMMENT ON COLUMN iam_menu.title IS '菜单标题';
COMMENT ON COLUMN iam_menu.sort_order IS '排序值';
COMMENT ON COLUMN iam_menu.visible IS '是否显示';
COMMENT ON COLUMN iam_menu.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_menu.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_menu.version IS '乐观锁版本号';
COMMENT ON COLUMN iam_menu.deleted IS '逻辑删除标记';

CREATE INDEX IF NOT EXISTS idx_iam_menu_parent
    ON iam_menu (parent_id, deleted);

CREATE TABLE IF NOT EXISTS iam_role (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam_role IS '角色定义';
COMMENT ON COLUMN iam_role.id IS 'Role 主键';
COMMENT ON COLUMN iam_role.tenant_id IS '作用域租户主键';
COMMENT ON COLUMN iam_role.code IS '角色编码';
COMMENT ON COLUMN iam_role.name IS '角色名称';
COMMENT ON COLUMN iam_role.status IS '角色状态';
COMMENT ON COLUMN iam_role.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_role.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN iam_role.version IS '乐观锁版本号';
COMMENT ON COLUMN iam_role.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_role_code_tenant
    ON iam_role (code, COALESCE(tenant_id, -1))
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS iam_role_permission (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE iam_role_permission IS '角色与权限绑定关系';
COMMENT ON COLUMN iam_role_permission.id IS 'RolePermission 主键';
COMMENT ON COLUMN iam_role_permission.role_id IS '角色主键';
COMMENT ON COLUMN iam_role_permission.permission_id IS '权限主键';
COMMENT ON COLUMN iam_role_permission.created_at IS '创建时间，带时区，默认按东八区查看';

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_role_permission
    ON iam_role_permission (role_id, permission_id);

CREATE TABLE IF NOT EXISTS iam_principal_role (
    id BIGINT PRIMARY KEY,
    principal_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE iam_principal_role IS '主体与角色绑定关系';
COMMENT ON COLUMN iam_principal_role.id IS 'PrincipalRole 主键';
COMMENT ON COLUMN iam_principal_role.principal_id IS '主体主键';
COMMENT ON COLUMN iam_principal_role.role_id IS '角色主键';
COMMENT ON COLUMN iam_principal_role.created_at IS '创建时间，带时区，默认按东八区查看';

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_principal_role
    ON iam_principal_role (principal_id, role_id);

CREATE TABLE IF NOT EXISTS sys_log (
    id BIGINT PRIMARY KEY,
    principal_id BIGINT,
    subject_id BIGINT,
    account_id BIGINT,
    module_code VARCHAR(64) NOT NULL,
    action_code VARCHAR(128) NOT NULL,
    result VARCHAR(32) NOT NULL,
    request_uri VARCHAR(500),
    detail_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_log IS '系统操作日志';
COMMENT ON COLUMN sys_log.id IS '系统日志主键';
COMMENT ON COLUMN sys_log.principal_id IS '关联 Principal 主键';
COMMENT ON COLUMN sys_log.subject_id IS '关联 Subject 主键';
COMMENT ON COLUMN sys_log.account_id IS '认证使用的 Account 主键';
COMMENT ON COLUMN sys_log.module_code IS '模块编码';
COMMENT ON COLUMN sys_log.action_code IS '动作编码';
COMMENT ON COLUMN sys_log.result IS '执行结果';
COMMENT ON COLUMN sys_log.request_uri IS '请求路径';
COMMENT ON COLUMN sys_log.detail_json IS '扩展详情 JSON';
COMMENT ON COLUMN sys_log.created_at IS '创建时间，带时区，默认按东八区查看';

CREATE INDEX IF NOT EXISTS idx_sys_log_principal_time
    ON sys_log (principal_id, created_at);

CREATE INDEX IF NOT EXISTS idx_sys_log_subject_time
    ON sys_log (subject_id, created_at);

CREATE INDEX IF NOT EXISTS idx_sys_log_account_time
    ON sys_log (account_id, created_at);

CREATE INDEX IF NOT EXISTS idx_sys_log_module_action_time
    ON sys_log (module_code, action_code, created_at);

CREATE INDEX IF NOT EXISTS idx_sys_log_trace_id
    ON sys_log ((detail_json -> 'request' ->> 'traceId'));

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id BIGINT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE sys_dict_type IS '字典类型';
COMMENT ON COLUMN sys_dict_type.id IS '字典类型主键';
COMMENT ON COLUMN sys_dict_type.code IS '字典编码';
COMMENT ON COLUMN sys_dict_type.name IS '字典名称';
COMMENT ON COLUMN sys_dict_type.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN sys_dict_type.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN sys_dict_type.version IS '乐观锁版本号';
COMMENT ON COLUMN sys_dict_type.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_dict_type_code
    ON sys_dict_type (code)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS sys_dict_item (
    id BIGINT PRIMARY KEY,
    type_code VARCHAR(64) NOT NULL,
    item_value VARCHAR(128) NOT NULL,
    item_label VARCHAR(128) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE sys_dict_item IS '字典项';
COMMENT ON COLUMN sys_dict_item.id IS '字典项主键';
COMMENT ON COLUMN sys_dict_item.type_code IS '所属字典编码';
COMMENT ON COLUMN sys_dict_item.item_value IS '字典值';
COMMENT ON COLUMN sys_dict_item.item_label IS '字典标签';
COMMENT ON COLUMN sys_dict_item.sort_order IS '排序值';
COMMENT ON COLUMN sys_dict_item.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN sys_dict_item.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN sys_dict_item.version IS '乐观锁版本号';
COMMENT ON COLUMN sys_dict_item.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_dict_item_value
    ON sys_dict_item (type_code, item_value)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL,
    value_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE sys_config IS '系统配置';
COMMENT ON COLUMN sys_config.id IS '配置主键';
COMMENT ON COLUMN sys_config.config_key IS '配置键';
COMMENT ON COLUMN sys_config.value_json IS '配置 JSON 值';
COMMENT ON COLUMN sys_config.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN sys_config.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN sys_config.version IS '乐观锁版本号';
COMMENT ON COLUMN sys_config.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_config_key
    ON sys_config (config_key)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS sys_file_resource (
    id BIGINT PRIMARY KEY,
    owner_principal_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    upload_expires_at TIMESTAMP WITH TIME ZONE,
    ready_at TIMESTAMP WITH TIME ZONE,
    consumed_at TIMESTAMP WITH TIME ZONE,
    delete_requested_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE sys_file_resource IS '文件资源元数据';
COMMENT ON COLUMN sys_file_resource.id IS '文件资源主键';
COMMENT ON COLUMN sys_file_resource.owner_principal_id IS '归属主体主键';
COMMENT ON COLUMN sys_file_resource.original_filename IS '原始文件名';
COMMENT ON COLUMN sys_file_resource.content_type IS '文件 MIME 类型';
COMMENT ON COLUMN sys_file_resource.size_bytes IS '文件字节大小';
COMMENT ON COLUMN sys_file_resource.status IS '文件状态';
COMMENT ON COLUMN sys_file_resource.upload_expires_at IS '临时上传过期时间';
COMMENT ON COLUMN sys_file_resource.ready_at IS '上传完成时间';
COMMENT ON COLUMN sys_file_resource.consumed_at IS '被业务字段消费时间';
COMMENT ON COLUMN sys_file_resource.delete_requested_at IS '业务请求删除时间';
COMMENT ON COLUMN sys_file_resource.deleted_at IS '对象物理删除完成时间';
COMMENT ON COLUMN sys_file_resource.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN sys_file_resource.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN sys_file_resource.version IS '乐观锁版本号';
COMMENT ON COLUMN sys_file_resource.deleted IS '逻辑删除标记';

CREATE INDEX IF NOT EXISTS idx_sys_file_resource_owner
    ON sys_file_resource (owner_principal_id, deleted);

CREATE INDEX IF NOT EXISTS idx_sys_file_resource_cleanup
    ON sys_file_resource (status, upload_expires_at, ready_at, delete_requested_at)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS sys_file_object (
    id BIGINT PRIMARY KEY,
    file_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    bucket VARCHAR(128) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    temporary_object_key VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_file_object IS '文件对象存储映射';
COMMENT ON COLUMN sys_file_object.id IS '文件对象主键';
COMMENT ON COLUMN sys_file_object.file_id IS '文件资源主键';
COMMENT ON COLUMN sys_file_object.provider IS '存储提供方';
COMMENT ON COLUMN sys_file_object.bucket IS '存储桶';
COMMENT ON COLUMN sys_file_object.object_key IS '对象键';
COMMENT ON COLUMN sys_file_object.temporary_object_key IS '上传期间使用的临时对象键';
COMMENT ON COLUMN sys_file_object.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN sys_file_object.updated_at IS '对象映射更新时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_file_object_file
    ON sys_file_object (file_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_file_object_storage_key
    ON sys_file_object (provider, bucket, object_key);


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V2__init_realtime_im_notification_tables.sql

CREATE TABLE IF NOT EXISTS notification_message (
    id BIGINT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    category VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    sender_principal_type VARCHAR(32),
    sender_principal_id BIGINT,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE notification_message IS '通知消息';
COMMENT ON COLUMN notification_message.id IS '通知消息主键';
COMMENT ON COLUMN notification_message.title IS '通知标题';
COMMENT ON COLUMN notification_message.content IS '通知正文';
COMMENT ON COLUMN notification_message.category IS '通知分类';
COMMENT ON COLUMN notification_message.sender_principal_type IS '发送主体类型';
COMMENT ON COLUMN notification_message.sender_principal_id IS '发送主体主键';
COMMENT ON COLUMN notification_message.payload_json IS '通知扩展载荷';
COMMENT ON COLUMN notification_message.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN notification_message.deleted IS '逻辑删除标记';

CREATE INDEX IF NOT EXISTS idx_notification_message_time
    ON notification_message (created_at, id)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS notification_delivery (
    id BIGINT PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    subject_id BIGINT,
    principal_type VARCHAR(32),
    principal_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'UNREAD',
    delivered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE notification_delivery IS '通知投递记录';
COMMENT ON COLUMN notification_delivery.id IS '通知投递主键';
COMMENT ON COLUMN notification_delivery.notification_id IS '通知消息主键';
COMMENT ON COLUMN notification_delivery.subject_id IS '接收 Subject 主键';
COMMENT ON COLUMN notification_delivery.principal_type IS '接收 Principal 类型';
COMMENT ON COLUMN notification_delivery.principal_id IS '接收 Principal 主键';
COMMENT ON COLUMN notification_delivery.status IS '投递状态';
COMMENT ON COLUMN notification_delivery.delivered_at IS '投递时间';
COMMENT ON COLUMN notification_delivery.read_at IS '阅读时间';
COMMENT ON COLUMN notification_delivery.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN notification_delivery.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN notification_delivery.version IS '乐观锁版本号';
COMMENT ON COLUMN notification_delivery.deleted IS '逻辑删除标记';

CREATE INDEX IF NOT EXISTS idx_notification_delivery_subject
    ON notification_delivery (subject_id, status, created_at, id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_notification_delivery_principal
    ON notification_delivery (principal_type, principal_id, status, created_at, id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_notification_delivery_notification
    ON notification_delivery (notification_id)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS im_conversation (
    id BIGINT PRIMARY KEY,
    conversation_type VARCHAR(32) NOT NULL,
    title VARCHAR(200),
    last_message_id BIGINT,
    last_message_preview VARCHAR(500),
    last_message_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE im_conversation IS 'IM 会话';
COMMENT ON COLUMN im_conversation.id IS '会话主键';
COMMENT ON COLUMN im_conversation.conversation_type IS '会话类型';
COMMENT ON COLUMN im_conversation.title IS '会话标题';
COMMENT ON COLUMN im_conversation.last_message_id IS '最后一条消息主键';
COMMENT ON COLUMN im_conversation.last_message_preview IS '最后一条消息预览';
COMMENT ON COLUMN im_conversation.last_message_at IS '最后一条消息时间';
COMMENT ON COLUMN im_conversation.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN im_conversation.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN im_conversation.version IS '乐观锁版本号';
COMMENT ON COLUMN im_conversation.deleted IS '逻辑删除标记';

CREATE INDEX IF NOT EXISTS idx_im_conversation_last_message
    ON im_conversation (last_message_at, id)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS im_conversation_member (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    principal_type VARCHAR(32) NOT NULL,
    principal_id BIGINT NOT NULL,
    subject_id BIGINT,
    display_name VARCHAR(128),
    member_role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    last_read_message_id BIGINT,
    last_read_at TIMESTAMP WITH TIME ZONE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE im_conversation_member IS 'IM 会话成员';
COMMENT ON COLUMN im_conversation_member.id IS '会话成员主键';
COMMENT ON COLUMN im_conversation_member.conversation_id IS '会话主键';
COMMENT ON COLUMN im_conversation_member.principal_type IS '成员 Principal 类型';
COMMENT ON COLUMN im_conversation_member.principal_id IS '成员 Principal 主键';
COMMENT ON COLUMN im_conversation_member.subject_id IS '成员 Subject 主键，用于用户队列推送';
COMMENT ON COLUMN im_conversation_member.display_name IS '成员显示名称快照';
COMMENT ON COLUMN im_conversation_member.member_role IS '成员角色';
COMMENT ON COLUMN im_conversation_member.last_read_message_id IS '最后已读消息主键';
COMMENT ON COLUMN im_conversation_member.last_read_at IS '最后已读时间';
COMMENT ON COLUMN im_conversation_member.joined_at IS '加入时间';
COMMENT ON COLUMN im_conversation_member.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN im_conversation_member.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN im_conversation_member.version IS '乐观锁版本号';
COMMENT ON COLUMN im_conversation_member.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX IF NOT EXISTS uk_im_conversation_member_principal
    ON im_conversation_member (conversation_id, principal_type, principal_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_im_conversation_member_subject
    ON im_conversation_member (subject_id, conversation_id)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS im_message (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_principal_type VARCHAR(32) NOT NULL,
    sender_principal_id BIGINT NOT NULL,
    sender_subject_id BIGINT,
    content_type VARCHAR(32) NOT NULL DEFAULT 'TEXT',
    content VARCHAR(4000),
    attachment_file_id BIGINT,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE im_message IS 'IM 消息';
COMMENT ON COLUMN im_message.id IS '消息主键';
COMMENT ON COLUMN im_message.conversation_id IS '会话主键';
COMMENT ON COLUMN im_message.sender_principal_type IS '发送主体类型';
COMMENT ON COLUMN im_message.sender_principal_id IS '发送主体主键';
COMMENT ON COLUMN im_message.sender_subject_id IS '发送 Subject 主键';
COMMENT ON COLUMN im_message.content_type IS '消息内容类型';
COMMENT ON COLUMN im_message.content IS '文本内容';
COMMENT ON COLUMN im_message.attachment_file_id IS '附件文件主键，预留给文件模块';
COMMENT ON COLUMN im_message.payload_json IS '消息扩展载荷';
COMMENT ON COLUMN im_message.status IS '消息状态';
COMMENT ON COLUMN im_message.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN im_message.revoked_at IS '撤回时间';
COMMENT ON COLUMN im_message.version IS '乐观锁版本号';
COMMENT ON COLUMN im_message.deleted IS '逻辑删除标记';

CREATE INDEX IF NOT EXISTS idx_im_message_conversation
    ON im_message (conversation_id, created_at, id)
    WHERE deleted = 0;


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V3__init_message_outbox_tables.sql

CREATE TABLE IF NOT EXISTS sys_message_outbox (
    id BIGINT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    business_key VARCHAR(255),
    exchange_name VARCHAR(255) NOT NULL,
    routing_key VARCHAR(255) NOT NULL,
    event_name VARCHAR(128) NOT NULL,
    payload_json JSONB NOT NULL,
    headers_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 10,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE
);

COMMENT ON TABLE sys_message_outbox IS '系统消息 Outbox';
COMMENT ON COLUMN sys_message_outbox.id IS 'Outbox 主键';
COMMENT ON COLUMN sys_message_outbox.message_id IS '消息幂等 ID';
COMMENT ON COLUMN sys_message_outbox.business_key IS '业务幂等键';
COMMENT ON COLUMN sys_message_outbox.exchange_name IS 'RabbitMQ exchange 名称';
COMMENT ON COLUMN sys_message_outbox.routing_key IS 'RabbitMQ routing key';
COMMENT ON COLUMN sys_message_outbox.event_name IS '业务事件名';
COMMENT ON COLUMN sys_message_outbox.payload_json IS '业务事件载荷 JSON';
COMMENT ON COLUMN sys_message_outbox.headers_json IS '消息头 JSON';
COMMENT ON COLUMN sys_message_outbox.status IS '发布状态';
COMMENT ON COLUMN sys_message_outbox.attempts IS '已尝试次数';
COMMENT ON COLUMN sys_message_outbox.max_attempts IS '最大尝试次数';
COMMENT ON COLUMN sys_message_outbox.next_attempt_at IS '下次可发送时间';
COMMENT ON COLUMN sys_message_outbox.last_error IS '最后一次发布错误';
COMMENT ON COLUMN sys_message_outbox.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN sys_message_outbox.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN sys_message_outbox.sent_at IS 'MQ broker confirm 成功时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_message_outbox_message_id
    ON sys_message_outbox (message_id);

CREATE INDEX IF NOT EXISTS idx_sys_message_outbox_due
    ON sys_message_outbox (status, next_attempt_at, id)
    WHERE status IN ('PENDING', 'FAILED', 'SENDING');

CREATE INDEX IF NOT EXISTS idx_sys_message_outbox_business_key
    ON sys_message_outbox (business_key)
    WHERE business_key IS NOT NULL;


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V4__move_menu_to_system_module.sql

ALTER TABLE IF EXISTS iam_menu RENAME TO sys_menu;

ALTER INDEX IF EXISTS idx_iam_menu_parent RENAME TO idx_sys_menu_parent;

COMMENT ON TABLE sys_menu IS '系统导航菜单定义';
COMMENT ON COLUMN sys_menu.id IS 'Menu 主键';
COMMENT ON COLUMN sys_menu.parent_id IS '父菜单主键';
COMMENT ON COLUMN sys_menu.permission_code IS '引用的权限编码';
COMMENT ON COLUMN sys_menu.path IS '前端路由路径';
COMMENT ON COLUMN sys_menu.title IS '菜单标题';
COMMENT ON COLUMN sys_menu.sort_order IS '排序值';
COMMENT ON COLUMN sys_menu.visible IS '是否显示';
COMMENT ON COLUMN sys_menu.created_at IS '创建时间，带时区，默认按东八区查看';
COMMENT ON COLUMN sys_menu.updated_at IS '更新时间，带时区，默认按东八区查看';
COMMENT ON COLUMN sys_menu.version IS '乐观锁版本号';
COMMENT ON COLUMN sys_menu.deleted IS '逻辑删除标记';


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V5__add_iam_cleanup_tasks.sql

CREATE TABLE IF NOT EXISTS iam_cleanup_task (
    id BIGINT PRIMARY KEY,
    task_type VARCHAR(64) NOT NULL,
    resource_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE iam_cleanup_task IS 'IAM 终态删除后的异步关系清理任务';
COMMENT ON COLUMN iam_cleanup_task.task_type IS '待清理资源类型';
COMMENT ON COLUMN iam_cleanup_task.resource_id IS '待清理资源主键';
COMMENT ON COLUMN iam_cleanup_task.status IS '任务执行状态';
COMMENT ON COLUMN iam_cleanup_task.attempts IS '集群节点领取次数';
COMMENT ON COLUMN iam_cleanup_task.last_error IS '最近一次失败摘要';

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_cleanup_task_resource
    ON iam_cleanup_task (task_type, resource_id);

CREATE INDEX IF NOT EXISTS idx_iam_cleanup_task_pending
    ON iam_cleanup_task (status, created_at);


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V6__add_outbox_claim_version.sql

ALTER TABLE sys_message_outbox
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN sys_message_outbox.version IS '抢占租约版本，每次 claim 和状态迁移递增';


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V7__use_boolean_menu_visibility.sql

ALTER TABLE sys_menu
    ALTER COLUMN visible DROP DEFAULT;

ALTER TABLE sys_menu
    ALTER COLUMN visible TYPE BOOLEAN USING visible <> 0;

ALTER TABLE sys_menu
    ALTER COLUMN visible SET DEFAULT TRUE;


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V9__establish_dictionary_contract.sql

ALTER TABLE sys_dict_type
    ADD COLUMN tenant_id BIGINT,
    ADD COLUMN kind VARCHAR(32) NOT NULL DEFAULT 'BUSINESS_CODE',
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'CONFIGURATION',
    ADD COLUMN name_key VARCHAR(255),
    ADD COLUMN name_labels JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN source_ref VARCHAR(500);

UPDATE sys_dict_type
SET name_labels = jsonb_build_object('zh-CN', name)
WHERE name_labels = '{}'::jsonb;

ALTER TABLE sys_dict_type
    DROP COLUMN name;

DROP INDEX IF EXISTS uk_sys_dict_type_code;

CREATE UNIQUE INDEX uk_sys_dict_type_scope_code
    ON sys_dict_type (COALESCE(tenant_id, -1), code);

CREATE INDEX idx_sys_dict_type_scope_status
    ON sys_dict_type (tenant_id, status, sort_order, id)
    WHERE deleted = 0;

ALTER TABLE sys_dict_item
    ADD COLUMN dict_type_id BIGINT,
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'CONFIGURATION',
    ADD COLUMN source_ref VARCHAR(500),
    ADD COLUMN label_key VARCHAR(255),
    ADD COLUMN label_labels JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN tone VARCHAR(32) NOT NULL DEFAULT 'DEFAULT',
    ADD COLUMN selectable BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

UPDATE sys_dict_item item
SET dict_type_id = type.id,
    label_labels = jsonb_build_object('zh-CN', item.item_label)
FROM sys_dict_type type
WHERE type.code = item.type_code
  AND type.tenant_id IS NULL;

ALTER TABLE sys_dict_item
    ALTER COLUMN dict_type_id SET NOT NULL,
    DROP COLUMN type_code,
    DROP COLUMN item_label;

DROP INDEX IF EXISTS uk_sys_dict_item_value;

CREATE UNIQUE INDEX uk_sys_dict_item_type_value_normalized
    ON sys_dict_item (dict_type_id, UPPER(item_value));

CREATE INDEX idx_sys_dict_item_type_status
    ON sys_dict_item (dict_type_id, status, selectable, sort_order, id)
    WHERE deleted = 0;

COMMENT ON COLUMN sys_dict_type.tenant_id IS '租户私有字典所属 Tenant；空表示平台全局字典';
COMMENT ON COLUMN sys_dict_type.kind IS '字典语义类型：代码枚举、业务编码或输入建议';
COMMENT ON COLUMN sys_dict_type.source IS '字典类型稳定事实的维护来源：代码或配置';
COMMENT ON COLUMN sys_dict_type.name_key IS '代码来源字典名称的国际化回退消息键';
COMMENT ON COLUMN sys_dict_type.name_labels IS '可配置字典名称的多语言标签';
COMMENT ON COLUMN sys_dict_type.status IS '字典类型状态';
COMMENT ON COLUMN sys_dict_type.sort_order IS '同一可见范围内的排序值';
COMMENT ON COLUMN sys_dict_type.source_ref IS '代码来源字典的 Java 枚举类型；配置来源为空';
COMMENT ON COLUMN sys_dict_item.dict_type_id IS '所属字典类型主键';
COMMENT ON COLUMN sys_dict_item.source IS '字典项稳定值的维护来源：代码或配置';
COMMENT ON COLUMN sys_dict_item.source_ref IS '代码来源字典项的 Java 枚举常量；配置来源为空';
COMMENT ON COLUMN sys_dict_item.label_key IS '代码来源字典项的国际化回退消息键';
COMMENT ON COLUMN sys_dict_item.label_labels IS '可配置字典项的多语言标签';
COMMENT ON COLUMN sys_dict_item.tone IS '有限的语义展示色调';
COMMENT ON COLUMN sys_dict_item.selectable IS '是否允许在新的输入中选择';
COMMENT ON COLUMN sys_dict_item.status IS '字典项状态';


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V10__harden_dictionary_integrity.sql

-- 字典类型、来源、状态、编码、标签与归属完整性由类型安全模型和 Repository 写入语义维护。


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V11__establish_audit_display_contract.sql

-- 操作日志保存可索引目标和稳定摘要消息引用；当前语言文本只在读取边界生成。
ALTER TABLE sys_log
    ADD COLUMN target_type VARCHAR(64),
    ADD COLUMN target_id VARCHAR(128),
    ADD COLUMN subject_display_name VARCHAR(128),
    ADD COLUMN principal_display_name VARCHAR(128);

-- 历史动作必须全部属于当前审计目录；发现目录外事实时停止迁移，要求先显式补充目录和消息资源。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sys_log log
        WHERE (log.module_code, log.action_code) NOT IN (
            VALUES
                ('authentication.login', 'password'),
                ('authentication.sessions', 'logout'),
                ('platform.accounts', 'create'),
                ('platform.accounts', 'update'),
                ('platform.accounts', 'change-status'),
                ('platform.accounts', 'change-platform-principal-status'),
                ('platform.accounts', 'delete'),
                ('platform.accounts', 'bind-password'),
                ('platform.accounts', 'replace-password'),
                ('platform.accounts', 'change-status'),
                ('platform.accounts', 'delete'),
                ('platform.accounts', 'revoke'),
                ('platform.accounts', 'revoke-all'),
                ('platform.tenants', 'create'),
                ('platform.tenants', 'update'),
                ('platform.tenants', 'change-status'),
                ('platform.tenant-members', 'create-account'),
                ('platform.tenant-members', 'change-status'),
                ('platform.tenant-members', 'replace-roles'),
                ('platform.tenant-dictionaries', 'create'),
                ('platform.tenant-dictionaries', 'update'),
                ('platform.tenant-dictionaries', 'delete'),
                ('platform.tenant-dictionaries', 'create-item'),
                ('platform.tenant-dictionaries', 'update-item'),
                ('platform.tenant-dictionaries', 'delete-item'),
                ('platform.tenant-dictionaries', 'change-sort'),
                ('platform.tenant-dictionaries', 'change-item-sort'),
                ('tenant.tenant-members', 'create-account'),
                ('tenant.tenant-members', 'change-status'),
                ('tenant.tenant-members', 'replace-roles'),
                ('tenant.dictionaries', 'create'),
                ('tenant.dictionaries', 'update'),
                ('tenant.dictionaries', 'delete'),
                ('tenant.dictionaries', 'create-item'),
                ('tenant.dictionaries', 'update-item'),
                ('tenant.dictionaries', 'delete-item'),
                ('tenant.dictionaries', 'change-sort'),
                ('tenant.dictionaries', 'change-item-sort'),
                ('system.dictionaries', 'create'),
                ('system.dictionaries', 'update'),
                ('system.dictionaries', 'delete'),
                ('system.dictionaries', 'create-item'),
                ('system.dictionaries', 'update-item'),
                ('system.dictionaries', 'delete-item'),
                ('system.dictionaries', 'change-sort'),
                ('system.dictionaries', 'change-item-sort')
        )
    ) THEN
        RAISE EXCEPTION 'sys_log contains operations outside the registered audit catalog';
    END IF;
END $$;

UPDATE sys_log
SET target_type = NULLIF(detail_json -> 'target' ->> 'type', ''),
    target_id = NULLIF(detail_json -> 'target' ->> 'id', ''),
    subject_display_name = NULLIF(detail_json -> 'actor' ->> 'subjectDisplayName', ''),
    principal_display_name = NULLIF(detail_json -> 'actor' ->> 'principalDisplayName', ''),
    detail_json = (detail_json - 'summary') || jsonb_build_object(
        'summaryKey', 'audit.summary.' || module_code || '.' || action_code,
        'summaryArgs', '[]'::jsonb
    );

COMMENT ON COLUMN sys_log.target_type IS '目标资源类型编码';
COMMENT ON COLUMN sys_log.target_id IS '目标资源标识';
COMMENT ON COLUMN sys_log.subject_display_name IS '操作发生时的 Subject 展示名称快照';
COMMENT ON COLUMN sys_log.principal_display_name IS '操作发生时的 Principal 展示名称快照';

CREATE INDEX idx_sys_log_target_time
    ON sys_log (target_type, target_id, created_at DESC)
    WHERE target_type IS NOT NULL AND target_id IS NOT NULL;

CREATE INDEX idx_sys_log_actor_option
    ON sys_log (subject_display_name, subject_id)
    WHERE subject_id IS NOT NULL AND subject_display_name IS NOT NULL;


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V13__repair_password_login_audit_payload.sql

-- 早期密码登录审计被过宽的 credential 脱敏片段误伤，资源主键可由索引目标恢复。
UPDATE sys_log
SET detail_json = jsonb_set(
        jsonb_set(
            detail_json,
            '{payload,credentialType}',
            to_jsonb('PASSWORD'::text),
            true
        ),
        '{payload,credentialId}',
        CASE
            WHEN target_type = 'credential' AND target_id ~ '^[0-9]+$'
                THEN to_jsonb(target_id::bigint)
            ELSE 'null'::jsonb
        END,
        true
    )
WHERE detail_json ->> 'payloadType' = 'authentication.password-login'
  AND (
      detail_json -> 'payload' ->> 'credentialType' = '***'
      OR detail_json -> 'payload' ->> 'credentialId' = '***'
  );


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V14__establish_role_management_contract.sql

ALTER TABLE iam_role
    ADD COLUMN IF NOT EXISTS built_in BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE iam_role
SET built_in = TRUE
WHERE deleted = 0
  AND (
      (tenant_id IS NULL AND code = 'PLATFORM_ADMIN')
      OR (tenant_id IS NOT NULL AND code = 'TENANT_ADMIN')
  );

COMMENT ON COLUMN iam_role.built_in IS '是否为代码维护的内置角色；内置角色编码、权限和删除由代码控制';

CREATE INDEX IF NOT EXISTS idx_iam_role_scope_status
    ON iam_role (tenant_id, status, id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_iam_principal_role_role
    ON iam_principal_role (role_id, principal_id);


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V16__establish_system_menu_contract.sql

ALTER TABLE sys_menu
    ADD COLUMN route_code VARCHAR(128),
    ADD COLUMN title_labels JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN icon_code VARCHAR(32) NOT NULL DEFAULT 'MENU';

WITH route_mapping(path, route_code, permission_code, title_zh_cn, title_en_us, icon_code) AS (
    VALUES
        ('/dashboard', 'workspace.dashboard', NULL, '工作台', 'Dashboard', 'DASHBOARD'),
        ('/platform/accounts', 'platform.accounts', 'platform:accounts:read', '平台账号', 'Platform accounts', 'KEYROUND'),
        ('/platform/roles', 'platform.roles', 'platform:roles:read', '平台角色', 'Platform roles', 'ROLES'),
        ('/platform/data-scopes/tenant', 'platform.data-scopes', 'platform:data-scopes:read', '数据范围', 'Data scope', 'DATA_SCOPE'),
        ('/platform/tenants', 'platform.tenants', 'platform:tenants:read', '平台租户', 'Platform tenants', 'TENANTS'),
        ('/tenant/tenants/{tenantId}/members', 'tenant.members', 'tenant:members:read', '成员管理', 'Members', 'KEYROUND'),
        ('/tenant/tenants/{tenantId}/roles', 'tenant.roles', 'tenant:roles:read', '角色管理', 'Roles', 'ROLES'),
        ('/tenant/tenants/{tenantId}/dictionaries', 'tenant.dictionaries', 'tenant:dictionaries:read', '字典管理', 'Dictionaries', 'DICTIONARIES'),
        ('/system/dictionaries', 'system.dictionaries', 'system:dictionaries:read', '字典管理', 'Dictionaries', 'DICTIONARIES'),
        ('/system/menus', 'system.menus', 'system:menus:read', '菜单管理', 'Menus', 'MENUS'),
        ('/system/operation-logs', 'system.operation-logs', 'system:operation-logs:read', '操作日志', 'Operation logs', 'AUDIT_LOGS')
), ranked AS (
    SELECT
        menu.id,
        mapping.route_code,
        mapping.permission_code,
        mapping.title_zh_cn,
        mapping.title_en_us,
        mapping.icon_code,
        ROW_NUMBER() OVER (PARTITION BY mapping.route_code ORDER BY menu.id) AS route_rank
    FROM sys_menu menu
    JOIN route_mapping mapping ON mapping.path = menu.path
    WHERE menu.deleted = 0
)
UPDATE sys_menu menu
SET route_code = CASE WHEN ranked.route_rank = 1 THEN ranked.route_code ELSE NULL END,
    permission_code = CASE WHEN ranked.route_rank = 1 THEN ranked.permission_code ELSE NULL END,
    title_labels = jsonb_build_object(
        'zh-CN', COALESCE(ranked.title_zh_cn, menu.title),
        'en-US', COALESCE(ranked.title_en_us, menu.title)),
    icon_code = ranked.icon_code
FROM ranked
WHERE ranked.id = menu.id;

UPDATE sys_menu
SET permission_code = NULL,
    title_labels = jsonb_build_object('zh-CN', title, 'en-US', title),
    icon_code = 'MENU'
WHERE title_labels = '{}'::jsonb;

ALTER TABLE sys_menu
    DROP COLUMN path,
    DROP COLUMN title;

CREATE UNIQUE INDEX uk_sys_menu_route
    ON sys_menu (route_code)
    WHERE deleted = 0 AND route_code IS NOT NULL;

INSERT INTO sys_menu (
    id, parent_id, permission_code, route_code, title_labels, icon_code, sort_order, visible, version, deleted
)
VALUES
    (-100, NULL, NULL, 'workspace.dashboard', '{"zh-CN":"工作台","en-US":"Dashboard"}', 'DASHBOARD', 10, TRUE, 0, 0),
    (-200, NULL, NULL, NULL, '{"zh-CN":"平台管理","en-US":"Platform"}', 'MENU', 20, TRUE, 0, 0),
    (-210, -200, 'platform:accounts:read', 'platform.accounts', '{"zh-CN":"平台账号","en-US":"Platform accounts"}', 'KEYROUND', 10, TRUE, 0, 0),
    (-220, -200, 'platform:roles:read', 'platform.roles', '{"zh-CN":"平台角色","en-US":"Platform roles"}', 'ROLES', 20, TRUE, 0, 0),
    (-230, -200, 'platform:data-scopes:read', 'platform.data-scopes', '{"zh-CN":"数据范围","en-US":"Data scope"}', 'DATA_SCOPE', 30, TRUE, 0, 0),
    (-240, -200, 'platform:tenants:read', 'platform.tenants', '{"zh-CN":"平台租户","en-US":"Platform tenants"}', 'TENANTS', 40, TRUE, 0, 0),
    (-300, NULL, NULL, NULL, '{"zh-CN":"租户管理","en-US":"Tenant"}', 'MENU', 30, TRUE, 0, 0),
    (-310, -300, 'tenant:members:read', 'tenant.members', '{"zh-CN":"成员管理","en-US":"Members"}', 'KEYROUND', 10, TRUE, 0, 0),
    (-320, -300, 'tenant:roles:read', 'tenant.roles', '{"zh-CN":"角色管理","en-US":"Roles"}', 'ROLES', 20, TRUE, 0, 0),
    (-330, -300, 'tenant:dictionaries:read', 'tenant.dictionaries', '{"zh-CN":"字典管理","en-US":"Dictionaries"}', 'DICTIONARIES', 30, TRUE, 0, 0),
    (-400, NULL, NULL, NULL, '{"zh-CN":"系统管理","en-US":"System"}', 'MENU', 40, TRUE, 0, 0),
    (-410, -400, 'system:dictionaries:read', 'system.dictionaries', '{"zh-CN":"字典管理","en-US":"Dictionaries"}', 'DICTIONARIES', 10, TRUE, 0, 0),
    (-420, -400, 'system:menus:read', 'system.menus', '{"zh-CN":"菜单管理","en-US":"Menus"}', 'MENUS', 20, TRUE, 0, 0),
    (-430, -400, 'system:operation-logs:read', 'system.operation-logs', '{"zh-CN":"操作日志","en-US":"Operation logs"}', 'AUDIT_LOGS', 30, TRUE, 0, 0)
ON CONFLICT DO NOTHING;

COMMENT ON COLUMN sys_menu.route_code IS '代码导航路由目录中的稳定编码；为空时表示结构分组';
COMMENT ON COLUMN sys_menu.permission_code IS '路由目录绑定的代码权限编码；结构分组为空';
COMMENT ON COLUMN sys_menu.title_labels IS '管理员维护的 zh-CN/en-US 导航标题';
COMMENT ON COLUMN sys_menu.icon_code IS '管理端支持的类型安全图标编码';


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V17__preserve_tenant_code_history.sql

DO $$
BEGIN
    IF EXISTS (
        SELECT code
        FROM iam_tenant
        GROUP BY code
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'iam_tenant contains duplicate historical codes; resolve ownership before V17';
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS iam_tenant_code (
    code VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_iam_tenant_code PRIMARY KEY (code)
);

COMMENT ON TABLE iam_tenant_code IS '租户当前及历史编码永久占用事实';
COMMENT ON COLUMN iam_tenant_code.code IS '全生命周期不可复用的租户编码';
COMMENT ON COLUMN iam_tenant_code.tenant_id IS '首次占用该编码的租户主键';
COMMENT ON COLUMN iam_tenant_code.created_at IS '编码首次占用时间';

CREATE INDEX IF NOT EXISTS idx_iam_tenant_code_tenant
    ON iam_tenant_code (tenant_id, created_at, code);

INSERT INTO iam_tenant_code (code, tenant_id, created_at)
SELECT code, id, created_at
FROM iam_tenant;

CREATE OR REPLACE FUNCTION sca4d_reject_iam_tenant_code_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'iam_tenant_code is append-only'
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_iam_tenant_code_append_only
BEFORE UPDATE OR DELETE ON iam_tenant_code
FOR EACH ROW
EXECUTE FUNCTION sca4d_reject_iam_tenant_code_mutation();


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V18__add_quartz_scheduler_tables.sql

-- Quartz 2.5.2 official PostgreSQL schema. Flyway is the only schema owner;
-- the install script's DROP statements are intentionally excluded.
CREATE TABLE QRTZ_JOB_DETAILS
(
    SCHED_NAME        VARCHAR(120) NOT NULL,
    JOB_NAME          VARCHAR(200) NOT NULL,
    JOB_GROUP         VARCHAR(200) NOT NULL,
    DESCRIPTION       VARCHAR(250) NULL,
    JOB_CLASS_NAME    VARCHAR(250) NOT NULL,
    IS_DURABLE        BOOL         NOT NULL,
    IS_NONCONCURRENT  BOOL         NOT NULL,
    IS_UPDATE_DATA    BOOL         NOT NULL,
    REQUESTS_RECOVERY BOOL         NOT NULL,
    JOB_DATA          BYTEA        NULL,
    PRIMARY KEY (SCHED_NAME, JOB_NAME, JOB_GROUP)
);

CREATE TABLE QRTZ_TRIGGERS
(
    SCHED_NAME     VARCHAR(120) NOT NULL,
    TRIGGER_NAME   VARCHAR(200) NOT NULL,
    TRIGGER_GROUP  VARCHAR(200) NOT NULL,
    JOB_NAME       VARCHAR(200) NOT NULL,
    JOB_GROUP      VARCHAR(200) NOT NULL,
    DESCRIPTION    VARCHAR(250) NULL,
    NEXT_FIRE_TIME BIGINT       NULL,
    PREV_FIRE_TIME BIGINT       NULL,
    PRIORITY       INTEGER      NULL,
    TRIGGER_STATE  VARCHAR(16)  NOT NULL,
    TRIGGER_TYPE   VARCHAR(8)   NOT NULL,
    START_TIME     BIGINT       NOT NULL,
    END_TIME       BIGINT       NULL,
    CALENDAR_NAME  VARCHAR(200) NULL,
    MISFIRE_INSTR  SMALLINT     NULL,
    JOB_DATA       BYTEA        NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

CREATE TABLE QRTZ_SIMPLE_TRIGGERS
(
    SCHED_NAME      VARCHAR(120) NOT NULL,
    TRIGGER_NAME    VARCHAR(200) NOT NULL,
    TRIGGER_GROUP   VARCHAR(200) NOT NULL,
    REPEAT_COUNT    BIGINT       NOT NULL,
    REPEAT_INTERVAL BIGINT       NOT NULL,
    TIMES_TRIGGERED BIGINT       NOT NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

CREATE TABLE QRTZ_CRON_TRIGGERS
(
    SCHED_NAME      VARCHAR(120) NOT NULL,
    TRIGGER_NAME    VARCHAR(200) NOT NULL,
    TRIGGER_GROUP   VARCHAR(200) NOT NULL,
    CRON_EXPRESSION VARCHAR(120) NOT NULL,
    TIME_ZONE_ID    VARCHAR(80),
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

CREATE TABLE QRTZ_SIMPROP_TRIGGERS
(
    SCHED_NAME    VARCHAR(120)   NOT NULL,
    TRIGGER_NAME  VARCHAR(200)   NOT NULL,
    TRIGGER_GROUP VARCHAR(200)   NOT NULL,
    STR_PROP_1    VARCHAR(512)   NULL,
    STR_PROP_2    VARCHAR(512)   NULL,
    STR_PROP_3    VARCHAR(512)   NULL,
    INT_PROP_1    INT            NULL,
    INT_PROP_2    INT            NULL,
    LONG_PROP_1   BIGINT         NULL,
    LONG_PROP_2   BIGINT         NULL,
    DEC_PROP_1    NUMERIC(13, 4) NULL,
    DEC_PROP_2    NUMERIC(13, 4) NULL,
    BOOL_PROP_1   BOOL           NULL,
    BOOL_PROP_2   BOOL           NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

CREATE TABLE QRTZ_BLOB_TRIGGERS
(
    SCHED_NAME    VARCHAR(120) NOT NULL,
    TRIGGER_NAME  VARCHAR(200) NOT NULL,
    TRIGGER_GROUP VARCHAR(200) NOT NULL,
    BLOB_DATA     BYTEA        NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

CREATE TABLE QRTZ_CALENDARS
(
    SCHED_NAME    VARCHAR(120) NOT NULL,
    CALENDAR_NAME VARCHAR(200) NOT NULL,
    CALENDAR      BYTEA        NOT NULL,
    PRIMARY KEY (SCHED_NAME, CALENDAR_NAME)
);

CREATE TABLE QRTZ_PAUSED_TRIGGER_GRPS
(
    SCHED_NAME    VARCHAR(120) NOT NULL,
    TRIGGER_GROUP VARCHAR(200) NOT NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_GROUP)
);

CREATE TABLE QRTZ_FIRED_TRIGGERS
(
    SCHED_NAME        VARCHAR(120) NOT NULL,
    ENTRY_ID          VARCHAR(95)  NOT NULL,
    TRIGGER_NAME      VARCHAR(200) NOT NULL,
    TRIGGER_GROUP     VARCHAR(200) NOT NULL,
    INSTANCE_NAME     VARCHAR(200) NOT NULL,
    FIRED_TIME        BIGINT       NOT NULL,
    SCHED_TIME        BIGINT       NOT NULL,
    PRIORITY          INTEGER      NOT NULL,
    STATE             VARCHAR(16)  NOT NULL,
    JOB_NAME          VARCHAR(200) NULL,
    JOB_GROUP         VARCHAR(200) NULL,
    IS_NONCONCURRENT  BOOL         NULL,
    REQUESTS_RECOVERY BOOL         NULL,
    PRIMARY KEY (SCHED_NAME, ENTRY_ID)
);

CREATE TABLE QRTZ_SCHEDULER_STATE
(
    SCHED_NAME        VARCHAR(120) NOT NULL,
    INSTANCE_NAME     VARCHAR(200) NOT NULL,
    LAST_CHECKIN_TIME BIGINT       NOT NULL,
    CHECKIN_INTERVAL  BIGINT       NOT NULL,
    PRIMARY KEY (SCHED_NAME, INSTANCE_NAME)
);

CREATE TABLE QRTZ_LOCKS
(
    SCHED_NAME VARCHAR(120) NOT NULL,
    LOCK_NAME  VARCHAR(40)  NOT NULL,
    PRIMARY KEY (SCHED_NAME, LOCK_NAME)
);

CREATE INDEX IDX_QRTZ_J_REQ_RECOVERY ON QRTZ_JOB_DETAILS (SCHED_NAME, REQUESTS_RECOVERY);
CREATE INDEX IDX_QRTZ_J_GRP ON QRTZ_JOB_DETAILS (SCHED_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_J ON QRTZ_TRIGGERS (SCHED_NAME, JOB_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_JG ON QRTZ_TRIGGERS (SCHED_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_C ON QRTZ_TRIGGERS (SCHED_NAME, CALENDAR_NAME);
CREATE INDEX IDX_QRTZ_T_G ON QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_GROUP);
CREATE INDEX IDX_QRTZ_T_STATE ON QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_N_STATE ON QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_N_G_STATE ON QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_GROUP, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_NEXT_FIRE_TIME ON QRTZ_TRIGGERS (SCHED_NAME, NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_ST ON QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_STATE, NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_MISFIRE ON QRTZ_TRIGGERS (SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE ON QRTZ_TRIGGERS (SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE_GRP
    ON QRTZ_TRIGGERS (SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME, TRIGGER_GROUP, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_FT_TRIG_INST_NAME ON QRTZ_FIRED_TRIGGERS (SCHED_NAME, INSTANCE_NAME);
CREATE INDEX IDX_QRTZ_FT_INST_JOB_REQ_RCVRY
    ON QRTZ_FIRED_TRIGGERS (SCHED_NAME, INSTANCE_NAME, REQUESTS_RECOVERY);
CREATE INDEX IDX_QRTZ_FT_J_G ON QRTZ_FIRED_TRIGGERS (SCHED_NAME, JOB_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_FT_JG ON QRTZ_FIRED_TRIGGERS (SCHED_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_FT_T_G ON QRTZ_FIRED_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP);
CREATE INDEX IDX_QRTZ_FT_TG ON QRTZ_FIRED_TRIGGERS (SCHED_NAME, TRIGGER_GROUP);


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V19__establish_schedule_contract.sql

CREATE TABLE schedule_definition
(
    id                      BIGINT PRIMARY KEY,
    name                    VARCHAR(200) NOT NULL,
    schedule_type           VARCHAR(16) NOT NULL,
    execute_at              TIMESTAMP WITH TIME ZONE,
    cron_expression         VARCHAR(120),
    time_zone               VARCHAR(80),
    status                  VARCHAR(16) NOT NULL,
    task_type               VARCHAR(128) NOT NULL,
    task_payload            JSONB NOT NULL,
    task_payload_version    INTEGER NOT NULL,
    definition_fingerprint  VARCHAR(64) NOT NULL,
    synchronization_error   VARCHAR(500),
    created_by_principal_id BIGINT NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted                 SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_schedule_definition_page
    ON schedule_definition (status, created_at DESC, id DESC)
    WHERE deleted = 0;
CREATE INDEX idx_schedule_definition_task
    ON schedule_definition (task_type, schedule_type, status)
    WHERE deleted = 0;

CREATE TABLE schedule_execution
(
    execution_id        BIGINT PRIMARY KEY,
    schedule_id         BIGINT NOT NULL,
    fire_key            VARCHAR(200) NOT NULL,
    trigger_source      VARCHAR(16) NOT NULL,
    scheduled_fire_time TIMESTAMP WITH TIME ZONE,
    actual_fire_time    TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at        TIMESTAMP WITH TIME ZONE,
    status              VARCHAR(16) NOT NULL,
    task_type           VARCHAR(128) NOT NULL,
    result_summary      VARCHAR(1000),
    safe_error_type     VARCHAR(200),
    safe_error_message  VARCHAR(500),
    executed_instance_id VARCHAR(200),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    UNIQUE (schedule_id, fire_key)
);

CREATE INDEX idx_schedule_execution_page
    ON schedule_execution (schedule_id, created_at DESC, execution_id DESC);

CREATE TABLE schedule_job_runtime
(
    job_group            VARCHAR(200) NOT NULL,
    job_name             VARCHAR(200) NOT NULL,
    last_actual_fire_time TIMESTAMP WITH TIME ZONE,
    last_result          VARCHAR(16),
    last_duration_millis BIGINT,
    safe_error_summary   VARCHAR(500),
    executed_instance_id VARCHAR(200),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (job_group, job_name)
);

COMMENT ON TABLE schedule_definition IS 'Schedule 业务定义唯一事实来源';
COMMENT ON TABLE schedule_execution IS 'Schedule 每次计划或人工执行事实';
COMMENT ON TABLE schedule_job_runtime IS '系统 Quartz Job 最近一次运行快照';


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V20__register_schedule_navigation.sql

INSERT INTO sys_menu (id, parent_id, permission_code, route_code, title_labels, icon_code, sort_order, visible, version, deleted)
VALUES
    (-250, -200, 'platform:schedules:read', 'platform.schedules', '{"zh-CN":"业务计划","en-US":"Schedules"}', 'SCHEDULES', 50, TRUE, 0, 0),
    (-425, -400, 'system:scheduled-jobs:read', 'system.scheduled-jobs', '{"zh-CN":"系统任务","en-US":"System jobs"}', 'SYSTEM_JOBS', 25, TRUE, 0, 0)
ON CONFLICT (id) DO NOTHING;


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V21__localize_permission_catalog.sql

ALTER TABLE iam_permission RENAME COLUMN name TO name_key;

COMMENT ON COLUMN iam_permission.name_key IS '代码权限目录声明的稳定本地化消息键';


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V22__establish_tenant_access_acl.sql

CREATE TABLE IF NOT EXISTS tenant_access_grant (
    id BIGINT PRIMARY KEY,
    principal_id BIGINT NOT NULL,
    tenant_id BIGINT,
    access_action VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE tenant_access_grant IS 'PLATFORM Principal 的租户领域访问规则';
COMMENT ON COLUMN tenant_access_grant.id IS '访问规则主键';
COMMENT ON COLUMN tenant_access_grant.principal_id IS '获得租户访问能力的 PLATFORM Principal 主键';
COMMENT ON COLUMN tenant_access_grant.tenant_id IS '具体租户主键，空值表示全部租户的默认访问等级';
COMMENT ON COLUMN tenant_access_grant.access_action IS '租户领域最高访问等级：READ、WRITE 或 MANAGE';
COMMENT ON COLUMN tenant_access_grant.created_at IS '规则创建时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_access_grant_principal_tenant
    ON tenant_access_grant (principal_id, COALESCE(tenant_id, 0));

CREATE INDEX IF NOT EXISTS idx_tenant_access_grant_tenant
    ON tenant_access_grant (tenant_id, principal_id)
    WHERE tenant_id IS NOT NULL;

UPDATE sys_menu
SET permission_code = 'platform:tenant-access:read',
    route_code = 'platform.tenant-access',
    title_labels = '{"zh-CN":"租户访问控制","en-US":"Tenant access"}'::jsonb,
    version = version + 1
WHERE route_code = 'platform.data-scopes'
  AND deleted = 0;


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V23__open_menu_icon_catalog.sql

UPDATE sys_menu
SET icon_code = CASE icon_code
    WHEN 'DASHBOARD' THEN 'LayoutDashboard'
    WHEN 'KEYROUND' THEN 'KeyRound'
    WHEN 'ROLES' THEN 'ShieldCheck'
    WHEN 'DATA_SCOPE' THEN 'SlidersHorizontal'
    WHEN 'TENANTS' THEN 'Building2'
    WHEN 'DICTIONARIES' THEN 'BookOpenText'
    WHEN 'MENUS' THEN 'PanelLeft'
    WHEN 'AUDIT_LOGS' THEN 'ScrollText'
    WHEN 'SCHEDULES' THEN 'CalendarClock'
    WHEN 'SYSTEM_JOBS' THEN 'ServerCog'
    WHEN 'MENU' THEN 'FolderTree'
    ELSE icon_code
END;

COMMENT ON COLUMN sys_menu.icon_code IS 'Lucide 图标导出名称';


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V24__establish_default_granted_permissions.sql

ALTER TABLE iam_permission
    ADD COLUMN granted_by_default BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN iam_permission.granted_by_default IS '是否不经角色绑定直接授予全部 Principal';

CREATE SEQUENCE iam_seed_id_seq
    AS BIGINT
    INCREMENT BY -1
    MINVALUE -9223372036854775807
    MAXVALUE -1
    START WITH -1
    NO CYCLE;

COMMENT ON SEQUENCE iam_seed_id_seq IS 'IAM Seed 静态角色和关系使用的负数主键序列';


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V25__register_mail_sender_navigation.sql

INSERT INTO sys_menu (id, parent_id, permission_code, route_code, title_labels, icon_code, sort_order, visible, version, deleted)
VALUES
    (-435, -400, 'system:mail-senders:read', 'system.mail-senders', '{"zh-CN":"邮件发送配置","en-US":"Mail senders"}', 'MailCheck', 28, TRUE, 0, 0)
ON CONFLICT (id) DO NOTHING;


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V26__register_mail_template_navigation.sql

INSERT INTO sys_menu (id, parent_id, permission_code, route_code, title_labels, icon_code, sort_order, visible, version, deleted)
VALUES
    (-436, -400, 'system:mail-templates:read', 'system.mail-templates', '{"zh-CN":"邮件模板","en-US":"Mail templates"}', 'FileText', 29, TRUE, 0, 0)
ON CONFLICT (id) DO NOTHING;


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V27__establish_account_second_factor_policy.sql

ALTER TABLE iam_account
    ADD COLUMN second_factor_required BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN iam_account.second_factor_required IS '创建 Session 前是否必须完成独立第二认证因素';


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V28__establish_mail_infrastructure.sql

CREATE TABLE sys_mail_sender (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    security VARCHAR(32) NOT NULL,
    username VARCHAR(255),
    password_key_id VARCHAR(64),
    password_nonce BYTEA,
    password_ciphertext BYTEA,
    from_address VARCHAR(255) NOT NULL,
    from_name VARCHAR(128),
    reply_to VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE sys_mail_sender IS 'SMTP 邮件发送配置';
COMMENT ON COLUMN sys_mail_sender.id IS '邮件发送配置主键';
COMMENT ON COLUMN sys_mail_sender.name IS '管理页面显示名称';
COMMENT ON COLUMN sys_mail_sender.host IS 'SMTP 服务器主机名或地址';
COMMENT ON COLUMN sys_mail_sender.port IS 'SMTP 服务器端口';
COMMENT ON COLUMN sys_mail_sender.security IS 'NONE、STARTTLS 或 IMPLICIT_TLS';
COMMENT ON COLUMN sys_mail_sender.username IS 'SMTP 登录用户名；匿名连接时为空';
COMMENT ON COLUMN sys_mail_sender.password_key_id IS '加密 SMTP 密码使用的 key-id';
COMMENT ON COLUMN sys_mail_sender.password_nonce IS '加密 SMTP 密码使用的 AES-GCM nonce';
COMMENT ON COLUMN sys_mail_sender.password_ciphertext IS '包含 GCM authentication tag 的 SMTP 密码密文';
COMMENT ON COLUMN sys_mail_sender.from_address IS '默认发件邮箱';
COMMENT ON COLUMN sys_mail_sender.from_name IS '默认发件人名称';
COMMENT ON COLUMN sys_mail_sender.reply_to IS '默认回复邮箱';
COMMENT ON COLUMN sys_mail_sender.status IS 'ACTIVE 或 DISABLED';
COMMENT ON COLUMN sys_mail_sender.version IS '乐观锁版本号';
COMMENT ON COLUMN sys_mail_sender.deleted IS '逻辑删除标记';

CREATE INDEX idx_sys_mail_sender_status
    ON sys_mail_sender (status, deleted);

CREATE TABLE sys_mail_template (
    id BIGINT PRIMARY KEY,
    family VARCHAR(64) NOT NULL,
    locale VARCHAR(35) NOT NULL,
    subject_template VARCHAR(512) NOT NULL,
    text_template TEXT NOT NULL,
    html_template TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE sys_mail_template IS '按 family 和 locale 管理的邮件模板';
COMMENT ON COLUMN sys_mail_template.id IS '邮件模板主键';
COMMENT ON COLUMN sys_mail_template.family IS '业务邮件族编码';
COMMENT ON COLUMN sys_mail_template.locale IS '规范化 BCP 47 语言标签';
COMMENT ON COLUMN sys_mail_template.subject_template IS '受限 Mustache 邮件主题模板';
COMMENT ON COLUMN sys_mail_template.text_template IS '受限 Mustache 纯文本正文模板';
COMMENT ON COLUMN sys_mail_template.html_template IS '受限 Mustache HTML 正文模板，渲染后执行 allowlist 清洗';
COMMENT ON COLUMN sys_mail_template.status IS 'ACTIVE 或 DISABLED';
COMMENT ON COLUMN sys_mail_template.version IS '乐观锁版本号';
COMMENT ON COLUMN sys_mail_template.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX uk_sys_mail_template_family_locale
    ON sys_mail_template (family, locale)
    WHERE deleted = 0;

CREATE INDEX idx_sys_mail_template_status
    ON sys_mail_template (status, deleted);


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V29__establish_recovery_code_sets.sql

CREATE TABLE iam_recovery_code_set (
    id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    replaced_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam_recovery_code_set IS 'Account 当前及历史恢复码集合';
COMMENT ON COLUMN iam_recovery_code_set.id IS '恢复码集合主键';
COMMENT ON COLUMN iam_recovery_code_set.account_id IS '所属 Account 主键';
COMMENT ON COLUMN iam_recovery_code_set.issued_at IS '恢复码仅一次展示的签发时间';
COMMENT ON COLUMN iam_recovery_code_set.replaced_at IS '集合被替换或 2FA 关闭的时间；当前集合为空';
COMMENT ON COLUMN iam_recovery_code_set.version IS '集合替换使用的乐观锁版本';

CREATE UNIQUE INDEX uk_iam_recovery_code_set_current
    ON iam_recovery_code_set (account_id)
    WHERE replaced_at IS NULL;

CREATE INDEX idx_iam_recovery_code_set_account
    ON iam_recovery_code_set (account_id, issued_at DESC);

CREATE TABLE iam_recovery_code (
    id BIGINT PRIMARY KEY,
    set_id BIGINT NOT NULL,
    code_hash VARCHAR(512) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE
);

COMMENT ON TABLE iam_recovery_code IS '恢复码 Argon2id 摘要及单次使用状态';
COMMENT ON COLUMN iam_recovery_code.id IS '恢复码记录主键';
COMMENT ON COLUMN iam_recovery_code.set_id IS '所属恢复码集合主键';
COMMENT ON COLUMN iam_recovery_code.code_hash IS '包含随机盐和参数的 Argon2id 编码摘要';
COMMENT ON COLUMN iam_recovery_code.created_at IS '恢复码签发时间';
COMMENT ON COLUMN iam_recovery_code.used_at IS '恢复码成功创建 Proof 的时间；未使用时为空';

CREATE INDEX idx_iam_recovery_code_set_unused
    ON iam_recovery_code (set_id, used_at);


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V30__establish_totp_credentials.sql

CREATE TABLE IF NOT EXISTS iam_totp (
    credential_id BIGINT PRIMARY KEY,
    secret_key_id VARCHAR(64) NOT NULL,
    secret_nonce BYTEA NOT NULL,
    secret_ciphertext BYTEA NOT NULL,
    last_accepted_step BIGINT NOT NULL DEFAULT -1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE iam_totp IS 'TOTP Credential 的加密共享密钥和已消费时间步';
COMMENT ON COLUMN iam_totp.credential_id IS '对应 iam_credential 主键';
COMMENT ON COLUMN iam_totp.secret_key_id IS 'AES-256-GCM keyring 密钥标识';
COMMENT ON COLUMN iam_totp.secret_nonce IS 'AES-256-GCM 随机 nonce';
COMMENT ON COLUMN iam_totp.secret_ciphertext IS '包含 GCM authentication tag 的 TOTP 密钥密文';
COMMENT ON COLUMN iam_totp.last_accepted_step IS '最近一次成功验证码对应的 30 秒 Unix 时间步';


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V31__add_passkey_credentials.sql

DROP INDEX IF EXISTS uk_iam_credential_account_type;

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_credential_account_single_type
    ON iam_credential (account_id, credential_type)
    WHERE deleted = 0 AND credential_type IN ('PASSWORD', 'TOTP');

CREATE TABLE IF NOT EXISTS iam_passkey_user_handle (
    account_id BIGINT PRIMARY KEY,
    user_handle BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE iam_passkey_user_handle IS 'Account 对应的 WebAuthn 随机 user handle';
COMMENT ON COLUMN iam_passkey_user_handle.account_id IS 'user handle 所属 Account 主键';
COMMENT ON COLUMN iam_passkey_user_handle.user_handle IS 'WebAuthn 向认证器公开的不透明用户标识';

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_passkey_user_handle
    ON iam_passkey_user_handle (user_handle);

CREATE TABLE IF NOT EXISTS iam_passkey (
    credential_id BIGINT PRIMARY KEY,
    passkey_credential_id BYTEA NOT NULL,
    public_key_cose BYTEA NOT NULL,
    signature_count BIGINT NOT NULL DEFAULT 0,
    aaguid BYTEA NOT NULL,
    backup_eligible BOOLEAN NOT NULL,
    backup_state BOOLEAN NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE
);

COMMENT ON TABLE iam_passkey IS 'PASSKEY Credential 的 WebAuthn 公钥和认证器状态';
COMMENT ON COLUMN iam_passkey.credential_id IS '对应 iam_credential 主键';
COMMENT ON COLUMN iam_passkey.passkey_credential_id IS '认证器生成且全局唯一的 WebAuthn credential ID';
COMMENT ON COLUMN iam_passkey.public_key_cose IS '验证 assertion 签名使用的 COSE 公钥';
COMMENT ON COLUMN iam_passkey.signature_count IS '最近一次成功 assertion 返回的签名计数';
COMMENT ON COLUMN iam_passkey.aaguid IS '注册时认证器返回的 AAGUID';
COMMENT ON COLUMN iam_passkey.backup_eligible IS '注册时确认的 credential backup eligibility';
COMMENT ON COLUMN iam_passkey.backup_state IS '最近一次 ceremony 确认的 credential backup state';
COMMENT ON COLUMN iam_passkey.display_name IS '账号安全页展示的 Passkey 名称';
COMMENT ON COLUMN iam_passkey.last_used_at IS '最近一次成功 assertion 的时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_passkey_credential_id
    ON iam_passkey (passkey_credential_id);

CREATE INDEX IF NOT EXISTS idx_iam_passkey_credential_parent
    ON iam_passkey (credential_id);


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V32__establish_external_identity_providers.sql

CREATE TABLE iam_identity_provider (
    id BIGINT PRIMARY KEY,
    scope_type VARCHAR(32) NOT NULL,
    scope_id BIGINT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    protocol VARCHAR(32) NOT NULL,
    registration_policy VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
    email_auto_link BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam_identity_provider IS 'GLOBAL 或 TENANT 作用域中的标准外部身份源';
COMMENT ON COLUMN iam_identity_provider.scope_type IS 'GLOBAL 或 TENANT';
COMMENT ON COLUMN iam_identity_provider.scope_id IS 'TENANT 主键；GLOBAL 作用域为空';
COMMENT ON COLUMN iam_identity_provider.code IS '当前作用域内用于认证入口和审计的不可变编码';
COMMENT ON COLUMN iam_identity_provider.name IS '登录页和管理页显示名称';
COMMENT ON COLUMN iam_identity_provider.protocol IS 'OIDC、OAUTH2 或 LDAP';
COMMENT ON COLUMN iam_identity_provider.registration_policy IS 'DISABLED 或 JIT';
COMMENT ON COLUMN iam_identity_provider.email_auto_link IS '是否允许用可信上游邮箱关联同作用域唯一 ACTIVE Account';
COMMENT ON COLUMN iam_identity_provider.status IS 'ACTIVE 或 DISABLED';
COMMENT ON COLUMN iam_identity_provider.version IS '乐观锁版本号';
COMMENT ON COLUMN iam_identity_provider.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX uk_iam_identity_provider_global_code
    ON iam_identity_provider (code)
    WHERE scope_type = 'GLOBAL' AND deleted = 0;

CREATE UNIQUE INDEX uk_iam_identity_provider_tenant_code
    ON iam_identity_provider (scope_id, code)
    WHERE scope_type = 'TENANT' AND deleted = 0;

CREATE INDEX idx_iam_identity_provider_scope_status
    ON iam_identity_provider (scope_type, scope_id, status, deleted);

CREATE TABLE iam_oidc_provider (
    provider_id BIGINT PRIMARY KEY,
    issuer VARCHAR(512) NOT NULL,
    client_id VARCHAR(256) NOT NULL,
    client_secret_key_id VARCHAR(64) NOT NULL,
    client_secret_nonce BYTEA NOT NULL,
    client_secret_ciphertext BYTEA NOT NULL,
    scopes VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE iam_oidc_provider IS 'OIDC Discovery、Client 和 scope 配置';
COMMENT ON COLUMN iam_oidc_provider.provider_id IS '对应 iam_identity_provider 主键';
COMMENT ON COLUMN iam_oidc_provider.issuer IS '执行 Discovery 和校验 iss 使用的精确 Issuer URI';
COMMENT ON COLUMN iam_oidc_provider.client_id IS 'OIDC Client 标识';
COMMENT ON COLUMN iam_oidc_provider.client_secret_key_id IS '加密 Client Secret 使用的 key-id';
COMMENT ON COLUMN iam_oidc_provider.client_secret_nonce IS '加密 Client Secret 使用的 AES-GCM nonce';
COMMENT ON COLUMN iam_oidc_provider.client_secret_ciphertext IS '包含 GCM authentication tag 的 Client Secret 密文';
COMMENT ON COLUMN iam_oidc_provider.scopes IS '以空格分隔且必须包含 openid 的请求 scope';

CREATE TABLE iam_oauth2_provider (
    provider_id BIGINT PRIMARY KEY,
    authorization_uri VARCHAR(1024) NOT NULL,
    token_uri VARCHAR(1024) NOT NULL,
    profile_uri VARCHAR(1024) NOT NULL,
    auxiliary_profile_uri VARCHAR(1024),
    client_id VARCHAR(256) NOT NULL,
    client_secret_key_id VARCHAR(64) NOT NULL,
    client_secret_nonce BYTEA NOT NULL,
    client_secret_ciphertext BYTEA NOT NULL,
    scopes VARCHAR(1024) NOT NULL,
    subject_path VARCHAR(256) NOT NULL,
    display_name_path VARCHAR(256),
    email_path VARCHAR(256),
    email_verified_path VARCHAR(256),
    avatar_path VARCHAR(256),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE iam_oauth2_provider IS '标准 OAuth2 端点、Client 和只读 JSONPath Profile 映射';
COMMENT ON COLUMN iam_oauth2_provider.provider_id IS '对应 iam_identity_provider 主键';
COMMENT ON COLUMN iam_oauth2_provider.authorization_uri IS 'Authorization Code + PKCE 授权端点';
COMMENT ON COLUMN iam_oauth2_provider.token_uri IS 'Authorization Code 换取 Token 的端点';
COMMENT ON COLUMN iam_oauth2_provider.profile_uri IS '使用 Access Token 读取主 Profile 的端点';
COMMENT ON COLUMN iam_oauth2_provider.auxiliary_profile_uri IS '可选辅助 Profile 端点';
COMMENT ON COLUMN iam_oauth2_provider.client_id IS 'OAuth2 Client 标识';
COMMENT ON COLUMN iam_oauth2_provider.client_secret_key_id IS '加密 Client Secret 使用的 key-id';
COMMENT ON COLUMN iam_oauth2_provider.client_secret_nonce IS '加密 Client Secret 使用的 AES-GCM nonce';
COMMENT ON COLUMN iam_oauth2_provider.client_secret_ciphertext IS '包含 GCM authentication tag 的 Client Secret 密文';
COMMENT ON COLUMN iam_oauth2_provider.scopes IS '以空格分隔的请求 scope';
COMMENT ON COLUMN iam_oauth2_provider.subject_path IS '从 Profile 读取唯一 external subject 的 JSONPath';
COMMENT ON COLUMN iam_oauth2_provider.display_name_path IS '读取展示名称的可选 JSONPath';
COMMENT ON COLUMN iam_oauth2_provider.email_path IS '读取邮箱的可选 JSONPath';
COMMENT ON COLUMN iam_oauth2_provider.email_verified_path IS '读取邮箱验证状态的可选 JSONPath';
COMMENT ON COLUMN iam_oauth2_provider.avatar_path IS '读取头像 URI 的可选 JSONPath';

CREATE TABLE iam_ldap_provider (
    provider_id BIGINT PRIMARY KEY,
    url VARCHAR(1024) NOT NULL,
    base_dn VARCHAR(512) NOT NULL,
    service_account_dn VARCHAR(512),
    service_account_password_key_id VARCHAR(64),
    service_account_password_nonce BYTEA,
    service_account_password_ciphertext BYTEA,
    user_search_base VARCHAR(512) NOT NULL,
    user_search_filter VARCHAR(512) NOT NULL,
    external_subject_attribute VARCHAR(128) NOT NULL,
    display_name_attribute VARCHAR(128),
    email_attribute VARCHAR(128),
    transport_security VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE iam_ldap_provider IS 'LDAP 搜索、用户 bind、TLS 和受控属性映射配置';
COMMENT ON COLUMN iam_ldap_provider.provider_id IS '对应 iam_identity_provider 主键';
COMMENT ON COLUMN iam_ldap_provider.url IS 'LDAP 或 LDAPS 服务器 URI';
COMMENT ON COLUMN iam_ldap_provider.base_dn IS '目录搜索根 DN';
COMMENT ON COLUMN iam_ldap_provider.service_account_dn IS '搜索用户使用的可选服务账号 DN';
COMMENT ON COLUMN iam_ldap_provider.service_account_password_key_id IS '加密服务账号密码使用的 key-id';
COMMENT ON COLUMN iam_ldap_provider.service_account_password_nonce IS '加密服务账号密码使用的 AES-GCM nonce';
COMMENT ON COLUMN iam_ldap_provider.service_account_password_ciphertext IS '包含 GCM authentication tag 的服务账号密码密文';
COMMENT ON COLUMN iam_ldap_provider.user_search_base IS '相对 base_dn 的用户搜索根';
COMMENT ON COLUMN iam_ldap_provider.user_search_filter IS '仅允许一个用户名参数的 LDAP 搜索过滤器';
COMMENT ON COLUMN iam_ldap_provider.external_subject_attribute IS '映射 external subject 的目录属性';
COMMENT ON COLUMN iam_ldap_provider.display_name_attribute IS '映射展示名称的可选目录属性';
COMMENT ON COLUMN iam_ldap_provider.email_attribute IS '映射邮箱的可选目录属性';
COMMENT ON COLUMN iam_ldap_provider.transport_security IS 'STARTTLS 或 LDAPS';

CREATE TABLE iam_external_identity (
    id BIGINT PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    external_subject VARCHAR(512) NOT NULL,
    display_name VARCHAR(256),
    email VARCHAR(256),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    avatar_uri VARCHAR(1024),
    last_authenticated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam_external_identity IS 'IdentityProvider external subject 与本地 Account 的关联';
COMMENT ON COLUMN iam_external_identity.provider_id IS '所属 IdentityProvider 主键';
COMMENT ON COLUMN iam_external_identity.account_id IS '关联的本地 Account 主键';
COMMENT ON COLUMN iam_external_identity.external_subject IS 'Provider 内唯一且不解释格式的外部主体标识';
COMMENT ON COLUMN iam_external_identity.display_name IS '最近一次成功认证返回的可选展示名称';
COMMENT ON COLUMN iam_external_identity.email IS '最近一次成功认证返回的可选邮箱';
COMMENT ON COLUMN iam_external_identity.email_verified IS '最近一次成功认证是否确认邮箱';
COMMENT ON COLUMN iam_external_identity.avatar_uri IS '最近一次成功认证返回的可选头像 URI';
COMMENT ON COLUMN iam_external_identity.last_authenticated_at IS '最近一次完成上游认证的时间';
COMMENT ON COLUMN iam_external_identity.version IS '乐观锁版本号';
COMMENT ON COLUMN iam_external_identity.deleted IS '逻辑删除标记';

CREATE UNIQUE INDEX uk_iam_external_identity_provider_subject
    ON iam_external_identity (provider_id, external_subject)
    WHERE deleted = 0;

CREATE UNIQUE INDEX uk_iam_external_identity_provider_account
    ON iam_external_identity (provider_id, account_id)
    WHERE deleted = 0;

CREATE INDEX idx_iam_external_identity_account
    ON iam_external_identity (account_id, deleted);


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V33__register_identity_provider_navigation.sql

INSERT INTO sys_menu (id, parent_id, permission_code, route_code, title_labels, icon_code, sort_order, visible, version, deleted)
VALUES
    (-340, -300, 'tenant:identity-providers:read', 'tenant.identity-providers', '{"zh-CN":"身份源","en-US":"Identity providers"}', 'Network', 40, TRUE, 0, 0),
    (-437, -400, 'system:identity-providers:read', 'system.identity-providers', '{"zh-CN":"身份源","en-US":"Identity providers"}', 'Network', 27, TRUE, 0, 0)
ON CONFLICT (id) DO NOTHING;


-- Consolidated 0.0.1 baseline source: sca4d-server/src/main/resources/db/migration/V34__init_flowable_authentication_process.sql

-- Flowable 7.2.0 PostgreSQL common, process engine and history schema.
-- Sources: flowable-engine-common-7.2.0.jar and flowable-engine-7.2.0.jar.

create table ACT_GE_PROPERTY (
    NAME_ varchar(64),
    VALUE_ varchar(300),
    REV_ integer,
    primary key (NAME_)
);

create table ACT_GE_BYTEARRAY (
    ID_ varchar(64),
    REV_ integer,
    NAME_ varchar(255),
    DEPLOYMENT_ID_ varchar(64),
    BYTES_ bytea,
    GENERATED_ boolean,
    primary key (ID_)
);

insert into ACT_GE_PROPERTY
values ('common.schema.version', '7.2.0.2', 1);

insert into ACT_GE_PROPERTY
values ('next.dbid', '1', 1);


create table ACT_RU_ENTITYLINK (
    ID_ varchar(64),
    REV_ integer,
    CREATE_TIME_ timestamp,
    LINK_TYPE_ varchar(255),
    SCOPE_ID_ varchar(255),
    SUB_SCOPE_ID_ varchar(255),
    SCOPE_TYPE_ varchar(255),
    SCOPE_DEFINITION_ID_ varchar(255),
    PARENT_ELEMENT_ID_ varchar(255),
    REF_SCOPE_ID_ varchar(255),
    REF_SCOPE_TYPE_ varchar(255),
    REF_SCOPE_DEFINITION_ID_ varchar(255),
    ROOT_SCOPE_ID_ varchar(255),
    ROOT_SCOPE_TYPE_ varchar(255),
    HIERARCHY_TYPE_ varchar(255),
    primary key (ID_)
);

create index ACT_IDX_ENT_LNK_SCOPE on ACT_RU_ENTITYLINK(SCOPE_ID_, SCOPE_TYPE_, LINK_TYPE_);
create index ACT_IDX_ENT_LNK_REF_SCOPE on ACT_RU_ENTITYLINK(REF_SCOPE_ID_, REF_SCOPE_TYPE_, LINK_TYPE_);
create index ACT_IDX_ENT_LNK_ROOT_SCOPE on ACT_RU_ENTITYLINK(ROOT_SCOPE_ID_, ROOT_SCOPE_TYPE_, LINK_TYPE_);
create index ACT_IDX_ENT_LNK_SCOPE_DEF on ACT_RU_ENTITYLINK(SCOPE_DEFINITION_ID_, SCOPE_TYPE_, LINK_TYPE_);

create table ACT_HI_ENTITYLINK (
    ID_ varchar(64),
    LINK_TYPE_ varchar(255),
    CREATE_TIME_ timestamp,
    SCOPE_ID_ varchar(255),
    SUB_SCOPE_ID_ varchar(255),
    SCOPE_TYPE_ varchar(255),
    SCOPE_DEFINITION_ID_ varchar(255),
    PARENT_ELEMENT_ID_ varchar(255),
    REF_SCOPE_ID_ varchar(255),
    REF_SCOPE_TYPE_ varchar(255),
    REF_SCOPE_DEFINITION_ID_ varchar(255),
    ROOT_SCOPE_ID_ varchar(255),
    ROOT_SCOPE_TYPE_ varchar(255),
    HIERARCHY_TYPE_ varchar(255),
    primary key (ID_)
);

create index ACT_IDX_HI_ENT_LNK_SCOPE on ACT_HI_ENTITYLINK(SCOPE_ID_, SCOPE_TYPE_, LINK_TYPE_);
create index ACT_IDX_HI_ENT_LNK_REF_SCOPE on ACT_HI_ENTITYLINK(REF_SCOPE_ID_, REF_SCOPE_TYPE_, LINK_TYPE_);
create index ACT_IDX_HI_ENT_LNK_ROOT_SCOPE on ACT_HI_ENTITYLINK(ROOT_SCOPE_ID_, ROOT_SCOPE_TYPE_, LINK_TYPE_);
create index ACT_IDX_HI_ENT_LNK_SCOPE_DEF on ACT_HI_ENTITYLINK(SCOPE_DEFINITION_ID_, SCOPE_TYPE_, LINK_TYPE_);


create table ACT_RU_IDENTITYLINK (
    ID_ varchar(64),
    REV_ integer,
    GROUP_ID_ varchar(255),
    TYPE_ varchar(255),
    USER_ID_ varchar(255),
    TASK_ID_ varchar(64),
    PROC_INST_ID_ varchar(64),
    PROC_DEF_ID_ varchar (64),
    SCOPE_ID_ varchar(255),
    SUB_SCOPE_ID_ varchar(255),
    SCOPE_TYPE_ varchar(255),
    SCOPE_DEFINITION_ID_ varchar(255),
    primary key (ID_)
);

create index ACT_IDX_IDENT_LNK_USER on ACT_RU_IDENTITYLINK(USER_ID_);
create index ACT_IDX_IDENT_LNK_GROUP on ACT_RU_IDENTITYLINK(GROUP_ID_);
create index ACT_IDX_IDENT_LNK_SCOPE on ACT_RU_IDENTITYLINK(SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_IDENT_LNK_SUB_SCOPE on ACT_RU_IDENTITYLINK(SUB_SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_IDENT_LNK_SCOPE_DEF on ACT_RU_IDENTITYLINK(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);

create table ACT_HI_IDENTITYLINK (
    ID_ varchar(64),
    GROUP_ID_ varchar(255),
    TYPE_ varchar(255),
    USER_ID_ varchar(255),
    TASK_ID_ varchar(64),
    CREATE_TIME_ timestamp,
    PROC_INST_ID_ varchar(64),
    SCOPE_ID_ varchar(255),
    SUB_SCOPE_ID_ varchar(255),
    SCOPE_TYPE_ varchar(255),
    SCOPE_DEFINITION_ID_ varchar(255),
    primary key (ID_)
);

create index ACT_IDX_HI_IDENT_LNK_USER on ACT_HI_IDENTITYLINK(USER_ID_);
create index ACT_IDX_HI_IDENT_LNK_SCOPE on ACT_HI_IDENTITYLINK(SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_HI_IDENT_LNK_SUB_SCOPE on ACT_HI_IDENTITYLINK(SUB_SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_HI_IDENT_LNK_SCOPE_DEF on ACT_HI_IDENTITYLINK(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);


create table ACT_RU_JOB (
    ID_ varchar(64) NOT NULL,
    REV_ integer,
    CATEGORY_ varchar(255),
    TYPE_ varchar(255) NOT NULL,
    LOCK_EXP_TIME_ timestamp,
    LOCK_OWNER_ varchar(255),
    EXCLUSIVE_ boolean,
    EXECUTION_ID_ varchar(64),
    PROCESS_INSTANCE_ID_ varchar(64),
    PROC_DEF_ID_ varchar(64),
    ELEMENT_ID_ varchar(255),
    ELEMENT_NAME_ varchar(255),
    SCOPE_ID_ varchar(255),
    SUB_SCOPE_ID_ varchar(255),
    SCOPE_TYPE_ varchar(255),
    SCOPE_DEFINITION_ID_ varchar(255),
    CORRELATION_ID_ varchar(255),
    RETRIES_ integer,
    EXCEPTION_STACK_ID_ varchar(64),
    EXCEPTION_MSG_ varchar(4000),
    DUEDATE_ timestamp,
    REPEAT_ varchar(255),
    HANDLER_TYPE_ varchar(255),
    HANDLER_CFG_ varchar(4000),
    CUSTOM_VALUES_ID_ varchar(64),
    CREATE_TIME_ timestamp,
    TENANT_ID_ varchar(255) default '',
    primary key (ID_)
);

create table ACT_RU_TIMER_JOB (
    ID_ varchar(64) NOT NULL,
    REV_ integer,
    CATEGORY_ varchar(255),
    TYPE_ varchar(255) NOT NULL,
    LOCK_EXP_TIME_ timestamp,
    LOCK_OWNER_ varchar(255),
    EXCLUSIVE_ boolean,
    EXECUTION_ID_ varchar(64),
    PROCESS_INSTANCE_ID_ varchar(64),
    PROC_DEF_ID_ varchar(64),
    ELEMENT_ID_ varchar(255),
    ELEMENT_NAME_ varchar(255),
    SCOPE_ID_ varchar(255),
    SUB_SCOPE_ID_ varchar(255),
    SCOPE_TYPE_ varchar(255),
    SCOPE_DEFINITION_ID_ varchar(255),
    CORRELATION_ID_ varchar(255),
    RETRIES_ integer,
    EXCEPTION_STACK_ID_ varchar(64),
    EXCEPTION_MSG_ varchar(4000),
    DUEDATE_ timestamp,
    REPEAT_ varchar(255),
    HANDLER_TYPE_ varchar(255),
    HANDLER_CFG_ varchar(4000),
    CUSTOM_VALUES_ID_ varchar(64),
    CREATE_TIME_ timestamp,
    TENANT_ID_ varchar(255) default '',
    primary key (ID_)
);

create table ACT_RU_SUSPENDED_JOB (
    ID_ varchar(64) NOT NULL,
    REV_ integer,
    CATEGORY_ varchar(255),
    TYPE_ varchar(255) NOT NULL,
    EXCLUSIVE_ boolean,
    EXECUTION_ID_ varchar(64),
    PROCESS_INSTANCE_ID_ varchar(64),
    PROC_DEF_ID_ varchar(64),
    ELEMENT_ID_ varchar(255),
    ELEMENT_NAME_ varchar(255),
    SCOPE_ID_ varchar(255),
    SUB_SCOPE_ID_ varchar(255),
    SCOPE_TYPE_ varchar(255),
    SCOPE_DEFINITION_ID_ varchar(255),
    CORRELATION_ID_ varchar(255),
    RETRIES_ integer,
    EXCEPTION_STACK_ID_ varchar(64),
    EXCEPTION_MSG_ varchar(4000),
    DUEDATE_ timestamp,
    REPEAT_ varchar(255),
    HANDLER_TYPE_ varchar(255),
    HANDLER_CFG_ varchar(4000),
    CUSTOM_VALUES_ID_ varchar(64),
    CREATE_TIME_ timestamp,
    TENANT_ID_ varchar(255) default '',
    primary key (ID_)
);

create table ACT_RU_DEADLETTER_JOB (
    ID_ varchar(64) NOT NULL,
    REV_ integer,
    CATEGORY_ varchar(255),
    TYPE_ varchar(255) NOT NULL,
    EXCLUSIVE_ boolean,
    EXECUTION_ID_ varchar(64),
    PROCESS_INSTANCE_ID_ varchar(64),
    PROC_DEF_ID_ varchar(64),
    ELEMENT_ID_ varchar(255),
    ELEMENT_NAME_ varchar(255),
    SCOPE_ID_ varchar(255),
    SUB_SCOPE_ID_ varchar(255),
    SCOPE_TYPE_ varchar(255),
    SCOPE_DEFINITION_ID_ varchar(255),
    CORRELATION_ID_ varchar(255),
    EXCEPTION_STACK_ID_ varchar(64),
    EXCEPTION_MSG_ varchar(4000),
    DUEDATE_ timestamp,
    REPEAT_ varchar(255),
    HANDLER_TYPE_ varchar(255),
    HANDLER_CFG_ varchar(4000),
    CUSTOM_VALUES_ID_ varchar(64),
    CREATE_TIME_ timestamp,
    TENANT_ID_ varchar(255) default '',
    primary key (ID_)
);

create table ACT_RU_HISTORY_JOB (
    ID_ varchar(64) NOT NULL,
    REV_ integer,
    LOCK_EXP_TIME_ timestamp,
    LOCK_OWNER_ varchar(255),
    RETRIES_ integer,
    EXCEPTION_STACK_ID_ varchar(64),
    EXCEPTION_MSG_ varchar(4000),
    HANDLER_TYPE_ varchar(255),
    HANDLER_CFG_ varchar(4000),
    CUSTOM_VALUES_ID_ varchar(64),
    ADV_HANDLER_CFG_ID_ varchar(64),
    CREATE_TIME_ timestamp,
    SCOPE_TYPE_ varchar(255),
    TENANT_ID_ varchar(255) default '',
    primary key (ID_)
);

create table ACT_RU_EXTERNAL_JOB (
    ID_ varchar(64) NOT NULL,
    REV_ integer,
    CATEGORY_ varchar(255),
    TYPE_ varchar(255) NOT NULL,
    LOCK_EXP_TIME_ timestamp,
    LOCK_OWNER_ varchar(255),
    EXCLUSIVE_ boolean,
    EXECUTION_ID_ varchar(64),
    PROCESS_INSTANCE_ID_ varchar(64),
    PROC_DEF_ID_ varchar(64),
    ELEMENT_ID_ varchar(255),
    ELEMENT_NAME_ varchar(255),
    SCOPE_ID_ varchar(255),
    SUB_SCOPE_ID_ varchar(255),
    SCOPE_TYPE_ varchar(255),
    SCOPE_DEFINITION_ID_ varchar(255),
    CORRELATION_ID_ varchar(255),
    RETRIES_ integer,
    EXCEPTION_STACK_ID_ varchar(64),
    EXCEPTION_MSG_ varchar(4000),
    DUEDATE_ timestamp,
    REPEAT_ varchar(255),
    HANDLER_TYPE_ varchar(255),
    HANDLER_CFG_ varchar(4000),
    CUSTOM_VALUES_ID_ varchar(64),
    CREATE_TIME_ timestamp,
    TENANT_ID_ varchar(255) default '',
    primary key (ID_)
);

create index ACT_IDX_JOB_EXCEPTION_STACK_ID on ACT_RU_JOB(EXCEPTION_STACK_ID_);
create index ACT_IDX_JOB_CUSTOM_VALUES_ID on ACT_RU_JOB(CUSTOM_VALUES_ID_);
create index ACT_IDX_JOB_CORRELATION_ID on ACT_RU_JOB(CORRELATION_ID_);

create index ACT_IDX_TIMER_JOB_EXCEPTION_STACK_ID on ACT_RU_TIMER_JOB(EXCEPTION_STACK_ID_);
create index ACT_IDX_TIMER_JOB_CUSTOM_VALUES_ID on ACT_RU_TIMER_JOB(CUSTOM_VALUES_ID_);
create index ACT_IDX_TIMER_JOB_CORRELATION_ID on ACT_RU_TIMER_JOB(CORRELATION_ID_);
create index ACT_IDX_TIMER_JOB_DUEDATE on ACT_RU_TIMER_JOB(DUEDATE_); 

create index ACT_IDX_SUSPENDED_JOB_EXCEPTION_STACK_ID on ACT_RU_SUSPENDED_JOB(EXCEPTION_STACK_ID_);
create index ACT_IDX_SUSPENDED_JOB_CUSTOM_VALUES_ID on ACT_RU_SUSPENDED_JOB(CUSTOM_VALUES_ID_);
create index ACT_IDX_SUSPENDED_JOB_CORRELATION_ID on ACT_RU_SUSPENDED_JOB(CORRELATION_ID_);

create index ACT_IDX_DEADLETTER_JOB_EXCEPTION_STACK_ID on ACT_RU_DEADLETTER_JOB(EXCEPTION_STACK_ID_);
create index ACT_IDX_DEADLETTER_JOB_CUSTOM_VALUES_ID on ACT_RU_DEADLETTER_JOB(CUSTOM_VALUES_ID_);
create index ACT_IDX_DEADLETTER_JOB_CORRELATION_ID on ACT_RU_DEADLETTER_JOB(CORRELATION_ID_);

create index ACT_IDX_EXTERNAL_JOB_EXCEPTION_STACK_ID on ACT_RU_EXTERNAL_JOB(EXCEPTION_STACK_ID_);
create index ACT_IDX_EXTERNAL_JOB_CUSTOM_VALUES_ID on ACT_RU_EXTERNAL_JOB(CUSTOM_VALUES_ID_);
create index ACT_IDX_EXTERNAL_JOB_CORRELATION_ID on ACT_RU_EXTERNAL_JOB(CORRELATION_ID_);

alter table ACT_RU_JOB
    add constraint ACT_FK_JOB_EXCEPTION
    foreign key (EXCEPTION_STACK_ID_)
    references ACT_GE_BYTEARRAY (ID_);

alter table ACT_RU_JOB
    add constraint ACT_FK_JOB_CUSTOM_VALUES
    foreign key (CUSTOM_VALUES_ID_)
    references ACT_GE_BYTEARRAY (ID_);

alter table ACT_RU_TIMER_JOB
    add constraint ACT_FK_TIMER_JOB_EXCEPTION
    foreign key (EXCEPTION_STACK_ID_)
    references ACT_GE_BYTEARRAY (ID_);

alter table ACT_RU_TIMER_JOB
    add constraint ACT_FK_TIMER_JOB_CUSTOM_VALUES
    foreign key (CUSTOM_VALUES_ID_)
    references ACT_GE_BYTEARRAY (ID_);

alter table ACT_RU_SUSPENDED_JOB
    add constraint ACT_FK_SUSPENDED_JOB_EXCEPTION
    foreign key (EXCEPTION_STACK_ID_)
    references ACT_GE_BYTEARRAY (ID_);

alter table ACT_RU_SUSPENDED_JOB
    add constraint ACT_FK_SUSPENDED_JOB_CUSTOM_VALUES
    foreign key (CUSTOM_VALUES_ID_)
    references ACT_GE_BYTEARRAY (ID_);

alter table ACT_RU_DEADLETTER_JOB
    add constraint ACT_FK_DEADLETTER_JOB_EXCEPTION
    foreign key (EXCEPTION_STACK_ID_)
    references ACT_GE_BYTEARRAY (ID_);

alter table ACT_RU_DEADLETTER_JOB
    add constraint ACT_FK_DEADLETTER_JOB_CUSTOM_VALUES
    foreign key (CUSTOM_VALUES_ID_)
    references ACT_GE_BYTEARRAY (ID_);

alter table ACT_RU_EXTERNAL_JOB
    add constraint ACT_FK_EXTERNAL_JOB_EXCEPTION
    foreign key (EXCEPTION_STACK_ID_)
    references ACT_GE_BYTEARRAY (ID_);

alter table ACT_RU_EXTERNAL_JOB
    add constraint ACT_FK_EXTERNAL_JOB_CUSTOM_VALUES
    foreign key (CUSTOM_VALUES_ID_)
    references ACT_GE_BYTEARRAY (ID_);

create index ACT_IDX_JOB_SCOPE on ACT_RU_JOB(SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_JOB_SUB_SCOPE on ACT_RU_JOB(SUB_SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_JOB_SCOPE_DEF on ACT_RU_JOB(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);

create index ACT_IDX_TJOB_SCOPE on ACT_RU_TIMER_JOB(SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_TJOB_SUB_SCOPE on ACT_RU_TIMER_JOB(SUB_SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_TJOB_SCOPE_DEF on ACT_RU_TIMER_JOB(SCOPE_DEFINITION_ID_, SCOPE_TYPE_); 

create index ACT_IDX_SJOB_SCOPE on ACT_RU_SUSPENDED_JOB(SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_SJOB_SUB_SCOPE on ACT_RU_SUSPENDED_JOB(SUB_SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_SJOB_SCOPE_DEF on ACT_RU_SUSPENDED_JOB(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);   

create index ACT_IDX_DJOB_SCOPE on ACT_RU_DEADLETTER_JOB(SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_DJOB_SUB_SCOPE on ACT_RU_DEADLETTER_JOB(SUB_SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_DJOB_SCOPE_DEF on ACT_RU_DEADLETTER_JOB(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);

create index ACT_IDX_EJOB_SCOPE on ACT_RU_EXTERNAL_JOB(SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_EJOB_SUB_SCOPE on ACT_RU_EXTERNAL_JOB(SUB_SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_EJOB_SCOPE_DEF on ACT_RU_EXTERNAL_JOB(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);

create table FLW_RU_BATCH (
    ID_ varchar(64) not null,
    REV_ integer,
    TYPE_ varchar(64) not null,
    SEARCH_KEY_ varchar(255),
    SEARCH_KEY2_ varchar(255),
    CREATE_TIME_ timestamp not null,
    COMPLETE_TIME_ timestamp,
    STATUS_ varchar(255),
    BATCH_DOC_ID_ varchar(64),
    TENANT_ID_ varchar(255) default '',
    primary key (ID_)
);

create table FLW_RU_BATCH_PART (
    ID_ varchar(64) not null,
    REV_ integer,
    BATCH_ID_ varchar(64),
    TYPE_ varchar(64) not null,
    SCOPE_ID_ varchar(64),
    SUB_SCOPE_ID_ varchar(64),
    SCOPE_TYPE_ varchar(64),
    SEARCH_KEY_ varchar(255),
    SEARCH_KEY2_ varchar(255),
    CREATE_TIME_ timestamp not null,
    COMPLETE_TIME_ timestamp,
    STATUS_ varchar(255),
    RESULT_DOC_ID_ varchar(64),
    TENANT_ID_ varchar(255) default '',
    primary key (ID_)
);

create index FLW_IDX_BATCH_PART on FLW_RU_BATCH_PART(BATCH_ID_);

alter table FLW_RU_BATCH_PART
    add constraint FLW_FK_BATCH_PART_PARENT
    foreign key (BATCH_ID_)
    references FLW_RU_BATCH (ID_);

create table ACT_RU_TASK (
    ID_ varchar(64),
    REV_ integer,
    EXECUTION_ID_ varchar(64),
    PROC_INST_ID_ varchar(64),
    PROC_DEF_ID_ varchar(64),
    TASK_DEF_ID_ varchar(64),
    SCOPE_ID_ varchar(255),
    SUB_SCOPE_ID_ varchar(255),
    SCOPE_TYPE_ varchar(255),
    SCOPE_DEFINITION_ID_ varchar(255),
    PROPAGATED_STAGE_INST_ID_ varchar(255),
    STATE_ varchar(255),
    NAME_ varchar(255),
    PARENT_TASK_ID_ varchar(64),
    DESCRIPTION_ varchar(4000),
    TASK_DEF_KEY_ varchar(255),
    OWNER_ varchar(255),
    ASSIGNEE_ varchar(255),
    DELEGATION_ varchar(64),
    PRIORITY_ integer,
    CREATE_TIME_ timestamp,
    IN_PROGRESS_TIME_ timestamp,
    IN_PROGRESS_STARTED_BY_ varchar(255),
    CLAIM_TIME_ timestamp,
    CLAIMED_BY_ varchar(255),
    SUSPENDED_TIME_ timestamp,
    SUSPENDED_BY_ varchar(255),
    IN_PROGRESS_DUE_DATE_ timestamp,
    DUE_DATE_ timestamp,
    CATEGORY_ varchar(255),
    SUSPENSION_STATE_ integer,
    TENANT_ID_ varchar(255) default '',
    FORM_KEY_ varchar(255),
    IS_COUNT_ENABLED_ boolean,
    VAR_COUNT_ integer, 
    ID_LINK_COUNT_ integer,
    SUB_TASK_COUNT_ integer,
    primary key (ID_)
);

create index ACT_IDX_TASK_CREATE on ACT_RU_TASK(CREATE_TIME_);
create index ACT_IDX_TASK_SCOPE on ACT_RU_TASK(SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_TASK_SUB_SCOPE on ACT_RU_TASK(SUB_SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_TASK_SCOPE_DEF on ACT_RU_TASK(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);

create table ACT_HI_TASKINST (
    ID_ varchar(64) not null,
    REV_ integer default 1,
    PROC_DEF_ID_ varchar(64),
    TASK_DEF_ID_ varchar(64),
    TASK_DEF_KEY_ varchar(255),
    PROC_INST_ID_ varchar(64),
    EXECUTION_ID_ varchar(64),
    SCOPE_ID_ varchar(255),
    SUB_SCOPE_ID_ varchar(255),
    SCOPE_TYPE_ varchar(255),
    SCOPE_DEFINITION_ID_ varchar(255),
    PROPAGATED_STAGE_INST_ID_ varchar(255),
    STATE_ varchar(255),
    NAME_ varchar(255),
    PARENT_TASK_ID_ varchar(64),
    DESCRIPTION_ varchar(4000),
    OWNER_ varchar(255),
    ASSIGNEE_ varchar(255),
    START_TIME_ timestamp not null,
    IN_PROGRESS_TIME_ timestamp,
    IN_PROGRESS_STARTED_BY_ varchar(255),
    CLAIM_TIME_ timestamp,
    CLAIMED_BY_ varchar(255),
    SUSPENDED_TIME_ timestamp,
    SUSPENDED_BY_ varchar(255),
    END_TIME_ timestamp,
    COMPLETED_BY_ varchar(255),
    DURATION_ bigint,
    DELETE_REASON_ varchar(4000),
    PRIORITY_ integer,
    IN_PROGRESS_DUE_DATE_ timestamp,
    DUE_DATE_ timestamp,
    FORM_KEY_ varchar(255),
    CATEGORY_ varchar(255),
    TENANT_ID_ varchar(255) default '',
    LAST_UPDATED_TIME_ timestamp,
    primary key (ID_)
);

create table ACT_HI_TSK_LOG (
    ID_ SERIAL PRIMARY KEY,
    TYPE_ varchar(64),
    TASK_ID_ varchar(64) not null,
    TIME_STAMP_ timestamp not null,
    USER_ID_ varchar(255),
    DATA_ varchar(4000),
    EXECUTION_ID_ varchar(64),
    PROC_INST_ID_ varchar(64),
    PROC_DEF_ID_ varchar(64),
    SCOPE_ID_ varchar(255),
    SCOPE_DEFINITION_ID_ varchar(255),
    SUB_SCOPE_ID_ varchar(255),
    SCOPE_TYPE_ varchar(255),
    TENANT_ID_ varchar(255) default ''
);

create index ACT_IDX_HI_TASK_SCOPE on ACT_HI_TASKINST(SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_HI_TASK_SUB_SCOPE on ACT_HI_TASKINST(SUB_SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_HI_TASK_SCOPE_DEF on ACT_HI_TASKINST(SCOPE_DEFINITION_ID_, SCOPE_TYPE_);
create index ACT_IDX_ACT_HI_TSK_LOG_TASK on ACT_HI_TSK_LOG(TASK_ID_);

create table ACT_RU_VARIABLE (
    ID_ varchar(64) not null,
    REV_ integer,
    TYPE_ varchar(255) not null,
    NAME_ varchar(255) not null,
    EXECUTION_ID_ varchar(64),
    PROC_INST_ID_ varchar(64),
    TASK_ID_ varchar(64),
    SCOPE_ID_ varchar(255),
    SUB_SCOPE_ID_ varchar(255),
    SCOPE_TYPE_ varchar(255),
    BYTEARRAY_ID_ varchar(64),
    DOUBLE_ double precision,
    LONG_ bigint,
    TEXT_ varchar(4000),
    TEXT2_ varchar(4000),
    META_INFO_ varchar(4000),
    primary key (ID_)
);

create index ACT_IDX_RU_VAR_SCOPE_ID_TYPE on ACT_RU_VARIABLE(SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_RU_VAR_SUB_ID_TYPE on ACT_RU_VARIABLE(SUB_SCOPE_ID_, SCOPE_TYPE_);

create index ACT_IDX_VAR_BYTEARRAY on ACT_RU_VARIABLE(BYTEARRAY_ID_);
alter table ACT_RU_VARIABLE 
    add constraint ACT_FK_VAR_BYTEARRAY 
    foreign key (BYTEARRAY_ID_) 
    references ACT_GE_BYTEARRAY (ID_);

create table ACT_HI_VARINST (
    ID_ varchar(64) not null,
    REV_ integer default 1,
    PROC_INST_ID_ varchar(64),
    EXECUTION_ID_ varchar(64),
    TASK_ID_ varchar(64),
    NAME_ varchar(255) not null,
    VAR_TYPE_ varchar(100),
    SCOPE_ID_ varchar(255),
    SUB_SCOPE_ID_ varchar(255),
    SCOPE_TYPE_ varchar(255),
    BYTEARRAY_ID_ varchar(64),
    DOUBLE_ double precision,
    LONG_ bigint,
    TEXT_ varchar(4000),
    TEXT2_ varchar(4000),
    META_INFO_ varchar(4000),
    CREATE_TIME_ timestamp,
    LAST_UPDATED_TIME_ timestamp,
    primary key (ID_)
);

create index ACT_IDX_HI_PROCVAR_NAME_TYPE on ACT_HI_VARINST(NAME_, VAR_TYPE_);
create index ACT_IDX_HI_VAR_SCOPE_ID_TYPE on ACT_HI_VARINST(SCOPE_ID_, SCOPE_TYPE_);
create index ACT_IDX_HI_VAR_SUB_ID_TYPE on ACT_HI_VARINST(SUB_SCOPE_ID_, SCOPE_TYPE_);


create table ACT_RU_EVENT_SUBSCR (
    ID_ varchar(64) not null,
    REV_ integer,
    EVENT_TYPE_ varchar(255) not null,
    EVENT_NAME_ varchar(255),
    EXECUTION_ID_ varchar(64),
    PROC_INST_ID_ varchar(64),
    ACTIVITY_ID_ varchar(64),
    CONFIGURATION_ varchar(255),
    CREATED_ timestamp not null,
    PROC_DEF_ID_ varchar(64),
    SUB_SCOPE_ID_ varchar(64),
    SCOPE_ID_ varchar(64),
    SCOPE_DEFINITION_ID_ varchar(64),
    SCOPE_DEFINITION_KEY_ varchar(255),
    SCOPE_TYPE_ varchar(64),
    LOCK_TIME_ timestamp,
    LOCK_OWNER_ varchar(255),
    TENANT_ID_ varchar(255) default '',
    primary key (ID_)
);

create index ACT_IDX_EVENT_SUBSCR_CONFIG_ on ACT_RU_EVENT_SUBSCR(CONFIGURATION_);
create index ACT_IDX_EVENT_SUBSCR on ACT_RU_EVENT_SUBSCR(EXECUTION_ID_);
create index ACT_IDX_EVENT_SUBSCR_PROC_ID on ACT_RU_EVENT_SUBSCR(PROC_INST_ID_);
create index ACT_IDX_EVENT_SUBSCR_SCOPEREF_ on ACT_RU_EVENT_SUBSCR(SCOPE_ID_, SCOPE_TYPE_);
create table ACT_RE_DEPLOYMENT (
    ID_ varchar(64),
    NAME_ varchar(255),
    CATEGORY_ varchar(255),
    KEY_ varchar(255),
    TENANT_ID_ varchar(255) default '',
    DEPLOY_TIME_ timestamp,
    DERIVED_FROM_ varchar(64),
    DERIVED_FROM_ROOT_ varchar(64),
    PARENT_DEPLOYMENT_ID_ varchar(255),
    ENGINE_VERSION_ varchar(255),
    primary key (ID_)
);

create table ACT_RE_MODEL (
    ID_ varchar(64) not null,
    REV_ integer,
    NAME_ varchar(255),
    KEY_ varchar(255),
    CATEGORY_ varchar(255),
    CREATE_TIME_ timestamp,
    LAST_UPDATE_TIME_ timestamp,
    VERSION_ integer,
    META_INFO_ varchar(4000),
    DEPLOYMENT_ID_ varchar(64),
    EDITOR_SOURCE_VALUE_ID_ varchar(64),
    EDITOR_SOURCE_EXTRA_VALUE_ID_ varchar(64),
    TENANT_ID_ varchar(255) default '',
    primary key (ID_)
);

create table ACT_RU_EXECUTION (
    ID_ varchar(64),
    REV_ integer,
    PROC_INST_ID_ varchar(64),
    BUSINESS_KEY_ varchar(255),
    PARENT_ID_ varchar(64),
    PROC_DEF_ID_ varchar(64),
    SUPER_EXEC_ varchar(64),
    ROOT_PROC_INST_ID_ varchar(64),
    ACT_ID_ varchar(255),
    IS_ACTIVE_ boolean,
    IS_CONCURRENT_ boolean,
    IS_SCOPE_ boolean,
    IS_EVENT_SCOPE_ boolean,
    IS_MI_ROOT_ boolean,
    SUSPENSION_STATE_ integer,
    CACHED_ENT_STATE_ integer,
    TENANT_ID_ varchar(255) default '',
    NAME_ varchar(255),
    START_ACT_ID_ varchar(255),
    START_TIME_ timestamp,
    START_USER_ID_ varchar(255),
    LOCK_TIME_ timestamp,
    LOCK_OWNER_ varchar(255),
    IS_COUNT_ENABLED_ boolean,
    EVT_SUBSCR_COUNT_ integer, 
    TASK_COUNT_ integer, 
    JOB_COUNT_ integer, 
    TIMER_JOB_COUNT_ integer,
    SUSP_JOB_COUNT_ integer,
    DEADLETTER_JOB_COUNT_ integer,
    EXTERNAL_WORKER_JOB_COUNT_ integer,
    VAR_COUNT_ integer, 
    ID_LINK_COUNT_ integer,
    CALLBACK_ID_ varchar(255),
    CALLBACK_TYPE_ varchar(255),
    REFERENCE_ID_ varchar(255),
    REFERENCE_TYPE_ varchar(255),
    PROPAGATED_STAGE_INST_ID_ varchar(255),
    BUSINESS_STATUS_ varchar(255),
    primary key (ID_)
);

create table ACT_RE_PROCDEF (
    ID_ varchar(64) NOT NULL,
    REV_ integer,
    CATEGORY_ varchar(255),
    NAME_ varchar(255),
    KEY_ varchar(255) NOT NULL,
    VERSION_ integer NOT NULL,
    DEPLOYMENT_ID_ varchar(64),
    RESOURCE_NAME_ varchar(4000),
    DGRM_RESOURCE_NAME_ varchar(4000),
    DESCRIPTION_ varchar(4000),
    HAS_START_FORM_KEY_ boolean,
    HAS_GRAPHICAL_NOTATION_ boolean,
    SUSPENSION_STATE_ integer,
    TENANT_ID_ varchar(255) default '',
    DERIVED_FROM_ varchar(64),
    DERIVED_FROM_ROOT_ varchar(64),
    DERIVED_VERSION_ integer NOT NULL default 0,
    ENGINE_VERSION_ varchar(255),
    primary key (ID_)
);

create table ACT_EVT_LOG (
    LOG_NR_ SERIAL PRIMARY KEY,
    TYPE_ varchar(64),
    PROC_DEF_ID_ varchar(64),
    PROC_INST_ID_ varchar(64),
    EXECUTION_ID_ varchar(64),
    TASK_ID_ varchar(64),
    TIME_STAMP_ timestamp not null,
    USER_ID_ varchar(255),
    DATA_ bytea,
    LOCK_OWNER_ varchar(255),
    LOCK_TIME_ timestamp null,
    IS_PROCESSED_ smallint default 0
);

create table ACT_PROCDEF_INFO (
	ID_ varchar(64) not null,
    PROC_DEF_ID_ varchar(64) not null,
    REV_ integer,
    INFO_JSON_ID_ varchar(64),
    primary key (ID_)
);

create table ACT_RU_ACTINST (
    ID_ varchar(64) not null,
    REV_ integer default 1,
    PROC_DEF_ID_ varchar(64) not null,
    PROC_INST_ID_ varchar(64) not null,
    EXECUTION_ID_ varchar(64) not null,
    ACT_ID_ varchar(255) not null,
    TASK_ID_ varchar(64),
    CALL_PROC_INST_ID_ varchar(64),
    ACT_NAME_ varchar(255),
    ACT_TYPE_ varchar(255) not null,
    ASSIGNEE_ varchar(255),
    COMPLETED_BY_ varchar(255),
    START_TIME_ timestamp not null,
    END_TIME_ timestamp,
    DURATION_ bigint,
    TRANSACTION_ORDER_ integer,
    DELETE_REASON_ varchar(4000),
    TENANT_ID_ varchar(255) default '',
    primary key (ID_)
);

create index ACT_IDX_EXEC_BUSKEY on ACT_RU_EXECUTION(BUSINESS_KEY_);
create index ACT_IDX_EXE_ROOT on ACT_RU_EXECUTION(ROOT_PROC_INST_ID_);
create index ACT_IDX_EXEC_REF_ID_ on ACT_RU_EXECUTION(REFERENCE_ID_);
create index ACT_IDX_VARIABLE_TASK_ID on ACT_RU_VARIABLE(TASK_ID_);

create index ACT_IDX_BYTEAR_DEPL on ACT_GE_BYTEARRAY(DEPLOYMENT_ID_);

create index ACT_IDX_RU_ACTI_START on ACT_RU_ACTINST(START_TIME_);
create index ACT_IDX_RU_ACTI_END on ACT_RU_ACTINST(END_TIME_);
create index ACT_IDX_RU_ACTI_PROC on ACT_RU_ACTINST(PROC_INST_ID_);
create index ACT_IDX_RU_ACTI_PROC_ACT on ACT_RU_ACTINST(PROC_INST_ID_, ACT_ID_);
create index ACT_IDX_RU_ACTI_EXEC on ACT_RU_ACTINST(EXECUTION_ID_);
create index ACT_IDX_RU_ACTI_EXEC_ACT on ACT_RU_ACTINST(EXECUTION_ID_, ACT_ID_);
create index ACT_IDX_RU_ACTI_TASK on ACT_RU_ACTINST(TASK_ID_);

alter table ACT_GE_BYTEARRAY
    add constraint ACT_FK_BYTEARR_DEPL
    foreign key (DEPLOYMENT_ID_) 
    references ACT_RE_DEPLOYMENT (ID_);

alter table ACT_RE_PROCDEF
    add constraint ACT_UNIQ_PROCDEF
    unique (KEY_,VERSION_, DERIVED_VERSION_, TENANT_ID_);
    
create index ACT_IDX_EXE_PROCINST on ACT_RU_EXECUTION(PROC_INST_ID_);
alter table ACT_RU_EXECUTION
    add constraint ACT_FK_EXE_PROCINST 
    foreign key (PROC_INST_ID_) 
    references ACT_RU_EXECUTION (ID_);

create index ACT_IDX_EXE_PARENT on ACT_RU_EXECUTION(PARENT_ID_);
alter table ACT_RU_EXECUTION
    add constraint ACT_FK_EXE_PARENT
    foreign key (PARENT_ID_) 
    references ACT_RU_EXECUTION (ID_);
    
create index ACT_IDX_EXE_SUPER on ACT_RU_EXECUTION(SUPER_EXEC_);
alter table ACT_RU_EXECUTION
    add constraint ACT_FK_EXE_SUPER
    foreign key (SUPER_EXEC_) 
    references ACT_RU_EXECUTION (ID_);
    

create index ACT_IDX_EXE_PROCDEF on ACT_RU_EXECUTION(PROC_DEF_ID_); 
alter table ACT_RU_EXECUTION
    add constraint ACT_FK_EXE_PROCDEF 
    foreign key (PROC_DEF_ID_) 
    references ACT_RE_PROCDEF (ID_);    
    

create index ACT_IDX_TSKASS_TASK on ACT_RU_IDENTITYLINK(TASK_ID_);
alter table ACT_RU_IDENTITYLINK
    add constraint ACT_FK_TSKASS_TASK
    foreign key (TASK_ID_) 
    references ACT_RU_TASK (ID_);
    
create index ACT_IDX_ATHRZ_PROCEDEF on ACT_RU_IDENTITYLINK(PROC_DEF_ID_);
alter table ACT_RU_IDENTITYLINK
    add constraint ACT_FK_ATHRZ_PROCEDEF
    foreign key (PROC_DEF_ID_) 
    references ACT_RE_PROCDEF (ID_);
    
create index ACT_IDX_IDL_PROCINST on ACT_RU_IDENTITYLINK(PROC_INST_ID_);
alter table ACT_RU_IDENTITYLINK
    add constraint ACT_FK_IDL_PROCINST
    foreign key (PROC_INST_ID_) 
    references ACT_RU_EXECUTION (ID_);    
    
create index ACT_IDX_TASK_EXEC on ACT_RU_TASK(EXECUTION_ID_);
alter table ACT_RU_TASK
    add constraint ACT_FK_TASK_EXE
    foreign key (EXECUTION_ID_)
    references ACT_RU_EXECUTION (ID_);
    
create index ACT_IDX_TASK_PROCINST on ACT_RU_TASK(PROC_INST_ID_);
alter table ACT_RU_TASK
    add constraint ACT_FK_TASK_PROCINST
    foreign key (PROC_INST_ID_)
    references ACT_RU_EXECUTION (ID_);
    
create index ACT_IDX_TASK_PROCDEF on ACT_RU_TASK(PROC_DEF_ID_);
alter table ACT_RU_TASK
  add constraint ACT_FK_TASK_PROCDEF
  foreign key (PROC_DEF_ID_)
  references ACT_RE_PROCDEF (ID_);
  
create index ACT_IDX_VAR_EXE on ACT_RU_VARIABLE(EXECUTION_ID_);
alter table ACT_RU_VARIABLE 
    add constraint ACT_FK_VAR_EXE
    foreign key (EXECUTION_ID_) 
    references ACT_RU_EXECUTION (ID_);

create index ACT_IDX_VAR_PROCINST on ACT_RU_VARIABLE(PROC_INST_ID_);
alter table ACT_RU_VARIABLE
    add constraint ACT_FK_VAR_PROCINST
    foreign key (PROC_INST_ID_)
    references ACT_RU_EXECUTION(ID_);

create index ACT_IDX_JOB_EXECUTION_ID on ACT_RU_JOB(EXECUTION_ID_);
alter table ACT_RU_JOB 
    add constraint ACT_FK_JOB_EXECUTION 
    foreign key (EXECUTION_ID_) 
    references ACT_RU_EXECUTION (ID_);

create index ACT_IDX_JOB_PROCESS_INSTANCE_ID on ACT_RU_JOB(PROCESS_INSTANCE_ID_);
alter table ACT_RU_JOB 
    add constraint ACT_FK_JOB_PROCESS_INSTANCE 
    foreign key (PROCESS_INSTANCE_ID_) 
    references ACT_RU_EXECUTION (ID_);

create index ACT_IDX_JOB_PROC_DEF_ID on ACT_RU_JOB(PROC_DEF_ID_);
alter table ACT_RU_JOB 
    add constraint ACT_FK_JOB_PROC_DEF
    foreign key (PROC_DEF_ID_) 
    references ACT_RE_PROCDEF (ID_);

create index ACT_IDX_TIMER_JOB_EXECUTION_ID on ACT_RU_TIMER_JOB(EXECUTION_ID_);
alter table ACT_RU_TIMER_JOB 
    add constraint ACT_FK_TIMER_JOB_EXECUTION 
    foreign key (EXECUTION_ID_) 
    references ACT_RU_EXECUTION (ID_);

create index ACT_IDX_TIMER_JOB_PROCESS_INSTANCE_ID on ACT_RU_TIMER_JOB(PROCESS_INSTANCE_ID_);
alter table ACT_RU_TIMER_JOB 
    add constraint ACT_FK_TIMER_JOB_PROCESS_INSTANCE 
    foreign key (PROCESS_INSTANCE_ID_) 
    references ACT_RU_EXECUTION (ID_);

create index ACT_IDX_TIMER_JOB_PROC_DEF_ID on ACT_RU_TIMER_JOB(PROC_DEF_ID_);
alter table ACT_RU_TIMER_JOB 
    add constraint ACT_FK_TIMER_JOB_PROC_DEF
    foreign key (PROC_DEF_ID_) 
    references ACT_RE_PROCDEF (ID_);

create index ACT_IDX_SUSPENDED_JOB_EXECUTION_ID on ACT_RU_SUSPENDED_JOB(EXECUTION_ID_);    
alter table ACT_RU_SUSPENDED_JOB 
    add constraint ACT_FK_SUSPENDED_JOB_EXECUTION 
    foreign key (EXECUTION_ID_) 
    references ACT_RU_EXECUTION (ID_);
    
create index ACT_IDX_SUSPENDED_JOB_PROCESS_INSTANCE_ID on ACT_RU_SUSPENDED_JOB(PROCESS_INSTANCE_ID_);    
alter table ACT_RU_SUSPENDED_JOB 
    add constraint ACT_FK_SUSPENDED_JOB_PROCESS_INSTANCE 
    foreign key (PROCESS_INSTANCE_ID_) 
    references ACT_RU_EXECUTION (ID_);

create index ACT_IDX_SUSPENDED_JOB_PROC_DEF_ID on ACT_RU_SUSPENDED_JOB(PROC_DEF_ID_);    
alter table ACT_RU_SUSPENDED_JOB 
    add constraint ACT_FK_SUSPENDED_JOB_PROC_DEF
    foreign key (PROC_DEF_ID_) 
    references ACT_RE_PROCDEF (ID_);

create index ACT_IDX_DEADLETTER_JOB_EXECUTION_ID on ACT_RU_DEADLETTER_JOB(EXECUTION_ID_);      
alter table ACT_RU_DEADLETTER_JOB 
    add constraint ACT_FK_DEADLETTER_JOB_EXECUTION 
    foreign key (EXECUTION_ID_) 
    references ACT_RU_EXECUTION (ID_);
 
create index ACT_IDX_DEADLETTER_JOB_PROCESS_INSTANCE_ID on ACT_RU_DEADLETTER_JOB(PROCESS_INSTANCE_ID_);        
alter table ACT_RU_DEADLETTER_JOB 
    add constraint ACT_FK_DEADLETTER_JOB_PROCESS_INSTANCE 
    foreign key (PROCESS_INSTANCE_ID_) 
    references ACT_RU_EXECUTION (ID_);
    
create index ACT_IDX_DEADLETTER_JOB_PROC_DEF_ID on ACT_RU_DEADLETTER_JOB(PROC_DEF_ID_);    
alter table ACT_RU_DEADLETTER_JOB 
    add constraint ACT_FK_DEADLETTER_JOB_PROC_DEF
    foreign key (PROC_DEF_ID_) 
    references ACT_RE_PROCDEF (ID_);
    
alter table ACT_RU_EVENT_SUBSCR
    add constraint ACT_FK_EVENT_EXEC
    foreign key (EXECUTION_ID_)
    references ACT_RU_EXECUTION(ID_);

create index ACT_IDX_MODEL_SOURCE on ACT_RE_MODEL(EDITOR_SOURCE_VALUE_ID_);
alter table ACT_RE_MODEL 
    add constraint ACT_FK_MODEL_SOURCE 
    foreign key (EDITOR_SOURCE_VALUE_ID_) 
    references ACT_GE_BYTEARRAY (ID_);

create index ACT_IDX_MODEL_SOURCE_EXTRA on ACT_RE_MODEL(EDITOR_SOURCE_EXTRA_VALUE_ID_);
alter table ACT_RE_MODEL 
    add constraint ACT_FK_MODEL_SOURCE_EXTRA 
    foreign key (EDITOR_SOURCE_EXTRA_VALUE_ID_) 
    references ACT_GE_BYTEARRAY (ID_);
    
create index ACT_IDX_MODEL_DEPLOYMENT on ACT_RE_MODEL(DEPLOYMENT_ID_);    
alter table ACT_RE_MODEL 
    add constraint ACT_FK_MODEL_DEPLOYMENT 
    foreign key (DEPLOYMENT_ID_) 
    references ACT_RE_DEPLOYMENT (ID_);        

create index ACT_IDX_PROCDEF_INFO_JSON on ACT_PROCDEF_INFO(INFO_JSON_ID_);
alter table ACT_PROCDEF_INFO 
    add constraint ACT_FK_INFO_JSON_BA 
    foreign key (INFO_JSON_ID_) 
    references ACT_GE_BYTEARRAY (ID_);

create index ACT_IDX_PROCDEF_INFO_PROC on ACT_PROCDEF_INFO(PROC_DEF_ID_);
alter table ACT_PROCDEF_INFO 
    add constraint ACT_FK_INFO_PROCDEF 
    foreign key (PROC_DEF_ID_) 
    references ACT_RE_PROCDEF (ID_);
    
alter table ACT_PROCDEF_INFO
    add constraint ACT_UNIQ_INFO_PROCDEF
    unique (PROC_DEF_ID_);

insert into ACT_GE_PROPERTY
values ('schema.version', '7.2.0.2', 1);

insert into ACT_GE_PROPERTY
values ('schema.history', 'create(7.2.0.2)', 1);
create table ACT_HI_PROCINST (
    ID_ varchar(64) not null,
    REV_ integer default 1,
    PROC_INST_ID_ varchar(64) not null,
    BUSINESS_KEY_ varchar(255),
    PROC_DEF_ID_ varchar(64) not null,
    START_TIME_ timestamp not null,
    END_TIME_ timestamp,
    DURATION_ bigint,
    START_USER_ID_ varchar(255),
    START_ACT_ID_ varchar(255),
    END_ACT_ID_ varchar(255),
    SUPER_PROCESS_INSTANCE_ID_ varchar(64),
    DELETE_REASON_ varchar(4000),
    TENANT_ID_ varchar(255) default '',
    NAME_ varchar(255),
    CALLBACK_ID_ varchar(255),
    CALLBACK_TYPE_ varchar(255),
    REFERENCE_ID_ varchar(255),
    REFERENCE_TYPE_ varchar(255),
    PROPAGATED_STAGE_INST_ID_ varchar(255),
    BUSINESS_STATUS_ varchar(255),
    primary key (ID_),
    unique (PROC_INST_ID_)
);

create table ACT_HI_ACTINST (
    ID_ varchar(64) not null,
    REV_ integer default 1,
    PROC_DEF_ID_ varchar(64) not null,
    PROC_INST_ID_ varchar(64) not null,
    EXECUTION_ID_ varchar(64) not null,
    ACT_ID_ varchar(255) not null,
    TASK_ID_ varchar(64),
    CALL_PROC_INST_ID_ varchar(64),
    ACT_NAME_ varchar(255),
    ACT_TYPE_ varchar(255) not null,
    ASSIGNEE_ varchar(255),
    COMPLETED_BY_ varchar(255),
    START_TIME_ timestamp not null,
    END_TIME_ timestamp,
    TRANSACTION_ORDER_ integer,
    DURATION_ bigint,
    DELETE_REASON_ varchar(4000),
    TENANT_ID_ varchar(255) default '',
    primary key (ID_)
);

create table ACT_HI_DETAIL (
    ID_ varchar(64) not null,
    TYPE_ varchar(255) not null,
    PROC_INST_ID_ varchar(64),
    EXECUTION_ID_ varchar(64),
    TASK_ID_ varchar(64),
    ACT_INST_ID_ varchar(64),
    NAME_ varchar(255) not null,
    VAR_TYPE_ varchar(64),
    REV_ integer,
    TIME_ timestamp not null,
    BYTEARRAY_ID_ varchar(64),
    DOUBLE_ double precision,
    LONG_ bigint,
    TEXT_ varchar(4000),
    TEXT2_ varchar(4000),
    primary key (ID_)
);

create table ACT_HI_COMMENT (
    ID_ varchar(64) not null,
    TYPE_ varchar(255),
    TIME_ timestamp not null,
    USER_ID_ varchar(255),
    TASK_ID_ varchar(64),
    PROC_INST_ID_ varchar(64),
    ACTION_ varchar(255),
    MESSAGE_ varchar(4000),
    FULL_MSG_ bytea,
    primary key (ID_)
);

create table ACT_HI_ATTACHMENT (
    ID_ varchar(64) not null,
    REV_ integer,
    USER_ID_ varchar(255),
    NAME_ varchar(255),
    DESCRIPTION_ varchar(4000),
    TYPE_ varchar(255),
    TASK_ID_ varchar(64),
    PROC_INST_ID_ varchar(64),
    URL_ varchar(4000),
    CONTENT_ID_ varchar(64),
    TIME_ timestamp,
    primary key (ID_)
);

create index ACT_IDX_HI_PRO_INST_END on ACT_HI_PROCINST(END_TIME_);
create index ACT_IDX_HI_PRO_I_BUSKEY on ACT_HI_PROCINST(BUSINESS_KEY_);
create index ACT_IDX_HI_PRO_SUPER_PROCINST on ACT_HI_PROCINST(SUPER_PROCESS_INSTANCE_ID_);
create index ACT_IDX_HI_ACT_INST_START on ACT_HI_ACTINST(START_TIME_);
create index ACT_IDX_HI_ACT_INST_END on ACT_HI_ACTINST(END_TIME_);
create index ACT_IDX_HI_DETAIL_PROC_INST on ACT_HI_DETAIL(PROC_INST_ID_);
create index ACT_IDX_HI_DETAIL_ACT_INST on ACT_HI_DETAIL(ACT_INST_ID_);
create index ACT_IDX_HI_DETAIL_TIME on ACT_HI_DETAIL(TIME_);
create index ACT_IDX_HI_DETAIL_NAME on ACT_HI_DETAIL(NAME_);
create index ACT_IDX_HI_DETAIL_TASK_ID on ACT_HI_DETAIL(TASK_ID_);
create index ACT_IDX_HI_PROCVAR_PROC_INST on ACT_HI_VARINST(PROC_INST_ID_);
create index ACT_IDX_HI_PROCVAR_TASK_ID on ACT_HI_VARINST(TASK_ID_);
create index ACT_IDX_HI_PROCVAR_EXE on ACT_HI_VARINST(EXECUTION_ID_);
create index ACT_IDX_HI_ACT_INST_PROCINST on ACT_HI_ACTINST(PROC_INST_ID_, ACT_ID_);
create index ACT_IDX_HI_ACT_INST_EXEC on ACT_HI_ACTINST(EXECUTION_ID_, ACT_ID_);
create index ACT_IDX_HI_IDENT_LNK_TASK on ACT_HI_IDENTITYLINK(TASK_ID_);
create index ACT_IDX_HI_IDENT_LNK_PROCINST on ACT_HI_IDENTITYLINK(PROC_INST_ID_);
create index ACT_IDX_HI_TASK_INST_PROCINST on ACT_HI_TASKINST(PROC_INST_ID_);


