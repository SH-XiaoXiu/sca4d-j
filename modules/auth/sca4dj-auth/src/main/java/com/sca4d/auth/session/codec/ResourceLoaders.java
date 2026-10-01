package com.sca4d.auth.session.codec;

import com.sca4d.core.api.authentication.principalsession.ResourceLoader;
import com.sca4d.core.api.data.DataGraph;
import com.sca4d.core.api.data.DataGraphExecutor;
import com.sca4d.core.api.data.DataKey;
import com.sca4d.core.api.data.DataOperation;
import com.sca4d.core.api.data.DataOperationExecutionException;
import com.sca4d.core.api.data.DataValues;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/** 使用 DataGraph 并发装载并合并所有 ResourceLoader 的静态资源贡献。 */
public class ResourceLoaders {

    @SuppressWarnings("rawtypes")
    private static final DataKey<List> PRINCIPALS = DataKey.of("sessionPrincipals", List.class);

    @SuppressWarnings("rawtypes")
    private static final DataKey<Map> MERGED = DataKey.of("mergedResources", Map.class);

    private final Executor executor;

    private final List<ResourceLoader> loaders;

    private final DataGraph graph;

    /**
     * 创建使用 DataGraph 并发调用所有资源 Loader 的装载服务。
     *
     * @param loaders 为 Principal 提供静态资源的 Loader 列表
     * @param executor 并发执行 Loader 的执行器
     */
    public ResourceLoaders(List<ResourceLoader> loaders, Executor executor) {
        this.loaders = List.copyOf(Objects.requireNonNull(loaders, "Session 资源 Loader 不能为空"));
        this.executor = Objects.requireNonNull(executor, "Session 资源装载执行器不能为空");
        this.graph = graph();
    }

    /** 同步装载并合并静态资源；异步节点由本对象的 Executor 调度。 */
    public Map<Long, List<Resource>> load(Collection<? extends Principal> principals) {
        try {
            return loadAsync(principals).toCompletableFuture().join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof DataOperationExecutionException operation
                    && operation.operationName().startsWith("resource-loader-")) {
                if (operation.getCause() instanceof NullPointerException nullFailure) {
                    throw nullFailure;
                }
                throw new IllegalStateException("Session 静态资源装载失败", operation.getCause());
            }
            if (cause instanceof DataOperationExecutionException operation) {
                cause = operation.getCause();
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    /** 异步装载并合并静态资源，结果只在全部 Loader 和合并节点成功后完成。 */
    @SuppressWarnings("unchecked")
    public CompletionStage<Map<Long, List<Resource>>> loadAsync(
            Collection<? extends Principal> principals) {
        List<Principal> checkedPrincipals = validatePrincipals(principals);
        DataValues inputs = DataValues.builder().put(PRINCIPALS, checkedPrincipals).build();
        return DataGraphExecutor.executeAsync(graph, inputs, executor)
                .thenApply(values -> (Map<Long, List<Resource>>) values.get(MERGED));
    }

    private DataGraph graph() {
        List<DataKey<?>> loaderOutputs = new ArrayList<>();
        DataGraph.Builder builder = DataGraph.builder().input(PRINCIPALS);
        for (int index = 0; index < loaders.size(); index++) {
            ResourceLoader loader = Objects.requireNonNull(loaders.get(index), "ResourceLoader 不能为空");
            @SuppressWarnings("rawtypes")
            DataKey<Map> output = DataKey.of("resourceLoader" + index, Map.class);
            loaderOutputs.add(output);
            builder.operation(
                    DataOperation.async(
                            "resource-loader-" + index,
                            List.of(PRINCIPALS),
                            output,
                            (values, ignored) -> java.util.concurrent.CompletableFuture.supplyAsync(
                                    () -> Objects.requireNonNull(
                                            loader.load(values.get(PRINCIPALS)),
                                            "ResourceLoader 不能返回 null"),
                                    ignored)));
        }
        List<DataKey<?>> mergeInputs = new ArrayList<>();
        mergeInputs.add(PRINCIPALS);
        mergeInputs.addAll(loaderOutputs);
        builder.operation(DataOperation.of("merge-resources", mergeInputs, MERGED, this::merge));
        return builder.build();
    }

    private List<Principal> validatePrincipals(Collection<? extends Principal> principals) {
        List<Principal> checkedPrincipals = List.copyOf(Objects.requireNonNull(principals, "Principal 列表不能为空"));
        LinkedHashSet<Long> principalIds = new LinkedHashSet<>();
        for (Principal principal : checkedPrincipals) {
            Principal checked = Objects.requireNonNull(principal, "Principal 不能为空");
            if (!principalIds.add(checked.principalId())) {
                throw new IllegalStateException("Session 包含重复 Principal: " + checked.principalId());
            }
        }
        return checkedPrincipals;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, List<Resource>> merge(DataValues values) {
        List<Principal> principals = values.get(PRINCIPALS);
        LinkedHashSet<Long> principalIds = principals.stream()
                .map(Principal::principalId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashMap<Long, List<Resource>> merged = new LinkedHashMap<>();
        LinkedHashMap<Long, Set<String>> resourceKeys = new LinkedHashMap<>();
        for (Long principalId : principalIds) {
            merged.put(principalId, new ArrayList<>());
            resourceKeys.put(principalId, new LinkedHashSet<>());
        }
        for (int index = 0; index < loaders.size(); index++) {
            @SuppressWarnings("rawtypes")
            DataKey<Map> output = DataKey.of("resourceLoader" + index, Map.class);
            Map<Long, ? extends Collection<? extends Resource>> loaded = (Map<Long, ? extends Collection<? extends Resource>>) values
                    .get(output);
            if (!principalIds.containsAll(loaded.keySet())) {
                throw new IllegalStateException("ResourceLoader 返回了输入外的 Principal");
            }
            for (Map.Entry<Long, ? extends Collection<? extends Resource>> entry : loaded.entrySet()) {
                List<Resource> target = merged.get(entry.getKey());
                for (Resource resource : Objects.requireNonNull(entry.getValue(), "Principal 静态资源不能为空")) {
                    Resource checked = Objects.requireNonNull(resource, "Session 静态资源不能为空");
                    String key = checked.type() + "\u0000" + checked.id();
                    if (!resourceKeys.get(entry.getKey()).add(key)) {
                        throw new IllegalStateException("Session 静态资源重复: " + checked.type() + "/" + checked.id());
                    }
                    target.add(checked);
                }
            }
        }
        return Map.copyOf(merged);
    }
}
