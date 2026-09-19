package com.sca4d.auth.session.codec;

import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.authentication.principalsession.PrincipalLoader;
import com.sca4d.core.api.identity.Principal;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** 选择唯一负责当前身份的 {@link PrincipalLoader}，并校验建立 Session 所需的 Principal。 */
public final class PrincipalLoaders {

    private final List<PrincipalLoader> loaders;

    /**
     * 创建 Principal 装载服务。
     *
     * @param loaders 应用安装的全部 `PrincipalLoader`
     */
    public PrincipalLoaders(@NotNull List<@NotNull PrincipalLoader> loaders) {
        this.loaders = List.copyOf(Objects.requireNonNull(loaders, "Principal Loader 列表不能为空"));
    }

    /**
     * 从唯一认领当前身份的 Loader 读取全部可选择 Principal。
     *
     * @param identity 已经重新读取并校验的 Subject 和 Account
     * @return Principal 标识互不重复的不可修改列表；没有 Loader 认领当前身份时为空列表
     * @throws IllegalStateException 多个 Loader 认领当前身份、Loader 返回空值或重复标识时抛出
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull Principal> load(@NotNull SessionIdentity identity) {
        SessionIdentity checkedIdentity = Objects.requireNonNull(identity, "SessionIdentity 不能为空");
        List<PrincipalLoader> matched = loaders.stream()
                .filter(loader -> loader.supports(checkedIdentity))
                .toList();
        if (matched.isEmpty()) {
            return List.of();
        }
        if (matched.size() != 1) {
            throw new IllegalStateException("多个 PrincipalLoader 认领 SessionIdentity");
        }

        List<? extends Principal> loaded = Objects.requireNonNull(
                matched.getFirst().load(checkedIdentity),
                "PrincipalLoader 不能返回 null");
        LinkedHashMap<Long, Principal> principals = new LinkedHashMap<>();
        for (Principal principal : loaded) {
            Principal checked = Objects.requireNonNull(principal, "PrincipalLoader 不能返回 null Principal");
            if (principals.putIfAbsent(checked.principalId(), checked) != null) {
                throw new IllegalStateException("PrincipalLoader 返回重复 Principal: " + checked.principalId());
            }
        }
        return List.copyOf(principals.values());
    }
}
