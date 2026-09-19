package com.sca4d.core.spring.web;

/** 表示一个 HTTP 输入字段未满足的约束。 */
public final class FieldViolation {

    private final String field;

    private final String message;

    /**
     * 创建一个字段拒绝原因。
     *
     * @param field 输入字段名称
     * @param message 当前语言的拒绝原因
     */
    public FieldViolation(String field, String message) {
        this.field = field;
        this.message = message;
    }

    /**
     * 返回输入字段名称。
     *
     * @return 输入字段名称
     */
    public String getField() {
        return field;
    }

    /**
     * 返回当前语言的拒绝原因。
     *
     * @return 当前语言的拒绝原因
     */
    public String getMessage() {
        return message;
    }
}
