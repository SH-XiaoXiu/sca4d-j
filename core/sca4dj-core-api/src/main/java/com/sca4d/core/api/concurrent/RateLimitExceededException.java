package com.sca4d.core.api.concurrent;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.utils.DurationUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 表示调用没有取得全部限流额度，并携带调用方再次尝试前的等待秒数。 */
public class RateLimitExceededException extends BusinessException {

    private final long retryAfterSeconds;

    /**
     * 创建限流拒绝异常。
     *
     * @param retryAfter 再次尝试前至少需要等待的正数时间
     * @throws ArithmeticException 等待时间超过秒数范围时抛出
     */
    public RateLimitExceededException(@NotNull Duration retryAfter) {
        super(CoreErrorCode.RATE_LIMITED);
        Duration checked = DurationUtils.requirePositive(retryAfter, "限流重试等待时间");
        long seconds = checked.getSeconds();
        this.retryAfterSeconds = Math.max(
                1,
                checked.getNano() == 0 ? seconds : Math.addExact(seconds, 1));
    }

    /**
     * 可直接写入 HTTP `Retry-After` 的等待秒数。
     *
     * @return 向上取整后的正整数秒数
     */
    @Contract(pure = true)
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
