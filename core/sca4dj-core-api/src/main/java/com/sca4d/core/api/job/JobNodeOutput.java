package com.sca4d.core.api.job;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 声明 Job 历史查询要读取的 Pipeline 节点及其输出类型，不保存任一次运行的实际输出。
 *
 * <p>该声明同时固定 Job key、定义版本和节点名称，历史查询不会用当前类型读取其他 Job
 * 或其他定义版本保存的 JSON。
 *
 * @param <T> 节点输出类型
 */
public class JobNodeOutput<T> {

    private final @NotNull Class<T> outputType;

    private final @NotNull JobKey jobKey;

    private final @NotNull String nodeKey;

    private final int definitionVersion;

    /**
     * 只允许 {@link PipelineJob#nodeOutput(String, Class)} 创建节点输出声明。
     *
     * @param jobKey 声明节点的 Job key
     * @param definitionVersion 声明节点时的 Job 定义版本
     * @param nodeKey 节点在 Job 定义中的名称
     * @param outputType 历史 JSON 应恢复成的类型
     * @throws IllegalArgumentException definitionVersion 不是正数时抛出
     */
    JobNodeOutput(
            @NotNull JobKey jobKey,
            int definitionVersion,
            @NotNull String nodeKey,
            @NotNull Class<T> outputType) {
        this.jobKey = Objects.requireNonNull(jobKey, "Job key 不能为空");
        if (definitionVersion < 1) {
            throw new IllegalArgumentException("Job 定义版本必须为正数");
        }
        this.definitionVersion = definitionVersion;
        this.nodeKey = Objects.requireNonNull(nodeKey, "Job 节点名称不能为空");
        this.outputType = Objects.requireNonNull(outputType, "Job 节点输出类型不能为空");
    }

    /** @return 声明节点的 Job key */
    @Contract(pure = true)
    public @NotNull JobKey jobKey() {
        return jobKey;
    }

    /** @return 声明节点时的 Job 定义版本 */
    @Contract(pure = true)
    public int definitionVersion() {
        return definitionVersion;
    }

    /** @return 节点在 Job 定义中的稳定名称 */
    @Contract(pure = true)
    public @NotNull String nodeKey() {
        return nodeKey;
    }

    /** @return 历史查询恢复节点 JSON 时使用的具体类型 */
    @Contract(pure = true)
    public @NotNull Class<T> outputType() {
        return outputType;
    }
}
