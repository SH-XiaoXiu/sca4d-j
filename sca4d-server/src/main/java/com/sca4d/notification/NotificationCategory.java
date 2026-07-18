package com.sca4d.notification;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;

/**
 * 通知分类。
 */
public enum NotificationCategory implements BaseEnum {

    /** 系统通知。 */
    SYSTEM("系统通知");

    private final String value;
    private final String description;

    NotificationCategory(@NotNull String description) {
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
     * 解析通知分类，未指定时使用系统通知。
     *
     * @param value 请求分类
     * @return 通知分类
     */
    public static @NotNull NotificationCategory resolve(@Nullable String value) {
        if (!StringUtils.hasText(value)) {
            return SYSTEM;
        }
        return BaseEnum.fromValue(NotificationCategory.class, value);
    }
}
