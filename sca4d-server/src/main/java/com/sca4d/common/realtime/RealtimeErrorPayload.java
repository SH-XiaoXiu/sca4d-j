package com.sca4d.common.realtime;

import com.sca4d.common.concurrent.ratelimit.RateLimitException;
import com.sca4d.common.exception.BizException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * WebSocket 用户错误队列载荷。
 */
public class RealtimeErrorPayload {

    private int code;
    private String messageKey;
    private List<Object> messageArgs = List.of();
    private String message;
    private Long retryAfterSeconds;
    private String destination;

    public RealtimeErrorPayload() {
    }

    /**
     * 创建尚未绑定客户端语言的实时错误载荷。
     *
     * <p>STOMP 帧处理线程没有可靠的 HTTP 语言信息，因此这里只保留消息键和参数，并把展示消息留空，不使用服务端默认语言生成客户端文本。
     *
     * @param code 固定业务错误码
     * @param messageKey 用于按客户端语言读取错误文本的资源键
     * @param messageArgs 不包含秘密的消息模板参数
     * @param retryAfterSeconds 可选客户端重试秒数
     * @param destination 可选被拒绝的 STOMP 目标
     */
    public RealtimeErrorPayload(
            int code,
            @NotNull String messageKey,
            @NotNull List<Object> messageArgs,
            @Nullable Long retryAfterSeconds,
            @Nullable String destination) {
        this.code = code;
        this.messageKey = messageKey;
        this.messageArgs = List.copyOf(messageArgs);
        this.message = null;
        this.retryAfterSeconds = retryAfterSeconds;
        this.destination = destination;
    }

    /**
     * 从业务异常创建错误载荷。
     *
     * @param exception 业务异常
     * @param destination STOMP 目标地址
     * @return 错误载荷
     */
    public static @NotNull RealtimeErrorPayload from(@NotNull BizException exception, @Nullable String destination) {
        return new RealtimeErrorPayload(
                exception.getCode(), exception.getMessageKey(), exception.getMessageArgs(), null, destination);
    }

    /**
     * 从限流异常创建错误载荷。
     *
     * @param exception 限流异常
     * @param destination STOMP 目标地址
     * @return 错误载荷
     */
    public static @NotNull RealtimeErrorPayload from(
            @NotNull RateLimitException exception, @Nullable String destination) {
        return new RealtimeErrorPayload(
                exception.getCode(),
                exception.getMessageKey(),
                exception.getMessageArgs(),
                exception.getRetryAfterSeconds(),
                destination);
    }

    /** @return 固定业务错误码 */
    public int getCode() {
        return code;
    }

    /** @param code 反序列化恢复的固定业务错误码 */
    public void setCode(int code) {
        this.code = code;
    }

    /** @return 由客户端语言目录解析的固定消息键 */
    public @NotNull String getMessageKey() {
        return messageKey;
    }

    /** @param messageKey 反序列化恢复的固定消息键 */
    public void setMessageKey(@NotNull String messageKey) {
        this.messageKey = messageKey;
    }

    /** @return 保持模板顺序的不可变消息参数 */
    public @NotNull List<Object> getMessageArgs() {
        return messageArgs;
    }

    /** @param messageArgs 反序列化恢复的消息模板参数 */
    public void setMessageArgs(@NotNull List<Object> messageArgs) {
        this.messageArgs = List.copyOf(messageArgs);
    }

    /** @return 可选已本地化文本；未绑定客户端语言时为 {@code null} */
    public @Nullable String getMessage() {
        return message;
    }

    /** @param message 反序列化恢复的可选已本地化文本 */
    public void setMessage(@Nullable String message) {
        this.message = message;
    }

    /** @return 可选客户端重试秒数 */
    public @Nullable Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    /** @param retryAfterSeconds 反序列化恢复的可选重试秒数 */
    public void setRetryAfterSeconds(@Nullable Long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** @return 可选被拒绝的 STOMP 目标 */
    public @Nullable String getDestination() {
        return destination;
    }

    /** @param destination 反序列化恢复的可选 STOMP 目标 */
    public void setDestination(@Nullable String destination) {
        this.destination = destination;
    }
}
