package com.sca4d.core.api.flow;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 保存跨多次调用的 {@link FlowDefinition} 当前节点、数据和消费状态。
 *
 * <p>实例保存当前节点、数据、过期时间和乐观版本。
 */
public final class FlowInstance {

    private final FlowId id;

    private final String definitionName;

    private final int definitionVersion;

    private final String currentNodeId;

    private final Map<String, Object> data;

    private final Instant startedAt;

    private final Instant expiresAt;

    private final Version version;

    private final boolean consumed;

    /**
     * 创建实例快照。
     *
     * @param id 实例标识
     * @param definitionName 定义名称
     * @param definitionVersion 定义版本
     * @param currentNodeId 当前节点
     * @param data 不可变数据
     * @param startedAt 开始时间
     * @param expiresAt 过期时间
     * @param version 乐观版本
     * @param consumed 终态结果是否已经消费
     */
    public FlowInstance(
            FlowId id,
            String definitionName,
            int definitionVersion,
            String currentNodeId,
            Map<String, ?> data,
            Instant startedAt,
            Instant expiresAt,
            Version version,
            boolean consumed) {
        this.id = Objects.requireNonNull(id, "流程实例标识不能为空");
        this.definitionName = TextUtils.requireText(definitionName, "流程定义名称");
        if (definitionVersion < 1) {
            throw new IllegalArgumentException("流程定义版本必须为正数");
        }
        this.definitionVersion = definitionVersion;
        this.currentNodeId = TextUtils.requireText(currentNodeId, "当前流程节点");
        this.data = Map.copyOf(Objects.requireNonNull(data, "流程数据不能为空"));
        this.startedAt = Objects.requireNonNull(startedAt, "流程开始时间不能为空");
        this.expiresAt = Objects.requireNonNull(expiresAt, "流程过期时间不能为空");
        if (!expiresAt.isAfter(startedAt)) {
            throw new IllegalArgumentException("流程过期时间必须晚于开始时间");
        }
        this.version = Objects.requireNonNull(version, "流程版本不能为空");
        this.consumed = consumed;
    }

    /**
     * 返回实例标识。
     *
     * @return 实例标识
     */
    public FlowId id() {
        return id;
    }

    /**
     * 返回定义名称。
     *
     * @return 定义名称
     */
    public String definitionName() {
        return definitionName;
    }

    /**
     * 返回定义版本。
     *
     * @return 定义版本
     */
    public int definitionVersion() {
        return definitionVersion;
    }

    /**
     * 返回当前节点标识。
     *
     * @return 当前节点标识
     */
    public String currentNodeId() {
        return currentNodeId;
    }

    /**
     * 返回不可变数据。
     *
     * @return 不可变数据
     */
    public @Unmodifiable Map<String, Object> data() {
        return data;
    }

    /**
     * 返回创建时间。
     *
     * @return 创建时间
     */
    public Instant startedAt() {
        return startedAt;
    }

    /**
     * 返回过期时间。
     *
     * @return 过期时间
     */
    public Instant expiresAt() {
        return expiresAt;
    }

    /**
     * 返回当前乐观版本。
     *
     * @return 当前乐观版本
     */
    public Version version() {
        return version;
    }

    /**
     * 返回终态结果已经消费时返回 {@code true}。
     *
     * @return 终态结果已经消费时返回 {@code true}
     */
    public boolean isConsumed() {
        return consumed;
    }

    /**
     * 判断实例是否已经过期。
     *
     * @param instant 判断时间
     * @return 判断时间不早于过期时间时返回 {@code true}
     */
    public boolean isExpiredAt(Instant instant) {
        return !Objects.requireNonNull(instant, "判断时间不能为空").isBefore(expiresAt);
    }

    /**
     * 生成进入下一节点的实例快照。
     *
     * @param nodeId 目标节点
     * @return 版本递增的新快照
     */
    @Contract(value = "_ -> new", pure = true)
    public FlowInstance moveTo(String nodeId) {
        return new FlowInstance(
                id,
                definitionName,
                definitionVersion,
                nodeId,
                data,
                startedAt,
                expiresAt,
                version.next(),
                false);
    }

    /**
     * 返回标记为已消费且版本递增的新快照。
     *
     * @return 标记为已消费且版本递增的新快照
     */
    @Contract(value = "-> new", pure = true)
    public FlowInstance consume() {
        if (consumed) {
            throw new IllegalStateException("流程结果只能消费一次");
        }
        return new FlowInstance(
                id,
                definitionName,
                definitionVersion,
                currentNodeId,
                data,
                startedAt,
                expiresAt,
                version.next(),
                true);
    }

}
