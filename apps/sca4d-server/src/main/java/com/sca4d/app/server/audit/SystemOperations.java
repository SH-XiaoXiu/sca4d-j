package com.sca4d.app.server.audit;

import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.OperationNode;
import com.sca4d.iam.base.rbac.PlatformRbacRepository;
import com.sca4d.iam.rbac.RbacDomain;
import com.sca4d.iam.rbac.RbacOperationModule;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 声明 Sca4d Server 系统管理接口使用的 Operation Catalog 节点。 */
@Component
public final class SystemOperations implements RbacOperationModule {
    private static final @NotNull String MODULE = "system";

    private static final @NotNull String AUDIT_RESOURCE = "audit";

    private static final @NotNull String SETTINGS_RESOURCE = "settings";

    private static final @NotNull String DICTIONARIES_RESOURCE = "dictionaries";

    private static final @NotNull String EMAIL_RESOURCE = "email";

    private static final @NotNull String SMS_RESOURCE = "sms";

    /** 读取审计记录。 */
    public static final @NotNull String AUDIT_READ = MODULE + ':' + AUDIT_RESOURCE + ':' + Action.READ_VALUE;

    /** 读取系统设置。 */
    public static final @NotNull String SETTINGS_READ = MODULE + ':' + SETTINGS_RESOURCE + ':' + Action.READ_VALUE;

    /** 修改系统设置。 */
    public static final @NotNull String SETTINGS_MANAGE = MODULE + ':' + SETTINGS_RESOURCE + ':' + Action.MANAGE_VALUE;

    /** 读取字典和聚合选项。 */
    public static final @NotNull String DICTIONARIES_READ = MODULE + ':' + DICTIONARIES_RESOURCE + ':'
            + Action.READ_VALUE;

    /** 管理动态字典和数据库选项。 */
    public static final @NotNull String DICTIONARIES_MANAGE = MODULE + ':' + DICTIONARIES_RESOURCE + ':'
            + Action.MANAGE_VALUE;

    /** 读取 Email 通知配置。 */
    public static final @NotNull String EMAIL_READ = MODULE + ':' + EMAIL_RESOURCE + ':' + Action.READ_VALUE;

    /** 管理 SMTP 配置和 Email 模板。 */
    public static final @NotNull String EMAIL_MANAGE = MODULE + ':' + EMAIL_RESOURCE + ':' + Action.MANAGE_VALUE;

    /** 读取 SMS Provider 与传输绑定。 */
    public static final @NotNull String SMS_READ = MODULE + ':' + SMS_RESOURCE + ':' + Action.READ_VALUE;

    /** 管理 SMS Provider 与传输绑定。 */
    public static final @NotNull String SMS_MANAGE = MODULE + ':' + SMS_RESOURCE + ':' + Action.MANAGE_VALUE;

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull OperationNode.Module operationTree() {
        return OperationNode.module(
                "system",
                "operation.system",
                900,
                OperationNode.resource(
                        AUDIT_RESOURCE,
                        "operation.system.audit",
                        100,
                        OperationNode.action(Action.READ_VALUE, "operation.system.audit.read", 100)),
                OperationNode.resource(
                        SETTINGS_RESOURCE,
                        "operation.system.settings",
                        200,
                        OperationNode.action(Action.READ_VALUE, "operation.system.settings.read", 100),
                        OperationNode.action(Action.MANAGE.value(), "operation.system.settings.manage", 200)),
                OperationNode.resource(
                        DICTIONARIES_RESOURCE,
                        "operation.system.dictionaries",
                        300,
                        OperationNode.action(Action.READ_VALUE, "operation.system.dictionaries.read", 100),
                        OperationNode.action(Action.MANAGE.value(), "operation.system.dictionaries.manage", 200)),
                OperationNode.resource(
                        EMAIL_RESOURCE,
                        "operation.system.email",
                        400,
                        OperationNode.action(Action.READ_VALUE, "operation.system.email.read", 100),
                        OperationNode.action(Action.MANAGE.value(), "operation.system.email.manage", 200)),
                OperationNode.resource(
                        SMS_RESOURCE,
                        "operation.system.sms",
                        500,
                        OperationNode.action(Action.READ_VALUE, "operation.system.sms.read", 100),
                        OperationNode.action(Action.MANAGE.value(), "operation.system.sms.manage", 200)));
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Set<@NotNull RbacDomain> domains() {
        return Set.of(PlatformRbacRepository.PLATFORM_DOMAIN);
    }
}
