package com.sca4d.common.id;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.file.service.DefaultFileService;
import com.sca4d.iam.principal.service.DefaultPrincipalService;
import com.sca4d.im.service.ImServiceImpl;
import com.sca4d.notification.service.NotificationServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

/** 验证业务代码在插入主表和子表前生成分布式 ID。 */
class DistributedIdBoundaryTest {

    @Test
    void ordinaryBusinessServicesShouldNotAllocatePersistenceIds() {
        assertThat(Arrays.stream(DefaultPrincipalService.class.getDeclaredFields()).map(Field::getType))
                .doesNotContain(IdGenerator.class);
        assertThat(Arrays.stream(ImServiceImpl.class.getDeclaredFields()).map(Field::getType))
                .doesNotContain(IdGenerator.class);
        assertThat(Arrays.stream(NotificationServiceImpl.class.getDeclaredFields()).map(Field::getType))
                .doesNotContain(IdGenerator.class);
    }

    @Test
    void fileServiceShouldRetainPrePersistenceResourceIdAllocation() {
        assertThat(Arrays.stream(DefaultFileService.class.getDeclaredFields()).map(Field::getType))
                .contains(IdGenerator.class);
    }
}
