package com.sca4d.core.api.dictionary;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** 表示一个枚举常量可以作为前端选择项，并声明它所属的字典。 */
public interface Dictionary extends BaseEnum {

    /**
     * 返回当前枚举所属字典的稳定键。
     *
     * <p>同一个枚举的所有常量必须返回相同键，不同枚举不得使用同一个键。
     *
     * @return 全局唯一的点分小写字典键
     */
    @NotNull
    String dictionaryKey();

    /**
     * 返回当前选项的国际化消息键。
     *
     * @return 前端展示当前选项时使用的消息键
     */
    @Contract(pure = true)
    default @NotNull String labelKey() {
        return "dictionary." + dictionaryKey() + "."
                + value().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * 返回数据库是否可以为当前代码字典增加枚举之外的选项。
     *
     * <p>同一个枚举的所有常量必须返回相同结果。默认关闭扩展，避免数据库值绕过 Java 类型限制。
     *
     * @return 允许增加数据库选项时返回 {@code true}
     */
    @Contract(pure = true)
    default boolean allowsDatabaseOptions() {
        return false;
    }
}
