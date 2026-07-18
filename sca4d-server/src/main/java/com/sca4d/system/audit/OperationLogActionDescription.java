package com.sca4d.system.audit;

import org.jetbrains.annotations.NotNull;

/** 当前请求语言下的一个操作日志动作选项。 */
public class OperationLogActionDescription {

    private final String code;
    private final String name;

    /**
     * 创建动作选项。
     *
     * @param code 参与筛选和持久化的固定动作编码
     * @param name 当前请求语言下的动作名称
     */
    public OperationLogActionDescription(@NotNull String code, @NotNull String name) {
        this.code = code;
        this.name = name;
    }

    /** @return 固定动作编码 */
    public @NotNull String getCode() {
        return code;
    }

    /** @return 当前请求语言下的动作名称 */
    public @NotNull String getName() {
        return name;
    }
}
