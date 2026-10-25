package com.sca4d.iot.api.model;

import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iot.api.message.AcknowledgementPolicy;
import com.sca4d.iot.api.message.RetryPolicy;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/** 定义一个稳定 code 的设备属性及其 desired 策略。 */
public class ThingProperty {

    private final @Nullable AcknowledgementPolicy acknowledgementPolicy;

    private final @Nullable RetryPolicy retryPolicy;

    private final @NotNull String code;

    private final @NotNull ThingPropertyAccess access;

    private final @NotNull ThingValueType valueType;

    private final boolean telemetry;

    private ThingProperty(@NotNull Builder builder) {
        this.code = builder.code;
        this.valueType = builder.valueType;
        this.access = builder.access;
        this.acknowledgementPolicy = builder.acknowledgementPolicy;
        this.retryPolicy = builder.retryPolicy;
        this.telemetry = builder.telemetry;
    }

    /** @param code 属性稳定 code @param valueType 递归值类型 @return 属性 Builder */
    @CheckReturnValue
    public static @NotNull Builder builder(@NotNull String code, @NotNull ThingValueType valueType) {
        return new Builder(code, valueType);
    }

    /** @return 属性稳定 code */
    public @NotNull String code() {
        return code;
    }

    /** @return 属性值类型 */
    public @NotNull ThingValueType valueType() {
        return valueType;
    }

    /** @return 属性读写方向 */
    public @NotNull ThingPropertyAccess access() {
        return access;
    }

    /** @return READ_WRITE 属性采用的 ACK 策略 */
    public @NotNull Optional<AcknowledgementPolicy> acknowledgementPolicy() {
        return Optional.ofNullable(acknowledgementPolicy);
    }

    /** @return READ_WRITE 属性采用的 retry 策略 */
    public @NotNull Optional<RetryPolicy> retryPolicy() {
        return Optional.ofNullable(retryPolicy);
    }

    /** @return 是否保存为 Telemetry 样本 */
    public boolean telemetry() {
        return telemetry;
    }

    /** 构造一个设备属性定义。 */
    public static class Builder {
        private final @NotNull String code;

        private final @NotNull ThingValueType valueType;

        private @NotNull ThingPropertyAccess access = ThingPropertyAccess.READ_ONLY;

        private @Nullable AcknowledgementPolicy acknowledgementPolicy;

        private @Nullable RetryPolicy retryPolicy;

        private boolean telemetry;

        private Builder(String code, ThingValueType valueType) {
            this.code = TextUtils.requireText(code, "属性 code");
            this.valueType = Objects.requireNonNull(valueType, "属性值类型不能为空");
        }

        /** @param value 读写方向 @return 当前 Builder */
        public @NotNull Builder access(@NotNull ThingPropertyAccess value) {
            this.access = Objects.requireNonNull(value, "属性访问方式不能为空");
            return this;
        }

        /** @param value 是否保存 Telemetry @return 当前 Builder */
        public @NotNull Builder telemetry(boolean value) {
            this.telemetry = value;
            return this;
        }

        /** @param acknowledgementPolicy ACK 策略 @param retryPolicy retry 策略 @return 当前 Builder */
        public @NotNull Builder desiredState(
                @NotNull AcknowledgementPolicy acknowledgementPolicy,
                @NotNull RetryPolicy retryPolicy) {
            this.acknowledgementPolicy = Objects.requireNonNull(
                    acknowledgementPolicy,
                    "ACK 策略 code 不能为空");
            this.retryPolicy = Objects.requireNonNull(retryPolicy, "retry 策略 code 不能为空");
            return this;
        }

        /** @return 已校验属性定义 */
        @CheckReturnValue
        public @NotNull ThingProperty build() {
            if (access == ThingPropertyAccess.READ_WRITE
                    && (acknowledgementPolicy == null || retryPolicy == null)) {
                throw new IllegalArgumentException("READ_WRITE 属性必须声明 ACK 和 retry 策略");
            }
            if (access == ThingPropertyAccess.READ_ONLY
                    && (acknowledgementPolicy != null || retryPolicy != null)) {
                throw new IllegalArgumentException("READ_ONLY 属性不能声明 desired 策略");
            }
            return new ThingProperty(this);
        }
    }
}
