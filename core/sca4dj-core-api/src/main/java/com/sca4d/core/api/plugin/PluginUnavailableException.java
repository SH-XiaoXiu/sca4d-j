package com.sca4d.core.api.plugin;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;

/** 表示公开服务或必需插件扩展当前不能接受调用，不等同于认证通过。 */
public final class PluginUnavailableException extends BusinessException {
    private final String detail;

    /** 创建仅包含目标编码的不可用错误。 */
    public PluginUnavailableException(String message) {
        super(CoreErrorCode.UNAVAILABLE);
        this.detail = message;
    }

    @Override
    public String getMessage() {
        return detail;
    }
}
