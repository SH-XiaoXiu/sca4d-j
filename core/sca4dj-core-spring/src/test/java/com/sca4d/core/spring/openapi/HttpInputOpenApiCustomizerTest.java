package com.sca4d.core.spring.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.spring.config.CoreSpringProperties;
import com.sca4d.core.spring.config.HttpInputValidationProperties;
import com.sca4d.core.spring.validation.NoDefaultValidation;
import com.sca4d.core.spring.web.annotation.ETag;
import com.sca4d.core.spring.web.annotation.IfMatch;
import com.sca4d.core.spring.web.list.ListParameters;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 验证 HttpInputOpenApiCustomizer 为请求体字段补充长度、数量和格式约束，为 If-Match 与 ETag 补充 Header，并为列表查询参数补充 sort 说明。 */
class HttpInputOpenApiCustomizerTest {

    @Test
    @DisplayName("Keeps An Unresolved Springdoc Parameter Absent")
    void keepsUnresolvedSpringdocParameterAbsent() throws Exception {
        HttpInputOpenApiCustomizer customizer = new HttpInputOpenApiCustomizer(
                new HttpInputValidationProperties(),
                new CoreSpringProperties());
        MethodParameter parameter = new MethodParameter(
                RequestTypes.class.getDeclaredMethod("accept", Long.class),
                0);

        assertThat(customizer.customize(null, parameter)).isNull();
    }

    @Test
    @DisplayName("Publishes If Match Header For A Version Protected Method")
    void publishesIfMatchHeaderForVersionProtectedMethod() throws Exception {
        HttpInputOpenApiCustomizer customizer = new HttpInputOpenApiCustomizer(
                new HttpInputValidationProperties(),
                new CoreSpringProperties());
        HandlerMethod handler = new HandlerMethod(
                new RequestTypes(),
                RequestTypes.class.getDeclaredMethod("replace"));

        Operation operation = customizer.customize(new Operation(), handler);

        assertThat(operation.getParameters()).singleElement().satisfies(parameter -> {
            assertThat(parameter.getName()).isEqualTo("If-Match");
            assertThat(parameter.getIn()).isEqualTo("header");
            assertThat(parameter.getRequired()).isTrue();
        });
    }

    @Test
    @DisplayName("为版本化响应声明 ETag Header")
    void publishesEtagHeaderForVersionedResponse() throws Exception {
        HttpInputOpenApiCustomizer customizer = new HttpInputOpenApiCustomizer(
                new HttpInputValidationProperties(),
                new CoreSpringProperties());
        HandlerMethod handler = new HandlerMethod(
                new RequestTypes(),
                RequestTypes.class.getDeclaredMethod("versioned"));
        Operation source = new Operation().responses(
                new ApiResponses().addApiResponse("200", new ApiResponse()));

        Operation operation = customizer.customize(source, handler);

        assertThat(operation.getResponses().get("200").getHeaders()).containsKey("ETag");
    }

    @Test
    @DisplayName("Publishes List Query Parameters")
    void publishesListQueryParameters() throws Exception {
        CoreSpringProperties coreProperties = new CoreSpringProperties();
        coreProperties.setSortMaxFields(3);
        HttpInputOpenApiCustomizer customizer = new HttpInputOpenApiCustomizer(
                new HttpInputValidationProperties(),
                coreProperties);
        HandlerMethod handler = new HandlerMethod(
                new RequestTypes(),
                RequestTypes.class.getDeclaredMethod("list", ListParameters.class));

        Operation operation = customizer.customize(new Operation(), handler);

        assertThat(operation.getParameters()).extracting(Parameter::getName)
                .containsExactly("page", "size", "sort");
        Parameter sort = operation.getParameters().get(2);
        assertThat(sort.getExplode()).isTrue();
        assertThat(sort.getSchema().getMaxItems()).isEqualTo(3);
        assertThat(sort.getDescription()).contains("FIELD,DIRECTION");
    }

    @Test
    @DisplayName("Publishes Type Defaults Only For Request Inputs")
    void publishesTypeDefaultsOnlyForRequestInputs() throws Exception {
        HttpInputValidationProperties properties = new HttpInputValidationProperties();
        properties.setMaxStringLength(12);
        properties.setMaxContainerSize(3);
        HttpInputOpenApiCustomizer customizer = new HttpInputOpenApiCustomizer(
                properties,
                new CoreSpringProperties());

        IntegerSchema count = customized(customizer, new IntegerSchema(), "count");
        StringSchema accountId = customized(customizer, new StringSchema(), "accountId");
        accountId.setType(null);
        accountId.addType("string");
        StringSchema name = customized(customizer, new StringSchema(), "name");
        ArraySchema ids = customized(
                customizer,
                new ArraySchema().items(new StringSchema()),
                "ids");
        ids.setType(null);
        ids.addType("array");
        IntegerSchema maxOnly = customized(customizer, new IntegerSchema(), "maxOnly");
        ArraySchema sized = customized(customizer, new ArraySchema(), "sized");
        StringSchema unrestricted = customized(customizer, new StringSchema(), "unrestricted");
        MapSchema labels = customized(customizer, new MapSchema(), "labels");
        labels.setAdditionalProperties(new StringSchema());

        ObjectSchema request = new ObjectSchema();
        request.setType(null);
        request.addType("object");
        request.addProperty("count", count);
        request.addProperty("accountId", accountId);
        request.addProperty("name", name);
        request.addProperty("ids", ids);
        request.addProperty("maxOnly", maxOnly);
        request.addProperty("sized", sized);
        request.addProperty("unrestricted", unrestricted);
        request.addProperty("labels", labels);

        StringSchema responseOnly = customized(customizer, new StringSchema(), "responseOnly");
        OpenAPI openApi = document(request, responseOnly);
        StringSchema disabledName = new StringSchema();
        openApi.getComponents()
                .addSchemas(
                        "DisabledRequest",
                        new ObjectSchema().addProperty("name", disabledName));
        Schema<?> disabledReference = customized(
                customizer,
                new Schema<>().$ref("#/components/schemas/DisabledRequest"),
                "disabledRequest");
        openApi.getPaths()
                .addPathItem(
                        "/disabled",
                        new PathItem().post(
                                new Operation().requestBody(
                                        new RequestBody().content(
                                                new Content().addMediaType(
                                                        "application/json",
                                                        new MediaType().schema(disabledReference))))));
        customizer.customise(openApi);

        assertThat(count.getMinimum()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(accountId.getPattern()).isEqualTo("^[1-9][0-9]*$");
        assertThat(name.getMaxLength()).isEqualTo(12);
        assertThat(ids.getMaxItems()).isEqualTo(3);
        assertThat(ids.getItems().getPattern()).isEqualTo("^[1-9][0-9]*$");
        assertThat(labels.getMaxProperties()).isEqualTo(3);
        assertThat(maxOnly.getMinimum()).isNull();
        assertThat(sized.getMaxItems()).isNull();
        assertThat(unrestricted.getMaxLength()).isNull();
        assertThat(disabledName.getMaxLength()).isNull();
        assertThat(responseOnly.getMaxLength()).isNull();
        assertThat(
                List.of(
                        count,
                        accountId,
                        name,
                        ids,
                        maxOnly,
                        sized,
                        unrestricted,
                        labels,
                        disabledReference,
                        responseOnly))
                .allSatisfy(schema -> assertThat(schema.getExtensions()).isNullOrEmpty());
    }

    /** @return 以字段的泛型类型和字段注解调用 customize 后得到的 Schema */
    @SuppressWarnings("unchecked")
    private static <S extends Schema<?>> S customized(
            HttpInputOpenApiCustomizer customizer,
            S schema,
            String fieldName) throws Exception {
        Field field = RequestTypes.class.getDeclaredField(fieldName);
        return (S) customizer.customize(
                schema,
                new AnnotatedType(field.getGenericType()).ctxAnnotations(field.getAnnotations()));
    }

    /** @return 包含一个请求体引用和一个仅用于响应的内联 Schema 的 OpenAPI 文档 */
    private static OpenAPI document(ObjectSchema request, StringSchema responseOnly) {
        Components components = new Components().addSchemas("RequestTypes", request);
        RequestBody requestBody = new RequestBody().content(
                new Content().addMediaType(
                        "application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/RequestTypes"))));
        ApiResponses responses = new ApiResponses().addApiResponse(
                "200",
                new ApiResponse().content(
                        new Content().addMediaType(
                                "application/json",
                                new MediaType().schema(responseOnly))));
        Operation operation = new Operation().requestBody(requestBody).responses(responses);
        return new OpenAPI()
                .components(components)
                .paths(new Paths().addPathItem("/requests", new PathItem().post(operation)));
    }

    /** 保存 OpenAPI 定制器测试需要识别的字段类型和覆盖注解。 */
    @SuppressWarnings("unused") // 字段只用于反射构造带声明注解的 AnnotatedType。
    private static class RequestTypes {

        private int count;

        private Long accountId;

        private String name;

        private List<Long> ids;

        @Max(10)
        private int maxOnly;

        @Size(max = 10)
        private List<Long> sized;

        @NoDefaultValidation
        private String unrestricted;

        private Map<String, String> labels;

        @NoDefaultValidation
        private RequestTypes disabledRequest;

        private String responseOnly;

        /** 接收 Springdoc 测试构造的 Controller 参数。 */
        @SuppressWarnings("unused")
        private void accept(Long value) {
        }

        /** 声明需要 `If-Match` 的测试 HTTP 方法。 */
        @IfMatch
        private void replace() {
        }

        /** 声明带排序字段枚举的列表参数。 */
        @SuppressWarnings("unused")
        private void list(ListParameters parameters) {
        }

        /** @return 带版本声明的测试响应 */
        private com.sca4d.core.spring.web.ApiResponse<VersionedResponse> versioned() {
            return com.sca4d.core.spring.web.ApiResponse.ok(new VersionedResponse());
        }
    }

    /** 提供 OpenAPI 响应头声明使用的版本化类型。 */
    private static class VersionedResponse {

        /** @return 固定测试版本 */
        @ETag
        public Version revision() {
            return new Version(1);
        }
    }
}
