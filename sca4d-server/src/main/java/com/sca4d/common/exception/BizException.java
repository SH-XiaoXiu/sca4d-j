package com.sca4d.common.exception;

import com.sca4d.common.i18n.MessageReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 业务异常。
 *
 * <p>该异常表示可预期、可安全返回前端的业务失败。异常只保存固定消息键和模板参数，不在业务层解析请求语言；它只接收具体
 * {@link BizErrorCode}，不直接接收 {@link ResponseCode}，避免把响应分类误当作业务错误码。
 */
public class BizException extends RuntimeException {

    private final BizErrorCode errorCode;
    private final MessageReference messageReference;
    private final Object data;

    /**
     * 创建业务异常。
     *
     * @param errorCode 具体业务错误码
     */
    public BizException(@NotNull BizErrorCode errorCode) {
        this(errorCode, MessageReference.of(errorCode.getMessageKey()), null, null);
    }

    /**
     * 创建使用错误码消息键和指定模板参数的业务异常。
     *
     * @param errorCode 具体业务错误码
     * @param messageArgs 消息模板参数；调用方不得传入秘密或技术异常文本
     */
    public BizException(@NotNull BizErrorCode errorCode, @NotNull Object... messageArgs) {
        this(errorCode, MessageReference.of(errorCode.getMessageKey(), messageArgs), null, null);
    }

    /**
     * 创建保留原始异常但只对外使用错误码固定消息键的业务异常。
     *
     * @param errorCode 具体业务错误码
     * @param cause 仅供服务端日志与诊断使用的原始异常
     */
    public BizException(@NotNull BizErrorCode errorCode, @Nullable Throwable cause) {
        this(errorCode, MessageReference.of(errorCode.getMessageKey()), null, cause);
    }

    /**
     * 创建包含消息引用、可选响应数据和原始异常的业务异常。
     *
     * @param errorCode 具体业务错误码
     * @param messageReference 当前失败场景的固定消息引用
     * @param data 可安全进入响应的附加数据
     * @param cause 仅供服务端日志与诊断使用的原始异常
     */
    protected BizException(
            @NotNull BizErrorCode errorCode,
            @NotNull MessageReference messageReference,
            @Nullable Object data,
            @Nullable Throwable cause) {
        super(messageReference.getKey(), cause);
        this.errorCode = errorCode;
        this.messageReference = messageReference;
        this.data = data;
    }

    /**
     * 获取具体业务错误码。
     *
     * @return 业务错误码
     */
    public @NotNull BizErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 获取本次异常对应的数值错误码。
     *
     * @return 错误码数值
     */
    public int getCode() {
        return errorCode.getCode();
    }

    /**
     * 获取响应分类。
     *
     * @return 响应分类
     */
    public @NotNull ResponseCode getResponseCode() {
        return errorCode.getCategory();
    }

    /**
     * 获取当前失败场景的固定消息键。
     *
     * @return HTTP 或消息响应按请求语言解析的消息键
     */
    public @NotNull String getMessageKey() {
        return messageReference.getKey();
    }

    /**
     * 获取消息模板参数。
     *
     * @return 保持调用方顺序的不可变参数集合
     */
    public @NotNull List<Object> getMessageArgs() {
        return messageReference.getArguments();
    }

    /**
     * 获取附加数据。
     *
     * @return 附加数据
     */
    public @Nullable Object getData() {
        return data;
    }
}
