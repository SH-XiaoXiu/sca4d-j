package com.sca4d.iot.api.model;

import com.sca4d.core.api.data.DataKey;
import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iot.api.message.AcknowledgementPolicy;
import com.sca4d.iot.api.message.RetryPolicy;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 定义一个稳定 code 的设备命令、输入和业务确认策略。 */
public class ThingCommand {

    private final @NotNull String code;

    private final @NotNull Map<String, ThingValueType> inputs;

    private final @NotNull Map<String, DataKey<?>> inputKeys;

    private final @NotNull AcknowledgementPolicy acknowledgementPolicy;

    private final @NotNull RetryPolicy retryPolicy;

    private ThingCommand(@NotNull Builder builder) {
        this.code = builder.code;
        this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(builder.inputs));
        this.inputKeys = Collections.unmodifiableMap(new LinkedHashMap<>(builder.inputKeys));
        this.acknowledgementPolicy = Objects.requireNonNull(builder.acknowledgementPolicy);
        this.retryPolicy = Objects.requireNonNull(builder.retryPolicy);
    }

    /** @param code 命令稳定 code @return 命令 Builder */
    @CheckReturnValue
    public static @NotNull Builder builder(@NotNull String code) {
        return new Builder(code);
    }

    /** @return 命令稳定 code */
    public @NotNull String code() {
        return code;
    }

    /** @return 不可修改输入字段 */
    public @NotNull @Unmodifiable Map<String, ThingValueType> inputs() {
        return inputs;
    }

    /** @return 以输入字段 code 索引的不可修改类型化键 */
    @Contract(pure = true)
    public @NotNull @Unmodifiable Map<String, DataKey<?>> inputKeys() {
        return inputKeys;
    }

    /** @return ACK 策略 code */
    public @NotNull AcknowledgementPolicy acknowledgementPolicy() {
        return acknowledgementPolicy;
    }

    /** @return retry 策略 code */
    public @NotNull RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    /** 构造一个命令定义。 */
    public static class Builder {
        private final @NotNull String code;

        private final @NotNull Map<String, ThingValueType> inputs = new LinkedHashMap<>();

        private final @NotNull Map<String, DataKey<?>> inputKeys = new LinkedHashMap<>();

        private @Nullable AcknowledgementPolicy acknowledgementPolicy;

        private @Nullable RetryPolicy retryPolicy;

        private Builder(String code) {
            this.code = TextUtils.requireText(code, "命令 code");
        }

        /** @param fieldCode 输入字段 code @param type 输入值类型 @return 当前 Builder */
        public @NotNull Builder input(@NotNull String fieldCode, @NotNull ThingValueType type) {
            String checked = TextUtils.requireText(fieldCode, "命令输入 code");
            ThingValueType checkedType = Objects.requireNonNull(type, "命令输入类型不能为空");
            return input(DataKey.of(checked, checkedType.kind().javaType()), checkedType);
        }

        /**
         * 使用类型化键声明一个命令输入。
         *
         * @param key 输入字段 code 和 Java 类型
         * @param type 输入值的物模型约束
         * @param <T> 输入字段的 Java 类型
         * @return 当前 Builder
         */
        public <T> @NotNull Builder input(
                @NotNull DataKey<T> key,
                @NotNull ThingValueType type) {
            DataKey<T> checkedKey = Objects.requireNonNull(key, "命令输入 DataKey 不能为空");
            ThingValueType checkedType = Objects.requireNonNull(type, "命令输入类型不能为空");
            if (!checkedKey.type().equals(checkedType.kind().javaType())) {
                throw new IllegalArgumentException("命令输入 DataKey Java 类型与物模型不一致: " + checkedKey.name());
            }
            if (inputs.putIfAbsent(checkedKey.name(), checkedType) != null) {
                throw new IllegalArgumentException("命令输入 code 重复: " + checkedKey.name());
            }
            inputKeys.put(checkedKey.name(), checkedKey);
            return this;
        }

        /** @param policy ACK 策略 @return 当前 Builder */
        public @NotNull Builder acknowledgementPolicy(@NotNull AcknowledgementPolicy policy) {
            this.acknowledgementPolicy = Objects.requireNonNull(policy, "ACK 策略不能为空");
            return this;
        }

        /** @param policy retry 策略 @return 当前 Builder */
        public @NotNull Builder retryPolicy(@NotNull RetryPolicy policy) {
            this.retryPolicy = Objects.requireNonNull(policy, "retry 策略不能为空");
            return this;
        }

        /** @return 已校验命令定义 */
        @CheckReturnValue
        public @NotNull ThingCommand build() {
            if (acknowledgementPolicy == null || retryPolicy == null) {
                throw new IllegalArgumentException("命令必须声明 ACK 和 retry 策略");
            }
            return new ThingCommand(this);
        }
    }
}
