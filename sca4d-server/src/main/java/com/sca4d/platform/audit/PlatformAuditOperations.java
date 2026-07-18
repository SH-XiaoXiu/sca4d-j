package com.sca4d.platform.audit;

import com.sca4d.common.audit.AuditOperationContributor;
import com.sca4d.common.audit.AuditOperationDefinition;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;

/** 平台 Account、操作员、租户、租户成员和代管字典操作的审计动作目录。 */
@Component
public class PlatformAuditOperations implements AuditOperationContributor {

    /** 平台 Account 修改记录使用的审计模块编码。 */
    public static final String ACCOUNTS = "platform.accounts";
    /** 平台操作员修改记录使用的审计模块编码。 */
    public static final String OPERATORS = "platform.operators";
    /** 平台角色修改记录使用的审计模块编码。 */
    public static final String ROLES = "platform.roles";
    /** 租户主记录修改使用的审计模块编码。 */
    public static final String TENANTS = "platform.tenants";
    /** PLATFORM Principal 租户 ACL 修改使用的审计模块编码。 */
    public static final String TENANT_ACCESS = "platform.tenant-access";
    /** Schedule 修改和人工执行使用的审计模块编码。 */
    public static final String SCHEDULES = "platform.schedules";
    /** 平台代管租户成员使用的审计模块编码。 */
    public static final String TENANT_MEMBERS = "platform.tenant-members";
    /** 平台代管租户 Account 安全操作使用的审计模块编码。 */
    public static final String TENANT_ACCOUNTS = "platform.tenant-accounts";
    /** 平台代管租户字典使用的审计模块编码。 */
    public static final String TENANT_DICTIONARIES = "platform.tenant-dictionaries";
    /** 平台代管租户角色使用的审计模块编码。 */
    public static final String TENANT_ROLES = "platform.tenant-roles";
    /** 创建 Account、角色、租户、Schedule 或字典的动作编码。 */
    public static final String CREATE = "create";
    /** 修改 Account、角色、租户、Schedule 或字典内容的动作编码。 */
    public static final String UPDATE = "update";
    /** 删除 Account、认证材料、角色、租户、Schedule 或字典的动作编码。 */
    public static final String DELETE = "delete";
    /** 完整替换租户 ACL 的动作编码。 */
    public static final String REPLACE = "replace";
    /** 修改 Account、认证材料、租户或成员启用状态的动作编码。 */
    public static final String CHANGE_STATUS = "change-status";
    /** 修改 PLATFORM Principal 启用状态的动作编码。 */
    public static final String CHANGE_PLATFORM_PRINCIPAL_STATUS = "change-platform-principal-status";
    /** 完整替换 PLATFORM Principal 平台角色的动作编码。 */
    public static final String REPLACE_PLATFORM_PRINCIPAL_ROLES = "replace-platform-principal-roles";
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
    /** 完整替换角色权限的动作编码。 */
    public static final String REPLACE_PERMISSIONS = "replace-permissions";
    /** 为 Schedule 创建人工执行记录的动作编码。 */
    public static final String CREATE_EXECUTION = "create-execution";
    /** 创建 Account 并加入租户的动作编码。 */
    public static final String CREATE_ACCOUNT = "create-account";
    /** 使用既有 Account 创建租户成员的动作编码。 */
    public static final String ADD_EXISTING_ACCOUNT = "add-existing-account";
    /** 完整替换租户成员角色的动作编码。 */
    public static final String REPLACE_ROLES = "replace-roles";
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

    /**
     * 返回平台管理操作当前能够产生的全部审计动作。
     *
     * @return 按 Account、租户、成员和租户字典分组的固定动作定义
     */
    @Override
    public @NotNull List<AuditOperationDefinition> auditOperations() {
        return List.of(
                AuditOperationDefinition.standard(ACCOUNTS, 11, CREATE, 10),
                AuditOperationDefinition.standard(ACCOUNTS, 11, UPDATE, 20),
                AuditOperationDefinition.standard(ACCOUNTS, 11, CHANGE_STATUS, 30),
                AuditOperationDefinition.standard(ACCOUNTS, 11, BIND_PASSWORD, 40),
                AuditOperationDefinition.standard(ACCOUNTS, 11, REPLACE_PASSWORD, 50),
                AuditOperationDefinition.standard(ACCOUNTS, 11, RESET_MFA, 60),
                AuditOperationDefinition.standard(ACCOUNTS, 11, REISSUE_RECOVERY_CODES, 70),
                AuditOperationDefinition.standard(ACCOUNTS, 11, REVOKE_SESSION, 80),
                AuditOperationDefinition.standard(ACCOUNTS, 11, REVOKE_SESSIONS, 90),
                AuditOperationDefinition.standard(ACCOUNTS, 11, DELETE, 100),
                AuditOperationDefinition.standard(OPERATORS, 12, CHANGE_PLATFORM_PRINCIPAL_STATUS, 10),
                AuditOperationDefinition.standard(OPERATORS, 12, CREATE, 20),
                AuditOperationDefinition.standard(OPERATORS, 12, REPLACE_PLATFORM_PRINCIPAL_ROLES, 30),
                AuditOperationDefinition.standard(ROLES, 18, CREATE, 10),
                AuditOperationDefinition.standard(ROLES, 18, UPDATE, 20),
                AuditOperationDefinition.standard(ROLES, 18, REPLACE_PERMISSIONS, 30),
                AuditOperationDefinition.standard(ROLES, 18, DELETE, 40),
                AuditOperationDefinition.standard(TENANTS, 20, CREATE, 10),
                AuditOperationDefinition.standard(TENANTS, 20, UPDATE, 20),
                AuditOperationDefinition.standard(TENANTS, 20, CHANGE_STATUS, 30),
                AuditOperationDefinition.standard(TENANTS, 20, DELETE, 40),
                AuditOperationDefinition.standard(TENANT_ACCESS, 25, REPLACE, 10),
                AuditOperationDefinition.standard(SCHEDULES, 27, CREATE, 10),
                AuditOperationDefinition.standard(SCHEDULES, 27, UPDATE, 20),
                AuditOperationDefinition.standard(SCHEDULES, 27, DELETE, 30),
                AuditOperationDefinition.standard(SCHEDULES, 27, CREATE_EXECUTION, 40),
                AuditOperationDefinition.standard(TENANT_ACCOUNTS, 29, BIND_PASSWORD, 10),
                AuditOperationDefinition.standard(TENANT_ACCOUNTS, 29, REPLACE_PASSWORD, 20),
                AuditOperationDefinition.standard(TENANT_ACCOUNTS, 29, RESET_MFA, 30),
                AuditOperationDefinition.standard(TENANT_ACCOUNTS, 29, REISSUE_RECOVERY_CODES, 40),
                AuditOperationDefinition.standard(TENANT_ACCOUNTS, 29, REVOKE_SESSION, 50),
                AuditOperationDefinition.standard(TENANT_ACCOUNTS, 29, REVOKE_SESSIONS, 60),
                AuditOperationDefinition.standard(TENANT_MEMBERS, 30, CREATE_ACCOUNT, 10),
                AuditOperationDefinition.standard(TENANT_MEMBERS, 30, ADD_EXISTING_ACCOUNT, 20),
                AuditOperationDefinition.standard(TENANT_MEMBERS, 30, CHANGE_STATUS, 30),
                AuditOperationDefinition.standard(TENANT_MEMBERS, 30, REPLACE_ROLES, 40),
                AuditOperationDefinition.standard(TENANT_DICTIONARIES, 40, CREATE, 10),
                AuditOperationDefinition.standard(TENANT_DICTIONARIES, 40, UPDATE, 20),
                AuditOperationDefinition.standard(TENANT_DICTIONARIES, 40, DELETE, 30),
                AuditOperationDefinition.standard(TENANT_DICTIONARIES, 40, CREATE_ITEM, 40),
                AuditOperationDefinition.standard(TENANT_DICTIONARIES, 40, UPDATE_ITEM, 50),
                AuditOperationDefinition.standard(TENANT_DICTIONARIES, 40, DELETE_ITEM, 60),
                AuditOperationDefinition.standard(TENANT_DICTIONARIES, 40, CHANGE_SORT, 70),
                AuditOperationDefinition.standard(TENANT_DICTIONARIES, 40, CHANGE_ITEM_SORT, 80),
                AuditOperationDefinition.standard(TENANT_ROLES, 45, CREATE, 10),
                AuditOperationDefinition.standard(TENANT_ROLES, 45, UPDATE, 20),
                AuditOperationDefinition.standard(TENANT_ROLES, 45, REPLACE_PERMISSIONS, 30),
                AuditOperationDefinition.standard(TENANT_ROLES, 45, DELETE, 40));
    }
}
