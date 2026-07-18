package com.sca4d.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.exception.BizException;
import com.sca4d.schedule.ScheduleErrorCode;
import org.junit.jupiter.api.Test;

/** 验证资源版本只接受一个强 If-Match ETag。 */
class IfMatchVersionTest {

    @Test
    void shouldReadOneStrongNonNegativeVersion() {
        assertThat(IfMatchVersion.require(
                "\"42\"",
                ScheduleErrorCode.SCHEDULE_IF_MATCH_REQUIRED,
                ScheduleErrorCode.SCHEDULE_VERSION_CHANGED))
                .isEqualTo(42L);
    }

    @Test
    void shouldDistinguishMissingAndInvalidPreconditions() {
        assertThatThrownBy(() -> IfMatchVersion.require(
                null,
                ScheduleErrorCode.SCHEDULE_IF_MATCH_REQUIRED,
                ScheduleErrorCode.SCHEDULE_VERSION_CHANGED))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ScheduleErrorCode.SCHEDULE_IF_MATCH_REQUIRED));
        for (String invalid : new String[] {"not-an-etag", "W/\"1\"", "*", "\"1\", \"2\"", "\"-1\""}) {
            assertThatThrownBy(() -> IfMatchVersion.require(
                    invalid,
                    ScheduleErrorCode.SCHEDULE_IF_MATCH_REQUIRED,
                    ScheduleErrorCode.SCHEDULE_VERSION_CHANGED))
                    .isInstanceOfSatisfying(
                            BizException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ScheduleErrorCode.SCHEDULE_VERSION_CHANGED));
        }
    }
}
