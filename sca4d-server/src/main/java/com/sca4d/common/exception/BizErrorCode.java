package com.sca4d.common.exception;

import org.jetbrains.annotations.NotNull;

/**
 * 业务错误码接口。
 *
 * <p>业务模块通过枚举实现该接口，提供数值错误码、消息资源键和响应分类。Web 或消息入口按请求语言解析最终文本。
 */
public interface BizErrorCode {

    /**
     * 业务错误码。
     *
     * @return 数值错误码
     */
    int getCode();

    /**
     * 错误消息资源键。
     *
     * @return 在共用消息资源中同时具有全部支持语言翻译的键
     */
    @NotNull
    String getMessageKey();

    /**
     * 响应分类。
     *
     * @return 通用响应分类
     */
    @NotNull
    ResponseCode getCategory();
}
