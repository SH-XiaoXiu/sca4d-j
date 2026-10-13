package com.sca4d.core.api.version;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** 一次契约选择的支持状态。 */
public enum CompatibilityStatus {
    /** 当前支持。 */
    SUPPORTED,
    /** 仍可调用但已弃用。 */
    DEPRECATED,
    /** 不允许调用。 */
    BLOCKED,
    /** 旧客户端没有版本声明，应用选择保持原行为。 */
    UNKNOWN;

    /** @return HTTP 和 JSON 使用的小写状态 */
    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
