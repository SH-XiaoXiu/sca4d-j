package com.sca4d.common.validation;

/**
 * 业务标识校验模式。
 */
public enum BizIdMode {
    /** 正整数 ID。 */
    NUM,
    /** 固定业务编码。 */
    CODE,
    /** 带命名空间的固定业务编码。 */
    NAMESPACED_CODE,
    /** 自定义正则。 */
    CUSTOM
}
