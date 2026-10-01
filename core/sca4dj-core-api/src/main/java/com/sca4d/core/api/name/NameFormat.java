package com.sca4d.core.api.name;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/** 在序列化边界选择名称节点的分隔符，格式不参与名称的身份比较。 */
public enum NameFormat {
    /** 操作编码和内部序列化名称使用冒号。 */
    COLON(':'),
    /** 资源类型和目录式分类使用斜杠。 */
    SLASH('/'),
    /** 点式逻辑名称使用点。 */
    DOT('.');

    private final char separator;

    NameFormat(char separator) {
        this.separator = separator;
    }

    /** 返回该序列化格式的分隔符。 */
    public char separator() {
        return separator;
    }

    /** 输出已校验节点；调用方负责选择该边界适用的格式。 */
    public @NotNull String join(@NotNull List<String> nodes) {
        return String.join(String.valueOf(separator), Objects.requireNonNull(nodes));
    }
}
