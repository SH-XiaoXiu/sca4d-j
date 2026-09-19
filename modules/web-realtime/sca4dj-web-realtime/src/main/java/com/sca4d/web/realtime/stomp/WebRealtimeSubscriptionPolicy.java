package com.sca4d.web.realtime.stomp;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.webrealtime.WebRealtimeTarget;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/** 由具体应用判断一个 STOMP destination 是否可以登记为 Resource 或 Topic 订阅。 */
public interface WebRealtimeSubscriptionPolicy {

    /** @param destination STOMP SUBSCRIBE destination @return 当前策略是否负责该 destination */
    boolean supports(@NotNull String destination);

    /**
     * 检查当前帧的 Session、Principal 与权限，并返回要登记的共享目标。
     *
     * @param call 当前 SUBSCRIBE 帧独有的 Call
     * @param destination App 定义的 STOMP destination
     * @return 已授权的 Resource 或 Topic 目标
     * @throws RuntimeException 身份、权限或目标无效时抛出，Spring 返回 STOMP ERROR 且不登记订阅
     */
    @CheckReturnValue
    @NotNull
    WebRealtimeTarget authorize(
            @NotNull Call call,
            @NotNull String destination);
}
