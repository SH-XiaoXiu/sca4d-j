package com.sca4d.system.audit;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 当前请求语言下的一个操作日志模块及其可用动作。 */
public class OperationLogModuleDescription {

    private final String code;
    private final String name;
    private final List<OperationLogActionDescription> actions;

    /**
     * 创建模块筛选项。
     *
     * @param code 参与筛选和持久化的固定模块编码
     * @param name 当前请求语言下的模块名称
     * @param actions 按目录顺序排列的模块动作
     */
    public OperationLogModuleDescription(
            @NotNull String code,
            @NotNull String name,
            @NotNull List<OperationLogActionDescription> actions) {
        this.code = code;
        this.name = name;
        this.actions = List.copyOf(actions);
    }

    /** @return 固定模块编码 */
    public @NotNull String getCode() {
        return code;
    }

    /** @return 当前请求语言下的模块名称 */
    public @NotNull String getName() {
        return name;
    }

    /** @return 按目录顺序排列的模块动作 */
    public @NotNull List<OperationLogActionDescription> getActions() {
        return actions;
    }
}
