package com.sca4d.iot;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;

/** 表示 IoT 公共模块能够稳定报告的业务失败。 */
@ErrorCodeRange(start = 2900, end = 2999)
public enum IotErrorCode implements ErrorCode {
    /** Product 不存在。 */
    PRODUCT_NOT_FOUND("2900", ErrorCategory.NOT_FOUND, "error.iot.product-not-found"),
    /** Device 不存在。 */
    DEVICE_NOT_FOUND("2901", ErrorCategory.NOT_FOUND, "error.iot.device-not-found"),
    /** 物模型不存在。 */
    THING_MODEL_NOT_FOUND("2902", ErrorCategory.NOT_FOUND, "error.iot.thing-model-not-found"),
    /** 唯一 code、序列号、clientId 或证书发生冲突。 */
    IDENTITY_CONFLICT("2903", ErrorCategory.CONFLICT, "error.iot.identity-conflict"),
    /** 物模型版本或内容发生冲突。 */
    THING_MODEL_CONFLICT("2904", ErrorCategory.CONFLICT, "error.iot.thing-model-conflict"),
    /** Product、Device 或下行消息状态不允许当前操作。 */
    STATE_CONFLICT("2905", ErrorCategory.CONFLICT, "error.iot.state-conflict"),
    /** 协议与物模型 code 或 Java 类型不匹配。 */
    PROTOCOL_CONFLICT("2906", ErrorCategory.CONFLICT, "error.iot.protocol-conflict"),
    /** 设备认证材料无效。 */
    DEVICE_AUTHENTICATION_FAILED("2907", ErrorCategory.AUTHENTICATION, "error.iot.authentication-failed"),
    /** IoT 输入不满足物模型或接入限制。 */
    INVALID_INPUT("2908", ErrorCategory.VALIDATION, "error.iot.invalid-input"),
    /** 必需的 IoT 外部依赖不可用。 */
    DEPENDENCY_UNAVAILABLE("2909", ErrorCategory.UNAVAILABLE, "error.iot.dependency-unavailable"),
    /** 物模型 JSON 语法不合法。 */
    THING_MODEL_JSON_INVALID("2910", ErrorCategory.VALIDATION, "error.iot.thing-model-json-invalid"),
    /** 物模型 JSON 使用了不支持的格式版本。 */
    THING_MODEL_FORMAT_UNSUPPORTED(
            "2911",
            ErrorCategory.VALIDATION,
            "error.iot.thing-model-format-unsupported"),
    /** 物模型 JSON 指定路径的字段不合法。 */
    THING_MODEL_FIELD_INVALID(
            "2912",
            ErrorCategory.VALIDATION,
            "error.iot.thing-model-field-invalid");

    private final String code;

    private final ErrorCategory category;

    private final String messageKey;

    IotErrorCode(String code, ErrorCategory category, String messageKey) {
        this.code = code;
        this.category = category;
        this.messageKey = messageKey;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public ErrorCategory category() {
        return category;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }
}
