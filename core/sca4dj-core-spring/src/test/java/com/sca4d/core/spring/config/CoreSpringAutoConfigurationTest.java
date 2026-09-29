package com.sca4d.core.spring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.IdempotencyFingerprints;
import com.sca4d.core.api.concurrent.IdempotencyStore;
import com.sca4d.core.api.concurrent.RateLimiter;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.OperationModule;
import com.sca4d.core.api.permission.OperationNode;
import com.sca4d.core.spring.id.SnowflakeIdGenerator;
import com.sca4d.core.spring.identity.PrincipalSelectionCallInterceptor;
import com.sca4d.core.spring.identity.PrincipalSelectionInterceptor;
import com.sca4d.core.spring.openapi.CoreOpenApiConfiguration;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.core.spring.redis.RedisBatches;
import com.sca4d.core.spring.web.GlobalExceptionHandler;
import com.sca4d.core.spring.web.HttpIdempotencyKeyResolver;
import com.sca4d.core.spring.web.MethodArgumentsIdempotencyKeyResolver;
import com.sca4d.core.spring.web.ResponseETagContractValidator;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Instant;

class CoreSpringAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withPropertyValues("spring.application.name=sca4d-test")
            .withConfiguration(
                    AutoConfigurations.of(
                            CoreJacksonAutoConfiguration.class,
                            JacksonAutoConfiguration.class,
                            WebMvcAutoConfiguration.class,
                            AppIsolationAutoConfiguration.class,
                            CoreSpringAutoConfiguration.class,
                            CoreOpenApiConfiguration.class))
            .withPropertyValues(
                    "spring.application.name=sca4d-test",
                    "sca4dj.i18n.supported-locales[0]=zh-CN",
                    "sca4dj.i18n.supported-locales[1]=en-US",
                    "sca4dj.i18n.default-locale=zh-CN")
            .withBean(RateLimiter.class, () -> mock(RateLimiter.class))
            .withBean(IdempotencyStore.class, () -> mock(IdempotencyStore.class))
            .withBean(DistributedLock.class, () -> mock(DistributedLock.class));

    @Test
    @DisplayName("Fails startup when Redis concurrency beans are absent")
    void failsStartupWhenRedisConcurrencyBeansAreAbsent() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CoreSpringAutoConfiguration.class))
                .withConfiguration(
                        AutoConfigurations.of(
                                CoreJacksonAutoConfiguration.class,
                                JacksonAutoConfiguration.class))
                .withPropertyValues(
                        "sca4dj.i18n.supported-locales[0]=zh-CN",
                        "sca4dj.i18n.default-locale=zh-CN")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(
                                    NoSuchBeanDefinitionException.class);
                });
    }

    @Test
    @DisplayName("Uses Scaffold JSON Mapper For Idempotency Fingerprints")
    void usesScaffoldJsonMapperForIdempotencyFingerprints() {
        contextRunner.run(context -> {
            ObjectMapper httpMapper = context.getBean(ObjectMapper.class);
            IdempotencyFingerprints fingerprints = context.getBean(IdempotencyFingerprints.class);

            assertThat(httpMapper.writeValueAsString(new Object[] {42L})).isEqualTo("[\"42\"]");
            assertThat(fingerprints.canonicalArguments(new Object[] {42L})).isEqualTo("[42]");
        });
    }

    @Test
    @DisplayName("Registers Core MVC Infrastructure")
    void registersCoreMvcInfrastructure() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            assertThat(context).hasSingleBean(PrincipalSelectionCallInterceptor.class);
            assertThat(context).hasSingleBean(PrincipalSelectionInterceptor.class);
            assertThat(context).hasSingleBean(OperationCatalog.class);
            assertThat(context).hasSingleBean(IdempotencyFingerprints.class);
            assertThat(context).hasSingleBean(HttpIdempotencyKeyResolver.class);
            assertThat(context).hasSingleBean(MethodArgumentsIdempotencyKeyResolver.class);
            assertThat(context).hasSingleBean(SqlBatches.class);
            assertThat(context).hasSingleBean(RedisBatches.class);
            assertThat(context).hasSingleBean(ResponseETagContractValidator.class);
            assertThat(context).hasSingleBean(OpenAPI.class);
            assertThat(context).hasBean("sca4djAppName");
            assertThat(context).hasSingleBean(IdGenerator.class);
            assertThat(context.getBean(IdGenerator.class)).isInstanceOf(SnowflakeIdGenerator.class);
            assertThat(context.getBean(OpenAPI.class).getInfo().getTitle()).isEqualTo("sca4d-test API");
        });
    }

    @Test
    @DisplayName("Registers ISO date-time request formatters")
    void registersIsoDateTimeRequestFormatters() {
        contextRunner.run(context -> {
            DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService(false);
            context.getBeansOfType(WebMvcConfigurer.class)
                    .values()
                    .forEach(configurer -> configurer.addFormatters(conversionService));

            assertThat(conversionService.convert("2026-08-05T12:34:56+08:00", Instant.class))
                    .isEqualTo(Instant.parse("2026-08-05T04:34:56Z"));
            assertThatThrownBy(() -> conversionService.convert("2026-08-05 12:34:56", Instant.class))
                    .isInstanceOf(ConversionFailedException.class);
        });
    }

    @Test
    @DisplayName("Collects Application Operation Modules")
    void collectsApplicationOperationModules() {
        OperationModule module = () -> OperationNode.module(
                TreeCode.ofSegment("PLATFORM"),
                "operation.platform",
                10,
                OperationNode.resource(
                        TreeCode.ofSegment("ROLES"),
                        "operation.platform.roles",
                        10,
                        OperationNode.action(
                                TreeCode.ofSegment("READ"),
                                "operation.platform.roles.read",
                                10)));

        contextRunner
                .withBean(OperationModule.class, () -> module)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OperationCatalog.class).actions())
                            .extracting(OperationNode.OperationAction::code)
                            .containsExactly(TreeCode.ofColon("PLATFORM:ROLES:READ"));
                });
    }

    @Test
    @DisplayName("Keeps Application ID Generator")
    void keepsApplicationIdGenerator() {
        IdGenerator applicationGenerator = () -> Id.of(42L);

        contextRunner
                .withBean(IdGenerator.class, () -> applicationGenerator)
                .run(context -> assertThat(context.getBean(IdGenerator.class)).isSameAs(applicationGenerator));
    }

    @Test
    @DisplayName("Keeps application OpenAPI definition")
    void keepsApplicationOpenApiDefinition() {
        OpenAPI applicationOpenApi = new OpenAPI();

        contextRunner
                .withBean(OpenAPI.class, () -> applicationOpenApi)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OpenAPI.class)).isSameAs(applicationOpenApi);
                });
    }
}
