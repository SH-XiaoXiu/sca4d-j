package com.sca4d.audit.payload;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** 保存一份准备写入数据库的 payload 类型、版本和 JSON。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
@ApiStatus.Internal
public class EncodedAuditPayload {
    private final @NotNull String json;

    private final @NotNull String type;

    private final int version;

    public EncodedAuditPayload(@NotNull String type, int version, @NotNull String json) {
        this.type = type;
        this.version = version;
        this.json = json;
    }

    /** @return payload 的稳定类型 */
    public @NotNull String type() {
        return type;
    }

    /** @return payload JSON 的正数版本 */
    public int version() {
        return version;
    }

    /** @return 已经完成脱敏和长度限制的 JSON */
    public @NotNull String json() {
        return json;
    }
}
