package com.sca4d.iot.api.model;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 定义一个稳定 code 的设备离散事件及其输出字段。 */
public final class ThingEvent {

    private final @NotNull String code;

    private final @NotNull Map<String, ThingValueType> outputs;

    private final @NotNull ThingEventDelivery delivery;

    private ThingEvent(@NotNull Builder builder) {
        this.code = builder.code;
        this.outputs = Collections.unmodifiableMap(new LinkedHashMap<>(builder.outputs));
        this.delivery = builder.delivery;
    }

    /** @param code 事件稳定 code @return 事件 Builder */
    @CheckReturnValue
    public static @NotNull Builder builder(@NotNull String code) {
        return new Builder(code);
    }

    /** @return 事件稳定 code */
    public @NotNull String code() {
        return code;
    }

    /** @return 不可修改的输出字段 */
    public @NotNull @Unmodifiable Map<String, ThingValueType> outputs() {
        return outputs;
    }

    /** @return 事件使用提交后发布还是 Outbox 可靠发布 */
    public @NotNull ThingEventDelivery delivery() {
        return delivery;
    }

    /** 构造事件输出定义。 */
    public static final class Builder {
        private final @NotNull String code;

        private final @NotNull Map<String, ThingValueType> outputs = new LinkedHashMap<>();

        private ThingEventDelivery delivery;

        private Builder(String code) {
            this.code = TextUtils.requireText(code, "事件 code");
        }

        /** @param fieldCode 输出字段 code @param type 输出值类型 @return 当前 Builder */
        public @NotNull Builder output(@NotNull String fieldCode, @NotNull ThingValueType type) {
            String checked = TextUtils.requireText(fieldCode, "事件输出 code");
            if (outputs.putIfAbsent(checked, Objects.requireNonNull(type, "事件输出类型不能为空")) != null) {
                throw new IllegalArgumentException("事件输出 code 重复: " + checked);
            }
            return this;
        }

        /** @param value 事件消息的发布方式 @return 当前 Builder */
        public @NotNull Builder delivery(@NotNull ThingEventDelivery value) {
            this.delivery = Objects.requireNonNull(value, "ThingEvent 发布方式不能为空");
            return this;
        }

        /** @return 不可修改事件定义 */
        @CheckReturnValue
        public @NotNull ThingEvent build() {
            if (delivery == null) {
                throw new IllegalArgumentException("ThingEvent 必须显式声明发布方式");
            }
            return new ThingEvent(this);
        }
    }
}
