package com.sca4d.core.api.error;

/**
 * 表示可由 HTTP 响应转换器识别的错误码（Error Code）。
 *
 * <p>具体错误码必须由枚举实现。Core 使用 {@link CoreErrorCode}，外围业务模块使用自己的错误码枚举，均交给
 * {@link ErrorCodeCatalog} 校验和索引。
 */
public interface ErrorCode {

    /**
     * 返回全局错误码。
     *
     * @return 全局错误码
     */
    String code();

    /**
     * 返回错误分类。
     *
     * @return 错误分类
     */
    ErrorCategory category();

    /**
     * 返回默认消息键，由外围国际化实现解析。
     *
     * @return 默认消息键，由外围国际化实现解析
     */
    String messageKey();
}
