package com.sca4d.common.realtime;

import com.sca4d.common.auth.PrincipalType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * 实时消息投递目标。
 */
public class RealtimeTarget {

    private final RealtimeTargetType type;
    private final String value;

    /**
     * 创建已经由对应工厂方法完成编码处理的实时目标。
     *
     * @param type 目标寻址类型
     * @param value 与目标类型对应的固定寻址值
     */
    private RealtimeTarget(@NotNull RealtimeTargetType type, @NotNull String value) {
        this.type = type;
        this.value = value;
    }

    /**
     * 创建登录身份目标。
     *
     * @param subjectId 登录身份 ID
     * @return 实时消息目标
     */
    public static @NotNull RealtimeTarget subject(@NotNull Long subjectId) {
        return new RealtimeTarget(RealtimeTargetType.SUBJECT, String.valueOf(subjectId));
    }

    /**
     * 创建执行主体目标。
     *
     * @param principalType 执行主体类型
     * @param principalId 执行主体 ID
     * @return 实时消息目标
     */
    public static @NotNull RealtimeTarget principal(
            @NotNull PrincipalType principalType, @NotNull Long principalId) {
        return new RealtimeTarget(
                RealtimeTargetType.PRINCIPAL, principalType.value().toLowerCase(Locale.ROOT) + "." + principalId);
    }

    /**
     * 创建租户目标。
     *
     * @param tenantId 租户 ID
     * @return 实时消息目标
     */
    public static @NotNull RealtimeTarget tenant(@NotNull Long tenantId) {
        return new RealtimeTarget(RealtimeTargetType.TENANT, String.valueOf(tenantId));
    }

    /**
     * 创建主题目标。
     *
     * <p>主题由业务模块或外部集成以常量注册，实时消息组件不维护主题枚举。
     *
     * @param topic 业务模块或外部集成定义的主题编码
     * @return 实时消息目标
     */
    public static @NotNull RealtimeTarget topic(@NotNull String topic) {
        return new RealtimeTarget(RealtimeTargetType.TOPIC, normalize(topic));
    }

    /**
     * 创建单连接目标。
     *
     * @param connectionId 连接 ID
     * @return 实时消息目标
     */
    public static @NotNull RealtimeTarget connection(@NotNull String connectionId) {
        return new RealtimeTarget(RealtimeTargetType.CONNECTION, normalize(connectionId));
    }

    /**
     * 从 routing key 解析实时目标。
     *
     * @param routingKey routing key
     * @return 实时消息目标
     */
    public static @NotNull RealtimeTarget fromRoutingKey(@NotNull String routingKey) {
        String normalized = normalize(routingKey);
        int index = normalized.indexOf('.');
        if (index <= 0 || index >= normalized.length() - 1) {
            throw new IllegalArgumentException("实时消息 routing key 格式错误");
        }
        RealtimeTargetType type = RealtimeTargetType.valueOf(normalized.substring(0, index).toUpperCase(Locale.ROOT));
        String value = normalized.substring(index + 1);
        return new RealtimeTarget(type, value);
    }

    /**
     * 返回实时目标的寻址类型。
     *
     * @return Subject、Principal、Tenant、Topic 或 Connection 类型
     */
    public @NotNull RealtimeTargetType getType() {
        return type;
    }

    /**
     * 返回不包含类型前缀的目标寻址值。
     *
     * @return 用于 routing key 和订阅匹配的固定值
     */
    public @NotNull String getValue() {
        return value;
    }

    /**
     * 转换为 RabbitMQ topic routing key。
     *
     * @return 路由键
     */
    public @NotNull String routingKey() {
        return type.name().toLowerCase(Locale.ROOT) + "." + value;
    }

    /**
     * 转换为 STOMP 目标地址。
     *
     * @return STOMP 目标地址
     */
    public @NotNull String stompDestination() {
        return RealtimeRoutes.realtimeTopic(routingKey());
    }

    /**
     * 按目标类型和寻址值比较两个实时目标。
     *
     * @param object 待比较对象
     * @return 类型和值均一致时返回 {@code true}
     */
    @Override
    public boolean equals(@Nullable Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof RealtimeTarget that)) {
            return false;
        }
        return type == that.type && value.equals(that.value);
    }

    /**
     * 计算与 {@link #equals(Object)} 一致的目标哈希值。
     *
     * @return 类型和值的组合哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    /**
     * 返回可直接用于消息路由和日志的 routing key。
     *
     * @return 包含目标类型前缀的固定文本
     */
    @Override
    public @NotNull String toString() {
        return routingKey();
    }

    /**
     * 将开放目标编码转换为 routing key 可以使用的小写文本。
     *
     * @param value 业务主题、连接标识或完整 routing key
     * @return 去除首尾空白并替换非法字符后的编码
     * @throws IllegalArgumentException 输入缺失或只包含空白时抛出
     */
    private static @NotNull String normalize(@Nullable String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("实时消息目标不能为空");
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._:-]", "_");
    }
}
