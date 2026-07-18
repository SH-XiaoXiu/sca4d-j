package com.sca4d.common.realtime.sse;

import com.sca4d.common.realtime.RealtimeEnvelope;
import com.sca4d.common.realtime.RealtimeTarget;
import org.jetbrains.annotations.NotNull;

/**
 * SSE 跨实例消息发布器。
 *
 * <p>默认实现通过共用 {@code MessagePublisher} 发布跨实例事件；该接口隔离实时通信模块与具体消息中间件。
 */
public interface SseMessagePublisher {

    /**
     * 发布 SSE 实时消息。
     *
     * @param target 投递目标
     * @param envelope 实时消息载荷
     */
    void publish(@NotNull RealtimeTarget target, @NotNull RealtimeEnvelope<?> envelope);
}
