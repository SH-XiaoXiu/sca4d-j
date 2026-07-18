package com.sca4d.system;

import com.sca4d.common.permission.PermissionModule;
import com.sca4d.common.permission.PermissionNode;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * 系统权限。
 */
@Component
public class SystemPermissions implements PermissionModule {

    private static final String SYSTEM = "system";
    private static final String OPERATION_LOGS = SYSTEM + ":operation-logs";
    private static final String DICTIONARIES = SYSTEM + ":dictionaries";
    private static final String MENUS = SYSTEM + ":menus";
    private static final String MAIL_SENDERS = SYSTEM + ":mail-senders";
    private static final String MAIL_TEMPLATES = SYSTEM + ":mail-templates";
    private static final String IDENTITY_PROVIDERS = SYSTEM + ":identity-providers";
    private static final String SCHEDULED_JOBS = SYSTEM + ":scheduled-jobs";

    /** 分页查询平台操作日志并读取详情的权限编码。 */
    public static final String OPERATION_LOGS_READ = OPERATION_LOGS + ":read";

    /** 读取平台全局字典及其共用选择项的权限编码。 */
    public static final String DICTIONARIES_READ = DICTIONARIES + ":read";

    /** 修改平台全局字典展示顺序和可配置内容的权限编码。 */
    public static final String DICTIONARIES_MANAGE = DICTIONARIES + ":manage";

    /** 读取平台共用菜单树和注册路由选项的权限编码。 */
    public static final String MENUS_READ = MENUS + ":read";

    /** 管理平台共用菜单层级、标题、图标、排序和显隐的权限编码。 */
    public static final String MENUS_MANAGE = MENUS + ":manage";

    /** 读取 SMTP 地址、发件资料、启停状态和密码配置标记的权限编码。 */
    public static final String MAIL_SENDERS_READ = MAIL_SENDERS + ":read";

    /** 创建、修改、测试、启停和删除 SMTP 配置的权限编码。 */
    public static final String MAIL_SENDERS_MANAGE = MAIL_SENDERS + ":manage";

    /** 读取邮件模板标识、内容、变量和启停状态的权限编码。 */
    public static final String MAIL_TEMPLATES_READ = MAIL_TEMPLATES + ":read";

    /** 创建、修改、预览、测试、启停和删除邮件模板的权限编码。 */
    public static final String MAIL_TEMPLATES_MANAGE = MAIL_TEMPLATES + ":manage";

    /** 读取 GLOBAL IdentityProvider 通用字段和协议安全配置的权限编码。 */
    public static final String IDENTITY_PROVIDERS_READ = IDENTITY_PROVIDERS + ":read";

    /** 创建、修改、测试、启停和删除 GLOBAL IdentityProvider 的权限编码。 */
    public static final String IDENTITY_PROVIDERS_MANAGE = IDENTITY_PROVIDERS + ":manage";

    /** 读取系统 Quartz Job 运行状态的权限编码。 */
    public static final String SCHEDULED_JOBS_READ = SCHEDULED_JOBS + ":read";

    @Override
    public @NotNull PermissionNode permissionTree() {
        return PermissionNode.module(SYSTEM, "permission.system", 100,
                PermissionNode.resource(OPERATION_LOGS, "permission.system.operation-logs", 100,
                        PermissionNode.action(OPERATION_LOGS_READ, "permission.system.operation-logs.read", 100)),
                PermissionNode.resource(DICTIONARIES, "permission.system.dictionaries", 110,
                        PermissionNode.action(DICTIONARIES_READ, "permission.system.dictionaries.read", 100),
                        PermissionNode.action(DICTIONARIES_MANAGE, "permission.system.dictionaries.manage", 200)),
                PermissionNode.resource(MENUS, "permission.system.menus", 120,
                        PermissionNode.action(MENUS_READ, "permission.system.menus.read", 100),
                        PermissionNode.action(MENUS_MANAGE, "permission.system.menus.manage", 200)),
                PermissionNode.resource(MAIL_SENDERS, "permission.system.mail-senders", 130,
                        PermissionNode.action(MAIL_SENDERS_READ, "permission.system.mail-senders.read", 100),
                        PermissionNode.action(MAIL_SENDERS_MANAGE, "permission.system.mail-senders.manage", 200)),
                PermissionNode.resource(MAIL_TEMPLATES, "permission.system.mail-templates", 135,
                        PermissionNode.action(MAIL_TEMPLATES_READ, "permission.system.mail-templates.read", 100),
                        PermissionNode.action(MAIL_TEMPLATES_MANAGE, "permission.system.mail-templates.manage", 200)),
                PermissionNode.resource(IDENTITY_PROVIDERS, "permission.system.identity-providers", 138,
                        PermissionNode.action(IDENTITY_PROVIDERS_READ, "permission.system.identity-providers.read", 100),
                        PermissionNode.action(IDENTITY_PROVIDERS_MANAGE, "permission.system.identity-providers.manage", 200)),
                PermissionNode.resource(SCHEDULED_JOBS, "permission.system.scheduled-jobs", 140,
                        PermissionNode.action(SCHEDULED_JOBS_READ, "permission.system.scheduled-jobs.read", 100)));
    }
}
