package com.sca4d.tenant.audit;

import com.sca4d.common.audit.AuditOperationContributor;
import com.sca4d.common.audit.AuditOperationDefinition;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;

/** 当前租户成员和租户字典管理操作的审计动作目录。 */
@Component
public class TenantAuditOperations implements AuditOperationContributor {

    /** 当前租户成员修改记录使用的审计模块编码。 */
    public static final String TENANT_MEMBERS = "tenant.tenant-members";
    /** 当前租户 Account 安全操作使用的审计模块编码。 */
    public static final String ACCOUNTS = "tenant.accounts";
    /** 当前租户字典修改记录使用的审计模块编码。 */
    public static final String DICTIONARIES = "tenant.dictionaries";
    /** 当前租户角色修改记录使用的审计模块编码。 */
    public static final String ROLES = "tenant.roles";
    /** 当前租户 IdentityProvider 修改和连接测试使用的审计模块编码。 */
    public static final String IDENTITY_PROVIDERS = "tenant.identity-providers";
    /** 创建字典或角色的动作编码。 */
    public static final String CREATE = "create";
    /** 修改字典或角色内容的动作编码。 */
    public static final String UPDATE = "update";
    /** 删除字典或角色的动作编码。 */
    public static final String DELETE = "delete";
    /** 创建 Account 并加入当前租户的动作编码。 */
    public static final String CREATE_ACCOUNT = "create-account";
    /** 使用既有 Account 创建当前租户成员的动作编码。 */
    public static final String ADD_EXISTING_ACCOUNT = "add-existing-account";
    /** 修改租户成员启用状态的动作编码。 */
    public static final String CHANGE_STATUS = "change-status";
    /** 为 Account 绑定首个密码认证材料的动作编码。 */
    public static final String BIND_PASSWORD = "bind-password";
    /** 替换 Account 现有密码认证材料的动作编码。 */
    public static final String REPLACE_PASSWORD = "replace-password";
    /** 重置 Account MFA 要求并重签或撤销恢复码的动作编码。 */
    public static final String RESET_MFA = "reset-mfa";
    /** 为已启用 MFA 的 Account 重签恢复码的动作编码。 */
    public static final String REISSUE_RECOVERY_CODES = "reissue-recovery-codes";
    /** 撤销 Account 指定 Session 的动作编码。 */
    public static final String REVOKE_SESSION = "revoke-session";
    /** 结束 Account 全部 Session 的动作编码。 */
    public static final String REVOKE_SESSIONS = "revoke-sessions";
    /** 完整替换租户成员角色的动作编码。 */
    public static final String REPLACE_ROLES = "replace-roles";
    /** 完整替换租户角色权限的动作编码。 */
    public static final String REPLACE_PERMISSIONS = "replace-permissions";
    /** 在租户字典中创建选项的动作编码。 */
    public static final String CREATE_ITEM = "create-item";
    /** 修改租户字典选项内容的动作编码。 */
    public static final String UPDATE_ITEM = "update-item";
    /** 删除租户字典选项的动作编码。 */
    public static final String DELETE_ITEM = "delete-item";
    /** 修改租户字典类型排序的动作编码。 */
    public static final String CHANGE_SORT = "change-sort";
    /** 修改同一租户字典内选项排序的动作编码。 */
    public static final String CHANGE_ITEM_SORT = "change-item-sort";
    /** 使用当前身份源配置读取上游元数据或建立连接的动作编码。 */
    public static final String TEST_CONNECTION = "test-connection";

    /**
     * 返回租户管理操作当前能够产生的全部审计动作。
     *
     * @return 按成员和字典模块分组的固定动作定义
     */
    @Override
    public @NotNull List<AuditOperationDefinition> auditOperations() {
        return List.of(
                AuditOperationDefinition.standard(ACCOUNTS, 45, BIND_PASSWORD, 10),
                AuditOperationDefinition.standard(ACCOUNTS, 45, REPLACE_PASSWORD, 20),
                AuditOperationDefinition.standard(ACCOUNTS, 45, RESET_MFA, 30),
                AuditOperationDefinition.standard(ACCOUNTS, 45, REISSUE_RECOVERY_CODES, 40),
                AuditOperationDefinition.standard(ACCOUNTS, 45, REVOKE_SESSION, 50),
                AuditOperationDefinition.standard(ACCOUNTS, 45, REVOKE_SESSIONS, 60),
                AuditOperationDefinition.standard(TENANT_MEMBERS, 50, CREATE_ACCOUNT, 10),
                AuditOperationDefinition.standard(TENANT_MEMBERS, 50, ADD_EXISTING_ACCOUNT, 20),
                AuditOperationDefinition.standard(TENANT_MEMBERS, 50, CHANGE_STATUS, 30),
                AuditOperationDefinition.standard(TENANT_MEMBERS, 50, REPLACE_ROLES, 40),
                AuditOperationDefinition.standard(DICTIONARIES, 60, CREATE, 10),
                AuditOperationDefinition.standard(DICTIONARIES, 60, UPDATE, 20),
                AuditOperationDefinition.standard(DICTIONARIES, 60, DELETE, 30),
                AuditOperationDefinition.standard(DICTIONARIES, 60, CREATE_ITEM, 40),
                AuditOperationDefinition.standard(DICTIONARIES, 60, UPDATE_ITEM, 50),
                AuditOperationDefinition.standard(DICTIONARIES, 60, DELETE_ITEM, 60),
                AuditOperationDefinition.standard(DICTIONARIES, 60, CHANGE_SORT, 70),
                AuditOperationDefinition.standard(DICTIONARIES, 60, CHANGE_ITEM_SORT, 80),
                AuditOperationDefinition.standard(ROLES, 65, CREATE, 10),
                AuditOperationDefinition.standard(ROLES, 65, UPDATE, 20),
                AuditOperationDefinition.standard(ROLES, 65, REPLACE_PERMISSIONS, 30),
                AuditOperationDefinition.standard(ROLES, 65, DELETE, 40),
                AuditOperationDefinition.standard(IDENTITY_PROVIDERS, 67, CREATE, 10),
                AuditOperationDefinition.standard(IDENTITY_PROVIDERS, 67, UPDATE, 20),
                AuditOperationDefinition.standard(IDENTITY_PROVIDERS, 67, CHANGE_STATUS, 30),
                AuditOperationDefinition.standard(IDENTITY_PROVIDERS, 67, TEST_CONNECTION, 40),
                AuditOperationDefinition.standard(IDENTITY_PROVIDERS, 67, DELETE, 50));
    }
}
