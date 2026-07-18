package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.auth.annotation.AuthSubject;
import com.sca4d.identity.oauth2.request.OAuth2AuthorizationCreateRequest;
import com.sca4d.identity.oauth2.request.OAuth2ProfilePreviewRequest;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Properties;

/** 验证 OpenAPI 标题、版本和安全方案配置。 */
class OpenApiConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(BuildProperties.class, () -> {
                Properties properties = new Properties();
                properties.setProperty("version", "0.0.1-alpha");
                return new BuildProperties(properties);
            })
            .withUserConfiguration(OpenApiConfig.class);

    @Test
    void shouldExposeScaffoldApiInformation() {
        contextRunner.run(context -> {
            OpenAPI openAPI = context.getBean(OpenAPI.class);

            assertThat(openAPI.getInfo().getTitle()).isEqualTo("SCA4D API");
            assertThat(openAPI.getInfo().getVersion()).isEqualTo("0.0.1-alpha");
        });
    }

    @Test
    void shouldExposeComposedRequiredConstraintInSchemas() {
        ModelConverters converters = new ModelConverters(true);
        converters.addConverter(new RequiredModelConverter());
        ResolvedSchema authorization = converters.resolveAsResolvedSchema(
                new AnnotatedType(OAuth2AuthorizationCreateRequest.class).resolveAsRef(true));
        ResolvedSchema preview = converters.resolveAsResolvedSchema(
                new AnnotatedType(OAuth2ProfilePreviewRequest.class).resolveAsRef(true));
        Schema<?> authorizationSchema = authorization.referencedSchemas.get("OAuth2AuthorizationCreateRequest");
        Schema<?> previewSchema = preview.referencedSchemas.get("OAuth2ProfilePreviewRequest");

        assertThat(authorizationSchema.getRequired()).containsExactly("providerCode");
        assertThat(previewSchema.getRequired()).containsExactly("profile");
    }

    @Test
    void shouldHideInjectedAuthParametersWithoutHidingOperations() {
        assertThat(AuthPrincipal.class.isAnnotationPresent(Hidden.class)).isFalse();
        assertThat(AuthSubject.class.isAnnotationPresent(Hidden.class)).isFalse();
        assertThat(AuthPrincipal.class.getAnnotation(Parameter.class).hidden()).isTrue();
        assertThat(AuthSubject.class.getAnnotation(Parameter.class).hidden()).isTrue();
    }
}
