package com.sca4d.file.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sca4d.common.boot.BootEnvironment;
import com.sca4d.config.properties.Sca4djFileProperties;
import com.sca4d.config.properties.Sca4djMinioProperties;
import org.junit.jupiter.api.Test;

/** 验证启用 MinIO 时检查桶是否可访问并报告失败原因。 */
class MinioBootCheckTest {

    @Test
    void shouldSkipCheckWhenLocalStorageIsSelected() {
        Sca4djFileProperties fileProperties = new Sca4djFileProperties();
        fileProperties.setStorage(FileStorageProvider.LOCAL);
        MinioFileStorage storage = mock(MinioFileStorage.class);

        MinioBootCheck check = new MinioBootCheck(fileProperties, new Sca4djMinioProperties(), storage);

        assertThat(check.policy().isEnabled()).isFalse();
        verifyNoInteractions(storage);
    }

    @Test
    void shouldRejectDefaultCredentialsInProductionLikeEnvironment() {
        MinioFileStorage storage = mock(MinioFileStorage.class);
        MinioBootCheck check = new MinioBootCheck(new Sca4djFileProperties(), new Sca4djMinioProperties(), storage);

        assertThat(check.check(BootEnvironment.PROD).isAvailable()).isFalse();
        verifyNoInteractions(storage);
    }

    @Test
    void shouldVerifyBucketWhenMinioConfigurationIsUsable() {
        MinioFileStorage storage = mock(MinioFileStorage.class);
        MinioBootCheck check = new MinioBootCheck(new Sca4djFileProperties(), new Sca4djMinioProperties(), storage);

        assertThat(check.check(BootEnvironment.LOCAL).isAvailable()).isTrue();

        verify(storage).verifyAvailable();
    }
}
