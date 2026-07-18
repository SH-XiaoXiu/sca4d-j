package com.sca4d.notification;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 通知投递状态。
 */
public enum NotificationDeliveryStatus implements BaseEnum {

    /** 未读。 */
    UNREAD("未读"),
    /** 已读。 */
    READ("已读");

    private final String value;
    private final String description;

    NotificationDeliveryStatus(@NotNull String description) {
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
     * 判断请求参数或数据库字段是否匹配当前投递状态。
     *
     * @param value 请求参数或数据库字段中的字符串
     * @return 匹配时返回 true
     */
    public boolean matches(@Nullable String value) {
        return value != null && this.value.equalsIgnoreCase(value);
    }
}
