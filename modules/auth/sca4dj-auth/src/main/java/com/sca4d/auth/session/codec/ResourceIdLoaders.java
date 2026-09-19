package com.sca4d.auth.session.codec;

import com.sca4d.core.api.authentication.principalsession.ResourceIdLoader;
import com.sca4d.core.api.authentication.principalsession.ResourceIdSet;
import com.sca4d.core.api.identity.Principal;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** 并发合并所有 ResourceIdLoader 为完整 Principal 贡献的数字 Resource 标识。 */
public final class ResourceIdLoaders {

    private final @NotNull List<ResourceIdLoader> loaders;

    private final @NotNull Executor executor;

    /**
     * 创建并发调用所有数字 Resource 标识 Loader 的装载服务。
     *
     * @param loaders 为完整 Principal 提供数字 Resource 标识的 Loader
     * @param executor 并发执行 Loader 的执行器
     */
    public ResourceIdLoaders(
            @NotNull List<@NotNull ResourceIdLoader> loaders,
            @NotNull Executor executor) {
        this.loaders = List.copyOf(Objects.requireNonNull(loaders, "Resource ID Loader 列表不能为空"));
        this.executor = Objects.requireNonNull(executor, "Resource ID Loader 执行器不能为空");
    }

    /**
     * 并发装载并合并每个完整 Principal 的数字 Resource 标识。
     *
     * @param principals 当前 Session 中完整装载且标识互不重复的 Principal
     * @return 按 Principal 分组的不可修改数字 Resource 标识集合；没有贡献时返回空映射
     * @throws IllegalStateException Loader 执行失败、返回未知 Principal 或重复 Resource 标识时抛出
     */
    @SuppressWarnings("DuplicatedCode") // 与 ResourceLoaders 的输入校验相似，但两者的合并规则和结果类型不同。
    @CheckReturnValue
    public @NotNull @Unmodifiable Map<@NotNull Long, @Unmodifiable List<@NotNull ResourceIdSet>> load(
            @NotNull Collection<? extends @NotNull Principal> principals) {
        List<Principal> checkedPrincipals = List.copyOf(Objects.requireNonNull(principals, "Principal 列表不能为空"));
        LinkedHashSet<Long> principalIds = new LinkedHashSet<>();
        for (Principal principal : checkedPrincipals) {
            Principal checked = Objects.requireNonNull(principal, "Principal 不能为空");
            if (!principalIds.add(checked.principalId())) {
                throw new IllegalStateException("Session 包含重复 Principal: " + checked.principalId());
            }
        }
        List<CompletableFuture<Map<Long, ? extends Collection<ResourceIdSet>>>> tasks = new ArrayList<>();
        for (ResourceIdLoader loader : loaders) {
            ResourceIdLoader checkedLoader = Objects.requireNonNull(loader, "ResourceIdLoader 不能为空");
            tasks.add(CompletableFuture.supplyAsync(() -> checkedLoader.load(checkedPrincipals), executor));
        }
        try {
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException exception) {
            throw new IllegalStateException("Session 数字 Resource 标识装载失败", exception.getCause());
        }

        // 多个 Loader 可以贡献同一种 Resource 类型；两个 Loader 返回同一个标识时直接拒绝建立 Session。
        LinkedHashMap<Long, LinkedHashMap<String, List<Long>>> merged = new LinkedHashMap<>();
        LinkedHashMap<Long, LinkedHashMap<String, Set<Long>>> seen = new LinkedHashMap<>();
        for (Long principalId : principalIds) {
            merged.put(principalId, new LinkedHashMap<>());
            seen.put(principalId, new LinkedHashMap<>());
        }
        for (CompletableFuture<Map<Long, ? extends Collection<ResourceIdSet>>> task : tasks) {
            Map<Long, ? extends Collection<ResourceIdSet>> loaded = Objects.requireNonNull(
                    task.join(),
                    "ResourceIdLoader 不能返回 null");
            if (!principalIds.containsAll(loaded.keySet())) {
                throw new IllegalStateException("ResourceIdLoader 返回了输入外的 Principal");
            }
            for (Map.Entry<Long, ? extends Collection<ResourceIdSet>> entry : loaded.entrySet()) {
                Collection<ResourceIdSet> contributed = Objects.requireNonNull(
                        entry.getValue(),
                        "Principal 数字 Resource 标识不能为空");
                for (ResourceIdSet resourceIdSet : contributed) {
                    ResourceIdSet checkedSet = Objects.requireNonNull(
                            resourceIdSet,
                            "ResourceIdLoader 不能返回 null ResourceIdSet");
                    List<Long> values = merged.get(entry.getKey())
                            .computeIfAbsent(checkedSet.resourceType(), ignored -> new ArrayList<>());
                    Set<Long> seenValues = seen.get(entry.getKey())
                            .computeIfAbsent(checkedSet.resourceType(), ignored -> new LinkedHashSet<>());
                    for (long resourceId : checkedSet.ids().toArray()) {
                        if (!seenValues.add(resourceId)) {
                            throw new IllegalStateException(
                                    "Session 数字 Resource 标识重复: "
                                            + checkedSet.resourceType() + "/" + resourceId);
                        }
                        values.add(resourceId);
                    }
                }
            }
        }
        LinkedHashMap<Long, List<ResourceIdSet>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, LinkedHashMap<String, List<Long>>> entry : merged.entrySet()) {
            List<ResourceIdSet> values = entry.getValue()
                    .entrySet()
                    .stream()
                    .map(group -> new ResourceIdSet(group.getKey(), group.getValue()))
                    .toList();
            if (!values.isEmpty()) {
                result.put(entry.getKey(), values);
            }
        }
        return Map.copyOf(result);
    }
}
