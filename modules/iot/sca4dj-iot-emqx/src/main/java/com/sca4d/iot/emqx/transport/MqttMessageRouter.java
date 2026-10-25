package com.sca4d.iot.emqx.transport;

import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.connection.DeviceConnectionResolver;
import com.sca4d.iot.api.protocol.DeviceProtocolMessage;
import com.sca4d.iot.api.protocol.DeviceUplinkDispatcher;
import com.sca4d.iot.emqx.connection.MqttDeviceConnection;
import com.sca4d.iot.emqx.protocol.MqttDeviceProtocol;
import com.sca4d.iot.emqx.protocol.MqttProtocolCatalog;
import com.sca4d.iot.emqx.protocol.MqttUplinkRoute;
import com.sca4d.iot.emqx.transport.exception.MqttMessageRejectedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 汇总自动生成的 MQTT DeviceProtocol 路由和具体应用登记的消息路由。 */
public class MqttMessageRouter {

    private final @NotNull List<MqttMessageRoute> routes;

    /**
     * 创建每个 Topic Filter 只有一个目标的路由表。
     *
     * @param protocols 当前应用安装的标准 MQTT 设备协议
     * @param devices MQTT Client ID 到 Device 的解析入口
     * @param uplinks 传输无关 Device 上行分发器
     * @param applicationRoutes 具体应用登记的其他 MQTT 消息路由
     */
    public MqttMessageRouter(
            @NotNull MqttProtocolCatalog protocols,
            @NotNull DeviceConnectionResolver devices,
            @NotNull DeviceUplinkDispatcher uplinks,
            @NotNull Collection<MqttMessageRoute> applicationRoutes) {
        MqttProtocolCatalog protocolCatalog = Objects.requireNonNull(protocols, "MQTT 协议目录不能为空");
        DeviceConnectionResolver resolver = Objects.requireNonNull(devices, "Device 连接解析入口不能为空");
        DeviceUplinkDispatcher dispatcher = Objects.requireNonNull(uplinks, "Device 上行分发器不能为空");
        List<MqttMessageRoute> values = new ArrayList<>(
                Objects.requireNonNull(applicationRoutes, "应用 MQTT 路由不能为空"));
        for (MqttDeviceProtocol protocol : protocolCatalog.protocols()) {
            for (MqttUplinkRoute route : protocol.uplinkRoutes()) {
                values.add(new MqttDeviceProtocolRoute(protocol, route, resolver, dispatcher));
            }
        }

        Map<String, MqttMessageRoute> indexed = new LinkedHashMap<>();
        for (MqttMessageRoute route : values) {
            MqttMessageRoute checked = Objects.requireNonNull(route, "MQTT 消息路由不能为空");
            String filter = validateFilter(checked.topicFilter());
            if (indexed.putIfAbsent(filter, checked) != null) {
                throw new IllegalArgumentException("MQTT Topic Filter 登记了多个路由: " + filter);
            }
        }
        this.routes = List.copyOf(indexed.values());
        for (int left = 0; left < routes.size(); left++) {
            for (int right = left + 1; right < routes.size(); right++) {
                String leftFilter = routes.get(left).topicFilter();
                String rightFilter = routes.get(right).topicFilter();
                if (filtersOverlap(leftFilter, rightFilter)) {
                    throw new IllegalArgumentException(
                            "MQTT Topic Filter 存在重叠路由: " + leftFilter + " <-> " + rightFilter);
                }
            }
        }
    }

    /** @return 去重排序后的全部共享订阅 Topic Filter */
    public @NotNull @Unmodifiable List<String> topicFilters() {
        return routes.stream().map(MqttMessageRoute::topicFilter).sorted().toList();
    }

    /**
     * 把消息交给唯一匹配的路由。
     *
     * @throws IllegalStateException 实际消息没有匹配构造时已经校验的唯一路由时抛出
     * @throws MqttMessageRejectedException 匹配路由明确拒绝当前消息时抛出
     */
    public void dispatch(@NotNull MqttMessage message) {
        MqttMessage input = Objects.requireNonNull(message, "MQTT 消息不能为空");
        List<MqttMessageRoute> matches = routes.stream()
                .filter(route -> matches(route.topicFilter(), input.topic()))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "MQTT Topic 没有唯一消息路由: " + input.topic() + ", matches=" + matches.size());
        }
        matches.getFirst().route(input);
    }

    /** 判断两个已经校验的 MQTT Topic Filter 是否可能匹配同一实际 Topic。 */
    static boolean filtersOverlap(@NotNull String left, @NotNull String right) {
        String[] leftLevels = left.split("/", -1);
        String[] rightLevels = right.split("/", -1);
        int index = 0;
        while (index < leftLevels.length && index < rightLevels.length) {
            String leftLevel = leftLevels[index];
            String rightLevel = rightLevels[index];
            if ("#".equals(leftLevel) || "#".equals(rightLevel)) {
                return true;
            }
            if (!"+".equals(leftLevel)
                    && !"+".equals(rightLevel)
                    && !leftLevel.equals(rightLevel)) {
                return false;
            }
            index++;
        }
        if (index == leftLevels.length && index == rightLevels.length) {
            return true;
        }
        return index < leftLevels.length && "#".equals(leftLevels[index])
                || index < rightLevels.length && "#".equals(rightLevels[index]);
    }

    /** MQTT Topic Filter 匹配，不把共享订阅前缀作为路由内容。 */
    static boolean matches(@NotNull String filter, @NotNull String topic) {
        String[] filters = filter.split("/", -1);
        String[] topics = topic.split("/", -1);
        int index = 0;
        for (; index < filters.length; index++) {
            String value = filters[index];
            if ("#".equals(value)) {
                return index == filters.length - 1;
            }
            if (index >= topics.length
                    || !("+".equals(value) || value.equals(topics[index]))) {
                return false;
            }
        }
        return index == topics.length;
    }

    /** 拒绝共享订阅前缀、空层级和不是完整层级的通配符。 */
    private static @NotNull String validateFilter(@NotNull String filter) {
        String checked = TextUtils.requireText(filter, "MQTT Topic Filter");
        if (checked.startsWith("$share/")
                || checked.startsWith("/")
                || checked.endsWith("/")
                || checked.contains("//")) {
            throw new IllegalArgumentException("MQTT 路由不能包含共享订阅前缀或空 Topic 层级");
        }
        String[] levels = checked.split("/", -1);
        for (int index = 0; index < levels.length; index++) {
            String level = levels[index];
            if ((level.contains("+") && !"+".equals(level))
                    || (level.contains("#") && !("#".equals(level) && index == levels.length - 1))) {
                throw new IllegalArgumentException("MQTT Topic Filter 通配符必须占据完整层级");
            }
        }
        return checked;
    }

    /** 把 MqttDeviceProtocol 的一条 Topic Filter 转换为传输无关 Device 消息。 */
    @SuppressWarnings("ClassCanBeRecord") // 路由适配类型遵守项目禁用 record 的规则。
    private static class MqttDeviceProtocolRoute implements MqttMessageRoute {

        private final DeviceConnectionResolver devices;

        private final DeviceUplinkDispatcher uplinks;

        private final MqttDeviceProtocol protocol;

        private final MqttUplinkRoute route;

        private MqttDeviceProtocolRoute(
                MqttDeviceProtocol protocol,
                MqttUplinkRoute route,
                DeviceConnectionResolver devices,
                DeviceUplinkDispatcher uplinks) {
            this.protocol = protocol;
            this.route = route;
            this.devices = devices;
            this.uplinks = uplinks;
        }

        @Override
        public @NotNull String topicFilter() {
            return route.topicFilter();
        }

        /** Topic 只定位连接 Device；实际设备协议始终由 IoT 分发器从 Device 台账选择。 */
        @Override
        public void route(@NotNull MqttMessage message) {
            MqttMessage input = Objects.requireNonNull(message, "MQTT 消息不能为空");
            String clientId;
            try {
                String topicIdentity = route.topicIdentity(input.topic());
                clientId = protocol.clientId(topicIdentity);
            } catch (IllegalArgumentException exception) {
                throw new MqttMessageRejectedException(
                        MqttMessageRejectionReason.TOPIC_INVALID,
                        "MQTT Topic 身份不符合协议",
                        exception);
            }
            Device device = devices.findDevice(MqttDeviceConnection.clientId(clientId))
                    .orElseThrow(() -> {
                        IllegalArgumentException failure = new IllegalArgumentException(
                                "MQTT Client ID 未登记 Device");
                        return new MqttMessageRejectedException(
                                MqttMessageRejectionReason.IDENTITY_INVALID,
                                failure.getMessage(),
                                failure);
                    });
            uplinks.dispatch(
                    new DeviceProtocolMessage(
                            device,
                            route.topicFilter(),
                            input.payload(),
                            input.observedAt()));
        }
    }
}
