package com.sca4d.im;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/**
 * IM 会话成员角色。
 */
public enum ImMemberRole implements BaseEnum {

    /** 群主。 */
    OWNER("群主"),
    /** 普通成员。 */
    MEMBER("普通成员");

    private final String value;
    private final String description;

    ImMemberRole(@NotNull String description) {
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
     * 解析创建会话时的初始成员角色。
     *
     * @param conversationType 会话类型
     * @param creator 是否为创建人
     * @return 成员角色
     */
    public static @NotNull ImMemberRole initialRole(@NotNull ImConversationType conversationType, boolean creator) {
        if (conversationType == ImConversationType.GROUP && creator) {
            return OWNER;
        }
        return MEMBER;
    }
}
