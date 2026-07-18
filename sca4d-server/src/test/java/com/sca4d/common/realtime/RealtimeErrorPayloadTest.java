package com.sca4d.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.concurrent.ratelimit.RateLimitException;
import com.sca4d.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

/** 验证实时错误内容保留消息键和参数但不伪造默认语言文本。 */
class RealtimeErrorPayloadTest {

    @Test
    void shouldKeepStableMessageReferenceWithoutPretendingToKnowClientLocale() {
        RealtimeErrorPayload payload = RealtimeErrorPayload.from(
                new RateLimitException(CommonErrorCode.TOO_MANY_REQUESTS, 7L), "/topic/realtime/im");

        assertThat(payload.getMessageKey()).isEqualTo("error.common.too-many-requests");
        assertThat(payload.getMessageArgs()).isEmpty();
        assertThat(payload.getMessage()).isNull();
        assertThat(payload.getRetryAfterSeconds()).isEqualTo(7L);
        assertThat(payload.getDestination()).isEqualTo("/topic/realtime/im");
    }
}
