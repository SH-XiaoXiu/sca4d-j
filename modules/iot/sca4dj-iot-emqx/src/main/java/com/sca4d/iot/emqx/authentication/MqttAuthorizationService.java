package com.sca4d.iot.emqx.authentication;

import static com.sca4d.core.api.name.NameFormat.COLON;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.emqx.protocol.MqttDeviceProtocol;
import com.sca4d.iot.emqx.protocol.MqttProtocolCatalog;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 统一解析应用授权策略或协议 ACL，并为连接失效判断生成稳定指纹。 */
public class MqttAuthorizationService {

    /** EMQX `client_attrs` 中保存的当前 MQTT ACL 指纹。 */
    public static final String FINGERPRINT_ATTRIBUTE = "authz_fingerprint";

    private final @NotNull List<MqttAuthorizationPolicy> policies;

    private final @NotNull MqttProtocolCatalog protocols;

    private final @NotNull Key fingerprintSchema;

    /** 创建应用策略优先、协议 ACL 作为默认规则且使用当前 App 隔离名称的 MQTT 授权服务。 */
    public MqttAuthorizationService(
            @NotNull MqttProtocolCatalog protocols,
            @NotNull Collection<MqttAuthorizationPolicy> policies) {
        this.protocols = Objects.requireNonNull(protocols, "MQTT 协议目录不能为空");
        this.policies = List.copyOf(Objects.requireNonNull(policies, "MQTT 授权策略不能为空"));
        this.fingerprintSchema = Key.of(TreeCode.ofColon("IOT:EMQX:AUTHORIZATION:V1"));
        if (this.policies.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("MQTT 授权策略不能包含空值");
        }
    }

    /**
     * 解析当前 Device 的完整 ACL；具体应用策略优先，否则使用协议精确 Topic ACL。
     *
     * @param device 已完成身份认证的 Device
     * @param identifierValue MQTT 连接身份
     * @return 完整 ACL 和稳定指纹
     */
    @CheckReturnValue
    public @NotNull MqttAuthorization resolve(
            @NotNull Device device,
            @NotNull String identifierValue) {
        Device checkedDevice = Objects.requireNonNull(device, "Device 不能为空");
        String checkedIdentifier = Objects.requireNonNull(identifierValue, "MQTT 连接身份不能为空");
        List<MqttAuthorizationPolicy> matches = policies.stream()
                .filter(policy -> policy.supports(checkedDevice))
                .toList();
        if (matches.size() > 1) {
            throw new IllegalStateException("同一 Device 匹配多个 MQTT 授权策略");
        }

        List<EmqxAclRule> rules = matches.size() == 1
                ? validateClosedAcl(matches.getFirst().rules(checkedDevice, checkedIdentifier))
                : protocolRules(checkedDevice, checkedIdentifier);
        return new MqttAuthorization(rules, fingerprint(rules));
    }

    /**
     * 判断 EMQX 当前连接是否持有由现行代码生成的 ACL。
     *
     * @param device 当前 Client ID 解析得到的 Device
     * @param identifierValue MQTT Client ID
     * @param clientAttributes Management API 返回的连接属性
     * @return 指纹存在且与当前 ACL 一致时为 {@code true}
     */
    @CheckReturnValue
    public boolean isCurrent(
            @NotNull Device device,
            @NotNull String identifierValue,
            @NotNull Map<String, String> clientAttributes) {
        String actual = Objects.requireNonNull(clientAttributes, "EMQX client attrs 不能为空")
                .get(FINGERPRINT_ATTRIBUTE);
        return actual != null
                && MessageDigest.isEqual(
                        resolve(device, identifierValue).fingerprint().getBytes(StandardCharsets.US_ASCII),
                        actual.getBytes(StandardCharsets.US_ASCII));
    }

    /** 使用协议声明的上下行 Topic 生成默认关闭 ACL。 */
    private @NotNull List<EmqxAclRule> protocolRules(Device device, String identifierValue) {
        MqttDeviceProtocol protocol = protocols.loadByCode(device.protocolCode());
        List<EmqxAclRule> rules = new ArrayList<>();
        protocol.deviceUplinkTopicFilters(identifierValue)
                .forEach(
                        topic -> rules.add(
                                new EmqxAclRule("allow", "publish", topic, List.of(1), false)));
        protocol.deviceDownlinkTopics(identifierValue)
                .forEach(
                        topic -> rules.add(
                                new EmqxAclRule("allow", "subscribe", "eq " + topic, List.of(1), null)));
        rules.add(new EmqxAclRule("deny", "all", "#", List.of(0, 1, 2), null));
        return List.copyOf(rules);
    }

    /** 要求扩展策略返回非空 ACL，并以拒绝全部 Topic 作为默认关闭规则。 */
    private static @NotNull List<EmqxAclRule> validateClosedAcl(@NotNull List<EmqxAclRule> rules) {
        List<EmqxAclRule> checked = List.copyOf(Objects.requireNonNull(rules, "MQTT ACL 不能为空"));
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("MQTT ACL 不能为空");
        }
        EmqxAclRule last = checked.getLast();
        if (!"deny".equals(last.permission())
                || !"all".equals(last.action())
                || !"#".equals(last.topic())) {
            throw new IllegalArgumentException("MQTT ACL 必须以拒绝全部 Topic 结束");
        }
        return checked;
    }

    /** 按有序规则和规范化 QoS 计算跨进程稳定的 SHA-256。 */
    private @NotNull String fingerprint(List<EmqxAclRule> rules) {
        ByteArrayOutputStream canonical = new ByteArrayOutputStream();
        appendFingerprintField(canonical, fingerprintSchema.format(COLON));
        for (EmqxAclRule rule : rules) {
            appendFingerprintField(canonical, rule.permission());
            appendFingerprintField(canonical, rule.action());
            appendFingerprintField(canonical, rule.topic());
            rule.qos()
                    .stream()
                    .sorted()
                    .forEach(qos -> appendFingerprintField(canonical, Integer.toString(qos)));
            Boolean retain = rule.retain();
            appendFingerprintField(canonical, retain == null ? "null" : retain.toString());
        }
        return DigestUtil.sha256Hex(canonical.toByteArray());
    }

    /** 使用长度前缀消除相邻字段连接后的歧义。 */
    private static void appendFingerprintField(ByteArrayOutputStream canonical, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        canonical.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        canonical.writeBytes(bytes);
    }
}
