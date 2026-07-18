package com.sca4d.im;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * IM 会话类型。
 */
public enum ImConversationType implements BaseEnum {

    /** 单聊。 */
    SINGLE("单聊"),
    /** 群聊。 */
    GROUP("群聊");

    private final String value;
    private final String description;

    ImConversationType(@NotNull String description) {
        this.value = BaseEnum.normalize(name());
        this.description = description;
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
     * 根据请求值和成员数量解析会话类型。
     *
     * @param value 请求声明的会话类型
     * @param memberSize 成员数量
     * @return 会话类型
     */
    public static @NotNull ImConversationType resolve(@Nullable ImConversationType value, int memberSize) {
        return value != null ? value : memberSize == 2 ? SINGLE : GROUP;
    }

    /**
     * 判断成员数量是否符合会话类型。
     *
     * @param memberSize 成员数量
     * @return true 表示成员数量合法
     */
    public boolean supportsMemberSize(int memberSize) {
        return switch (this) {
            case SINGLE -> memberSize == 2;
            case GROUP -> memberSize >= 2;
        };
    }
}
