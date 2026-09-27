package com.sca4d.core.api.identity;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 表示系统以某个稳定领域身份自主执行操作，不随实例启停改变。 */
public class SystemPrincipal implements Principal {

    private final long id;

    private final @NotNull TreeCode code;

    /** 创建数据库已经确认的系统领域身份；数字 ID 不表达权限等级。 */
    public SystemPrincipal(long id, @NotNull TreeCode code) {
        if (id <= 0) {
            throw new IllegalArgumentException("系统 Principal ID 必须为正数");
        }
        this.id = id;
        this.code = Objects.requireNonNull(code, "系统身份编码不能为空");
    }

    @Override
    public long principalId() {
        return id;
    }

    /** 返回代码声明的稳定领域编码。 */
    @Contract(pure = true)
    public @NotNull TreeCode code() {
        return code;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof SystemPrincipal principal
                        && id == principal.id
                        && code.equals(principal.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, code);
    }
}
