package com.sca4d.core.api.webrealtime;

import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 标识 Web Realtime 发布需要匹配的共享连接索引。 */
public final class WebRealtimeTarget {

    /** 表示 Web Realtime 支持的目标索引种类。 */
    public enum Type {
        /** 匹配 Account 的全部在线浏览器连接。 */
        ACCOUNT,
        /** 匹配 Subject 的全部在线浏览器连接。 */
        SUBJECT,
        /** 匹配包含 Principal 的全部在线浏览器连接。 */
        PRINCIPAL,
        /** 匹配已获准订阅指定 Resource 的连接。 */
        RESOURCE,
        /** 匹配已获准订阅指定 Topic 的连接。 */
        TOPIC,
        /** 匹配由指定 Session 建立的全部连接。 */
        SESSION,
        /** 匹配一个明确的浏览器连接。 */
        CONNECTION
    }

    private final @NotNull Type type;

    private final @Nullable String qualifier;

    private final @NotNull String id;

    private WebRealtimeTarget(@NotNull Type type, @Nullable String qualifier, @NotNull String id) {
        this.type = Objects.requireNonNull(type, "Web Realtime 目标类型不能为空");
        this.qualifier = qualifier == null ? null : checked(qualifier, "Web Realtime 目标限定类型");
        this.id = checked(id, "Web Realtime 目标标识");
        if ((type == Type.RESOURCE) == (this.qualifier == null)) {
            throw new IllegalArgumentException("只有 Resource 目标需要限定类型");
        }
    }

    /** 按已经持久化的目标类型、限定类型和标识恢复目标。 */
    @CheckReturnValue
    @Contract("_, _, _ -> new")
    public static @NotNull WebRealtimeTarget of(
            @NotNull Type type,
            @Nullable String qualifier,
            @NotNull String id) {
        return new WebRealtimeTarget(type, qualifier, id);
    }

    /** @param accountId Account 标识 @return Account 目标 */
    @CheckReturnValue
    @Contract(value = "_ -> new", pure = true)
    public static @NotNull WebRealtimeTarget account(long accountId) {
        return positive(Type.ACCOUNT, accountId, "Account 标识");
    }

    /** @param subjectId Subject 标识 @return Subject 目标 */
    @CheckReturnValue
    @Contract(value = "_ -> new", pure = true)
    public static @NotNull WebRealtimeTarget subject(long subjectId) {
        return positive(Type.SUBJECT, subjectId, "Subject 标识");
    }

    /** @param principalId Principal 标识 @return Principal 目标 */
    @CheckReturnValue
    @Contract(value = "_ -> new", pure = true)
    public static @NotNull WebRealtimeTarget principal(long principalId) {
        return positive(Type.PRINCIPAL, principalId, "Principal 标识");
    }

    /** @param resource 需要匹配类型和标识的 Resource @return Resource 目标 */
    @CheckReturnValue
    @Contract(value = "_ -> new", pure = true)
    public static @NotNull WebRealtimeTarget resource(@NotNull Resource resource) {
        Resource checked = Objects.requireNonNull(resource, "Resource 不能为空");
        return new WebRealtimeTarget(Type.RESOURCE, checked.type(), checked.id());
    }

    /** @param topic 具体应用授权后登记的 Topic @return Topic 目标 */
    @CheckReturnValue
    @Contract(value = "_ -> new", pure = true)
    public static @NotNull WebRealtimeTarget topic(@NotNull String topic) {
        return new WebRealtimeTarget(Type.TOPIC, null, topic);
    }

    /** @param sessionId Session 标识 @return Session 目标 */
    @CheckReturnValue
    @Contract(value = "_ -> new", pure = true)
    public static @NotNull WebRealtimeTarget session(@NotNull SessionId sessionId) {
        return new WebRealtimeTarget(
                Type.SESSION,
                null,
                Objects.requireNonNull(sessionId, "SessionId 不能为空").value());
    }

    /** @param connectionId 浏览器连接标识 @return Connection 目标 */
    @CheckReturnValue
    @Contract(value = "_ -> new", pure = true)
    public static @NotNull WebRealtimeTarget connection(@NotNull ConnectionId connectionId) {
        return new WebRealtimeTarget(
                Type.CONNECTION,
                null,
                Objects.requireNonNull(connectionId, "ConnectionId 不能为空").value());
    }

    /** @return 目标索引种类 */
    @Contract(pure = true)
    public @NotNull Type type() {
        return type;
    }

    /** @return Resource 类型；其他目标返回 {@code null} */
    @Contract(pure = true)
    public @Nullable String qualifier() {
        return qualifier;
    }

    /** @return 目标在当前种类和限定类型下的稳定标识 */
    @Contract(pure = true)
    public @NotNull String id() {
        return id;
    }

    /** @return 可直接用于共享索引的无歧义目标键 */
    @Contract(pure = true)
    public @NotNull String key() {
        return qualifier == null
                ? type.name() + ":" + id
                : type.name() + ":" + segment(qualifier) + ":" + segment(id);
    }

    /** 创建使用正数标识的目标。 */
    private static @NotNull WebRealtimeTarget positive(@NotNull Type type, long id, @NotNull String name) {
        if (id <= 0) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
        return new WebRealtimeTarget(type, null, Long.toString(id));
    }

    /** 校验目标字段适合写入 Redis 索引。 */
    private static @NotNull String checked(@NotNull String value, @NotNull String name) {
        String checked = TextUtils.requireText(value, name);
        if (checked.length() > 256) {
            throw new IllegalArgumentException(name + "不能超过 256 个字符");
        }
        return checked;
    }

    /** 使用长度前缀避免 Resource 类型或标识中的分隔符产生相同索引键。 */
    @Contract(pure = true)
    private static @NotNull String segment(@NotNull String value) {
        return value.length() + ":" + value;
    }
}
