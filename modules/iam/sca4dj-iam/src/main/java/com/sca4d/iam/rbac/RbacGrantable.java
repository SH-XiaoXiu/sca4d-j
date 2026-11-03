package com.sca4d.iam.rbac;

import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.Attribute;
import com.sca4d.core.api.name.code.AttributeAxis;
import com.sca4d.core.api.name.code.AttributeConstraint;
import com.sca4d.core.api.permission.OperationNode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 声明 Operation 叶子可以授予哪些 {@link RbacDomain}。
 *
 * <p>{@link #GRANTABLE} 轴的值是 {@code GRANTABLE:<domain>}，由 {@link #of(RbacDomain)} 根据领域编码生成，
 * 领域集合是开放的，新增领域不需要修改本类。叶子只能授予它声明了 {@code GRANTABLE:<domain>} 的领域；
 * 没有声明任何 {@code GRANTABLE} 值的叶子不能授予任何领域，{@link PermissionSelectionRegistry} 会拒绝这样的目录。
 * IAM 自动配置把 {@link #GRANTABLE} 登记为属性轴 Bean。
 */
public class RbacGrantable {

    /** 可授予领域轴，叶子可以不声明，也可以声明多个值；每个值对应一个 {@link RbacDomain}。 */
    public static final @NotNull AttributeAxis GRANTABLE = new AttributeAxis(
            new Attribute("GRANTABLE"),
            AttributeConstraint.OPTIONAL);

    /**
     * @param domain 叶子可以授予的 RBAC 领域
     * @return {@code GRANTABLE:<domain>}，可直接作为叶子声明的属性
     * @throws NullPointerException 领域为 {@code null} 时抛出
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static @NotNull Attribute of(@NotNull RbacDomain domain) {
        return GRANTABLE.root().child(Objects.requireNonNull(domain, "RBAC 领域不能为空").format(NameFormat.COLON));
    }

    /**
     * @param leaf 已经登记到 Operation Catalog 的叶子
     * @return 叶子声明了 {@code GRANTABLE:<domain>} 的全部领域；没有声明时为空
     * @throws IllegalStateException {@code GRANTABLE} 值不是 {@code GRANTABLE:<domain>} 两段形式时抛出
     */
    @CheckReturnValue
    public static @NotNull @Unmodifiable Set<@NotNull RbacDomain> domainsOf(@NotNull OperationNode.Leaf leaf) {
        LinkedHashSet<RbacDomain> domains = new LinkedHashSet<>();
        for (Attribute attribute : Objects.requireNonNull(leaf, "Operation 叶子不能为空").get(GRANTABLE)) {
            if (attribute.size() != GRANTABLE.root().size() + 1) {
                throw new IllegalStateException(
                        "GRANTABLE 属性必须是 GRANTABLE:<domain> 两段形式 | operation=" + leaf.code()
                                + " attribute=" + attribute);
            }
            domains.add(new RbacDomain(attribute.nodes().get(attribute.size() - 1)));
        }
        return Set.copyOf(domains);
    }

    private RbacGrantable() {
    }
}
