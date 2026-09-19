package com.sca4d.iot.api.protocol;

import com.sca4d.iot.api.model.ThingModel;
import org.jetbrains.annotations.NotNull;

/** 声明一个稳定 code 的设备协议并验证它与物模型成员的映射。 */
public interface DeviceProtocol {

    /** @return 在一个 App 内唯一且跨部署稳定的协议 code */
    @NotNull
    String code();

    /**
     * 验证协议引用的属性、事件和命令 code 及 Java 类型。
     *
     * @param model 待绑定物模型
     * @throws IllegalArgumentException code、Java 类型或约束不一致时抛出
     */
    void validate(@NotNull ThingModel model);
}
