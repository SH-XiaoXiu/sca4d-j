package com.sca4d.app.server.audit;

import com.sca4d.core.api.name.code.TreeCode;
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
public class SystemOperations implements RbacOperationModule {
    /** 读取审计记录。 */
    public static final @NotNull String AUDIT_READ_VALUE = "SYSTEM:AUDIT:READ";

    /** 管理动态字典和数据库选项。 */
    public static final @NotNull String DICTIONARIES_MANAGE_VALUE = "SYSTEM:DICTIONARIES:MANAGE";

    /** 读取字典和聚合选项。 */
    public static final @NotNull String DICTIONARIES_READ_VALUE = "SYSTEM:DICTIONARIES:READ";

    /** 管理 SMTP 配置和 Email 模板。 */
    public static final @NotNull String EMAIL_MANAGE_VALUE = "SYSTEM:EMAIL:MANAGE";

    /** 读取 Email 通知配置。 */
    public static final @NotNull String EMAIL_READ_VALUE = "SYSTEM:EMAIL:READ";

    /** 修改系统设置。 */
    public static final @NotNull String SETTINGS_MANAGE_VALUE = "SYSTEM:SETTINGS:MANAGE";

    /** 读取系统设置。 */
    public static final @NotNull String SETTINGS_READ_VALUE = "SYSTEM:SETTINGS:READ";

    /** 管理 SMS Provider 与传输绑定。 */
    public static final @NotNull String SMS_MANAGE_VALUE = "SYSTEM:SMS:MANAGE";

    /** 读取 SMS Provider 与传输绑定。 */
    public static final @NotNull String SMS_READ_VALUE = "SYSTEM:SMS:READ";

    private static final @NotNull TreeCode MODULE = TreeCode.ofSegment("SYSTEM");

    private static final @NotNull TreeCode AUDIT_RESOURCE = TreeCode.ofSegment("AUDIT");

    private static final @NotNull TreeCode SETTINGS_RESOURCE = TreeCode.ofSegment("SETTINGS");

    private static final @NotNull TreeCode DICTIONARIES_RESOURCE = TreeCode.ofSegment("DICTIONARIES");

    private static final @NotNull TreeCode EMAIL_RESOURCE = TreeCode.ofSegment("EMAIL");

    private static final @NotNull TreeCode SMS_RESOURCE = TreeCode.ofSegment("SMS");

    public static final @NotNull TreeCode AUDIT_READ = TreeCode.ofColon(AUDIT_READ_VALUE);

    public static final @NotNull TreeCode SETTINGS_READ = TreeCode.ofColon(SETTINGS_READ_VALUE);

    public static final @NotNull TreeCode SETTINGS_MANAGE = TreeCode.ofColon(SETTINGS_MANAGE_VALUE);

    public static final @NotNull TreeCode DICTIONARIES_READ = TreeCode.ofColon(DICTIONARIES_READ_VALUE);

    public static final @NotNull TreeCode DICTIONARIES_MANAGE = TreeCode.ofColon(DICTIONARIES_MANAGE_VALUE);

    public static final @NotNull TreeCode EMAIL_READ = TreeCode.ofColon(EMAIL_READ_VALUE);

    public static final @NotNull TreeCode EMAIL_MANAGE = TreeCode.ofColon(EMAIL_MANAGE_VALUE);

    public static final @NotNull TreeCode SMS_READ = TreeCode.ofColon(SMS_READ_VALUE);

    public static final @NotNull TreeCode SMS_MANAGE = TreeCode.ofColon(SMS_MANAGE_VALUE);

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull OperationNode.Module operationTree() {
        return OperationNode.module(
                MODULE,
                "operation.system",
                900,
                OperationNode.resource(
                        AUDIT_RESOURCE,
                        "operation.system.audit",
                        100,
                        OperationNode.action(Action.READ.code(), "operation.system.audit.read", 100)),
                OperationNode.resource(
                        SETTINGS_RESOURCE,
                        "operation.system.settings",
                        200,
                        OperationNode.action(Action.READ.code(), "operation.system.settings.read", 100),
                        OperationNode.action(Action.MANAGE.code(), "operation.system.settings.manage", 200)),
                OperationNode.resource(
                        DICTIONARIES_RESOURCE,
                        "operation.system.dictionaries",
                        300,
                        OperationNode.action(Action.READ.code(), "operation.system.dictionaries.read", 100),
                        OperationNode.action(Action.MANAGE.code(), "operation.system.dictionaries.manage", 200)),
                OperationNode.resource(
                        EMAIL_RESOURCE,
                        "operation.system.email",
                        400,
                        OperationNode.action(Action.READ.code(), "operation.system.email.read", 100),
                        OperationNode.action(Action.MANAGE.code(), "operation.system.email.manage", 200)),
                OperationNode.resource(
                        SMS_RESOURCE,
                        "operation.system.sms",
                        500,
                        OperationNode.action(Action.READ.code(), "operation.system.sms.read", 100),
                        OperationNode.action(Action.MANAGE.code(), "operation.system.sms.manage", 200)));
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Set<@NotNull RbacDomain> domains() {
        return Set.of(PlatformRbacRepository.PLATFORM_DOMAIN);
    }
}
