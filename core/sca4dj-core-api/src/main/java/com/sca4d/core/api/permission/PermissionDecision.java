package com.sca4d.core.api.permission;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * 表示权限策略作出的允许或拒绝判定（Permission Decision）。
 *
 * <p>具体资源通常只需要允许或拒绝。集合资源被部分允许时，判定可以携带资源范围（Resource Scope，{@link ResourceScope}），由
 * 数据仓库（Repository）将范围落实为查询或原子写入条件。
 */
public final class PermissionDecision {

    private static final PermissionDecision ALLOWED = new PermissionDecision(true, null, null);

    private final boolean allowed;

    private final @Nullable String reason;

    private final @Nullable ResourceScope scope;

    private PermissionDecision(boolean allowed, @Nullable String reason, @Nullable ResourceScope scope) {
        this.allowed = allowed;
        this.reason = reason;
        this.scope = scope;
    }

    /**
     * 返回允许执行的权限判定。
     *
     * @return 允许执行的权限判定
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static PermissionDecision allow() {
        return ALLOWED;
    }

    /**
     * 创建携带业务资源范围的允许判定。
     *
     * @param scope 当前允许判定可以作用的业务资源子集
     * @return 携带指定资源范围的允许判定
     * @throws IllegalArgumentException 资源类型为空白时抛出
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static PermissionDecision allow(ResourceScope scope) {
        ResourceScope checked = Objects.requireNonNull(scope, "资源范围不能为空");
        TextUtils.requireText(checked.resourceType(), "资源范围类型");
        return new PermissionDecision(true, null, checked);
    }

    /**
     * 创建拒绝判定。
     *
     * @param reason 非空白拒绝原因
     * @return 拒绝判定
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static PermissionDecision deny(String reason) {
        return new PermissionDecision(false, TextUtils.requireText(reason, "权限拒绝原因"), null);
    }

    /**
     * 返回允许执行时返回 {@code true}。
     *
     * @return 允许执行时返回 {@code true}
     */
    @Contract(pure = true)
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * 返回拒绝原因；允许执行时为空。
     *
     * @return 拒绝原因；允许执行时为空
     */
    @Contract(pure = true)
    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    /**
     * 返回允许判定携带的业务资源范围。
     *
     * @return 集合资源被部分允许时返回资源范围；不需要限制范围或拒绝时为空
     */
    @Contract(pure = true)
    public Optional<ResourceScope> scope() {
        return Optional.ofNullable(scope);
    }

    /**
     * 按明确类型取得允许判定携带的业务资源范围。
     *
     * @param scopeType 业务模块定义的资源范围类型
     * @param <S> 资源范围类型
     * @return 类型匹配的业务资源范围
     * @throws IllegalStateException 当前判定未允许、没有携带范围或范围类型不匹配时抛出
     */
    @Contract(pure = true)
    public <S extends ResourceScope> S scopeAs(Class<S> scopeType) {
        Class<S> checkedType = Objects.requireNonNull(scopeType, "资源范围类型不能为空");
        if (!allowed || scope == null) {
            throw new IllegalStateException("当前权限判定没有可用的资源范围");
        }
        if (!checkedType.isInstance(scope)) {
            throw new IllegalStateException(
                    "资源范围类型不匹配: 实际 " + scope.getClass().getName() + "，要求 " + checkedType.getName());
        }
        return checkedType.cast(scope);
    }
}
