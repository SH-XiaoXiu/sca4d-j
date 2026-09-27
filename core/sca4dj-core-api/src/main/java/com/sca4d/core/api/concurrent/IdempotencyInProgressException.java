package com.sca4d.core.api.concurrent;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.utils.DurationUtils;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 表示同一幂等请求仍由其他执行者处理。 */
public class IdempotencyInProgressException extends BusinessException {

    private final long retryAfterSeconds;

    /** @param retryAfter 再次提交前至少需要等待的正数时间 */
    public IdempotencyInProgressException(@NotNull Duration retryAfter) {
        super(CoreErrorCode.IDEMPOTENCY_IN_PROGRESS);
        Duration checked = DurationUtils.requirePositive(retryAfter, "幂等重试等待时间");
        this.retryAfterSeconds = Math.max(1, Math.ceilDiv(checked.toNanos(), Duration.ofSeconds(1).toNanos()));
    }

    /** @return 可直接写入 HTTP {@code Retry-After} 的正整数秒数 */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
