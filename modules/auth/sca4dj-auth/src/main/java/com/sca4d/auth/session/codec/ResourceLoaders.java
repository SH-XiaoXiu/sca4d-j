package com.sca4d.auth.session.codec;

import com.sca4d.core.api.authentication.principalsession.ResourceLoader;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.resource.Resource;

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

/** 并发合并所有 ResourceLoader 的静态资源贡献。 */
public class ResourceLoaders {

    private final List<ResourceLoader> loaders;

    private final Executor executor;

    /**
     * 创建并发调用所有资源 Loader 的装载服务。
     *
     * @param loaders 为 Principal 提供静态资源的 Loader 列表
     * @param executor 并发执行 Loader 的执行器
     */
    public ResourceLoaders(List<ResourceLoader> loaders, Executor executor) {
        this.loaders = List.copyOf(Objects.requireNonNull(loaders, "Session 资源 Loader 不能为空"));
        this.executor = Objects.requireNonNull(executor, "Session 资源装载执行器不能为空");
    }

    /**
     * 并发装载并合并每个 Principal 的静态资源。
     *
     * @param principals 当前 Session 中互不重复的 Principal
     * @return 按输入 Principal 分组的不可修改资源映射；没有资源时仍保留对应 Principal
     * @throws IllegalStateException Loader 执行失败、返回输入外的 Principal 或返回重复资源时抛出
     */
    public Map<Long, List<Resource>> load(Collection<? extends Principal> principals) {
        List<Principal> checkedPrincipals = List.copyOf(Objects.requireNonNull(principals, "Principal 列表不能为空"));
        LinkedHashSet<Long> principalIds = new LinkedHashSet<>();
        for (Principal principal : checkedPrincipals) {
            Principal checked = Objects.requireNonNull(principal, "Principal 不能为空");
            if (!principalIds.add(checked.principalId())) {
                throw new IllegalStateException("Session 包含重复 Principal: " + checked.principalId());
            }
        }
        List<CompletableFuture<Map<Long, ? extends Collection<? extends Resource>>>> tasks = new ArrayList<>();
        for (ResourceLoader loader : loaders) {
            tasks.add(CompletableFuture.supplyAsync(() -> loader.load(checkedPrincipals), executor));
        }
        try {
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException exception) {
            throw new IllegalStateException("Session 静态资源装载失败", exception.getCause());
        }
        LinkedHashMap<Long, List<Resource>> merged = new LinkedHashMap<>();
        LinkedHashMap<Long, Set<String>> resourceKeys = new LinkedHashMap<>();
        for (Long principalId : principalIds) {
            merged.put(principalId, new ArrayList<>());
            resourceKeys.put(principalId, new LinkedHashSet<>());
        }
        for (CompletableFuture<Map<Long, ? extends Collection<? extends Resource>>> task : tasks) {
            Map<Long, ? extends Collection<? extends Resource>> loaded = Objects
                    .requireNonNull(task.join(), "ResourceLoader 不能返回 null");
            if (!principalIds.containsAll(loaded.keySet())) {
                throw new IllegalStateException("ResourceLoader 返回了输入外的 Principal");
            }
            for (Map.Entry<Long, ? extends Collection<? extends Resource>> entry : loaded.entrySet()) {
                List<Resource> values = merged.get(entry.getKey());
                for (Resource resource : Objects.requireNonNull(entry.getValue(), "Principal 静态资源不能为空")) {
                    Resource checked = Objects.requireNonNull(resource, "Session 静态资源不能为空");
                    String key = checked.type() + "\u0000" + checked.id();
                    if (!resourceKeys.get(entry.getKey()).add(key)) {
                        throw new IllegalStateException("Session 静态资源重复: " + checked.type() + "/" + checked.id());
                    }
                    values.add(checked);
                }
            }
        }
        return Map.copyOf(merged);
    }
}
