package com.sca4d.common.audit;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 失败审计中保留的异常类型和业务错误消息引用。 */
public class AuditError {

    private String type;
    private String messageKey;
    private List<Object> messageArgs = List.of();

    /** 创建供 Jackson 反序列化逐字段填充的空错误信息。 */
    public AuditError() {
    }

    /**
     * 创建业务调用失败快照。
     *
     * @param type 原始异常类名，异常来源未提供时为 null
     * @param messageKey 业务异常的固定消息键；内部异常不保存
     * @param messageArgs 不包含秘密的消息模板参数
     */
    public AuditError(
            @Nullable String type,
            @Nullable String messageKey,
            @Nullable List<Object> messageArgs) {
        this.type = type;
        this.messageKey = messageKey;
        this.messageArgs = messageArgs == null ? List.of() : List.copyOf(messageArgs);
    }

    /** @return 原始异常类名，历史记录未保存时为 null */
    public @Nullable String getType() {
        return type;
    }

    /** @param type 原始异常类名 */
    public void setType(@Nullable String type) {
        this.type = type;
    }

    /** @return 业务异常固定消息键，内部异常或未提供时为 null */
    public @Nullable String getMessageKey() {
        return messageKey;
    }

    /** @param messageKey 业务异常固定消息键 */
    public void setMessageKey(@Nullable String messageKey) {
        this.messageKey = messageKey;
    }

    /** @return 保持模板顺序且不包含秘密的消息参数 */
    public List<Object> getMessageArgs() {
        return messageArgs;
    }

    /** @param messageArgs 业务异常消息模板参数 */
    public void setMessageArgs(@Nullable List<Object> messageArgs) {
        this.messageArgs = messageArgs == null ? List.of() : List.copyOf(messageArgs);
    }
}
