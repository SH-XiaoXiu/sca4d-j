package com.sca4d.core.spring.validation;

/** 表示 BizId 接受的业务标识格式。 */
public enum BizIdMode {
    /** 正整数 ID。 */
    NUM,
    /** 字母或数字开头的固定业务编码。 */
    CODE,
    /** 使用点、下划线或中划线分段的命名空间编码。 */
    NAMESPACED_CODE,
    /** 由注解显式提供正则表达式的格式。 */
    CUSTOM
}
