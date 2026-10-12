package com.sca4d.core.spring.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.retry.Backoff;
import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.core.spring.json.Sca4djMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

/** 验证进程重启后规则仍产生相同次数和等待，损坏快照不能变成默认规则。 */
class RetryRuleCodecTest {
    @Test
    void restoresAllRuleKinds() {
        RetryRuleCodec codec = new RetryRuleCodec(new Sca4djMapper());
        for (RetryRule original : List.of(
                RetryRule.once(),
                RetryRule.atMost(5, Backoff.sequence(Duration.ofMillis(1), Duration.ofMillis(7))),
                RetryRule.unlimited(Backoff.exponential(Duration.ofMillis(1), 2, Duration.ofMillis(20))))) {
            RetryRule restored = codec.decode(codec.encode(original));
            assertThat(restored.maximumAttempts()).isEqualTo(original.maximumAttempts());
            for (long attempt : new long[] {1, 2, 5, 30, Long.MAX_VALUE}) {
                assertThat(restored.backoff().delayAfter(attempt)).isEqualTo(original.backoff().delayAfter(attempt));
            }
        }
    }

    @Test
    void rejectsUnsupportedAndCorruptSnapshots() {
        RetryRuleCodec codec = new RetryRuleCodec(new Sca4djMapper());
        for (String value : List.of(
                "null",
                "{}",
                "{\"version\":2}",
                "{\"version\":1,\"maxAttempts\":null,\"backoff\":{\"type\":\"FIXED\",\"delayMillis\":0}}")) {
            assertThatThrownBy(() -> codec.decode(value)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
