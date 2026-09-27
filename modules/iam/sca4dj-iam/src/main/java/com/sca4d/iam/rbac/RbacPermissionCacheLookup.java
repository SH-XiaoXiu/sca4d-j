package com.sca4d.iam.rbac;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/** 保存一次 RBAC 权限缓存读取的命中结果和并发回填修订号。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class RbacPermissionCacheLookup {

    private final @NotNull @Unmodifiable Map<@NotNull RbacScope, @NotNull Map<@NotNull Long, @NotNull List<@NotNull PermissionSelector>>> cachedSelectors;

    private final @NotNull @Unmodifiable Map<@NotNull RbacScope, @NotNull Map<@NotNull Long, @NotNull Long>> revisions;

    /**
     * 创建一次缓存读取结果。
     *
     * @param cachedSelectors 只包含缓存命中坐标的不可修改权限选择符
     * @param revisions 缓存实现为请求坐标读取的修订号；没有修订号的坐标不得回填
     */
    public RbacPermissionCacheLookup(
            @NotNull Map<@NotNull RbacScope, ? extends Map<@NotNull Long, ? extends List<@NotNull PermissionSelector>>> cachedSelectors,
            @NotNull Map<@NotNull RbacScope, ? extends Map<@NotNull Long, @NotNull Long>> revisions) {
        this.cachedSelectors = copySelectors(cachedSelectors);
        this.revisions = copyRevisions(revisions);
    }

    /**
     * 返回缓存命中的权限选择符。
     *
     * @return 只包含命中坐标的不可修改结果
     */
    @Contract(pure = true)
    @CheckReturnValue
    public @NotNull @Unmodifiable Map<@NotNull RbacScope, @NotNull Map<@NotNull Long, @NotNull List<@NotNull PermissionSelector>>> cachedSelectors() {
        return Map.copyOf(cachedSelectors);
    }

    /**
     * 返回缓存读取时指定坐标的修订号。
     *
     * @param scope RBAC 范围
     * @param principalId Principal 标识
     * @return 缓存实现支持并发安全回填时返回修订号，否则为空
     */
    @Contract(pure = true)
    @CheckReturnValue
    public @NotNull OptionalLong revision(@NotNull RbacScope scope, @NotNull Long principalId) {
        Long revision = revisions
                .getOrDefault(Objects.requireNonNull(scope, "RBAC 范围不能为空"), Map.of())
                .get(Objects.requireNonNull(principalId, "Principal 标识不能为空"));
        return revision == null ? OptionalLong.empty() : OptionalLong.of(revision);
    }

    /** 复制缓存命中结果，并拒绝空坐标、空列表和空选择符。 */
    private static @NotNull @Unmodifiable Map<@NotNull RbacScope, @NotNull Map<@NotNull Long, @NotNull List<@NotNull PermissionSelector>>> copySelectors(
            @NotNull Map<@NotNull RbacScope, ? extends Map<@NotNull Long, ? extends List<@NotNull PermissionSelector>>> source) {
        LinkedHashMap<RbacScope, Map<Long, List<PermissionSelector>>> result = new LinkedHashMap<>();
        for (Map.Entry<RbacScope, ? extends Map<Long, ? extends List<PermissionSelector>>> scopeEntry : Objects
                .requireNonNull(source, "缓存命中结果不能为空")
                .entrySet()) {
            RbacScope scope = Objects.requireNonNull(scopeEntry.getKey(), "RBAC 范围不能为空");
            LinkedHashMap<Long, List<PermissionSelector>> principals = new LinkedHashMap<>();
            for (Map.Entry<Long, ? extends List<PermissionSelector>> principalEntry : Objects
                    .requireNonNull(scopeEntry.getValue(), "Principal 缓存结果不能为空")
                    .entrySet()) {
                Long principalId = Objects.requireNonNull(principalEntry.getKey(), "Principal 标识不能为空");
                principals.put(
                        principalId,
                        List.copyOf(Objects.requireNonNull(principalEntry.getValue(), "权限选择符列表不能为空")));
            }
            result.put(scope, Map.copyOf(principals));
        }
        return Map.copyOf(result);
    }

    /** 复制缓存修订号，并拒绝空坐标和负修订号。 */
    private static @NotNull @Unmodifiable Map<@NotNull RbacScope, @NotNull Map<@NotNull Long, @NotNull Long>> copyRevisions(
            @NotNull Map<@NotNull RbacScope, ? extends Map<@NotNull Long, @NotNull Long>> source) {
        LinkedHashMap<RbacScope, Map<Long, Long>> result = new LinkedHashMap<>();
        for (Map.Entry<RbacScope, ? extends Map<Long, Long>> scopeEntry : Objects
                .requireNonNull(source, "缓存修订号不能为空")
                .entrySet()) {
            RbacScope scope = Objects.requireNonNull(scopeEntry.getKey(), "RBAC 范围不能为空");
            LinkedHashMap<Long, Long> principals = new LinkedHashMap<>();
            for (Map.Entry<Long, Long> principalEntry : Objects
                    .requireNonNull(scopeEntry.getValue(), "Principal 缓存修订号不能为空")
                    .entrySet()) {
                Long principalId = Objects.requireNonNull(principalEntry.getKey(), "Principal 标识不能为空");
                Long revision = Objects.requireNonNull(principalEntry.getValue(), "缓存修订号不能为空");
                if (revision < 0) {
                    throw new IllegalArgumentException("缓存修订号不能为负数");
                }
                principals.put(principalId, revision);
            }
            result.put(scope, Map.copyOf(principals));
        }
        return Map.copyOf(result);
    }
}
