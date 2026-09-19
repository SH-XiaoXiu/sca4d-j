package com.sca4d.iot.emqx.protocol;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 选择一个 MQTT Topic Filter 的上行协议 Pipeline。 */
public final class MqttUplinkRoute {

    private final @NotNull String topicFilter;

    private final int identityLevel;

    private MqttUplinkRoute(@NotNull String topicFilter, int identityLevel) {
        this.topicFilter = validateFilter(topicFilter);
        String[] levels = this.topicFilter.split("/", -1);
        if (identityLevel < 0 || identityLevel >= levels.length || !"+".equals(levels[identityLevel])) {
            throw new IllegalArgumentException("设备身份 level 必须指向 Topic Filter 中的 + 通配符");
        }
        this.identityLevel = identityLevel;
    }

    /**
     * 把 Topic Filter 中唯一的 `+` level 解释为 Topic 设备身份。
     *
     * @param topicFilter MQTT Topic Filter
     * @return 上行路由
     */
    @CheckReturnValue
    public static @NotNull MqttUplinkRoute topic(@NotNull String topicFilter) {
        String checked = validateFilter(topicFilter);
        String[] levels = checked.split("/", -1);
        int found = -1;
        for (int index = 0; index < levels.length; index++) {
            if ("+".equals(levels[index])) {
                if (found >= 0)
                    throw new IllegalArgumentException("存在多个 + 时必须明确设备身份 level");
                found = index;
            }
        }
        if (found < 0)
            throw new IllegalArgumentException("上行 Topic Filter 必须包含设备身份的 + level");
        return new MqttUplinkRoute(checked, found);
    }

    /** @param topicFilter MQTT Topic Filter @param identityLevel 设备身份所在的零起始 level @return 上行路由 */
    @CheckReturnValue
    public static @NotNull MqttUplinkRoute topic(@NotNull String topicFilter, int identityLevel) {
        return new MqttUplinkRoute(topicFilter, identityLevel);
    }

    /** @return MQTT Topic Filter */
    public @NotNull String topicFilter() {
        return topicFilter;
    }

    /** @param topic 已匹配的实际 Topic @return Topic 中声明的设备身份 */
    public @NotNull String topicIdentity(@NotNull String topic) {
        String[] levels = Objects.requireNonNull(topic, "MQTT Topic 不能为空").split("/", -1);
        if (levels.length <= identityLevel || levels[identityLevel].isBlank()) {
            throw new IllegalArgumentException("MQTT Topic 缺少设备身份");
        }
        return levels[identityLevel];
    }

    /** @param topicIdentity Topic 中的设备身份 @return 只允许该 Device 发布的 Topic Filter */
    public @NotNull String deviceTopicFilter(@NotNull String topicIdentity) {
        String[] levels = topicFilter.split("/", -1);
        levels[identityLevel] = TextUtils.requireText(topicIdentity, "MQTT Topic 设备身份");
        return String.join("/", levels);
    }

    /** 拒绝共享订阅前缀和空 topic level。 */
    private static String validateFilter(String value) {
        String checked = TextUtils.requireText(value, "MQTT uplink topic filter");
        if (checked.startsWith("$share/")
                || checked.startsWith("/")
                || checked.endsWith("/")
                || checked.contains("//")) {
            throw new IllegalArgumentException("协议 Topic Filter 不能包含共享订阅前缀或空 level");
        }
        return checked;
    }
}
