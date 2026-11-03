package com.sca4d.iam.base.rbac;

import com.sca4d.core.api.name.code.Attribute;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.OperationAxes;
import com.sca4d.core.api.permission.OperationCode;
import com.sca4d.core.api.permission.OperationModule;
import com.sca4d.core.api.permission.OperationNode;
import com.sca4d.iam.rbac.RbacGrantable;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/**
 * 声明 Platform 角色、Account 和 Principal 角色管理使用的 Operation 模块。
 *
 * <p>每个叶子声明标准属性轴 {@link OperationAxes} 的 {@code INTENT} 和 {@code NATURE}；
 * 创建、修改 Platform 角色和分配 Principal 角色会改变权限，额外声明 {@code RISK:PRIVILEGE:ESCALATION}，
 * 删除类叶子额外声明 {@code RISK:IRREVERSIBLE}。{@code PRINCIPAL_ROLES_ASSIGN}
 * 额外声明 {@code VISIBILITY:UNLISTED}，不列入授权编辑器目录。
 * 所有叶子声明 {@code GRANTABLE:PLATFORM}，只能授予 Platform 域角色。
 *
 * <p>{@code *_VALUE} 字符串是注解参数使用的唯一手写编码，同名去掉后缀的 {@link OperationCode} 常量由它构造。
 */
public class PlatformOperations implements OperationModule {

    /**
     * 创建 Platform Account 或发送账号邀请。
     */
    public static final @NotNull String ACCOUNTS_CREATE_VALUE = "PLATFORM:ACCOUNTS:CREATE";

    /**
     * 删除 Platform Account。
     */
    public static final @NotNull String ACCOUNTS_DELETE_VALUE = "PLATFORM:ACCOUNTS:DELETE";

    /**
     * 读取 Platform Account 目录。
     */
    public static final @NotNull String ACCOUNTS_READ_VALUE = "PLATFORM:ACCOUNTS:READ";

    /**
     * 修改 Platform Account 资料、状态、邮箱验证和认证设置。
     */
    public static final @NotNull String ACCOUNTS_UPDATE_VALUE = "PLATFORM:ACCOUNTS:UPDATE";

    /**
     * 分配或移除 PlatformPrincipal 的角色。
     */
    public static final @NotNull String PRINCIPAL_ROLES_ASSIGN_VALUE = "PLATFORM:PRINCIPAL_ROLES:ASSIGN";

    /**
     * 创建 Platform 角色。
     */
    public static final @NotNull String ROLES_CREATE_VALUE = "PLATFORM:ROLES:CREATE";

    /**
     * 删除 Platform 角色。
     */
    public static final @NotNull String ROLES_DELETE_VALUE = "PLATFORM:ROLES:DELETE";

    /**
     * 读取 Platform 角色。
     */
    public static final @NotNull String ROLES_READ_VALUE = "PLATFORM:ROLES:READ";

    /**
     * 修改 Platform 角色名称、多语言字段、状态和 Operation。
     */
    public static final @NotNull String ROLES_UPDATE_VALUE = "PLATFORM:ROLES:UPDATE";

    /** 读取 Platform 角色的 Operation 编码，由 {@link #ROLES_READ_VALUE} 构造。 */
    public static final @NotNull OperationCode ROLES_READ = new OperationCode(ROLES_READ_VALUE);

    /** 创建 Platform 角色的 Operation 编码，由 {@link #ROLES_CREATE_VALUE} 构造。 */
    public static final @NotNull OperationCode ROLES_CREATE = new OperationCode(ROLES_CREATE_VALUE);

    /** 修改 Platform 角色的 Operation 编码，由 {@link #ROLES_UPDATE_VALUE} 构造。 */
    public static final @NotNull OperationCode ROLES_UPDATE = new OperationCode(ROLES_UPDATE_VALUE);

    /** 删除 Platform 角色的 Operation 编码，由 {@link #ROLES_DELETE_VALUE} 构造。 */
    public static final @NotNull OperationCode ROLES_DELETE = new OperationCode(ROLES_DELETE_VALUE);

    /** 读取 Platform Account 的 Operation 编码，由 {@link #ACCOUNTS_READ_VALUE} 构造。 */
    public static final @NotNull OperationCode ACCOUNTS_READ = new OperationCode(ACCOUNTS_READ_VALUE);

    /** 创建 Platform Account 的 Operation 编码，由 {@link #ACCOUNTS_CREATE_VALUE} 构造。 */
    public static final @NotNull OperationCode ACCOUNTS_CREATE = new OperationCode(ACCOUNTS_CREATE_VALUE);

    /** 修改 Platform Account 的 Operation 编码，由 {@link #ACCOUNTS_UPDATE_VALUE} 构造。 */
    public static final @NotNull OperationCode ACCOUNTS_UPDATE = new OperationCode(ACCOUNTS_UPDATE_VALUE);

    /** 删除 Platform Account 的 Operation 编码，由 {@link #ACCOUNTS_DELETE_VALUE} 构造。 */
    public static final @NotNull OperationCode ACCOUNTS_DELETE = new OperationCode(ACCOUNTS_DELETE_VALUE);

    /** 分配或移除 PlatformPrincipal 角色的 Operation 编码，由 {@link #PRINCIPAL_ROLES_ASSIGN_VALUE} 构造。 */
    public static final @NotNull OperationCode PRINCIPAL_ROLES_ASSIGN = new OperationCode(
            PRINCIPAL_ROLES_ASSIGN_VALUE);

    /** Platform 叶子只能授予 PLATFORM 域角色，所有叶子都携带该属性。 */
    private static final @NotNull Attribute PLATFORM_GRANTABLE = RbacGrantable
            .of(PlatformRbacRepository.PLATFORM_DOMAIN);

    // 目录的根分组、分组和自定义动词都从上面的编码取得，避免再手写一份。
    private static final @NotNull TreeCode ROOT = TreeCode.ofSegment(ROLES_READ.nodes().getFirst());

    private static final @NotNull TreeCode ROLES_GROUP = TreeCode.ofSegment(ROLES_READ.nodes().get(1));

    private static final @NotNull TreeCode ACCOUNTS_GROUP = TreeCode.ofSegment(ACCOUNTS_READ.nodes().get(1));

    private static final @NotNull TreeCode PRINCIPAL_ROLES_GROUP = TreeCode
            .ofSegment(PRINCIPAL_ROLES_ASSIGN.nodes().get(1));

    private static final @NotNull Action ASSIGN = new Action(PRINCIPAL_ROLES_ASSIGN.verb());

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckReturnValue
    public @NotNull OperationNode.Group operationTree() {
        return OperationNode.root(
                ROOT,
                100,
                OperationNode.group(
                        ROLES_GROUP,
                        100,
                        OperationNode.leaf(
                                Action.READ,
                                100,
                                OperationAxes.Intent.READ,
                                OperationAxes.Nature.FUNCTION,
                                PLATFORM_GRANTABLE),
                        OperationNode.leaf(
                                Action.CREATE,
                                200,
                                OperationAxes.Intent.CREATE,
                                OperationAxes.Nature.FUNCTION,
                                OperationAxes.Risk.PRIVILEGE_ESCALATION,
                                PLATFORM_GRANTABLE)
                                .requires(ROLES_READ),
                        OperationNode.leaf(
                                Action.UPDATE,
                                300,
                                OperationAxes.Intent.UPDATE,
                                OperationAxes.Nature.FUNCTION,
                                OperationAxes.Risk.PRIVILEGE_ESCALATION,
                                PLATFORM_GRANTABLE)
                                .requires(ROLES_READ),
                        OperationNode.leaf(
                                Action.DELETE,
                                400,
                                OperationAxes.Intent.DELETE,
                                OperationAxes.Nature.FUNCTION,
                                OperationAxes.Risk.IRREVERSIBLE,
                                PLATFORM_GRANTABLE)
                                .requires(ROLES_READ)),
                OperationNode.group(
                        ACCOUNTS_GROUP,
                        200,
                        OperationNode.leaf(
                                Action.READ,
                                100,
                                OperationAxes.Intent.READ,
                                OperationAxes.Nature.FUNCTION,
                                PLATFORM_GRANTABLE),
                        OperationNode.leaf(
                                Action.CREATE,
                                200,
                                OperationAxes.Intent.CREATE,
                                OperationAxes.Nature.FUNCTION,
                                PLATFORM_GRANTABLE)
                                .requires(ACCOUNTS_READ),
                        OperationNode.leaf(
                                Action.UPDATE,
                                300,
                                OperationAxes.Intent.UPDATE,
                                OperationAxes.Nature.FUNCTION,
                                PLATFORM_GRANTABLE)
                                .requires(ACCOUNTS_READ),
                        OperationNode.leaf(
                                Action.DELETE,
                                400,
                                OperationAxes.Intent.DELETE,
                                OperationAxes.Nature.FUNCTION,
                                OperationAxes.Risk.IRREVERSIBLE,
                                PLATFORM_GRANTABLE)
                                .requires(ACCOUNTS_READ)),
                OperationNode.group(
                        PRINCIPAL_ROLES_GROUP,
                        300,
                        OperationNode.leaf(
                                ASSIGN,
                                100,
                                OperationAxes.Intent.ASSIGN,
                                OperationAxes.Nature.FUNCTION,
                                OperationAxes.Risk.PRIVILEGE_ESCALATION,
                                OperationAxes.Visibility.UNLISTED,
                                PLATFORM_GRANTABLE)
                                // 授权编辑器中分配角色时需要选择账号和角色，因此依赖二者的读取权限。
                                .requires(ACCOUNTS_READ, ROLES_READ)));
    }
}
