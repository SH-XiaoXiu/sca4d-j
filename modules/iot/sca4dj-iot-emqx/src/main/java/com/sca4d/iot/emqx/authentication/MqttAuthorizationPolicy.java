package com.sca4d.iot.emqx.authentication;

import com.sca4d.iot.api.Device;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 允许具体应用按 Device 选择一组完整且有序的 EMQX MQTT ACL。 */
public interface MqttAuthorizationPolicy {

    /**
     * 判断当前策略是否负责指定 Device。
     *
     * @param device 已经完成连接认证的 Device
     * @return 当前策略负责该 Device 时为 {@code true}
     */
    boolean supports(@NotNull Device device);

    /**
     * 返回当前连接使用的完整 ACL。
     *
     * <p>规则必须按拒绝优先顺序排列，并以拒绝全部 Topic 结束。服务会拒绝空规则或没有兜底拒绝的结果。
     *
     * @param device 已经完成连接认证的 Device
     * @param clientId MQTT CONNECT Client ID
     * @return 交给 EMQX HTTP Authenticator 的有序 ACL
     */
    @NotNull
    List<EmqxAclRule> rules(@NotNull Device device, @NotNull String clientId);
}
