package com.sca4d.app.iot.simulator;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/** 读取两个设备模拟器连接 EMQX 所需的进程环境变量。 */
public final class SimulatorConfiguration {

    private final @NotNull String host;

    private final int port;

    private final boolean tls;

    private final @NotNull Duration reportInterval;

    private final @NotNull String selectedDevices;

    private final @NotNull String standardClientId;

    private final @NotNull String standardSecret;

    private final @NotNull String legacyClientId;

    private final @NotNull String legacySecret;

    /** 从进程环境读取并校验模拟器连接参数。 */
    private SimulatorConfiguration(@NotNull Map<String, String> environment) {
        this.host = value(environment, "SIMULATOR_MQTT_HOST", "localhost");
        this.port = integer(environment, "SIMULATOR_MQTT_PORT", 1883, 1, 65535);
        this.tls = Boolean.parseBoolean(value(environment, "SIMULATOR_MQTT_TLS", "false"));
        this.reportInterval = Duration.ofSeconds(
                integer(
                        environment,
                        "SIMULATOR_REPORT_INTERVAL_SECONDS",
                        5,
                        1,
                        3600));
        this.selectedDevices = value(environment, "SIMULATOR_DEVICE", "all").toLowerCase(Locale.ROOT);
        if (!selectedDevices.equals("all")
                && !selectedDevices.equals("standard")
                && !selectedDevices.equals("legacy")) {
            throw new IllegalArgumentException("SIMULATOR_DEVICE 只允许 all、standard 或 legacy");
        }
        this.standardClientId = selected("standard")
                ? required(environment, "SIMULATOR_STANDARD_CLIENT_ID")
                : "unused-standard";
        this.standardSecret = selected("standard")
                ? required(environment, "SIMULATOR_STANDARD_SECRET")
                : "unused-standard";
        this.legacyClientId = selected("legacy")
                ? required(environment, "SIMULATOR_LEGACY_CLIENT_ID")
                : "unused-legacy";
        this.legacySecret = selected("legacy")
                ? required(environment, "SIMULATOR_LEGACY_SECRET")
                : "unused-legacy";
    }

    /** @return 由当前进程环境构造的配置 */
    @CheckReturnValue
    public static @NotNull SimulatorConfiguration fromEnvironment() {
        return new SimulatorConfiguration(System.getenv());
    }

    /** @return EMQX MQTT listener 主机 */
    public @NotNull String host() {
        return host;
    }

    /** @return EMQX MQTT listener 端口 */
    public int port() {
        return port;
    }

    /** @return MQTT 连接是否使用系统默认 TLS trust store */
    public boolean tls() {
        return tls;
    }

    /** @return 周期状态上报间隔 */
    public @NotNull Duration reportInterval() {
        return reportInterval;
    }

    /** @return 规范 JSON 设备的 MQTT clientId */
    public @NotNull String standardClientId() {
        return standardClientId;
    }

    /** @return 规范 JSON 设备创建时显示一次的共享 Secret */
    public @NotNull String standardSecret() {
        return standardSecret;
    }

    /** @return 旧冷柜的 MQTT clientId */
    public @NotNull String legacyClientId() {
        return legacyClientId;
    }

    /** @return 旧冷柜创建时显示一次的共享 Secret */
    public @NotNull String legacySecret() {
        return legacySecret;
    }

    /**
     * 判断当前进程是否启动指定类型的模拟设备。
     *
     * @param device `standard` 或 `legacy`
     * @return 配置允许启动该设备时返回 {@code true}
     */
    public boolean selected(@NotNull String device) {
        return selectedDevices.equals("all") || selectedDevices.equals(device);
    }

    /** 读取非空环境变量，不存在时使用默认值。 */
    private static @NotNull String value(
            @NotNull Map<String, String> environment,
            @NotNull String key,
            @NotNull String defaultValue) {
        String value = environment.get(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    /** 读取必须显式提供的非空环境变量。 */
    private static @NotNull String required(
            @NotNull Map<String, String> environment,
            @NotNull String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(key + " 必须显式配置");
        return value.trim();
    }

    /** 读取并校验有界整数环境变量。 */
    private static int integer(
            @NotNull Map<String, String> environment,
            @NotNull String key,
            int defaultValue,
            int minimum,
            int maximum) {
        String value = environment.get(key);
        int parsed;
        try {
            parsed = value == null || value.isBlank() ? defaultValue : Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " 必须是整数", exception);
        }
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException(key + " 超出允许范围");
        }
        return parsed;
    }
}
