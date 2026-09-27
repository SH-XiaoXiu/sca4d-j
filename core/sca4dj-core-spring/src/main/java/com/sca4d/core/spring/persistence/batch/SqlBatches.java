package com.sca4d.core.spring.persistence.batch;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** 按 SQL 条目和绑定参数限制分批执行 Repository 已经确定的数据库操作。 */
public class SqlBatches {

    private final int maxItems;

    private final int maxBindParameters;

    /** @param properties SQL 批次条目数和绑定参数限制 */
    public SqlBatches(@NotNull SqlBatchProperties properties) {
        SqlBatchProperties checked = Objects.requireNonNull(properties, "SQL 批次配置不能为空");
        this.maxItems = checked.getMaxItems();
        this.maxBindParameters = checked.getMaxBindParameters();
    }

    /**
     * 分批执行查询并按批次顺序合并结果。
     *
     * <p>该方法不去重输入、不校验查询结果与输入的对应关系，也不创建事务或重试。Repository 仍需解释缺失、重复和顺序语义。
     *
     * @param items 保持调用方顺序的非空元素集合
     * @param parametersPerItem 每个集合元素生成的绑定参数数量
     * @param fixedParameters 每条 SQL 不随集合大小变化的绑定参数数量
     * @param query 接收一个不可修改批次并返回当前批次结果的查询
     * @param <T> 批量输入类型
     * @param <R> 查询结果类型
     * @return 按批次返回顺序合并的不可修改结果；空输入返回空列表且不调用 query
     */
    @CheckReturnValue
    public <T, R> @NotNull @Unmodifiable List<R> query(
            @NotNull Collection<? extends T> items,
            int parametersPerItem,
            int fixedParameters,
            @NotNull Function<? super List<T>, ? extends Collection<? extends R>> query) {
        List<T> checked = items(items);
        Function<? super List<T>, ? extends Collection<? extends R>> operation = Objects
                .requireNonNull(query, "SQL 批量查询不能为空");
        if (checked.isEmpty()) {
            return List.of();
        }
        int size = batchSize(parametersPerItem, fixedParameters);
        List<R> result = new ArrayList<>();
        for (int offset = 0; offset < checked.size(); offset += size) {
            List<T> batch = List.copyOf(checked.subList(offset, Math.min(offset + size, checked.size())));
            Collection<? extends R> loaded = Objects.requireNonNull(operation.apply(batch), "SQL 批量查询结果不能为空");
            for (R value : loaded) {
                result.add(Objects.requireNonNull(value, "SQL 批量查询结果不能包含空元素"));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 分批执行没有合并结果的数据库操作。
     *
     * <p>该方法不创建事务或解释影响行数；回调失败时立即停止并原样抛出异常。
     *
     * @param items 保持调用方顺序的非空元素集合
     * @param parametersPerItem 每个集合元素生成的绑定参数数量
     * @param fixedParameters 每条 SQL 不随集合大小变化的绑定参数数量
     * @param operation 接收一个不可修改批次的数据库操作
     * @param <T> 批量输入类型
     */
    public <T> void forEach(
            @NotNull Collection<? extends T> items,
            int parametersPerItem,
            int fixedParameters,
            @NotNull Consumer<? super List<T>> operation) {
        List<T> checked = items(items);
        Consumer<? super List<T>> action = Objects.requireNonNull(operation, "SQL 批量操作不能为空");
        if (checked.isEmpty()) {
            return;
        }
        int size = batchSize(parametersPerItem, fixedParameters);
        for (int offset = 0; offset < checked.size(); offset += size) {
            action.accept(List.copyOf(checked.subList(offset, Math.min(offset + size, checked.size()))));
        }
    }

    /** 计算同时满足条目和参数上限的正数批次大小。 */
    private int batchSize(int parametersPerItem, int fixedParameters) {
        if (parametersPerItem < 1 || fixedParameters < 0 || fixedParameters >= maxBindParameters) {
            throw new IllegalArgumentException("SQL 批次绑定参数数量不合法");
        }
        int byParameters = (maxBindParameters - fixedParameters) / parametersPerItem;
        if (byParameters < 1) {
            throw new IllegalArgumentException("单个 SQL 批次条目超过绑定参数上限");
        }
        return Math.min(maxItems, byParameters);
    }

    /** 复制并校验批量输入，不改变重复元素和出现顺序。 */
    private static <T> @NotNull List<T> items(@NotNull Collection<? extends T> items) {
        Collection<? extends T> source = Objects.requireNonNull(items, "SQL 批量输入不能为空");
        List<T> checked = new ArrayList<>(source.size());
        for (T item : source) {
            checked.add(Objects.requireNonNull(item, "SQL 批量输入不能包含空元素"));
        }
        return checked;
    }
}
