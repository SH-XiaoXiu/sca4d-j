package com.sca4d.core.api.name.code;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 保存一条属性轴：一个轴根 {@link Attribute} 和对象在这条轴上的取值约束。
 *
 * <p>轴根下的严格后代是这条轴的属性值，轴根自身不是属性值。
 * 本类只保存轴根和约束并判断属性是否属于这条轴；是否满足约束由使用属性的对象在自己的构造或登记阶段检查。
 */
public class AttributeAxis {

    private final @NotNull Attribute root;

    private final @NotNull AttributeConstraint constraint;

    /**
     * @param root       轴根；轴根下的严格后代是这条轴的属性值
     * @param constraint 对象在这条轴上的取值约束
     * @throws NullPointerException 任一参数为 {@code null} 时抛出
     */
    public AttributeAxis(@NotNull Attribute root, @NotNull AttributeConstraint constraint) {
        this.root = Objects.requireNonNull(root, "属性轴根不能为空");
        this.constraint = Objects.requireNonNull(constraint, "属性轴约束不能为空");
    }

    /**
     * @return 轴根
     */
    public @NotNull Attribute root() {
        return root;
    }

    /**
     * @return 对象在这条轴上的取值约束
     */
    public @NotNull AttributeConstraint constraint() {
        return constraint;
    }

    /**
     * @param attribute 待判断的属性
     * @return 属性是轴根的严格后代时为 {@code true}；轴根自身和其他位置的属性返回 {@code false}
     * @throws NullPointerException 属性为 {@code null} 时抛出
     */
    @Contract(pure = true)
    public boolean contains(@NotNull Attribute attribute) {
        Attribute checked = Objects.requireNonNull(attribute, "属性不能为空");
        return checked.size() > root.size() && checked.isUnder(root);
    }
}
