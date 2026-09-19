package com.sca4d.notification;

import com.sca4d.core.api.notification.NotificationCode;
import com.sca4d.core.api.notification.NotificationType;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 校验并保存当前应用安装的类型化通知定义。 */
public final class NotificationTypeCatalog {

    private final @NotNull Map<@NotNull NotificationCode, @NotNull NotificationType<?>> types;

    /** @param types Spring 容器中安装的全部通知类型 */
    public NotificationTypeCatalog(@NotNull Collection<? extends NotificationType<?>> types) {
        Map<NotificationCode, NotificationType<?>> checked = new LinkedHashMap<>();
        for (NotificationType<?> type : Objects.requireNonNull(types, "通知类型不能为空")) {
            NotificationType<?> value = Objects.requireNonNull(type, "通知类型不能包含 null");
            if (checked.putIfAbsent(value.code(), value) != null) {
                throw new IllegalArgumentException("重复通知类型: " + value.code());
            }
        }
        this.types = Map.copyOf(checked);
    }

    /** @return 当前应用安装的不可修改通知类型列表 */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull NotificationType<?>> types() {
        return List.copyOf(types.values());
    }

    /** 要求调用方使用当前 Spring 应用注册的同一个通知类型对象。 */
    public <T> @NotNull NotificationType<T> require(@NotNull NotificationType<T> type) {
        NotificationType<T> checked = Objects.requireNonNull(type, "通知类型不能为空");
        if (types.get(checked.code()) != checked) {
            throw new IllegalArgumentException("通知类型未在当前应用注册: " + checked.code());
        }
        return checked;
    }
}
