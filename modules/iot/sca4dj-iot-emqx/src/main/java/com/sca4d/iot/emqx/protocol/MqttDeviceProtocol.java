package com.sca4d.iot.emqx.protocol;

import com.sca4d.core.api.data.DataKey;
import com.sca4d.core.api.data.DataValues;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.message.DeviceUplink;
import com.sca4d.iot.api.model.ThingCommand;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingProperty;
import com.sca4d.iot.api.model.ThingValueType;
import com.sca4d.iot.api.protocol.DeviceProtocolMessage;
import com.sca4d.iot.api.protocol.DeviceUplinkProtocol;
import com.sca4d.iot.api.state.ReportedMergeMode;
import com.sca4d.iot.emqx.transport.MqttMessageRejectedException;
import com.sca4d.iot.emqx.transport.MqttMessageRejectionReason;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 定义 MQTT Topic 路由、Core Pipeline 和物模型字段映射。 */
public final class MqttDeviceProtocol implements DeviceUplinkProtocol {

    private static final MqttTopicIdentityMapper DIRECT_TOPIC_IDENTITY = new MqttTopicIdentityMapper() {
        @Override
        public @NotNull String topicIdentity(@NotNull String clientId) {
            return TextUtils.requireText(clientId, "MQTT Client ID");
        }

        @Override
        public @NotNull String clientId(@NotNull String topicIdentity) {
            return TextUtils.requireText(topicIdentity, "MQTT Topic 设备身份");
        }
    };

    private final @NotNull String code;

    private final @NotNull Map<String, MqttUplinkDefinition> uplinks;

    private final @NotNull Map<String, MqttUplinkRoute> uplinkRoutes;

    private final @NotNull Map<String, DownlinkDefinition> downlinks;

    private final @NotNull MqttTopicIdentityMapper topicIdentityMapper;

    private MqttDeviceProtocol(@NotNull Builder builder) {
        this.code = builder.code;
        this.uplinks = Map.copyOf(builder.uplinks);
        this.uplinkRoutes = Map.copyOf(builder.uplinkRoutes);
        this.downlinks = Map.copyOf(builder.downlinks);
        this.topicIdentityMapper = builder.topicIdentityMapper;
    }

    /**
     * 创建 MQTT 设备协议构建器。
     *
     * @param code 具体应用内唯一的协议 code
     * @return 空协议构建器
     */
    @CheckReturnValue
    public static @NotNull Builder builder(@NotNull String code) {
        return new Builder(code);
    }

    @Override
    public @NotNull String code() {
        return code;
    }

    /**
     * 验证全部 DataKey 名称和 Java 类型与物模型一致。
     *
     * @param model 协议绑定的物模型版本
     */
    @Override
    public void validate(@NotNull ThingModel model) {
        ThingModel checked = Objects.requireNonNull(model, "ThingModel 不能为空");
        uplinks.values()
                .stream()
                .filter(definition -> definition.kind() == MqttUplinkKind.REPORTED)
                .forEach(
                        definition -> definition.keys()
                                .forEach(key -> validatePropertyKey(checked, key)));
        downlinks.values().forEach(definition -> {
            MqttDownlinkRoute route = definition.route;
            if (route.kind() == DeviceDownlinkKind.DESIRED) {
                ThingProperty property = checked.properties().get(route.targetCode());
                if (property == null || definition.keys.size() != 1) {
                    throw new IllegalArgumentException("desired route 未匹配物模型属性");
                }
                validateType(definition.keys.getFirst(), property.valueType());
                return;
            }
            ThingCommand command = checked.commands().get(route.targetCode());
            if (command == null || command.inputs().size() != definition.keys.size()) {
                throw new IllegalArgumentException("command route 未匹配物模型 command");
            }
            definition.keys.forEach(key -> {
                ThingValueType type = command.inputs().get(key.name());
                if (type == null) {
                    throw new IllegalArgumentException("command input code 未登记: " + key.name());
                }
                validateType(key, type);
            });
        });
    }

    /**
     * 返回协议订阅的全部上行 Topic Filter。
     *
     * @return 协议声明顺序不作为处理顺序保证
     */
    public @NotNull List<String> uplinkTopicFilters() {
        return List.copyOf(uplinks.keySet());
    }

    /** @return 用于生成标准 MQTT 消息路由的全部上行路由 */
    public @NotNull List<MqttUplinkRoute> uplinkRoutes() {
        return List.copyOf(uplinkRoutes.values());
    }

    /**
     * 把协议 Topic Filter 中的 Device 层替换为指定 clientId。
     *
     * @param clientId Device MQTT clientId
     * @return 该 Device 可以发布的全部上行 Topic Filter
     */
    public @NotNull List<String> deviceUplinkTopicFilters(@NotNull String clientId) {
        String topicIdentity = topicIdentityMapper.topicIdentity(clientId);
        return uplinks.keySet()
                .stream()
                .map(filter -> uplinkRoute(filter).deviceTopicFilter(topicIdentity))
                .toList();
    }

    /**
     * 生成指定 Device 的全部精确下行 Topic。
     *
     * @param clientId Device MQTT clientId
     * @return 不包含通配符的 Topic
     */
    public @NotNull List<String> deviceDownlinkTopics(@NotNull String clientId) {
        String topicIdentity = topicIdentityMapper.topicIdentity(clientId);
        return downlinks.values()
                .stream()
                .map(value -> value.route.topicTemplate().replace("{identity}", topicIdentity))
                .toList();
    }

    /** @param topicIdentity Topic 中解析出的设备身份 @return 对应 MQTT Client ID */
    public @NotNull String clientId(@NotNull String topicIdentity) {
        return topicIdentityMapper.clientId(topicIdentity);
    }

    /** 根据 Topic Filter 选择 Pipeline，并解码为 IoT 标准上行结果。 */
    @Override
    public @NotNull DeviceUplink decode(
            @NotNull Device device,
            @NotNull DeviceProtocolMessage message) {
        Device checkedDevice = Objects.requireNonNull(device, "Device 不能为空");
        DeviceProtocolMessage checkedMessage = Objects.requireNonNull(message, "Device 协议消息不能为空");
        if (checkedDevice.id() != checkedMessage.deviceId()) {
            throw new IllegalArgumentException("Device 与协议消息标识不一致");
        }
        MqttUplinkDefinition definition = uplinks.get(checkedMessage.code());
        if (definition == null) {
            throw new IllegalArgumentException("MQTT 协议没有上行消息路由: " + checkedMessage.code());
        }
        try {
            if (definition.kind() == MqttUplinkKind.REPORTED) {
                return DeviceUplink.reported(
                        definition.decodeReported(checkedMessage.payload(), checkedMessage.observedAt()),
                        List.of());
            }
            DecodedMqttAcknowledgement decoded = definition.decodeAcknowledgement(checkedMessage.payload());
            return DeviceUplink.acknowledged(
                    new DeviceAcknowledgement(
                            decoded.messageId(),
                            decoded.ackCode(),
                            decoded.data(),
                            "DEVICE",
                            decoded.sourceAcknowledgementId().orElse(null),
                            decoded.acknowledgedAt().orElse(checkedMessage.observedAt()),
                            checkedMessage.observedAt()));
        } catch (MqttMessageRejectedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MqttMessageRejectedException(
                    MqttMessageRejectionReason.MESSAGE_INVALID,
                    "MQTT Payload 不符合协议消息定义",
                    exception);
        }
    }

    /**
     * 执行目标路由绑定的 Core Pipeline 并生成 MQTT 消息。
     *
     * @param message 标准下行消息
     * @param clientId Device MQTT Client ID；Topic 使用协议配置的可逆身份映射
     * @return 最终 Topic 和厂商 payload
     * @throws IllegalArgumentException 没有匹配下行定义或消息数据缺少已声明字段时抛出
     */
    public @NotNull EncodedMqttMessage encode(
            @NotNull DeviceDownlink message,
            @NotNull String clientId) {
        DeviceDownlink checked = Objects.requireNonNull(message, "下行消息不能为空");
        String key = routeKey(checked.kind(), checked.targetCode());
        DownlinkDefinition definition = downlinks.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("下行消息没有 MQTT Pipeline: " + key);
        }

        DataValues.Builder values = DataValues.builder();
        if (checked.kind() == DeviceDownlinkKind.DESIRED) {
            put(values, definition.keys.getFirst(), checked.data().get("value"));
        } else {
            for (DataKey<?> dataKey : definition.keys) {
                put(values, dataKey, checked.data().get(dataKey.name()));
            }
        }
        byte[] payload = definition.pipeline.run(new MqttDownlinkFrame(checked, values.build()));
        String topicIdentity = topicIdentityMapper.topicIdentity(clientId);
        return new EncodedMqttMessage(
                definition.route.topicTemplate().replace("{identity}", topicIdentity),
                payload);
    }

    /** 根据 Filter 读取负责解析 Device clientId 的路由。 */
    private MqttUplinkRoute uplinkRoute(String filter) {
        return Objects.requireNonNull(uplinkRoutes.get(filter), "MQTT uplink route 不存在");
    }

    /** 验证 reported DataKey 引用的属性。 */
    private static void validatePropertyKey(ThingModel model, DataKey<?> key) {
        ThingProperty property = model.properties().get(key.name());
        if (property == null) {
            throw new IllegalArgumentException("reported property code 未登记: " + key.name());
        }
        validateType(key, property.valueType());
    }

    /** 验证物模型固定 Java 标量和结构化值类型。 */
    private static void validateType(DataKey<?> key, ThingValueType type) {
        Class<?> expected = type.kind().javaType();
        if (!expected.equals(key.type())) {
            throw new IllegalArgumentException("DataKey Java 类型与物模型不一致: " + key.name());
        }
    }

    /** 返回下行 route 唯一 key。 */
    private static String routeKey(DeviceDownlinkKind kind, String targetCode) {
        return kind.name() + ':' + targetCode;
    }

    /** 复制并检查一组映射 DataKey。 */
    private static List<DataKey<?>> validatedKeys(DataKey<?>[] keys, String name) {
        List<DataKey<?>> values = List.copyOf(Arrays.asList(keys));
        if (values.isEmpty()) {
            throw new IllegalArgumentException(name + "至少需要一个 DataKey");
        }
        Set<String> names = new LinkedHashSet<>();
        for (DataKey<?> key : values) {
            if (!names.add(Objects.requireNonNull(key, "DataKey 不能为空").name())) {
                throw new IllegalArgumentException(name + "包含重复 DataKey: " + key.name());
            }
        }
        return values;
    }

    /** 在通配符边界执行 DataKey Java 类型检查。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void put(DataValues.Builder values, DataKey key, Object value) {
        values.put(key, Objects.requireNonNull(value, "下行消息缺少 DataKey 值: " + key.name()));
    }

    /** 构造 MQTT 路由、转换和物模型映射。 */
    public static final class Builder {

        private final @NotNull String code;

        private final @NotNull Map<String, MqttUplinkDefinition> uplinks = new LinkedHashMap<>();

        private final @NotNull Map<String, MqttUplinkRoute> uplinkRoutes = new LinkedHashMap<>();

        private final @NotNull Map<String, DownlinkDefinition> downlinks = new LinkedHashMap<>();

        private @NotNull MqttTopicIdentityMapper topicIdentityMapper = DIRECT_TOPIC_IDENTITY;

        private Builder(@NotNull String code) {
            this.code = TextUtils.requireText(code, "MQTT protocol code");
        }

        /**
         * 设置 MQTT Client ID 与 Topic 设备身份的可逆映射。
         *
         * @param mapper 当前协议全部上下行 Topic 共用的身份映射
         * @return 当前构建器
         */
        public @NotNull Builder topicIdentity(@NotNull MqttTopicIdentityMapper mapper) {
            this.topicIdentityMapper = Objects.requireNonNull(mapper, "MQTT Topic 身份映射不能为空");
            return this;
        }

        /**
         * 登记 reported state 上行路由、转换和字段组。
         *
         * @param route 上行 Topic Filter
         * @param groupCode reported 字段组 code
         * @param mergeMode patch 或 snapshot
         * @param pipeline 把 MQTT payload 转为类型化字段的 Core Pipeline
         * @param keys 允许写入该字段组的物模型属性
         * @return 当前构建器
         */
        public @NotNull Builder reported(
                @NotNull MqttUplinkRoute route,
                @NotNull String groupCode,
                @NotNull ReportedMergeMode mergeMode,
                @NotNull Pipeline<byte[], DataValues> pipeline,
                DataKey<?> @NotNull... keys) {
            List<DataKey<?>> checkedKeys = validatedKeys(keys, "reported 定义");
            return uplink(
                    route,
                    new MqttUplinkDefinition(
                            Objects.requireNonNull(pipeline, "reported Pipeline 不能为空"),
                            TextUtils.requireText(groupCode, "reported group code"),
                            Objects.requireNonNull(mergeMode, "reported merge mode 不能为空"),
                            checkedKeys));
        }

        /**
         * 登记业务 ACK 上行路由和转换。
         *
         * @param route 上行 Topic Filter
         * @param pipeline 把 MQTT payload 转为标准业务 ACK 的 Core Pipeline
         * @return 当前构建器
         */
        public @NotNull Builder acknowledgement(
                @NotNull MqttUplinkRoute route,
                @NotNull Pipeline<byte[], DecodedMqttAcknowledgement> pipeline) {
            return uplink(route, new MqttUplinkDefinition(pipeline));
        }

        /**
         * 登记下行路由、转换和输入字段。
         *
         * @param route desired 属性或 command 路由
         * @param pipeline 把标准消息和值转为 MQTT payload 的 Core Pipeline
         * @param keys desired 值或 command 输入使用的 DataKey
         * @return 当前构建器
         */
        public @NotNull Builder downlink(
                @NotNull MqttDownlinkRoute route,
                @NotNull Pipeline<MqttDownlinkFrame, byte[]> pipeline,
                DataKey<?> @NotNull... keys) {
            MqttDownlinkRoute checkedRoute = Objects.requireNonNull(route, "MQTT downlink route 不能为空");
            List<DataKey<?>> checkedKeys = validatedKeys(keys, "downlink 定义");
            String key = routeKey(checkedRoute.kind(), checkedRoute.targetCode());
            DownlinkDefinition definition = new DownlinkDefinition(
                    checkedRoute,
                    Objects.requireNonNull(pipeline, "MQTT downlink Pipeline 不能为空"),
                    checkedKeys);
            if (downlinks.putIfAbsent(key, definition) != null) {
                throw new IllegalArgumentException("MQTT downlink route 重复: " + key);
            }
            return this;
        }

        /**
         * 校验协议至少声明一个路由并创建不可修改定义。
         *
         * @return MQTT 设备协议
         */
        @CheckReturnValue
        public @NotNull MqttDeviceProtocol build() {
            if (uplinks.isEmpty() && downlinks.isEmpty()) {
                throw new IllegalArgumentException("MQTT 协议至少需要一个上行或下行路由");
            }
            return new MqttDeviceProtocol(this);
        }

        /** 登记一个上行 Topic Filter 并拒绝重复。 */
        private Builder uplink(MqttUplinkRoute route, MqttUplinkDefinition definition) {
            MqttUplinkRoute checked = Objects.requireNonNull(route, "MQTT uplink route 不能为空");
            if (uplinks.putIfAbsent(
                    checked.topicFilter(),
                    Objects.requireNonNull(definition, "MQTT uplink definition 不能为空")) != null) {
                throw new IllegalArgumentException(
                        "MQTT uplink Topic Filter 重复: " + checked.topicFilter());
            }
            uplinkRoutes.put(checked.topicFilter(), checked);
            return this;
        }
    }

    /** 保存一条下行路由、Core Pipeline 和输入字段。 */
    @SuppressWarnings("ClassCanBeRecord") // 协议内部类型遵守项目禁用 record 的规则。
    private static final class DownlinkDefinition {

        private final MqttDownlinkRoute route;

        private final Pipeline<MqttDownlinkFrame, byte[]> pipeline;

        private final List<DataKey<?>> keys;

        private DownlinkDefinition(
                MqttDownlinkRoute route,
                Pipeline<MqttDownlinkFrame, byte[]> pipeline,
                List<DataKey<?>> keys) {
            this.route = route;
            this.pipeline = pipeline;
            this.keys = keys;
        }
    }
}
