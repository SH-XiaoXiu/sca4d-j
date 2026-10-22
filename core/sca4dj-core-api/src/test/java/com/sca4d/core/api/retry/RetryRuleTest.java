package com.sca4d.core.api.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/** 验证跨同步和持久化执行方式共用的次数、失败分类和退避契约。 */
class RetryRuleTest {
    @Test
    @DisplayName("总尝试次数包含首次执行，失败分类与次数耗尽分别停止")
    void countsInitialAttemptAndClassifiesFailure() {
        RetryPolicy<String> policy = RetryPolicy.forFailures(
                RetryRule.atMost(3, Backoff.fixed(Duration.ofSeconds(5))),
                "temporary"::equals);
        assertEquals(
                Duration.ofSeconds(5),
                policy.evaluate(RetryContext.failed(1, "temporary")).retryDelay().orElseThrow());
        assertTrue(policy.evaluate(RetryContext.failed(2, "temporary")).retrying());
        assertEquals(
                RetryStopReason.ATTEMPTS_EXHAUSTED,
                policy.evaluate(RetryContext.failed(3, "temporary")).stopReason().orElseThrow());
        assertEquals(
                RetryStopReason.NOT_RETRYABLE,
                policy.evaluate(RetryContext.failed(1, "invalid")).stopReason().orElseThrow());
        assertEquals(
                RetryStopReason.DISABLED,
                RetryPolicy.always(RetryRule.once())
                        .evaluate(RetryContext.failed(1, "failure"))
                        .stopReason()
                        .orElseThrow());
    }

    @Test
    @DisplayName("指数退避封顶且大次数不溢出，序列用完后重复末项")
    void backoffCapsAndRepeatsLastInterval() {
        Backoff exponential = Backoff.exponential(Duration.ofSeconds(1), 2, Duration.ofSeconds(5));
        assertEquals(Duration.ofSeconds(1), exponential.delayAfter(1));
        assertEquals(Duration.ofSeconds(2), exponential.delayAfter(2));
        assertEquals(Duration.ofSeconds(4), exponential.delayAfter(3));
        assertEquals(Duration.ofSeconds(5), exponential.delayAfter(Long.MAX_VALUE));
        Backoff sequence = Backoff.sequence(Duration.ZERO, Duration.ofSeconds(7));
        assertEquals(Duration.ZERO, sequence.delayAfter(1));
        assertEquals(Duration.ofSeconds(7), sequence.delayAfter(Long.MAX_VALUE));
        assertTrue(
                RetryPolicy.always(RetryRule.unlimited(exponential))
                        .evaluate(RetryContext.failed((long) Integer.MAX_VALUE + 1, "failure"))
                        .retrying());
    }

    @Test
    @DisplayName("非法时长和无限零等待被拒绝，计数不能回绕")
    void rejectsInvalidRulesAndCounterOverflow() {
        assertThrows(IllegalArgumentException.class, () -> RetryRule.atMost(0, Backoff.fixed(Duration.ZERO)));
        assertThrows(IllegalArgumentException.class, () -> RetryRule.unlimited(Backoff.fixed(Duration.ZERO)));
        assertThrows(
                IllegalArgumentException.class,
                () -> RetryRule.unlimited(Backoff.sequence(Duration.ofMillis(1), Duration.ZERO)));
        assertThrows(IllegalArgumentException.class, () -> Backoff.fixed(Duration.ofNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> Backoff.fixed(Duration.ofSeconds(Long.MAX_VALUE)));
        assertThrows(IllegalArgumentException.class, () -> Backoff.exponential(Duration.ZERO, 2, Duration.ofMillis(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> Backoff.exponential(Duration.ofMillis(1), Double.NaN, Duration.ofMillis(2)));
        assertThrows(IllegalArgumentException.class, Backoff::sequence);
        assertThrows(IllegalArgumentException.class, () -> RetryContext.failed(0, "failure"));
        assertThrows(
                ArithmeticException.class,
                () -> RetryPolicy.always(RetryRule.unlimited(Backoff.fixed(Duration.ofMillis(1))))
                        .evaluate(RetryContext.failed(Long.MAX_VALUE, "failure")));
    }
}
