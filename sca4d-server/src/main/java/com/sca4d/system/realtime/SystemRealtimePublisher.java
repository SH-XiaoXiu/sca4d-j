package com.sca4d.system.realtime;

import com.sca4d.common.realtime.RealtimePublishRequest;
import com.sca4d.common.realtime.RealtimePublisher;
import com.sca4d.common.realtime.RealtimeRoutes;
import com.sca4d.common.realtime.RealtimeTarget;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 发布不包含菜单内容的系统状态变化事件。 */
@Component
@RequiredArgsConstructor
public class SystemRealtimePublisher {

    private static final String EVENT_MENUS_CHANGED = "system.menus.changed";

    private final @NotNull ObjectProvider<RealtimePublisher> realtimePublisher;

    /**
     * 通知全部已认证 SSE 连接重新读取其权限过滤后的菜单。
     *
     * <p>事件只包含状态变化，不携带未经当前 Principal 权限过滤的菜单数据。底层发布器在菜单事务提交后发送。
     */
    public void menusChanged() {
        RealtimePublisher publisher = realtimePublisher.getIfAvailable();
        if (publisher != null) {
            publisher.publish(RealtimePublishRequest.sse(
                    RealtimeTarget.topic(RealtimeRoutes.CLIENT_STATE_TOPIC), EVENT_MENUS_CHANGED, null));
        }
    }
}
