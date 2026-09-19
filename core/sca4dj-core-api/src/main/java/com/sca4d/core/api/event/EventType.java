package com.sca4d.core.api.event;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

/** 使用稳定编码、协议版本和 payload 类型声明一种业务事件。 */
public final class EventType<T> {

    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final @NotNull String code;

    private final int protocolVersion;

    private final @NotNull Class<T> payloadType;

    /**
     * 创建跨发布端和消费端共用的事件类型。
     *
     * @param code 跨版本保持稳定的事件编码
     * @param protocolVersion 正数协议版本；payload 结构发生不兼容变化时增加
     * @param payloadType 序列化和反序列化 payload 使用的具体类型
     * @throws IllegalArgumentException 编码或协议版本无效时抛出
     */
    public EventType(@NotNull String code, int protocolVersion, @NotNull Class<T> payloadType) {
        String checkedCode = TextUtils.requireText(code, "事件编码");
        if (!CODE.matcher(checkedCode).matches()) {
            throw new IllegalArgumentException("事件编码只能包含字母、数字、点、下划线和连字符");
        }
        if (protocolVersion <= 0) {
            throw new IllegalArgumentException("事件协议版本必须为正数");
        }
        this.code = checkedCode;
        this.protocolVersion = protocolVersion;
        this.payloadType = Objects.requireNonNull(payloadType, "事件 payload 类型不能为空");
    }

    /** @return 跨发布端和消费端保持稳定的事件编码 */
    @Contract(pure = true)
    public @NotNull String code() {
        return code;
    }

    /** @return 当前 payload 结构的正数协议版本 */
    @Contract(pure = true)
    public int protocolVersion() {
        return protocolVersion;
    }

    /** @return 序列化和反序列化 payload 使用的具体类型 */
    @Contract(pure = true)
    public @NotNull Class<T> payloadType() {
        return payloadType;
    }

    /** @return 两个事件类型是否声明相同编码、版本和 payload 类型 */
    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other
                || other instanceof EventType<?> that
                        && protocolVersion == that.protocolVersion
                        && code.equals(that.code)
                        && payloadType.equals(that.payloadType);
    }

    /** @return 事件编码、协议版本和 payload 类型共同计算的哈希值 */
    @Override
    @Contract(pure = true)
    public int hashCode() {
        return Objects.hash(code, protocolVersion, payloadType);
    }

    /** @return 事件编码和协议版本，格式为 {@code code@version} */
    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return code + '@' + protocolVersion;
    }
}
