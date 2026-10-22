package com.sca4d.app.server.iot.view;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.core.api.i18n.LocalizableText;
import com.sca4d.core.spring.web.view.BaseView;
import com.sca4d.core.spring.web.view.Localize;
import com.sca4d.iot.api.model.ThingCommand;
import com.sca4d.iot.api.model.ThingEvent;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingModelPresentation;
import com.sca4d.iot.api.model.ThingProperty;
import com.sca4d.iot.api.model.ThingValueType;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 返回 ThingModel 技术定义和当前请求 Locale 对应的全部嵌套展示名称。 */
@SuppressWarnings("unused")
public class IotThingModelView extends BaseView {

    private final int formatVersion;

    private final @NotNull String productCode;

    private final int version;

    @Localize(source = "displayNameText")
    private final @NotNull String displayName;

    private final @NotNull List<Command> commands;

    private final @NotNull List<Event> events;

    private final @NotNull List<Property> properties;

    private final @NotNull LocalizableText localizedDisplayName;

    /** 从展示结果构造一个已本地化的完整 ThingModel HTTP 投影。 */
    private IotThingModelView(@NotNull ThingModelPresentation presentation) {
        ThingModel model = presentation.model();
        this.formatVersion = model.formatVersion();
        this.productCode = model.productCode();
        this.version = model.version();
        this.displayName = model.productCode();
        this.localizedDisplayName = presentation.textAt("/displayName");
        this.properties = model.properties()
                .values()
                .stream()
                .map(
                        property -> new Property(
                                presentation,
                                "/properties/" + escape(property.code()),
                                property))
                .toList();
        this.events = model.events()
                .values()
                .stream()
                .map(
                        event -> new Event(
                                presentation,
                                "/events/" + escape(event.code()),
                                event))
                .toList();
        this.commands = model.commands()
                .values()
                .stream()
                .map(
                        command -> new Command(
                                presentation,
                                "/commands/" + escape(command.code()),
                                command))
                .toList();
    }

    /** @param presentation ThingModel 技术定义和展示文本 @return 本地化 HTTP 视图 */
    @CheckReturnValue
    public static @NotNull IotThingModelView from(
            @NotNull ThingModelPresentation presentation) {
        return new IotThingModelView(
                Objects.requireNonNull(presentation, "ThingModel 展示结果不能为空"));
    }

    /** @return ThingModel JSON 格式版本 */
    public int getFormatVersion() {
        return formatVersion;
    }

    /** @return 所属 Product code */
    public @NotNull String getProductCode() {
        return productCode;
    }

    /** @return Product 下连续递增的版本 */
    public int getVersion() {
        return version;
    }

    /** @return 当前请求 Locale 的 ThingModel 展示名称 */
    public @NotNull String getDisplayName() {
        return displayName;
    }

    /** @return 按定义顺序排列的属性 */
    public @NotNull @Unmodifiable List<Property> getProperties() {
        return List.copyOf(properties);
    }

    /** @return 按定义顺序排列的事件 */
    public @NotNull @Unmodifiable List<Event> getEvents() {
        return List.copyOf(events);
    }

    /** @return 按定义顺序排列的命令 */
    public @NotNull @Unmodifiable List<Command> getCommands() {
        return List.copyOf(commands);
    }

    /** @return ThingModel 根展示名称的本地化来源 */
    @SuppressWarnings("ProtectedMemberInFinalClass")
    protected @NotNull LocalizableText displayNameText() {
        return localizedDisplayName;
    }

    /** 返回一个 ThingModel 属性及当前请求 Locale 的展示名称。 */
    public static class Property extends BaseView {

        private final @NotNull LocalizableText localizedDisplayName;

        private final @NotNull String access;

        private final @Nullable String acknowledgementPolicy;

        private final @NotNull String code;

        @Localize(source = "displayNameText")
        private final @NotNull String displayName;

        private final @Nullable String retryPolicy;

        private final @NotNull ValueType valueType;

        private final boolean telemetry;

        /** 从一个属性定义构造本地化 HTTP 投影。 */
        private Property(
                @NotNull ThingModelPresentation presentation,
                @NotNull String path,
                @NotNull ThingProperty property) {
            this.code = property.code();
            this.displayName = property.code();
            this.localizedDisplayName = presentation.textAt(path + "/displayName");
            this.valueType = new ValueType(presentation, path + "/valueType", property.valueType());
            this.access = property.access().name();
            this.telemetry = property.telemetry();
            this.acknowledgementPolicy = property.acknowledgementPolicy()
                    .map(policy -> policy.code().format(COLON))
                    .orElse(null);
            this.retryPolicy = property.retryPolicy().map(policy -> policy.code().format(COLON)).orElse(null);
        }

        /** @return 属性稳定 code */
        public @NotNull String getCode() {
            return code;
        }

        /** @return 当前请求 Locale 的属性名称 */
        public @NotNull String getDisplayName() {
            return displayName;
        }

        /** @return 属性递归值类型 */
        public @NotNull ValueType getValueType() {
            return valueType;
        }

        /** @return 属性读写方向 */
        public @NotNull String getAccess() {
            return access;
        }

        /** @return 属性是否写入 Telemetry */
        public boolean isTelemetry() {
            return telemetry;
        }

        /** @return 可写属性 ACK 策略 code；只读属性为空 */
        public @Nullable String getAcknowledgementPolicy() {
            return acknowledgementPolicy;
        }

        /** @return 可写属性 retry 策略 code；只读属性为空 */
        public @Nullable String getRetryPolicy() {
            return retryPolicy;
        }

        /** @return 属性展示名称的本地化来源 */
        @SuppressWarnings("ProtectedMemberInFinalClass")
        protected @NotNull LocalizableText displayNameText() {
            return localizedDisplayName;
        }
    }

    /** 返回一个 ThingModel 事件及本地化输出字段。 */
    public static class Event extends BaseView {

        private final @NotNull List<Field> outputs;

        private final @NotNull LocalizableText localizedDisplayName;

        private final @NotNull String code;

        private final @NotNull String delivery;

        @Localize(source = "displayNameText")
        private final @NotNull String displayName;

        /** 从一个事件定义构造本地化 HTTP 投影。 */
        private Event(
                @NotNull ThingModelPresentation presentation,
                @NotNull String path,
                @NotNull ThingEvent event) {
            this.code = event.code();
            this.displayName = event.code();
            this.localizedDisplayName = presentation.textAt(path + "/displayName");
            this.delivery = event.delivery().name();
            this.outputs = fields(presentation, path + "/outputs", event.outputs());
        }

        /** @return 事件稳定 code */
        public @NotNull String getCode() {
            return code;
        }

        /** @return 当前请求 Locale 的事件名称 */
        public @NotNull String getDisplayName() {
            return displayName;
        }

        /** @return 事件发布方式 */
        public @NotNull String getDelivery() {
            return delivery;
        }

        /** @return 事件输出字段 */
        public @NotNull @Unmodifiable List<Field> getOutputs() {
            return List.copyOf(outputs);
        }

        /** @return 事件展示名称的本地化来源 */
        @SuppressWarnings("ProtectedMemberInFinalClass")
        protected @NotNull LocalizableText displayNameText() {
            return localizedDisplayName;
        }
    }

    /** 返回一个 ThingModel 命令及本地化输入字段。 */
    public static class Command extends BaseView {

        private final @NotNull List<Field> inputs;

        private final @NotNull LocalizableText localizedDisplayName;

        private final @NotNull String acknowledgementPolicy;

        private final @NotNull String code;

        @Localize(source = "displayNameText")
        private final @NotNull String displayName;

        private final @NotNull String retryPolicy;

        /** 从一个命令定义构造本地化 HTTP 投影。 */
        private Command(
                @NotNull ThingModelPresentation presentation,
                @NotNull String path,
                @NotNull ThingCommand command) {
            this.code = command.code();
            this.displayName = command.code();
            this.localizedDisplayName = presentation.textAt(path + "/displayName");
            this.inputs = fields(presentation, path + "/inputs", command.inputs());
            this.acknowledgementPolicy = command.acknowledgementPolicy().code().format(COLON);
            this.retryPolicy = command.retryPolicy().code().format(COLON);
        }

        /** @return 命令稳定 code */
        public @NotNull String getCode() {
            return code;
        }

        /** @return 当前请求 Locale 的命令名称 */
        public @NotNull String getDisplayName() {
            return displayName;
        }

        /** @return 命令输入字段 */
        public @NotNull @Unmodifiable List<Field> getInputs() {
            return List.copyOf(inputs);
        }

        /** @return 命令 ACK 策略 code */
        public @NotNull String getAcknowledgementPolicy() {
            return acknowledgementPolicy;
        }

        /** @return 命令 retry 策略 code */
        public @NotNull String getRetryPolicy() {
            return retryPolicy;
        }

        /** @return 命令展示名称的本地化来源 */
        @SuppressWarnings("ProtectedMemberInFinalClass")
        protected @NotNull LocalizableText displayNameText() {
            return localizedDisplayName;
        }
    }

    /** 返回事件输出、命令输入或 object 成员的本地化字段定义。 */
    public static class Field extends BaseView {

        private final @NotNull LocalizableText localizedDisplayName;

        private final @NotNull String code;

        @Localize(source = "displayNameText")
        private final @NotNull String displayName;

        private final @NotNull ValueType valueType;

        /** 从一个具名递归字段构造本地化 HTTP 投影。 */
        private Field(
                @NotNull ThingModelPresentation presentation,
                @NotNull String path,
                @NotNull String code,
                @NotNull ThingValueType valueType) {
            this.code = code;
            this.displayName = code;
            this.localizedDisplayName = presentation.textAt(path + "/displayName");
            this.valueType = new ValueType(presentation, path + "/valueType", valueType);
        }

        /** @return 字段稳定 code */
        public @NotNull String getCode() {
            return code;
        }

        /** @return 当前请求 Locale 的字段名称 */
        public @NotNull String getDisplayName() {
            return displayName;
        }

        /** @return 字段递归值类型 */
        public @NotNull ValueType getValueType() {
            return valueType;
        }

        /** @return 字段展示名称的本地化来源 */
        @SuppressWarnings("ProtectedMemberInFinalClass")
        protected @NotNull LocalizableText displayNameText() {
            return localizedDisplayName;
        }
    }

    /** 返回 enum code 及当前请求 Locale 的展示名称。 */
    public static class EnumValue extends BaseView {

        private final @NotNull LocalizableText localizedDisplayName;

        private final @NotNull String code;

        @Localize(source = "displayNameText")
        private final @NotNull String displayName;

        /** 从一个 enum code 构造本地化 HTTP 投影。 */
        private EnumValue(
                @NotNull ThingModelPresentation presentation,
                @NotNull String path,
                @NotNull String code) {
            this.code = code;
            this.displayName = code;
            this.localizedDisplayName = presentation.textAt(path + "/displayName");
        }

        /** @return enum 稳定 code */
        public @NotNull String getCode() {
            return code;
        }

        /** @return 当前请求 Locale 的 enum 名称 */
        public @NotNull String getDisplayName() {
            return displayName;
        }

        /** @return enum 展示名称的本地化来源 */
        @SuppressWarnings("ProtectedMemberInFinalClass")
        protected @NotNull LocalizableText displayNameText() {
            return localizedDisplayName;
        }
    }

    /** 返回 ThingModel 递归值类型及全部技术约束和本地化成员。 */
    public static class ValueType {

        private final @Nullable Integer maximumItems;

        private final @Nullable Integer maximumLength;

        private final @Nullable Integer scale;

        private final @NotNull List<EnumValue> enumValues;

        private final @NotNull List<Field> optionalFields;

        private final @NotNull List<Field> requiredFields;

        private final @NotNull String kind;

        private final @Nullable String maximum;

        private final @Nullable String minimum;

        private final @Nullable String pattern;

        private final @Nullable String unit;

        private final @Nullable ValueType elementType;

        /** 从递归值类型构造技术约束和嵌套展示字段。 */
        private ValueType(
                @NotNull ThingModelPresentation presentation,
                @NotNull String path,
                @NotNull ThingValueType type) {
            this.kind = type.kind().name();
            this.minimum = type.minimum().orElse(null);
            this.maximum = type.maximum().orElse(null);
            this.scale = type.scale().orElse(null);
            this.unit = type.unit().orElse(null);
            this.maximumLength = type.maximumLength().orElse(null);
            this.pattern = type.pattern().orElse(null);
            this.maximumItems = type.maximumItems().orElse(null);
            this.enumValues = type.enumValues()
                    .stream()
                    .map(
                            code -> new EnumValue(
                                    presentation,
                                    path + "/enumValues/" + escape(code),
                                    code))
                    .toList();
            this.requiredFields = fields(
                    presentation,
                    path + "/requiredFields",
                    type.requiredFields());
            this.optionalFields = fields(
                    presentation,
                    path + "/optionalFields",
                    type.optionalFields());
            this.elementType = type.elementType()
                    .map(
                            element -> new ValueType(
                                    presentation,
                                    path + "/elementType",
                                    element))
                    .orElse(null);
        }

        /** @return 固定值类型种类 */
        public @NotNull String getKind() {
            return kind;
        }

        /** @return 可选最小值十进制文本 */
        public @Nullable String getMinimum() {
            return minimum;
        }

        /** @return 可选最大值十进制文本 */
        public @Nullable String getMaximum() {
            return maximum;
        }

        /** @return 可选 decimal 小数位数 */
        public @Nullable Integer getScale() {
            return scale;
        }

        /** @return 可选数值单位 code */
        public @Nullable String getUnit() {
            return unit;
        }

        /** @return 可选字符串最大长度 */
        public @Nullable Integer getMaximumLength() {
            return maximumLength;
        }

        /** @return 可选 Java 正则表达式；字符串必须完整匹配 */
        public @Nullable String getPattern() {
            return pattern;
        }

        /** @return 可选数组最大元素数 */
        public @Nullable Integer getMaximumItems() {
            return maximumItems;
        }

        /** @return enum 值及本地化名称 */
        public @NotNull @Unmodifiable List<EnumValue> getEnumValues() {
            return List.copyOf(enumValues);
        }

        /** @return object 必需字段 */
        public @NotNull @Unmodifiable List<Field> getRequiredFields() {
            return List.copyOf(requiredFields);
        }

        /** @return object 可选字段 */
        public @NotNull @Unmodifiable List<Field> getOptionalFields() {
            return List.copyOf(optionalFields);
        }

        /** @return array 元素类型；非 array 类型为空 */
        public @Nullable ValueType getElementType() {
            return elementType;
        }
    }

    /** 把具名字段 Map 转成保持定义顺序的本地化视图列表。 */
    private static @NotNull List<Field> fields(
            @NotNull ThingModelPresentation presentation,
            @NotNull String path,
            @NotNull Map<String, ThingValueType> fields) {
        return fields.entrySet()
                .stream()
                .map(
                        entry -> new Field(
                                presentation,
                                path + "/" + escape(entry.getKey()),
                                entry.getKey(),
                                entry.getValue()))
                .toList();
    }

    /** 按 RFC 6901 转义路径片段。 */
    private static @NotNull String escape(@NotNull String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
