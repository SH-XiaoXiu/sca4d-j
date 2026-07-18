package com.sca4d.common.realtime;

import cn.hutool.core.util.IdUtil;
import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 实时消息共用载荷。
 *
 * <p>{@code event} 由业务模块以常量定义并与前端或其他消费者共享，公共实时模块只负责传输，不维护事件枚举。
 *
 * @param <T> 业务数据类型
 */
public class RealtimeEnvelope<T> {

    private String id;
    private String event;
    private String traceId;
    private OffsetDateTime timestamp;
    private T data;
    private Map<String, Object> metadata = Map.of();

    /** 创建供 Jackson 反序列化后通过 setter 恢复字段的空信封。 */
    public RealtimeEnvelope() {
    }

    /**
     * 创建实时消息载荷。
     *
     * @param event 业务模块定义的固定事件编码
     * @param data 业务数据
     * @param metadata 元数据
     */
    public RealtimeEnvelope(@NotNull String event, @Nullable T data, @NotNull Map<String, Object> metadata) {
        this.id = IdUtil.fastSimpleUUID();
        this.event = event;
        this.traceId = traceId();
        this.timestamp = TimeSupport.now();
        this.data = data;
        this.metadata = Map.copyOf(metadata);
    }

    /**
     * 创建实时消息载荷。
     *
     * @param event 业务模块定义的固定事件编码
     * @param data 业务数据
     * @return 实时消息载荷
     */
    public static <T> @NotNull RealtimeEnvelope<T> of(@NotNull String event, @Nullable T data) {
        return new RealtimeEnvelope<>(event, data, Map.of());
    }

    /**
     * 返回本条实时消息的随机唯一标识。
     *
     * @return 单次发布使用的消息标识
     */
    public @NotNull String getId() {
        return id;
    }

    /** @param id 反序列化恢复的消息标识 */
    public void setId(@NotNull String id) {
        this.id = id;
    }

    /**
     * 返回业务模块声明的开放事件编码。
     *
     * @return 消费者用于分派载荷的固定事件编码
     */
    public @NotNull String getEvent() {
        return event;
    }

    /** @param event 业务模块声明的固定事件编码 */
    public void setEvent(@NotNull String event) {
        this.event = event;
    }

    /**
     * 返回创建信封时继承的请求追踪标识。
     *
     * @return 请求内创建时的 traceId；请求外发布时为 {@code null}
     */
    public @Nullable String getTraceId() {
        return traceId;
    }

    /** @param traceId 反序列化恢复的可选请求追踪标识 */
    public void setTraceId(@Nullable String traceId) {
        this.traceId = traceId;
    }

    /**
     * 返回信封创建时间。
     *
     * @return 共用项目时钟生成的带时区时间
     */
    public @NotNull OffsetDateTime getTimestamp() {
        return timestamp;
    }

    /** @param timestamp 反序列化恢复的信封创建时间 */
    public void setTimestamp(@NotNull OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 返回当前事件的业务载荷。
     *
     * @return 业务数据；无数据事件可以为 {@code null}
     */
    public @Nullable T getData() {
        return data;
    }

    /** @param data 当前事件携带的可选业务数据 */
    public void setData(@Nullable T data) {
        this.data = data;
    }

    /**
     * 返回信封级扩展元数据的不可变快照。
     *
     * @return 创建或反序列化时复制的元数据
     */
    public @NotNull Map<String, Object> getMetadata() {
        return metadata;
    }

    /** @param metadata 需要复制为不可变快照的信封元数据 */
    public void setMetadata(@NotNull Map<String, Object> metadata) {
        this.metadata = Map.copyOf(metadata);
    }

    /**
     * 从当前请求读取可传播的 traceId，不为请求外消息伪造追踪关系。
     *
     * @return 当前请求 traceId；没有请求态时返回 {@code null}
     */
    private @Nullable String traceId() {
        CurrentRequest context = CurrentRequestHolder.currentOrNull();
        return context == null ? null : context.getTraceId();
    }
}
