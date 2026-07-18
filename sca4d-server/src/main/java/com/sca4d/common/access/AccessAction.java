package com.sca4d.common.access;

import com.sca4d.common.dictionary.CodeDictionary;
import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/**
 * 资源范围使用的固定访问等级。
 *
 * <p>具体业务动作由权限码表达；访问等级只决定当前 Principal 可以作用的资源范围，避免业务模块动作退化为开放字符串。
 */
@CodeDictionary(code = "common.access-action", nameKey = "dictionary.common.access-action")
public enum AccessAction implements BaseEnum {
    /** 查询、详情和导出使用的读取范围。 */
    READ("READ", "读取", 1),
    /** 修改、控制和状态变更使用的写入范围。 */
    WRITE("WRITE", "写入", 2),
    /** 删除、授权和所有权转移使用的管理范围。 */
    MANAGE("MANAGE", "管理", 3);

    private final String value;
    private final String description;
    private final int level;

    AccessAction(@NotNull String value, @NotNull String description, int level) {
        this.value = value;
        this.description = description;
        this.level = level;
    }

    @Override
    public @NotNull String value() {
        return value;
    }

    @Override
    public @NotNull String description() {
        return description;
    }

    /**
     * 判断当前访问等级是否覆盖 Repository 要求的最低等级。
     *
     * <p>高等级可以执行低等级允许的读取，例如写入前读取资源版本；低等级不能反向执行写入或管理操作。
     *
     * @param required Repository 当前操作要求的最低等级
     * @return 当前等级覆盖最低等级时返回 true
     */
    public boolean permits(@NotNull AccessAction required) {
        return level >= required.level;
    }
}
