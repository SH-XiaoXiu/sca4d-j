package com.sca4d.common.realtime;

import com.sca4d.common.auth.PrincipalType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 实时消息发布入口。
 */
public interface RealtimePublisher {

    /**
     * 发布实时消息。
     *
     * <p>当前线程存在 Spring 事务同步时，只在事务成功提交后发送；没有事务同步时立即发送。
     *
     * @param request 发布请求
     */
    void publish(@NotNull RealtimePublishRequest request);

    /**
     * 立即发布已经完成数据库提交或不依赖数据库事务的实时消息。
     *
     * <p>该方法不会等待当前线程上的事务同步回调。调用方必须确认消息对应的数据已经提交，或消息不读取数据库写入结果。
     *
     * @param request 发布请求
     */
    void publishNow(@NotNull RealtimePublishRequest request);

    /**
     * 向指定目标发布实时消息。
     *
     * @param target 投递目标
     * @param event 业务模块定义的固定事件编码
     * @param data 业务载荷
     */
    default void send(@NotNull RealtimeTarget target, @NotNull String event, @Nullable Object data) {
        publish(RealtimePublishRequest.all(target, event, data));
    }

    /**
     * 向登录身份发布实时消息。
     *
     * @param subjectId 登录身份 ID
     * @param event 业务模块定义的固定事件编码
     * @param data 业务载荷
     */
    default void sendToSubject(@NotNull Long subjectId, @NotNull String event, @Nullable Object data) {
        send(RealtimeTarget.subject(subjectId), event, data);
    }

    /**
     * 向执行主体发布实时消息。
     *
     * @param principalType 执行主体类型
     * @param principalId 执行主体 ID
     * @param event 业务模块定义的固定事件编码
     * @param data 业务载荷
     */
    default void sendToPrincipal(
            @NotNull PrincipalType principalType, @NotNull Long principalId, @NotNull String event, @Nullable Object data) {
        send(RealtimeTarget.principal(principalType, principalId), event, data);
    }

    /**
     * 向租户发布实时消息。
     *
     * @param tenantId 租户 ID
     * @param event 业务模块定义的固定事件编码
     * @param data 业务载荷
     */
    default void sendToTenant(@NotNull Long tenantId, @NotNull String event, @Nullable Object data) {
        send(RealtimeTarget.tenant(tenantId), event, data);
    }

    /**
     * 向公共主题发布实时消息。
     *
     * @param topic 业务模块或外部集成定义的主题编码
     * @param event 业务模块定义的固定事件编码
     * @param data 业务载荷
     */
    default void sendToTopic(@NotNull String topic, @NotNull String event, @Nullable Object data) {
        send(RealtimeTarget.topic(topic), event, data);
    }
}
