package com.sca4d.core.api.notification;

import com.sca4d.core.api.code.TreeCode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * 在应用启动时声明一种通知允许的渠道、Java 默认值和模板变量。
 */
public final class NotificationDefinition<T> {

    private final @NotNull Set<@NotNull TreeCode> requiredChannels = new LinkedHashSet<>();

    private final @NotNull Map<@NotNull TreeCode, @NotNull Boolean> optionalChannels = new LinkedHashMap<>();

    private final @NotNull Map<@NotNull String, @NotNull BiFunction<T, Locale, String>> variables = new LinkedHashMap<>();

    private boolean frozen;

    /**
     * 声明一个不能被设置关闭的必发渠道。
     */
    public void required(@NotNull TreeCode channel) {
        requireMutable();
        TreeCode checked = Objects.requireNonNull(channel, "通知渠道不能为空");
        if (requiredChannels.contains(checked) || optionalChannels.containsKey(checked)) {
            throw new IllegalArgumentException("重复声明通知渠道: " + checked);
        }
        requiredChannels.add(checked);
    }

    /**
     * 声明一个可以由系统默认值和接收人设置控制的渠道。
     */
    public void optional(@NotNull TreeCode channel, boolean defaultEnabled) {
        requireMutable();
        TreeCode checked = Objects.requireNonNull(channel, "通知渠道不能为空");
        if (requiredChannels.contains(checked) || optionalChannels.containsKey(checked)) {
            throw new IllegalArgumentException("重复声明通知渠道: " + checked);
        }
        optionalChannels.put(checked, defaultEnabled);
    }

    /**
     * 声明统一模板可以读取的一个变量。
     *
     * @param name     模板使用的稳定变量名
     * @param resolver 按领域通知对象和最终 Locale 生成非敏感文本值的回调
     */
    public void variable(
            @NotNull String name,
            @NotNull BiFunction<T, Locale, String> resolver) {
        requireMutable();
        String checkedName = Notification.requireVariableName(name);
        if (variables.putIfAbsent(checkedName, Objects.requireNonNull(resolver, "通知变量回调不能为空")) != null) {
            throw new IllegalArgumentException("重复声明通知变量: " + checkedName);
        }
    }

    /**
     * @return 不能被系统或接收人设置关闭的渠道
     */
    public @NotNull @Unmodifiable Set<@NotNull TreeCode> requiredChannels() {
        return Set.copyOf(requiredChannels);
    }

    /**
     * @return 可选渠道及其 Java 初始默认值
     */
    public @NotNull @Unmodifiable Map<@NotNull TreeCode, @NotNull Boolean> optionalChannels() {
        return Map.copyOf(optionalChannels);
    }

    /**
     * @return 模板变量及其领域值解析回调
     */
    public @NotNull @Unmodifiable Map<@NotNull String, @NotNull BiFunction<T, Locale, String>> variables() {
        return Map.copyOf(variables);
    }

    /**
     * 完成 `NotificationType` 创建后禁止继续修改声明。
     */
    @ApiStatus.Internal
    void freeze() {
        if (requiredChannels.isEmpty() && optionalChannels.isEmpty()) {
            throw new IllegalArgumentException("通知类型至少需要声明一个渠道");
        }
        frozen = true;
    }

    /**
     * 拒绝在 NotificationType 创建完成后继续改变公开契约。
     */
    private void requireMutable() {
        if (frozen) {
            throw new IllegalStateException("通知类型定义已经完成");
        }
    }
}
