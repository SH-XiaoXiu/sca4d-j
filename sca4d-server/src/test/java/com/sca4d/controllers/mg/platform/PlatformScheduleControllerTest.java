package com.sca4d.controllers.mg.platform;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.schedule.ScheduleErrorCode;
import com.sca4d.schedule.ScheduleService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** 验证平台 Schedule 接口的查询、创建、替换、删除和人工执行。 */
class PlatformScheduleControllerTest {

    @Test
    void shouldMapMissingAndMalformedIfMatchToStablePreconditionErrors() {
        ScheduleService service = mock(ScheduleService.class);
        PlatformScheduleController controller = new PlatformScheduleController(
                service, new ObjectMapper(), LocalizedMessagesFixture.create());

        assertThatThrownBy(() -> controller.delete(1L, null))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ScheduleErrorCode.SCHEDULE_IF_MATCH_REQUIRED));
        assertThatThrownBy(() -> controller.delete(1L, "not-an-etag"))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ScheduleErrorCode.SCHEDULE_VERSION_CHANGED));
        assertThatThrownBy(() -> controller.delete(1L, "W/\"1\""))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ScheduleErrorCode.SCHEDULE_VERSION_CHANGED));
        verifyNoInteractions(service);
    }
}
