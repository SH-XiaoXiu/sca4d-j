package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.exception.BizException;
import com.sca4d.controllers.FileSignatureController;
import com.sca4d.controllers.FileTransportController;
import com.sca4d.file.FileService;
import com.sca4d.file.FileServiceUnavailable;
import com.sca4d.iam.profile.signature.DefaultProfileAvatarFileSignaturePolicy;
import com.sca4d.im.signature.ImFileSignaturePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 验证关闭文件服务时不会注册存储和签名相关 Bean。 */
class FileOptionalBoundaryTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FileDisabledConfiguration.class)
            .withPropertyValues("sca4dj.file.enabled=false");

    @Test
    void shouldProvideFailClosedServiceWithoutFileHttpOrSignatureBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FileService.class);
            assertThat(context.getBean(FileService.class)).isInstanceOf(FileServiceUnavailable.class);
            assertThat(context).doesNotHaveBean(FileSignatureController.class);
            assertThat(context).doesNotHaveBean(FileTransportController.class);
            assertThat(context).doesNotHaveBean(ImFileSignaturePolicy.class);
            assertThat(context).doesNotHaveBean(DefaultProfileAvatarFileSignaturePolicy.class);
            assertThatThrownBy(() -> context.getBean(FileService.class).requestDelete(1L))
                    .isInstanceOfSatisfying(BizException.class, exception -> assertThat(exception.getMessageKey())
                            .isEqualTo("error.file.disabled"));
        });
    }

    /** 只导入文件相关 Bean，用于验证关闭开关后的装配结果。 */
    @Configuration(proxyBeanMethods = false)
    @Import({
            FileFallbackConfig.class,
            FileSignatureController.class,
            FileTransportController.class,
            ImFileSignaturePolicy.class,
            DefaultProfileAvatarFileSignaturePolicy.class
    })
    static class FileDisabledConfiguration {
    }
}
