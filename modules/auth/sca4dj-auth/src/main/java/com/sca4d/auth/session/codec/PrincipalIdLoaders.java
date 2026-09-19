package com.sca4d.auth.session.codec;

import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.authentication.principalsession.PrincipalIdLoader;
import com.sca4d.core.api.authentication.principalsession.PrincipalIdSet;
import com.sca4d.core.api.identity.Principal;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 调用当前身份支持的 PrincipalIdLoader，并校验每种 Principal 类型只有一个标识来源。 */
public final class PrincipalIdLoaders {

    private final @NotNull List<PrincipalIdLoader<?>> loaders;

    /**
     * 创建紧凑 Principal 标识装载服务。
     *
     * @param loaders 应用安装的全部 PrincipalIdLoader
     */
    public PrincipalIdLoaders(@NotNull List<@NotNull PrincipalIdLoader<?>> loaders) {
        this.loaders = List.copyOf(Objects.requireNonNull(loaders, "Principal ID Loader 列表不能为空"));
    }

    /**
     * 读取当前身份支持的全部紧凑 Principal 标识集合。
     *
     * @param identity 已经重新读取并校验的 Subject 和 Account
     * @return 按 Loader 顺序保存的非空 Principal 标识集合；没有贡献时为空列表
     * @throws IllegalStateException 多个 Loader 认领同一 Principal 类型，或 Loader 返回空值时抛出
     * @throws IllegalArgumentException Loader 返回非正数或重复标识时抛出
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull PrincipalIdSet<?>> load(
            @NotNull SessionIdentity identity) {
        SessionIdentity checkedIdentity = Objects.requireNonNull(identity, "SessionIdentity 不能为空");
        LinkedHashMap<Class<? extends Principal>, PrincipalIdSet<?>> loadedByType = new LinkedHashMap<>();
        Set<Class<? extends Principal>> claimedTypes = new LinkedHashSet<>();
        for (PrincipalIdLoader<?> loader : loaders) {
            PrincipalIdLoader<?> checkedLoader = Objects.requireNonNull(loader, "PrincipalIdLoader 不能为空");
            if (!checkedLoader.supports(checkedIdentity)) {
                continue;
            }
            Class<? extends Principal> principalType = Objects.requireNonNull(
                    checkedLoader.principalType(),
                    "PrincipalIdLoader 类型不能为空");
            if (!claimedTypes.add(principalType)) {
                throw new IllegalStateException(
                        "多个 PrincipalIdLoader 认领 Principal 类型: "
                                + principalType.getName());
            }
            Collection<Long> ids = Objects.requireNonNull(
                    checkedLoader.load(checkedIdentity),
                    "PrincipalIdLoader 不能返回 null");
            if (!ids.isEmpty()) {
                loadedByType.put(principalType, createSet(checkedLoader, ids));
            }
        }
        return List.copyOf(loadedByType.values());
    }

    /** 使用 Loader 声明的泛型类型创建已经完成基本校验的 Principal 标识集合。 */
    private static <P extends Principal> @NotNull PrincipalIdSet<P> createSet(
            @NotNull PrincipalIdLoader<P> loader,
            @NotNull Collection<@NotNull Long> ids) {
        return new PrincipalIdSet<>(loader.principalType(), ids);
    }
}
