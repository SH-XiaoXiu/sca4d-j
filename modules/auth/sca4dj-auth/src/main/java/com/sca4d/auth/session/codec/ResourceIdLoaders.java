package com.sca4d.auth.session.codec;

import com.sca4d.auth.exception.AuthException;
import com.sca4d.core.api.authentication.principalsession.ResourceIdLoader;
import com.sca4d.core.api.authentication.principalsession.ResourceIdSet;
import com.sca4d.core.api.data.DataGraph;
import com.sca4d.core.api.data.DataGraphExecutor;
import com.sca4d.core.api.data.DataKey;
import com.sca4d.core.api.data.DataOperation;
import com.sca4d.core.api.data.DataValues;
import com.sca4d.core.api.data.exception.DataOperationExecutionException;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.name.code.TreeCode;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/** 使用 DataGraph 并发装载并合并 Principal 的数字 Resource 标识。 */
public class ResourceIdLoaders {

    @SuppressWarnings("rawtypes")
    private static final DataKey<List> PRINCIPALS = DataKey.of("sessionPrincipals", List.class);

    @SuppressWarnings("rawtypes")
    private static final DataKey<Map> MERGED = DataKey.of("mergedResourceIds", Map.class);

    private final Executor executor;

    private final List<ResourceIdLoader> loaders;

    private final DataGraph graph;

    /** 创建使用 DataGraph 并发调用所有数字 Resource Loader 的装载服务。 */
    public ResourceIdLoaders(
            @NotNull List<@NotNull ResourceIdLoader> loaders,
            @NotNull Executor executor) {
        this.loaders = List.copyOf(Objects.requireNonNull(loaders, "Resource ID Loader 列表不能为空"));
        this.executor = Objects.requireNonNull(executor, "Resource ID Loader 执行器不能为空");
        this.graph = graph();
    }

    /** 同步装载并合并数字 Resource 标识。 */
    public @NotNull @Unmodifiable Map<Long, List<ResourceIdSet>> load(
            @NotNull Collection<? extends @NotNull Principal> principals) {
        try {
            return loadAsync(principals).toCompletableFuture().join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof DataOperationExecutionException operation
                    && operation.operationName().startsWith("resource-id-loader-")) {
                throw new AuthException("Session 数字 Resource 标识装载失败", operation.getCause());
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

    /** 异步装载并合并数字 Resource 标识。 */
    @SuppressWarnings("unchecked")
    @CheckReturnValue
    public @NotNull CompletionStage<@Unmodifiable Map<Long, List<ResourceIdSet>>> loadAsync(
            @NotNull Collection<? extends @NotNull Principal> principals) {
        List<Principal> checkedPrincipals = validatePrincipals(principals);
        DataValues inputs = DataValues.builder().put(PRINCIPALS, checkedPrincipals).build();
        return DataGraphExecutor.executeAsync(graph, inputs, executor)
                .thenApply(values -> (Map<Long, List<ResourceIdSet>>) values.get(MERGED));
    }

    private DataGraph graph() {
        List<DataKey<?>> outputs = new ArrayList<>();
        DataGraph.Builder builder = DataGraph.builder().input(PRINCIPALS);
        for (int index = 0; index < loaders.size(); index++) {
            ResourceIdLoader loader = Objects.requireNonNull(loaders.get(index), "ResourceIdLoader 不能为空");
            @SuppressWarnings("rawtypes")
            DataKey<Map> output = DataKey.of("resourceIdLoader" + index, Map.class);
            outputs.add(output);
            builder.operation(
                    DataOperation.async(
                            "resource-id-loader-" + index,
                            List.of(PRINCIPALS),
                            output,
                            (values, ignored) -> CompletableFuture.supplyAsync(
                                    () -> Objects.requireNonNull(
                                            loader.load(values.get(PRINCIPALS)),
                                            "ResourceIdLoader 不能返回 null"),
                                    ignored)));
        }
        List<DataKey<?>> mergeInputs = new ArrayList<>();
        mergeInputs.add(PRINCIPALS);
        mergeInputs.addAll(outputs);
        builder.operation(DataOperation.of("merge-resource-ids", mergeInputs, MERGED, this::merge));
        return builder.build();
    }

    private List<Principal> validatePrincipals(Collection<? extends Principal> principals) {
        List<Principal> checkedPrincipals = List.copyOf(Objects.requireNonNull(principals, "Principal 列表不能为空"));
        Set<Long> ids = new LinkedHashSet<>();
        for (Principal principal : checkedPrincipals) {
            Principal checked = Objects.requireNonNull(principal, "Principal 不能为空");
            if (!ids.add(checked.principalId())) {
                throw new IllegalStateException("Session 包含重复 Principal: " + checked.principalId());
            }
        }
        return checkedPrincipals;
    }

    /** 按 Loader 顺序合并数字 Resource 标识并按资源类型分组，拒绝重复标识或输入之外的 Principal。 */
    @SuppressWarnings("unchecked")
    private Map<Long, List<ResourceIdSet>> merge(DataValues values) {
        List<Principal> principals = values.get(PRINCIPALS);
        LinkedHashSet<Long> principalIds = principals.stream()
                .map(Principal::principalId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashMap<Long, LinkedHashMap<TreeCode, List<Long>>> merged = new LinkedHashMap<>();
        LinkedHashMap<Long, LinkedHashMap<TreeCode, Set<Long>>> seen = new LinkedHashMap<>();
        for (Long principalId : principalIds) {
            merged.put(principalId, new LinkedHashMap<>());
            seen.put(principalId, new LinkedHashMap<>());
        }
        for (int index = 0; index < loaders.size(); index++) {
            @SuppressWarnings("rawtypes")
            DataKey<Map> output = DataKey.of("resourceIdLoader" + index, Map.class);
            Map<Long, ? extends Collection<ResourceIdSet>> loaded = (Map<Long, ? extends Collection<ResourceIdSet>>) values
                    .get(output);
            if (!principalIds.containsAll(loaded.keySet())) {
                throw new IllegalStateException("ResourceIdLoader 返回了输入外的 Principal");
            }
            for (Map.Entry<Long, ? extends Collection<ResourceIdSet>> entry : loaded.entrySet()) {
                for (ResourceIdSet resourceIdSet : Objects.requireNonNull(
                        entry.getValue(),
                        "Principal 数字 Resource 标识不能为空")) {
                    ResourceIdSet checked = Objects.requireNonNull(
                            resourceIdSet,
                            "ResourceIdLoader 不能返回 null ResourceIdSet");
                    List<Long> target = merged.get(entry.getKey())
                            .computeIfAbsent(checked.resourceType(), ignored -> new ArrayList<>());
                    Set<Long> seenValues = seen.get(entry.getKey())
                            .computeIfAbsent(checked.resourceType(), ignored -> new LinkedHashSet<>());
                    for (long id : checked.ids().toArray()) {
                        if (!seenValues.add(id)) {
                            throw new IllegalStateException(
                                    "Session 数字 Resource 标识重复: "
                                            + checked.resourceType() + "/" + id);
                        }
                        target.add(id);
                    }
                }
            }
        }
        LinkedHashMap<Long, List<ResourceIdSet>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, LinkedHashMap<TreeCode, List<Long>>> entry : merged.entrySet()) {
            List<ResourceIdSet> valuesForPrincipal = entry.getValue()
                    .entrySet()
                    .stream()
                    .map(group -> new ResourceIdSet(group.getKey(), group.getValue()))
                    .toList();
            if (!valuesForPrincipal.isEmpty()) {
                result.put(entry.getKey(), valuesForPrincipal);
            }
        }
        return Map.copyOf(result);
    }
}
