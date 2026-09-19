package com.sca4d.core.api.permission;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 表示对资源（Resource）执行的动作（Action）。
 *
 * <p>内置常量列出常见动作，不构成完整列表。业务模块可以定义自己的动作，例如设备重命名使用
 * {@code new Action("rename")}，设备控制使用 {@code new Action("control")}。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目禁止使用 Java record，保留普通 class。
public final class Action {

    /** `INVOKE` 的稳定动作键，可用于注解参数。 */
    public static final @NotNull String INVOKE_VALUE = "invoke";

    /** `CREATE` 的稳定动作键，可用于注解参数。 */
    public static final @NotNull String CREATE_VALUE = "create";

    /** `READ` 的稳定动作键，可用于注解参数。 */
    public static final @NotNull String READ_VALUE = "read";

    /** `WRITE` 的稳定动作键，可用于注解参数。 */
    public static final @NotNull String WRITE_VALUE = "write";

    /** `UPDATE` 的稳定动作键，可用于注解参数。 */
    public static final @NotNull String UPDATE_VALUE = "update";

    /** `DELETE` 的稳定动作键，可用于注解参数。 */
    public static final @NotNull String DELETE_VALUE = "delete";

    /** `MANAGE` 的稳定动作键，可用于 Operation 目录和业务动作。 */
    public static final @NotNull String MANAGE_VALUE = "manage";

    /** `OPERATE` 的稳定动作键，可用于 Operation 目录和业务动作。 */
    public static final @NotNull String OPERATE_VALUE = "operate";

    /** 调用固定业务入口。 */
    public static final @NotNull Action INVOKE = new Action(INVOKE_VALUE);

    /** 创建资源。 */
    public static final @NotNull Action CREATE = new Action(CREATE_VALUE);

    /** 读取资源。 */
    public static final @NotNull Action READ = new Action(READ_VALUE);

    /** 写入资源内容。 */
    public static final @NotNull Action WRITE = new Action(WRITE_VALUE);

    /** 更新资源。 */
    public static final @NotNull Action UPDATE = new Action(UPDATE_VALUE);

    /** 删除资源。 */
    public static final @NotNull Action DELETE = new Action(DELETE_VALUE);

    /** 管理资源及其生命周期。 */
    public static final @NotNull Action MANAGE = new Action(MANAGE_VALUE);

    /** 执行业务资源提供的操作。 */
    public static final @NotNull Action OPERATE = new Action(OPERATE_VALUE);

    private final @NotNull String value;

    /**
     * 创建动作。
     *
     * @param value 非空白动作键
     * @throws NullPointerException value 为空时抛出
     * @throws IllegalArgumentException value 为空白文本时抛出
     */
    public Action(@NotNull String value) {
        this.value = TextUtils.requireText(value, "动作键");
    }

    /**
     * 返回动作键。
     *
     * @return 动作键
     */
    public @NotNull String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Action action && value.equals(action.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public @NotNull String toString() {
        return value;
    }
}
