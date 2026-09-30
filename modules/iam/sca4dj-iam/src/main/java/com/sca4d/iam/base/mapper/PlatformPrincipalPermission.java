package com.sca4d.iam.base.mapper;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存启用 Platform 角色为一个 Principal 提供的 Operation 选择符。 */
@SuppressWarnings("ClassCanBeRecord")
public class PlatformPrincipalPermission {

    private final @NotNull Long principalId;

    private final @NotNull String permissionSelector;

    /**
     * 创建 Principal Operation 查询结果。
     *
     * @param principalId PlatformPrincipal 标识
     * @param permissionSelector 具体 ACTION 编码或内部 {@code "*"} 选择符
     */
    public PlatformPrincipalPermission(
            @NotNull Long principalId,
            @NotNull String permissionSelector) {
        this.principalId = Objects.requireNonNull(principalId, "PlatformPrincipal 标识不能为空");
        this.permissionSelector = Objects.requireNonNull(permissionSelector, "Operation 选择符不能为空");
    }

    /**
     * 得到 Operation 的 PlatformPrincipal 标识。
     *
     * @return PlatformPrincipal 标识
     */
    @Contract(pure = true)
    public @NotNull Long principalId() {
        return principalId;
    }

    /**
     * 角色保存的 Operation 选择符。
     *
     * @return 具体 ACTION 编码或内部 {@code "*"} 选择符
     */
    @Contract(pure = true)
    public @NotNull String permissionSelector() {
        return permissionSelector;
    }
}
