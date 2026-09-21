package com.sca4d.iot.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.model.ThingCommand;
import com.sca4d.iot.api.model.ThingEvent;
import com.sca4d.iot.api.model.ThingEventDelivery;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingProperty;
import com.sca4d.iot.api.model.ThingPropertyAccess;
import com.sca4d.iot.api.model.ThingValueType;
import com.sca4d.iot.message.BuiltInDevicePolicies;
import com.sca4d.iot.message.DevicePolicyCatalog;
import com.sca4d.iot.message.policy.DefaultCommandRetryPolicy;
import com.sca4d.iot.message.policy.DefaultStateSyncRetryPolicy;
import com.sca4d.iot.message.policy.DeviceResultAcknowledgementPolicy;
import com.sca4d.iot.message.policy.ReportedEqualsDesiredAcknowledgementPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证静态 Builder 与严格 JSON 使用同一种物模型对象和稳定规范内容。 */
class ThingModelJsonCodecTest {

    private final ThingModelJsonCodec codec = new ThingModelJsonCodec(
            new ObjectMapper(),
            new DevicePolicyCatalog(
                    BuiltInDevicePolicies.acknowledgements(),
                    BuiltInDevicePolicies.retries()));

    @Test
    @DisplayName("Builder 编码和 JSON 往返保持稳定字段顺序")
    void keepsCanonicalContentAcrossBuilderAndJsonRoundTrip() {
        ThingModel model = ThingModel.builder("air-conditioner", 1)
                .property(
                        ThingProperty.builder("power", ThingValueType.booleanType())
                                .access(ThingPropertyAccess.READ_WRITE)
                                .desiredState(
                                        new ReportedEqualsDesiredAcknowledgementPolicy(),
                                        new DefaultStateSyncRetryPolicy())
                                .build())
                .property(
                        ThingProperty.builder(
                                "temperature",
                                ThingValueType.decimalType()
                                        .minimum("-40")
                                        .maximum("100")
                                        .scale(1)
                                        .unit("celsius")
                                        .build())
                                .telemetry(true)
                                .build())
                .event(
                        ThingEvent.builder("fault")
                                .delivery(ThingEventDelivery.RELIABLE)
                                .output("code", ThingValueType.stringType().maximumLength(32).build())
                                .output("occurredAt", ThingValueType.timestampType())
                                .build())
                .command(
                        ThingCommand.builder("set-mode")
                                .input(
                                        "mode",
                                        ThingValueType.enumType()
                                                .value("cool")
                                                .value("heat")
                                                .build())
                                .acknowledgementPolicy(new DeviceResultAcknowledgementPolicy())
                                .retryPolicy(new DefaultCommandRetryPolicy())
                                .build())
                .build();

        String encoded = codec.encode(model);

        assertThat(encoded).isEqualTo(
                "{\"formatVersion\":2,\"productCode\":\"air-conditioner\",\"version\":1,"
                        + "\"properties\":["
                        + "{\"code\":\"power\",\"valueType\":{\"kind\":\"BOOLEAN\"},"
                        + "\"access\":\"READ_WRITE\",\"telemetry\":false,"
                        + "\"acknowledgementPolicy\":\"IOT/DOWNLINK/ACK/REPORTED/EQUALS/DESIRED\",\"retryPolicy\":\"IOT/DOWNLINK/RETRY/STATE/SYNC/DEFAULT\"},"
                        + "{\"code\":\"temperature\",\"valueType\":{\"kind\":\"DECIMAL\","
                        + "\"minimum\":\"-40\",\"maximum\":\"100\",\"scale\":1,\"unit\":\"celsius\"},"
                        + "\"access\":\"READ_ONLY\",\"telemetry\":true}],"
                        + "\"events\":[{\"code\":\"fault\",\"delivery\":\"RELIABLE\",\"outputs\":["
                        + "{\"code\":\"code\",\"valueType\":{\"kind\":\"STRING\",\"maximumLength\":32}},"
                        + "{\"code\":\"occurredAt\",\"valueType\":{\"kind\":\"TIMESTAMP\"}}]}],"
                        + "\"commands\":[{\"code\":\"set-mode\",\"inputs\":[{\"code\":\"mode\",\"valueType\":{"
                        + "\"kind\":\"ENUM\",\"enumValues\":[{\"code\":\"cool\"},"
                        + "{\"code\":\"heat\"}]}}],"
                        + "\"acknowledgementPolicy\":\"IOT/DOWNLINK/ACK/DEVICE/RESULT\",\"retryPolicy\":\"IOT/DOWNLINK/RETRY/COMMAND/DEFAULT\"}]}");
        assertThat(codec.encode(codec.decode(encoded))).isEqualTo(encoded);
    }

    @Test
    @DisplayName("字符串 pattern 在物模型 JSON 往返中保持不变")
    void keepsStringPatternAcrossJsonRoundTrip() {
        ThingModel model = ThingModel.builder("gateway", 1)
                .command(
                        ThingCommand.builder("set-net-id")
                                .input(
                                        "netId",
                                        ThingValueType.stringType()
                                                .maximumLength(4)
                                                .pattern("[0-9a-f]{4}")
                                                .build())
                                .acknowledgementPolicy(new DeviceResultAcknowledgementPolicy())
                                .retryPolicy(new DefaultCommandRetryPolicy())
                                .build())
                .build();

        String encoded = codec.encode(model);

        assertThat(encoded).contains("\"pattern\":\"[0-9a-f]{4}\"");
        assertThat(codec.encode(codec.decode(encoded))).isEqualTo(encoded);
    }

    @Test
    @DisplayName("拒绝未知字段和显式 null")
    void rejectsUnknownFieldsAndExplicitNulls() {
        String minimum = "{\"formatVersion\":2,\"productCode\":\"demo\",\"version\":1,"
                + "\"properties\":[],\"events\":[],\"commands\":[]";

        assertThatThrownBy(() -> codec.decode(minimum + ",\"extra\":true}"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.errorCode()).isEqualTo(IotErrorCode.THING_MODEL_FIELD_INVALID);
                            assertThat(exception.messageArguments()).containsExactly("/extra");
                        });
        assertThatThrownBy(() -> codec.decode(minimum + ",\"displayName\":null}"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.errorCode()).isEqualTo(IotErrorCode.THING_MODEL_FIELD_INVALID);
                            assertThat(exception.messageArguments()).containsExactly("/displayName");
                        });
    }

    @Test
    @DisplayName("JSON 语法和格式版本使用独立稳定错误码")
    void reportsStableSyntaxAndFormatErrors() {
        assertThatThrownBy(() -> codec.decode("{"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(IotErrorCode.THING_MODEL_JSON_INVALID));
        assertThatThrownBy(
                () -> codec.decode(
                        "{\"formatVersion\":1,\"productCode\":\"demo\",\"version\":1,"
                                + "\"properties\":[],\"events\":[],\"commands\":[]}"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.errorCode())
                                    .isEqualTo(IotErrorCode.THING_MODEL_FORMAT_UNSUPPORTED);
                            assertThat(exception.messageArguments()).containsExactly(1);
                        });
    }
}
