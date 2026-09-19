package com.sca4d.app.iot.simulator;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** 启动规范 JSON 设备和旧式二进制设备，并持续连接真实 EMQX。 */
public final class IotDeviceSimulatorApplication {

    private static final Logger LOG = LoggerFactory.getLogger(IotDeviceSimulatorApplication.class);

    private IotDeviceSimulatorApplication() {
    }

    /**
     * 从环境变量读取两个已登记 Device 的 clientId 和共享 Secret，连接后持续模拟真实行为。
     *
     * @param arguments 当前模拟器不读取命令行参数
     */
    public static void main(String @NotNull [] arguments) throws InterruptedException {
        SimulatorConfiguration configuration = SimulatorConfiguration.fromEnvironment();
        List<MqttDeviceSimulator> devices = new ArrayList<>();
        if (configuration.selected("standard")) {
            devices.add(new StandardClimateDeviceSimulator(configuration));
        }
        if (configuration.selected("legacy")) {
            devices.add(new LegacyFreezerDeviceSimulator(configuration));
        }
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> close(devices),
                                "iot-simulator-shutdown"));
        try {
            devices.forEach(MqttDeviceSimulator::start);
            LOG.info("IoT 设备模拟器已启动 | deviceCount={}", devices.size());
            new CountDownLatch(1).await();
        } catch (RuntimeException exception) {
            close(devices);
            throw exception;
        }
    }

    /** 逆序断开已经创建的设备，单台设备关闭失败不阻止其他设备断开。 */
    private static void close(@NotNull List<MqttDeviceSimulator> devices) {
        for (int index = devices.size() - 1; index >= 0; index--) {
            try {
                devices.get(index).close();
            } catch (RuntimeException exception) {
                LOG.warn("IoT 模拟设备关闭失败 | index={}", index, exception);
            }
        }
    }
}
