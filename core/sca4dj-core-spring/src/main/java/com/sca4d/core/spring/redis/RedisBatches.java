package com.sca4d.core.spring.redis;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** 按 pipeline 命令数量限制分批执行已经确定的 Redis 操作。 */
public final class RedisBatches {

    private final int maxCommands;

    /** @param properties Redis pipeline 命令数量限制 */
    public RedisBatches(@NotNull RedisPipelineProperties properties) {
        this.maxCommands = Objects.requireNonNull(properties, "Redis pipeline 配置不能为空").getMaxCommands();
    }

    /**
     * 分批执行 Redis 查询并按批次顺序合并结果。
     *
     * @param items 保持调用方顺序的非空元素集合
     * @param commandsPerItem 每个集合元素在 pipeline 中生成的命令数量
     * @param query 接收一个不可修改批次并返回当前批次结果的查询
     * @param <T> 批量输入类型
     * @param <R> 查询结果类型
     * @return 按批次返回顺序合并的不可修改结果；空输入不调用 query
     */
    @CheckReturnValue
    public <T, R> @NotNull @Unmodifiable List<R> query(
            @NotNull Collection<? extends T> items,
            int commandsPerItem,
            @NotNull Function<? super List<T>, ? extends Collection<? extends R>> query) {
        List<T> checked = items(items);
        Function<? super List<T>, ? extends Collection<? extends R>> operation = Objects
                .requireNonNull(query, "Redis 批量查询不能为空");
        if (checked.isEmpty()) {
            return List.of();
        }
        int size = batchSize(commandsPerItem);
        List<R> result = new ArrayList<>();
        for (int offset = 0; offset < checked.size(); offset += size) {
            List<T> batch = List.copyOf(checked.subList(offset, Math.min(offset + size, checked.size())));
            Collection<? extends R> loaded = Objects.requireNonNull(operation.apply(batch), "Redis 批量查询结果不能为空");
            for (R value : loaded) {
                result.add(Objects.requireNonNull(value, "Redis 批量查询结果不能包含空元素"));
            }
        }
        return List.copyOf(result);
    }

    /** 分批执行没有合并结果的 Redis pipeline 操作，失败时停止并传播异常。 */
    public <T> void forEach(
            @NotNull Collection<? extends T> items,
            int commandsPerItem,
            @NotNull Consumer<? super List<T>> operation) {
        List<T> checked = items(items);
        Consumer<? super List<T>> action = Objects.requireNonNull(operation, "Redis 批量操作不能为空");
        if (checked.isEmpty()) {
            return;
        }
        int size = batchSize(commandsPerItem);
        for (int offset = 0; offset < checked.size(); offset += size) {
            action.accept(List.copyOf(checked.subList(offset, Math.min(offset + size, checked.size()))));
        }
    }

    /** 根据每项命令数计算正数批次大小。 */
    private int batchSize(int commandsPerItem) {
        if (commandsPerItem < 1 || commandsPerItem > maxCommands) {
            throw new IllegalArgumentException("Redis 每项 pipeline 命令数量不合法");
        }
        return maxCommands / commandsPerItem;
    }

    /** 复制并校验批量输入，不改变重复元素和出现顺序。 */
    private static <T> @NotNull List<T> items(@NotNull Collection<? extends T> items) {
        Collection<? extends T> source = Objects.requireNonNull(items, "Redis 批量输入不能为空");
        List<T> checked = new ArrayList<>(source.size());
        for (T item : source) {
            checked.add(Objects.requireNonNull(item, "Redis 批量输入不能包含空元素"));
        }
        return checked;
    }
}
