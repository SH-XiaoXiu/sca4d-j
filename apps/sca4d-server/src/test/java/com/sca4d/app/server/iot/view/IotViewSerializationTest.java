package com.sca4d.app.server.iot.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.app.server.iot.IotProductCatalogItem;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.i18n.MessageText;
import com.sca4d.core.api.i18n.TextLocalizer;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.spring.web.view.BaseViewJacksonModule;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.ProductDefinition;
import com.sca4d.iot.api.message.IotFailureCode;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingModelPresentation;
import com.sca4d.iot.api.model.ThingProperty;
import com.sca4d.iot.api.model.ThingValueType;
import com.sca4d.iot.api.protocol.DeviceProtocol;
import com.sca4d.iot.api.schedule.DeviceScheduleExecution;
import com.sca4d.iot.api.schedule.DeviceScheduleExecutionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 检查 IoT HTTP 视图输出请求语言文本，并隐藏动态翻译和历史失败原值。 */
class IotViewSerializationTest {

    private static final Locale ZH_CN = Locale.forLanguageTag("zh-CN");

    private static final Locale EN_US = Locale.forLanguageTag("en-US");

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("ThingModel 嵌套名称按请求 Locale 逐字段回退到默认 Locale")
    void localizesNestedThingModelNamesWithDefaultLocaleFallback() throws Exception {
        ThingModel model = ThingModel.builder("climate", 1)
                .property(
                        ThingProperty.builder(
                                "settings",
                                ThingValueType.objectType()
                                        .requiredField(
                                                "mode",
                                                ThingValueType.enumType()
                                                        .value("cool")
                                                        .value("heat")
                                                        .build())
                                        .optionalField(
                                                "code",
                                                ThingValueType.stringType()
                                                        .maximumLength(4)
                                                        .pattern("[0-9a-f]{4}")
                                                        .build())
                                        .build())
                                .build())
                .build();
        Map<String, String> defaults = new LinkedHashMap<>();
        ThingModelPresentation.requiredTextPaths(model)
                .forEach((path, fallback) -> defaults.put(path, "默认-" + fallback));
        LocalizedFields i18n = LocalizedFields.of(
                Map.of(
                        ZH_CN,
                        defaults,
                        EN_US,
                        Map.of(
                                "/displayName",
                                "Climate model",
                                "/properties/settings/displayName",
                                "Settings")));
        LocaleContextHolder.setLocale(EN_US);

        JsonNode json = json(IotThingModelView.from(ThingModelPresentation.translated(model, i18n)));

        assertThat(json.path("displayName").textValue()).isEqualTo("Climate model");
        assertThat(json.at("/properties/0/displayName").textValue()).isEqualTo("Settings");
        assertThat(json.at("/properties/0/valueType/requiredFields/0/displayName").textValue())
                .isEqualTo("默认-mode");
        assertThat(
                json.at("/properties/0/valueType/requiredFields/0/valueType/enumValues/0/displayName")
                        .textValue())
                .isEqualTo("默认-cool");
        assertThat(
                json.at("/properties/0/valueType/optionalFields/0/valueType/pattern")
                        .textValue())
                .isEqualTo("[0-9a-f]{4}");
        assertThat(json.has("localizedDisplayName")).isFalse();
    }

    @Test
    @DisplayName("代码 Product 目录项按请求 Locale 返回名称且不伪造数据库字段")
    void localizesCodeProductCatalogWithoutDatabaseProfile() throws Exception {
        DeviceProtocol protocol = mock(DeviceProtocol.class);
        when(protocol.code()).thenReturn("gateway-v1");
        ProductDefinition definition = new ProductDefinition(
                "gateway",
                MessageText.of("product.gateway.display-name"),
                Set.of(DeviceAccessMethod.anonymous()),
                Set.of("gateway-v1"));
        LocaleContextHolder.setLocale(EN_US);

        JsonNode json = json(
                IotProductCatalogView.from(
                        new Page<>(
                                List.of(
                                        new IotProductCatalogItem(
                                                ProductBinding.code("gateway"),
                                                definition,
                                                null)),
                                new PageRequest(1, 20),
                                1),
                        List.of(DeviceAccessMethod.anonymous()),
                        List.of(protocol)));

        assertThat(json.at("/products/0/displayName").textValue()).isEqualTo("Gateway");
        assertThat(json.at("/products/0/source").textValue()).isEqualTo("CODE");
        assertThat(json.at("/products/0/id").isNull()).isTrue();
        assertThat(json.at("/products/0/status").isNull()).isTrue();
        assertThat(json.at("/products/0/createdAt").isNull()).isTrue();
        assertThat(json.at("/products/0/updatedAt").isNull()).isTrue();
        assertThat(json.at("/accessMethods/0").textValue()).isEqualTo("anonymous");
        assertThat(json.at("/protocolCodes/0").textValue()).isEqualTo("gateway-v1");
    }

    @Test
    @DisplayName("调度失败返回稳定 code 和本地化消息")
    void localizesScheduleFailureCodeAndMessage() throws Exception {
        LocaleContextHolder.setLocale(EN_US);

        JsonNode moduleError = json(scheduleFailure(IotErrorCode.INVALID_INPUT.code()));
        JsonNode builtInFailure = json(scheduleFailure(IotFailureCode.ACK_TIMEOUT.code()));

        assertThat(moduleError.at("/failure/code").textValue()).isEqualTo("2908");
        assertThat(moduleError.at("/failure/message").textValue())
                .isEqualTo("IoT input violates its constraints");
        assertThat(builtInFailure.at("/failure/code").textValue()).isEqualTo("ACK_TIMEOUT");
        assertThat(builtInFailure.at("/failure/message").textValue())
                .isEqualTo("Timed out waiting for device acknowledgement");
    }

    @Test
    @DisplayName("未知历史调度失败不返回原始文本")
    void replacesUnknownHistoricalScheduleFailure() throws Exception {
        LocaleContextHolder.setLocale(ZH_CN);

        JsonNode json = json(scheduleFailure("database error: private detail"));

        assertThat(json.at("/failure/code").textValue()).isEqualTo("LEGACY_FAILURE");
        assertThat(json.at("/failure/message").textValue()).isEqualTo("无法识别历史失败原因");
        assertThat(json.toString()).doesNotContain("private detail");
    }

    /** 创建一条固定时间且失败的调度执行视图。 */
    private static IotDeviceScheduleExecutionView scheduleFailure(String failureCode) {
        Instant plannedAt = Instant.parse("2026-08-15T01:00:00Z");
        return IotDeviceScheduleExecutionView.from(
                new DeviceScheduleExecution(
                        1L,
                        2L,
                        plannedAt,
                        plannedAt.plusSeconds(1),
                        DeviceScheduleExecutionStatus.FAILED,
                        null,
                        failureCode));
    }

    /** 使用真实 BaseView Jackson 模块序列化一个 HTTP 视图。 */
    private static JsonNode json(Object view) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        mapper.registerModule(new BaseViewJacksonModule(localizer()));
        return mapper.readTree(mapper.writeValueAsString(view));
    }

    /** 创建覆盖本测试中静态消息和动态翻译的本地化入口。 */
    private static TextLocalizer localizer() {
        Map<Locale, Map<String, String>> messages = Map.of(
                ZH_CN,
                Map.of(
                        IotFailureCode.LEGACY_FAILURE.messageKey(),
                        "无法识别历史失败原因"),
                EN_US,
                Map.of(
                        "product.gateway.display-name",
                        "Gateway",
                        IotErrorCode.INVALID_INPUT.messageKey(),
                        "IoT input violates its constraints",
                        IotFailureCode.ACK_TIMEOUT.messageKey(),
                        "Timed out waiting for device acknowledgement"));
        return new TextLocalizer(
                new LocaleCatalog(List.of(ZH_CN, EN_US), ZH_CN),
                (key, locale, arguments) -> Optional.ofNullable(
                        messages.getOrDefault(locale, Map.of()).get(key)));
    }
}
