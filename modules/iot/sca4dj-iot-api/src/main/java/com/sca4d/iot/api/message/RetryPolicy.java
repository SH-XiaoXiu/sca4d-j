package com.sca4d.iot.api.message;

import com.sca4d.core.api.code.TreeCode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 根据设备消息业务失败决定是否以及何时再次发送。 */
public interface RetryPolicy {
    /** @return App 内唯一且跨部署稳定的策略 code */
    @NotNull
    TreeCode code();

    /** @param message 当前消息 @param failure 传输或业务失败 code @param now 当前时刻 @return 无副作用 retry 判断 */
    @CheckReturnValue
    @NotNull
    RetryDecision evaluate(
            @NotNull DeviceDownlink message,
            @NotNull IotFailureCode failure,
            @NotNull Instant now);
}
