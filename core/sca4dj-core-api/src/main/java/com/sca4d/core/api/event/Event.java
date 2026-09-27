package com.sca4d.core.api.event;

import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 保存一种已经发生的业务事件及对应的强类型 payload 数据。 */
public final class Event<T> extends Message {

    private final @NotNull EventType<T> type;

    private final @Nullable String businessKey;

    private final @NotNull Instant occurredAt;

    private final @NotNull T payload;

    /**
     * 创建待发布业务事件，并在运行时确认 payload 符合 `EventType` 声明。
     *
     * @param type 事件编码、协议版本和 payload 类型
     * @param businessKey 可选的业务幂等或顺序键，最长 256 个字符
     * @param occurredAt 业务事件实际发生时间
     * @param payload 非空业务内容
     * @throws IllegalArgumentException 业务键为空白、过长或 payload 类型不匹配时抛出
     */
    public Event(
            @NotNull EventType<T> type,
            @Nullable String businessKey,
            @NotNull Instant occurredAt,
            @NotNull T payload) {
        this.type = Objects.requireNonNull(type, "事件类型不能为空");
        String checkedBusinessKey = businessKey == null
                ? null
                : TextUtils.requireText(businessKey, "事件业务键");
        if (checkedBusinessKey != null && checkedBusinessKey.length() > 256) {
            throw new IllegalArgumentException("事件业务键不能超过 256 个字符");
        }
        this.businessKey = checkedBusinessKey;
        this.occurredAt = Objects.requireNonNull(occurredAt, "事件发生时间不能为空");
        T checkedPayload = Objects.requireNonNull(payload, "事件 payload 不能为空");
        if (!type.payloadType().isInstance(checkedPayload)) {
            throw new IllegalArgumentException("事件 payload 与 EventType 声明的类型不一致");
        }
        this.payload = checkedPayload;
    }

    /** @return 事件编码、协议版本和 payload 类型 */
    @Contract(pure = true)
    public @NotNull EventType<T> type() {
        return type;
    }

    @Override
    public @NotNull TreeCode code() {
        return type.code();
    }

    @Override
    public int protocolVersion() {
        return type.protocolVersion();
    }

    /** @return 可选的业务幂等或顺序键 */
    @Contract(pure = true)
    public @Nullable String businessKey() {
        return businessKey;
    }

    /** @return 业务事件实际发生时间 */
    @Contract(pure = true)
    public @NotNull Instant occurredAt() {
        return occurredAt;
    }

    /** @return 符合 `EventType.payloadType()` 声明的业务内容 */
    @Contract(pure = true)
    public @NotNull T payload() {
        return payload;
    }
}
