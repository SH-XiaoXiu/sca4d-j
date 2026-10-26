package com.sca4d.core.api.plugin.exception;

import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;

/** 表示公开服务或必需插件扩展当前不能接受调用，不等同于认证通过。 */
public final class PluginUnavailableException extends BusinessException {
    private final String detail;

    /** 创建不可用错误，异常消息使用调用方提供的目标说明。 */
    public PluginUnavailableException(String message) {
        super(CoreErrorCode.UNAVAILABLE);
        this.detail = message;
    }

    @Override
    public String getMessage() {
        return detail;
    }
}
