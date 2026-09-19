package com.sca4d.core.api.error;

/** 表示 Core 和业务模块使用的错误分类。 */
public enum ErrorCategory {
    /** 输入不满足业务要求。 */
    VALIDATION,
    /** 缺少或无法确认认证身份。 */
    AUTHENTICATION,
    /** 当前业务主体没有执行权限。 */
    PERMISSION,
    /** 目标对象不存在。 */
    NOT_FOUND,
    /** 当前状态与操作发生冲突。 */
    CONFLICT,
    /** 调用超过入口允许的频率。 */
    RATE_LIMIT,
    /** 外部依赖暂时不可用。 */
    UNAVAILABLE,
    /** 未归类的内部错误。 */
    INTERNAL
}
