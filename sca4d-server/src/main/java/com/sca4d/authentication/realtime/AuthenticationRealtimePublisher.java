package com.sca4d.authentication.realtime;

import com.sca4d.common.realtime.RealtimePublishRequest;
import com.sca4d.common.realtime.RealtimePublisher;
import com.sca4d.common.realtime.RealtimeTarget;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 向受影响 Subject 的在线连接发布认证 Session 状态变化。 */
@Component
@RequiredArgsConstructor
public class AuthenticationRealtimePublisher {

    private static final String EVENT_SESSION_CHANGED = "authentication.session.changed";

    private final @NotNull ObjectProvider<RealtimePublisher> realtimePublisher;

    /**
     * 通知指定 Subject 重新读取当前 Principal 的 Session 快照。
     *
     * @param subjectId 已完成 Redis Session 替换或结束的 Subject 主键
     */
    public void sessionChanged(@NotNull Long subjectId) {
        RealtimePublisher publisher = realtimePublisher.getIfAvailable();
        if (publisher != null) {
            publisher.publishNow(RealtimePublishRequest.sse(
                    RealtimeTarget.subject(subjectId), EVENT_SESSION_CHANGED, null));
        }
    }
}
