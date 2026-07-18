package com.sca4d.common.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.enums.BaseEnum;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;

/** 验证分页、时间和基础值对象的公共行为。 */
class CoreSupportTest {

    @Test
    void timeSupportShouldUseUtcAsStorageTime() {
        OffsetDateTime dateTime = TimeSupport.from(Instant.parse("2026-06-28T00:00:00Z"));

        assertThat(dateTime.getOffset().getTotalSeconds()).isZero();
        assertThat(TimeSupport.now().getOffset()).isEqualTo(TimeSupport.STORAGE_ZONE);
    }

    @Test
    void timeSupportShouldConvertToDefaultBusinessZone() {
        OffsetDateTime dateTime = TimeSupport.toDefaultZone(Instant.parse("2026-06-28T00:00:00Z"));

        assertThat(dateTime.getOffset().getTotalSeconds()).isEqualTo(8 * 60 * 60);
        assertThat(TimeSupport.format(OffsetDateTime.parse("2026-06-28T00:00:00Z")))
                .isEqualTo("2026-06-28 08:00:00");
    }

    @Test
    void coreEnumsShouldImplementBaseEnum() {
        assertThat(DeletedStatus.NORMAL.value()).isEqualTo("NORMAL");
        assertThat(DeletedStatus.DELETED.description()).isEqualTo("已删除");
        assertThat(EnabledStatus.ENABLED.value()).isEqualTo("ENABLED");
        assertThat(SortDirection.ASC.description()).isEqualTo("升序");
        assertThat(BaseEnum.fromValue(SortDirection.class, "desc")).isEqualTo(SortDirection.DESC);
    }
}
