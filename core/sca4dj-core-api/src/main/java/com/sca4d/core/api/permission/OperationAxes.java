package com.sca4d.core.api.permission;

import com.sca4d.core.api.name.code.Attribute;
import com.sca4d.core.api.name.code.AttributeAxis;
import com.sca4d.core.api.name.code.AttributeConstraint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * 列出 Operation 叶子的标准属性轴 {@code INTENT}、{@code NATURE}、{@code RISK} 和 {@code VISIBILITY}，以及它们的常用属性值。
 *
 * <p>Spring 装配会把 {@link #standard()} 登记到每个 {@link OperationCatalog}，所以每个 {@link OperationNode.Leaf}
 * 都必须各声明一个 {@code INTENT} 值和一个 {@code NATURE} 值，{@code RISK} 可以不声明或声明多个。
 * {@code VISIBILITY} 轴可以不声明，只有 {@code VISIBILITY:UNLISTED} 一个值。
 * 轴上的值都是开放词汇：业务可以在轴根下增加自己的值，例如在 {@code RISK} 下增加 {@code RISK:EXPORT:BULK}，
 * 并在自己的 {@link AttributeAxis} Bean 中登记新的轴。
 */
public class OperationAxes {

    /**
     * 操作意图轴，每个叶子必须且只能有一个值。
     *
     * <p>{@code INTENT:READ} 只读取数据，{@code INTENT:ACCESS} 授予整个范围的访问入口，
     * {@code INTENT:WRITE} 之下的值表示会改变数据或触发外部效果，例如 {@code INTENT:WRITE:DELETE}。
     * 叶子应使用 {@code INTENT:WRITE} 的后代，不直接使用 {@code INTENT:WRITE} 自身。
     */
    public static final @NotNull AttributeAxis INTENT = new AttributeAxis(
            new Attribute("INTENT"),
            AttributeConstraint.REQUIRED_EXCLUSIVE);

    /** 操作性质轴，每个叶子必须且只能有一个值：功能入口或数据范围。 */
    public static final @NotNull AttributeAxis NATURE = new AttributeAxis(
            new Attribute("NATURE"),
            AttributeConstraint.REQUIRED_EXCLUSIVE);

    /** 风险轴，叶子可以不声明，也可以声明多个值。 */
    public static final @NotNull AttributeAxis RISK = new AttributeAxis(
            new Attribute("RISK"),
            AttributeConstraint.OPTIONAL);

    /** 展示轴，叶子可以不声明；声明后控制叶子是否列入授权编辑器目录。 */
    public static final @NotNull AttributeAxis VISIBILITY = new AttributeAxis(
            new Attribute("VISIBILITY"),
            AttributeConstraint.OPTIONAL);

    /** {@link #standard()} 返回的不可变列表。 */
    private static final @NotNull @Unmodifiable List<AttributeAxis> STANDARD = List
            .of(INTENT, NATURE, RISK, VISIBILITY);

    /**
     * @return 按 {@code INTENT}、{@code NATURE}、{@code RISK}、{@code VISIBILITY} 顺序排列的标准属性轴，
     *         可直接传给 {@link OperationCatalog}
     */
    public static @NotNull @Unmodifiable List<AttributeAxis> standard() {
        return STANDARD;
    }

    /** {@link #INTENT} 轴的常用值。 */
    public static class Intent {

        /** 只读取数据，不修改任何状态。 */
        public static final @NotNull Attribute READ = INTENT.root().child("READ");

        /** 授予整个范围的访问入口，通常由通配叶子 {@code *} 使用。 */
        public static final @NotNull Attribute ACCESS = INTENT.root().child("ACCESS");

        /** 改变数据或触发外部效果的所有意图的共同上级；叶子使用它的后代。 */
        public static final @NotNull Attribute WRITE = INTENT.root().child("WRITE");

        /** 创建新对象。 */
        public static final @NotNull Attribute CREATE = WRITE.child("CREATE");

        /** 修改已有对象。 */
        public static final @NotNull Attribute UPDATE = WRITE.child("UPDATE");

        /** 删除对象。 */
        public static final @NotNull Attribute DELETE = WRITE.child("DELETE");

        /** 对对象执行除增删改以外的业务动作。 */
        public static final @NotNull Attribute OPERATE = WRITE.child("OPERATE");

        /** 导出数据。 */
        public static final @NotNull Attribute EXPORT = WRITE.child("EXPORT");

        /** 导入数据。 */
        public static final @NotNull Attribute IMPORT = WRITE.child("IMPORT");

        /** 分配或移除对象之间的关联。 */
        public static final @NotNull Attribute ASSIGN = WRITE.child("ASSIGN");

        /** 发送消息或通知。 */
        public static final @NotNull Attribute SEND = WRITE.child("SEND");

        /** 重置对象状态或凭据。 */
        public static final @NotNull Attribute RESET = WRITE.child("RESET");

        /** 确认一个待确认的结果。 */
        public static final @NotNull Attribute CONFIRM = WRITE.child("CONFIRM");

        /** 跳过常规条件强制执行。 */
        public static final @NotNull Attribute FORCE = WRITE.child("FORCE");

        /** 显示默认被隐藏或脱敏的内容。 */
        public static final @NotNull Attribute REVEAL = WRITE.child("REVEAL");

        private Intent() {
        }
    }

    /** {@link #NATURE} 轴的常用值。 */
    public static class Nature {

        /** 功能入口：授权后可以使用某项功能。 */
        public static final @NotNull Attribute FUNCTION = NATURE.root().child("FUNCTION");

        /** 数据范围：授权后可以访问一个范围内的全部数据。 */
        public static final @NotNull Attribute DATA_SCOPE = NATURE.root().child("DATA_SCOPE");

        private Nature() {
        }
    }

    /** {@link #RISK} 轴的通用值；只和某个应用有关的风险值由该应用在 {@code RISK} 下自行定义。 */
    public static class Risk {

        /** 执行后无法恢复。 */
        public static final @NotNull Attribute IRREVERSIBLE = RISK.root().child("IRREVERSIBLE");

        /** 可以提升授予对象的权限。 */
        public static final @NotNull Attribute PRIVILEGE_ESCALATION = RISK.root()
                .child("PRIVILEGE")
                .child("ESCALATION");

        /** 可以扩大对象能访问的数据范围。 */
        public static final @NotNull Attribute SCOPE_WIDENING = RISK.root().child("SCOPE").child("WIDENING");

        /** 可以看到凭据明文或其他敏感凭据信息。 */
        public static final @NotNull Attribute CREDENTIAL_DISCLOSURE = RISK.root()
                .child("CREDENTIAL")
                .child("DISCLOSURE");

        /** 可以创建、重置或替换凭据。 */
        public static final @NotNull Attribute CREDENTIAL_MANAGEMENT = RISK.root()
                .child("CREDENTIAL")
                .child("MANAGEMENT");

        private Risk() {
        }
    }

    /** {@link #VISIBILITY} 轴的值。 */
    public static class Visibility {

        /**
         * 不列入授权编辑器目录。叶子仍然存在于 {@link OperationCatalog}，也可以被授予和判断，
         * 只是授权编辑器的目录展示应当跳过它，并移除因此没有可见叶子的分组。
         */
        public static final @NotNull Attribute UNLISTED = VISIBILITY.root().child("UNLISTED");

        private Visibility() {
        }
    }

    private OperationAxes() {
    }
}
