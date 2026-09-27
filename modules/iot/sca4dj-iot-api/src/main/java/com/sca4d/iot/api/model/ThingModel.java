package com.sca4d.iot.api.model;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 保存 Product 下一个不可修改且已经发布的物模型版本。 */
public final class ThingModel {

    /** 当前 JSON 格式版本。 */
    public static final int FORMAT_VERSION = 1;

    private final @NotNull String productCode;

    private final int version;

    private final @NotNull Map<String, ThingProperty> properties;

    private final @NotNull Map<String, ThingEvent> events;

    private final @NotNull Map<String, ThingCommand> commands;

    private ThingModel(@NotNull Builder builder) {
        this.productCode = builder.productCode;
        this.version = builder.version;
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(builder.properties));
        this.events = Collections.unmodifiableMap(new LinkedHashMap<>(builder.events));
        this.commands = Collections.unmodifiableMap(new LinkedHashMap<>(builder.commands));
    }

    /** @param productCode 所属 Product code @param version 从 1 开始的版本 @return 物模型 Builder */
    @CheckReturnValue
    public static @NotNull Builder builder(@NotNull String productCode, int version) {
        return new Builder(productCode, version);
    }

    /** @return 固定 JSON 格式版本 */
    public int formatVersion() {
        return FORMAT_VERSION;
    }

    /** @return 所属 Product code */
    public @NotNull String productCode() {
        return productCode;
    }

    /** @return Product 下连续递增的版本 */
    public int version() {
        return version;
    }

    /** @return code 索引的不可修改属性 */
    public @NotNull @Unmodifiable Map<String, ThingProperty> properties() {
        return properties;
    }

    /** @return code 索引的不可修改事件 */
    public @NotNull @Unmodifiable Map<String, ThingEvent> events() {
        return events;
    }

    /** @return code 索引的不可修改命令 */
    public @NotNull @Unmodifiable Map<String, ThingCommand> commands() {
        return commands;
    }

    /** @param code 属性 code @return 匹配属性 */
    public @NotNull Optional<ThingProperty> property(@NotNull String code) {
        return Optional.ofNullable(properties.get(code));
    }

    /** @param code 命令 code @return 匹配命令 */
    public @NotNull Optional<ThingCommand> command(@NotNull String code) {
        return Optional.ofNullable(commands.get(code));
    }

    /** 构造一个完整物模型版本。 */
    public static final class Builder {
        private final @NotNull String productCode;

        private final int version;

        private final @NotNull Map<String, ThingProperty> properties = new LinkedHashMap<>();

        private final @NotNull Map<String, ThingEvent> events = new LinkedHashMap<>();

        private final @NotNull Map<String, ThingCommand> commands = new LinkedHashMap<>();

        private Builder(String productCode, int version) {
            this.productCode = TextUtils.requireText(productCode, "Product code");
            if (version < 1) {
                throw new IllegalArgumentException("物模型版本必须从 1 开始");
            }
            this.version = version;
        }

        /** @param value 属性定义 @return 当前 Builder */
        public @NotNull Builder property(@NotNull ThingProperty value) {
            put(properties, value.code(), value, "属性");
            return this;
        }

        /** @param value 事件定义 @return 当前 Builder */
        public @NotNull Builder event(@NotNull ThingEvent value) {
            put(events, value.code(), value, "事件");
            return this;
        }

        /** @param value 命令定义 @return 当前 Builder */
        public @NotNull Builder command(@NotNull ThingCommand value) {
            put(commands, value.code(), value, "命令");
            return this;
        }

        /** @return 不可修改物模型 */
        @CheckReturnValue
        public @NotNull ThingModel build() {
            return new ThingModel(this);
        }

        /** 按 code 加入一种物模型成员，并拒绝同类成员重复使用该 code。 */
        private static <T> void put(Map<String, T> values, String code, T value, String name) {
            if (values.putIfAbsent(code, value) != null) {
                throw new IllegalArgumentException(name + " code 重复: " + code);
            }
        }
    }
}
