package com.sca4d.iot.model;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.message.AcknowledgementPolicy;
import com.sca4d.iot.api.message.RetryPolicy;
import com.sca4d.iot.api.model.ThingCommand;
import com.sca4d.iot.api.model.ThingEvent;
import com.sca4d.iot.api.model.ThingEventDelivery;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingProperty;
import com.sca4d.iot.api.model.ThingPropertyAccess;
import com.sca4d.iot.api.model.ThingValueKind;
import com.sca4d.iot.api.model.ThingValueType;
import com.sca4d.iot.message.DevicePolicyCatalog;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 严格解析物模型 JSON，并把 Builder 对象编码成相同的规范内容。 */
public final class ThingModelJsonCodec {

    private static final Set<String> MODEL_FIELDS = Set.of(
            "formatVersion",
            "productCode",
            "version",
            "properties",
            "events",
            "commands");

    private static final Set<String> PROPERTY_FIELDS = Set.of(
            "code",
            "valueType",
            "access",
            "telemetry",
            "acknowledgementPolicy",
            "retryPolicy");

    private static final Set<String> EVENT_FIELDS = Set.of("code", "delivery", "outputs");

    private static final Set<String> COMMAND_FIELDS = Set.of(
            "code",
            "inputs",
            "acknowledgementPolicy",
            "retryPolicy");

    private static final Set<String> FIELD_FIELDS = Set.of("code", "valueType");

    private static final Set<String> TYPE_FIELDS = Set.of(
            "kind",
            "minimum",
            "maximum",
            "scale",
            "unit",
            "maximumLength",
            "pattern",
            "maximumItems",
            "enumValues",
            "requiredFields",
            "optionalFields",
            "elementType");

    private static final Set<String> ENUM_FIELDS = Set.of("code");

    private final @NotNull ObjectMapper objectMapper;

    private final @NotNull DevicePolicyCatalog policies;

    /** @param objectMapper App 统一 JSON 映射器 @param policies App 已登记的策略对象目录 */
    public ThingModelJsonCodec(
            @NotNull ObjectMapper objectMapper,
            @NotNull DevicePolicyCatalog policies) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "物模型 ObjectMapper 不能为空");
        this.policies = Objects.requireNonNull(policies, "设备策略目录不能为空");
    }

    /**
     * 解析严格物模型 JSON。
     *
     * @param json 完整 JSON 文本
     * @return 与静态 Builder 相同的不可变对象
     * @throws BusinessException JSON 语法、格式版本、字段类型或模型约束不合法时抛出
     */
    @CheckReturnValue
    public @NotNull ThingModel decode(@NotNull String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(Objects.requireNonNull(json, "物模型 JSON 不能为空"));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(IotErrorCode.THING_MODEL_JSON_INVALID, exception);
        }
        try {
            requireObject(root, "");
            rejectUnknown(root, MODEL_FIELDS, "");
            rejectNull(root, "");
            int formatVersion = requiredInt(root, "formatVersion", "");
            if (formatVersion != ThingModel.FORMAT_VERSION) {
                throw new BusinessException(
                        IotErrorCode.THING_MODEL_FORMAT_UNSUPPORTED,
                        null,
                        formatVersion);
            }
            ThingModel.Builder builder = ThingModel.builder(
                    requiredText(root, "productCode", ""),
                    requiredInt(root, "version", ""));
            List<JsonNode> properties = requiredArray(root, "properties", "");
            for (int index = 0; index < properties.size(); index++) {
                builder.property(property(properties.get(index), "/properties/" + index));
            }
            List<JsonNode> events = requiredArray(root, "events", "");
            for (int index = 0; index < events.size(); index++) {
                builder.event(event(events.get(index), "/events/" + index));
            }
            List<JsonNode> commands = requiredArray(root, "commands", "");
            for (int index = 0; index < commands.size(); index++) {
                builder.command(command(commands.get(index), "/commands/" + index));
            }
            return builder.build();
        } catch (IllegalArgumentException exception) {
            throw invalidField("/", exception);
        }
    }

    /**
     * 编码规范 JSON；同一物模型对象始终得到相同属性顺序和内容。
     *
     * @param model 物模型对象
     * @return 规范 JSON
     */
    @CheckReturnValue
    public @NotNull String encode(@NotNull ThingModel model) {
        ThingModel checked = Objects.requireNonNull(model, "物模型不能为空");
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("formatVersion", checked.formatVersion());
        root.put("productCode", checked.productCode());
        root.put("version", checked.version());
        root.put("properties", checked.properties().values().stream().map(this::propertyMap).toList());
        root.put("events", checked.events().values().stream().map(this::eventMap).toList());
        root.put("commands", checked.commands().values().stream().map(this::commandMap).toList());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("物模型无法编码", exception);
        }
    }

    /** 解析一个属性定义。 */
    private ThingProperty property(JsonNode node, String path) {
        requireObject(node, path);
        rejectUnknown(node, PROPERTY_FIELDS, path);
        rejectNull(node, path);
        ThingPropertyAccess access;
        try {
            access = ThingPropertyAccess.valueOf(requiredText(node, "access", path));
        } catch (IllegalArgumentException exception) {
            throw invalidField(path(path, "access"), exception);
        }
        boolean telemetry = false;
        if (node.has("telemetry")) {
            JsonNode value = required(node, "telemetry", path);
            if (!value.isBoolean()) {
                throw invalidField(path(path, "telemetry"), null);
            }
            telemetry = value.booleanValue();
        }
        ThingProperty.Builder builder = ThingProperty
                .builder(
                        requiredText(node, "code", path),
                        valueType(
                                required(node, "valueType", path),
                                path(path, "valueType")))
                .access(access)
                .telemetry(telemetry);
        if (access == ThingPropertyAccess.READ_WRITE) {
            builder.desiredState(
                    acknowledgementPolicy(node, path),
                    retryPolicy(node, path));
        } else if (node.has("acknowledgementPolicy") || node.has("retryPolicy")) {
            String field = node.has("acknowledgementPolicy")
                    ? "acknowledgementPolicy"
                    : "retryPolicy";
            throw invalidField(path(path, field), null);
        }
        try {
            return builder.build();
        } catch (IllegalArgumentException exception) {
            throw invalidField(path, exception);
        }
    }

    /** 解析一个事件定义。 */
    private ThingEvent event(JsonNode node, String path) {
        requireObject(node, path);
        rejectUnknown(node, EVENT_FIELDS, path);
        rejectNull(node, path);
        ThingEventDelivery delivery;
        try {
            delivery = ThingEventDelivery.valueOf(requiredText(node, "delivery", path));
        } catch (IllegalArgumentException exception) {
            throw invalidField(path(path, "delivery"), exception);
        }
        ThingEvent.Builder builder = ThingEvent.builder(requiredText(node, "code", path))
                .delivery(delivery);
        List<JsonNode> outputs = requiredArray(node, "outputs", path);
        for (int index = 0; index < outputs.size(); index++) {
            field(outputs.get(index), builder::output, path(path, "outputs") + "/" + index);
        }
        try {
            return builder.build();
        } catch (IllegalArgumentException exception) {
            throw invalidField(path, exception);
        }
    }

    /** 解析一个命令定义。 */
    private ThingCommand command(JsonNode node, String path) {
        requireObject(node, path);
        rejectUnknown(node, COMMAND_FIELDS, path);
        rejectNull(node, path);
        ThingCommand.Builder builder = ThingCommand.builder(requiredText(node, "code", path))
                .acknowledgementPolicy(acknowledgementPolicy(node, path))
                .retryPolicy(retryPolicy(node, path));
        List<JsonNode> inputs = requiredArray(node, "inputs", path);
        for (int index = 0; index < inputs.size(); index++) {
            field(inputs.get(index), builder::input, path(path, "inputs") + "/" + index);
        }
        try {
            return builder.build();
        } catch (IllegalArgumentException exception) {
            throw invalidField(path, exception);
        }
    }

    /** 解析事件输出或命令输入的 code 和递归值类型。 */
    private void field(JsonNode node, FieldConsumer consumer, String path) {
        requireObject(node, path);
        rejectUnknown(node, FIELD_FIELDS, path);
        rejectNull(node, path);
        try {
            consumer.accept(
                    requiredText(node, "code", path),
                    valueType(required(node, "valueType", path), path(path, "valueType")));
        } catch (IllegalArgumentException exception) {
            throw invalidField(path, exception);
        }
    }

    /** 递归解析一个值类型。 */
    private ThingValueType valueType(JsonNode node, String path) {
        requireObject(node, path);
        rejectUnknown(node, TYPE_FIELDS, path);
        rejectNull(node, path);
        ThingValueKind kind;
        try {
            kind = ThingValueKind.valueOf(requiredText(node, "kind", path));
        } catch (IllegalArgumentException exception) {
            throw invalidField(path(path, "kind"), exception);
        }
        if (kind == ThingValueKind.BOOLEAN) {
            rejectUnknown(node, Set.of("kind"), path);
            return ThingValueType.booleanType();
        }
        if (kind == ThingValueKind.TIMESTAMP) {
            rejectUnknown(node, Set.of("kind"), path);
            return ThingValueType.timestampType();
        }
        ThingValueType.Builder builder = switch (kind) {
            case INTEGER -> ThingValueType.integerType();
            case DECIMAL -> ThingValueType.decimalType();
            case STRING -> ThingValueType.stringType();
            case NUM -> ThingValueType.enumType();
            case OBJECT -> ThingValueType.objectType();
            case ARRAY -> ThingValueType.arrayType(
                    valueType(
                            required(node, "elementType", path),
                            path(path, "elementType")));
            default -> throw invalidField(path(path, "kind"), null);
        };
        if (node.has("minimum")) {
            builder.minimum(requiredText(node, "minimum", path));
        }
        if (node.has("maximum")) {
            builder.maximum(requiredText(node, "maximum", path));
        }
        if (node.has("scale")) {
            builder.scale(requiredInt(node, "scale", path));
        }
        if (node.has("unit")) {
            builder.unit(requiredText(node, "unit", path));
        }
        if (node.has("maximumLength")) {
            builder.maximumLength(requiredInt(node, "maximumLength", path));
        }
        if (node.has("pattern")) {
            builder.pattern(requiredText(node, "pattern", path));
        }
        if (node.has("maximumItems")) {
            builder.maximumItems(requiredInt(node, "maximumItems", path));
        }
        if (node.has("enumValues")) {
            List<JsonNode> enumValues = requiredArray(node, "enumValues", path);
            for (int index = 0; index < enumValues.size(); index++) {
                JsonNode value = enumValues.get(index);
                String valuePath = path(path, "enumValues") + "/" + index;
                requireObject(value, valuePath);
                rejectUnknown(value, ENUM_FIELDS, valuePath);
                rejectNull(value, valuePath);
                builder.value(requiredText(value, "code", valuePath));
            }
        }
        addObjectFields(node, "requiredFields", builder, true, path);
        addObjectFields(node, "optionalFields", builder, false, path);
        try {
            return builder.build();
        } catch (IllegalArgumentException exception) {
            throw invalidField(path, exception);
        }
    }

    /** 解析 object 的必需或可选字段集合。 */
    private void addObjectFields(
            JsonNode node,
            String fieldName,
            ThingValueType.Builder builder,
            boolean required,
            String path) {
        if (!node.has(fieldName)) {
            return;
        }
        List<JsonNode> fields = requiredArray(node, fieldName, path);
        for (int index = 0; index < fields.size(); index++) {
            JsonNode field = fields.get(index);
            String fieldPath = path(path, fieldName) + "/" + index;
            requireObject(field, fieldPath);
            rejectUnknown(field, FIELD_FIELDS, fieldPath);
            rejectNull(field, fieldPath);
            String code = requiredText(field, "code", fieldPath);
            ThingValueType fieldType = valueType(
                    required(field, "valueType", fieldPath),
                    path(fieldPath, "valueType"));
            if (required) {
                try {
                    builder.requiredField(code, fieldType);
                } catch (IllegalArgumentException exception) {
                    throw invalidField(fieldPath, exception);
                }
            } else {
                try {
                    builder.optionalField(code, fieldType);
                } catch (IllegalArgumentException exception) {
                    throw invalidField(fieldPath, exception);
                }
            }
        }
    }

    /** 把属性编码成稳定字段顺序的 Map。 */
    private Map<String, Object> propertyMap(ThingProperty property) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("code", property.code());
        value.put("valueType", typeMap(property.valueType()));
        value.put("access", property.access().name());
        value.put("telemetry", property.telemetry());
        property.acknowledgementPolicy()
                .ifPresent(policy -> value.put("acknowledgementPolicy", policy.code().format(COLON)));
        property.retryPolicy().ifPresent(policy -> value.put("retryPolicy", policy.code().format(COLON)));
        return value;
    }

    /** 把事件编码成稳定字段顺序的 Map。 */
    private Map<String, Object> eventMap(ThingEvent event) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("code", event.code());
        value.put("delivery", event.delivery().name());
        value.put("outputs", fields(event.outputs()));
        return value;
    }

    /** 把命令编码成稳定字段顺序的 Map。 */
    private Map<String, Object> commandMap(ThingCommand command) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("code", command.code());
        value.put("inputs", fields(command.inputs()));
        value.put("acknowledgementPolicy", command.acknowledgementPolicy().code().format(COLON));
        value.put("retryPolicy", command.retryPolicy().code().format(COLON));
        return value;
    }

    /** 把字段映射编码成有序数组。 */
    private List<Map<String, Object>> fields(Map<String, ThingValueType> fields) {
        return fields.entrySet()
                .stream()
                .map(entry -> fieldMap(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** 把一个具名字段编码成稳定字段顺序的 Map。 */
    private Map<String, Object> fieldMap(String code, ThingValueType type) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("code", code);
        value.put("valueType", typeMap(type));
        return value;
    }

    /** 递归编码一个值类型。 */
    private Map<String, Object> typeMap(ThingValueType type) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("kind", type.kind().name());
        type.minimum().ifPresent(item -> value.put("minimum", item));
        type.maximum().ifPresent(item -> value.put("maximum", item));
        type.scale().ifPresent(item -> value.put("scale", item));
        type.unit().ifPresent(item -> value.put("unit", item));
        type.maximumLength().ifPresent(item -> value.put("maximumLength", item));
        type.pattern().ifPresent(item -> value.put("pattern", item));
        type.maximumItems().ifPresent(item -> value.put("maximumItems", item));
        if (!type.enumValues().isEmpty()) {
            value.put(
                    "enumValues",
                    type.enumValues().stream().map(ThingModelJsonCodec::enumValueMap).toList());
        }
        if (!type.requiredFields().isEmpty()) {
            value.put("requiredFields", fields(type.requiredFields()));
        }
        if (!type.optionalFields().isEmpty()) {
            value.put("optionalFields", fields(type.optionalFields()));
        }
        type.elementType().ifPresent(item -> value.put("elementType", typeMap(item)));
        return value;
    }

    /** 把一个 enum 值编码成稳定字段顺序的 Map。 */
    private static Map<String, Object> enumValueMap(String code) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("code", code);
        return value;
    }

    /** 读取并解析 ACK 策略 code，未知 code 关联到对应 JSON 字段。 */
    private AcknowledgementPolicy acknowledgementPolicy(JsonNode node, String path) {
        String fieldPath = path(path, "acknowledgementPolicy");
        try {
            return policies.acknowledgement(
                    requiredText(node, "acknowledgementPolicy", path));
        } catch (IllegalArgumentException exception) {
            throw invalidField(fieldPath, exception);
        }
    }

    /** 读取并解析 retry 策略 code，未知 code 关联到对应 JSON 字段。 */
    private RetryPolicy retryPolicy(JsonNode node, String path) {
        String fieldPath = path(path, "retryPolicy");
        try {
            return policies.retry(requiredText(node, "retryPolicy", path));
        } catch (IllegalArgumentException exception) {
            throw invalidField(fieldPath, exception);
        }
    }

    /** 返回必需字段并拒绝缺失。 */
    private static JsonNode required(JsonNode node, String field, String path) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw invalidField(path(path, field), null);
        }
        return value;
    }

    /** 返回必需文本字段。 */
    private static String requiredText(JsonNode node, String field, String path) {
        JsonNode value = required(node, field, path);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw invalidField(path(path, field), null);
        }
        return value.textValue();
    }

    /** 返回必需整数字段。 */
    private static int requiredInt(JsonNode node, String field, String path) {
        JsonNode value = required(node, field, path);
        if (!value.canConvertToInt() || !value.isIntegralNumber()) {
            throw invalidField(path(path, field), null);
        }
        return value.intValue();
    }

    /** 返回必需数组字段的快照。 */
    private static List<JsonNode> requiredArray(JsonNode node, String field, String path) {
        JsonNode value = required(node, field, path);
        if (!value.isArray()) {
            throw invalidField(path(path, field), null);
        }
        List<JsonNode> result = new ArrayList<>();
        value.forEach(result::add);
        return List.copyOf(result);
    }

    /** 要求当前节点是 JSON object。 */
    private static void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw invalidField(path.isEmpty() ? "/" : path, null);
        }
    }

    /** 拒绝当前 object 的未知字段。 */
    private static void rejectUnknown(JsonNode node, Set<String> allowed, String path) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw invalidField(path(path, field), null);
            }
        });
    }

    /** 拒绝当前 object 直接包含显式 null。 */
    private static void rejectNull(JsonNode node, String path) {
        node.fieldNames().forEachRemaining(field -> {
            if (node.get(field).isNull()) {
                throw invalidField(path(path, field), null);
            }
        });
    }

    /** 把字段名追加到 RFC 6901 JSON Pointer。 */
    private static @NotNull String path(@NotNull String parent, @NotNull String field) {
        return parent + "/" + field.replace("~", "~0").replace("/", "~1");
    }

    /** 创建携带稳定错误码和字段路径的业务异常。 */
    private static @NotNull BusinessException invalidField(
            @NotNull String path,
            @Nullable Throwable cause) {
        return new BusinessException(
                IotErrorCode.THING_MODEL_FIELD_INVALID,
                cause,
                path);
    }

    /** 接收一个 code 和 ThingValueType。 */
    @FunctionalInterface
    private interface FieldConsumer {
        /** @param code 字段 code @param type 字段类型 */
        void accept(String code, ThingValueType type);
    }
}
