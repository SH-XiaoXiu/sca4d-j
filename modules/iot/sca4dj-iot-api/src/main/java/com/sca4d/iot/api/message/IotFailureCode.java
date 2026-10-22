package com.sca4d.iot.api.message;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** 保存可持久化的 IoT 处理失败 code 及其静态消息键。 */
public class IotFailureCode {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,127}");

    /** Transport 没有完成一次下行发送。 */
    public static final IotFailureCode TRANSPORT_FAILED = new IotFailureCode(
            "TRANSPORT_FAILED",
            "failure.iot.transport-failed");

    /** 等待 Device ACK 超时。 */
    public static final IotFailureCode ACK_TIMEOUT = new IotFailureCode(
            "ACK_TIMEOUT",
            "failure.iot.ack-timeout");

    /** Device ACK 明确报告执行失败。 */
    public static final IotFailureCode DEVICE_REPORTED_FAILURE = new IotFailureCode(
            "DEVICE_REPORTED_FAILURE",
            "failure.iot.device-reported-failure");

    /** Command 已用完允许的重试次数。 */
    public static final IotFailureCode COMMAND_RETRY_EXHAUSTED = new IotFailureCode(
            "COMMAND_RETRY_EXHAUSTED",
            "failure.iot.command-retry-exhausted");

    /** 当前 retry 策略不允许再次发送。 */
    public static final IotFailureCode RETRY_DISABLED = new IotFailureCode(
            "RETRY_DISABLED",
            "failure.iot.retry-disabled");

    /** retry 策略与下行消息种类不匹配。 */
    public static final IotFailureCode RETRY_POLICY_MISMATCH = new IotFailureCode(
            "RETRY_POLICY_MISMATCH",
            "failure.iot.retry-policy-mismatch");

    private static final List<IotFailureCode> BUILT_INS = List.of(
            TRANSPORT_FAILED,
            ACK_TIMEOUT,
            DEVICE_REPORTED_FAILURE,
            COMMAND_RETRY_EXHAUSTED,
            RETRY_DISABLED,
            RETRY_POLICY_MISMATCH);

    private final @NotNull String code;

    private final @NotNull String messageKey;

    /** 创建经过格式校验的失败 code 和消息键。 */
    private IotFailureCode(@NotNull String code, @NotNull String messageKey) {
        String checkedCode = TextUtils.requireText(code, "IoT 失败 code");
        if (!CODE.matcher(checkedCode).matches()) {
            throw new IllegalArgumentException("IoT 失败 code 必须是最长 128 位的大写字母、数字或下划线");
        }
        this.code = checkedCode;
        this.messageKey = TextUtils.requireText(messageKey, "IoT 失败消息键");
    }

    /**
     * 创建具体应用或协议扩展的失败 code。
     *
     * @param code 以大写字母开头、最长 128 位且只包含大写字母、数字和下划线的 code
     * @param messageKey 应用消息目录中登记的静态消息键
     * @return 新失败 code
     */
    @CheckReturnValue
    public static @NotNull IotFailureCode of(
            @NotNull String code,
            @NotNull String messageKey) {
        return new IotFailureCode(code, messageKey);
    }

    /** @return 模块内置且必须由默认消息目录覆盖的失败 code */
    public static @NotNull @Unmodifiable List<IotFailureCode> builtIns() {
        return BUILT_INS;
    }

    /** @return 可持久化且跨版本稳定的 code */
    public @NotNull String code() {
        return code;
    }

    /** @return 外围展示失败时使用的静态消息键 */
    public @NotNull String messageKey() {
        return messageKey;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof IotFailureCode that
                        && code.equals(that.code)
                        && messageKey.equals(that.messageKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, messageKey);
    }

    @Override
    public @NotNull String toString() {
        return code;
    }
}
