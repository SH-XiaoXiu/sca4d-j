package com.sca4d.app.server.audit;

import com.sca4d.core.api.name.code.Attribute;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.OperationAxes;
import com.sca4d.core.api.permission.OperationCode;
import com.sca4d.core.api.permission.OperationModule;
import com.sca4d.core.api.permission.OperationNode;
import com.sca4d.iam.base.rbac.PlatformRbacRepository;
import com.sca4d.iam.rbac.RbacGrantable;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/** 声明 Sca4d Server 系统管理接口使用的 Operation Catalog 节点。 */
@Component
public class SystemOperations implements OperationModule {

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

    public static final @NotNull OperationCode AUDIT_READ = new OperationCode(AUDIT_READ_VALUE);

    public static final @NotNull OperationCode SETTINGS_READ = new OperationCode(SETTINGS_READ_VALUE);

    public static final @NotNull OperationCode SETTINGS_MANAGE = new OperationCode(SETTINGS_MANAGE_VALUE);

    public static final @NotNull OperationCode DICTIONARIES_READ = new OperationCode(DICTIONARIES_READ_VALUE);

    public static final @NotNull OperationCode DICTIONARIES_MANAGE = new OperationCode(DICTIONARIES_MANAGE_VALUE);

    public static final @NotNull OperationCode EMAIL_READ = new OperationCode(EMAIL_READ_VALUE);

    public static final @NotNull OperationCode EMAIL_MANAGE = new OperationCode(EMAIL_MANAGE_VALUE);

    public static final @NotNull OperationCode SMS_READ = new OperationCode(SMS_READ_VALUE);

    public static final @NotNull OperationCode SMS_MANAGE = new OperationCode(SMS_MANAGE_VALUE);

    private static final @NotNull TreeCode ROOT = TreeCode.ofSegment("SYSTEM");

    private static final @NotNull Attribute GRANTABLE = RbacGrantable.of(PlatformRbacRepository.PLATFORM_DOMAIN);

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull OperationNode.Group operationTree() {
        return OperationNode.root(
                ROOT,
                900,
                group(
                        "AUDIT",
                        100,
                        OperationNode.leaf(
                                Action.READ,
                                100,
                                OperationAxes.Intent.READ,
                                OperationAxes.Nature.FUNCTION,
                                GRANTABLE)),
                group("SETTINGS", 200, leaf(Action.READ, 100), leaf(Action.MANAGE, 200)),
                group("DICTIONARIES", 300, leaf(Action.READ, 100), leaf(Action.MANAGE, 200)),
                group("EMAIL", 400, leaf(Action.READ, 100), leaf(Action.MANAGE, 200)),
                group("SMS", 500, leaf(Action.READ, 100), leaf(Action.MANAGE, 200)));
    }

    private static @NotNull OperationNode.Declaration group(
            @NotNull String segment,
            int sortOrder,
            @NotNull OperationNode.Declaration... children) {
        return OperationNode.group(TreeCode.ofSegment(segment), sortOrder, children);
    }

    private static @NotNull OperationNode.Declaration leaf(@NotNull Action action, int sortOrder) {
        return OperationNode.leaf(
                action,
                sortOrder,
                action == Action.READ ? OperationAxes.Intent.READ : OperationAxes.Intent.UPDATE,
                OperationAxes.Nature.FUNCTION,
                GRANTABLE);
    }
}
