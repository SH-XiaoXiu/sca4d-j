package com.sca4d.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationChannel;
import com.sca4d.core.api.notification.NotificationCode;
import com.sca4d.core.api.notification.NotificationRecipient;
import com.sca4d.core.api.notification.NotificationRecipientType;
import com.sca4d.core.api.region.Region;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 保存 `notification.requested@2` 已冻结接收人、语言、区域、变量和渠道的线路字段。 */
public final class NotificationRequested {

    private final @NotNull String code;

    private final @NotNull String recipientType;

    private final @NotNull String recipientId;

    private final @NotNull String languageTag;

    private final @Nullable String regionCode;

    private final @NotNull Map<@NotNull String, @NotNull String> variables;

    private final @NotNull List<@NotNull String> channels;

    /** 创建一项只包含平台无关路由事实的通知消息。 */
    @JsonCreator
    public NotificationRequested(
            @JsonProperty("code") @NotNull String code,
            @JsonProperty("recipientType") @NotNull String recipientType,
            @JsonProperty("recipientId") @NotNull String recipientId,
            @JsonProperty("languageTag") @NotNull String languageTag,
            @JsonProperty("regionCode") @Nullable String regionCode,
            @JsonProperty("variables") @NotNull Map<@NotNull String, @NotNull String> variables,
            @JsonProperty("channels") @NotNull List<@NotNull String> channels) {
        Set<NotificationChannel> parsedChannels = Objects.requireNonNull(channels, "通知渠道不能为空")
                .stream()
                .map(NotificationChannel::new)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Notification checked = new Notification(
                new NotificationCode(code),
                new NotificationRecipient(new NotificationRecipientType(recipientType), recipientId),
                Locale.forLanguageTag(Objects.requireNonNull(languageTag, "通知语言标签不能为空")),
                variables,
                regionCode == null ? null : new Region(regionCode),
                parsedChannels);
        this.code = checked.code().value();
        this.recipientType = checked.recipient().type().value();
        this.recipientId = checked.recipient().id();
        this.languageTag = checked.locale().toLanguageTag();
        this.regionCode = checked.region() == null ? null : checked.region().code();
        this.variables = Map.copyOf(checked.variables());
        this.channels = checked.channels().stream().map(NotificationChannel::value).sorted().toList();
    }

    /** 从类型化通知框架已经解析的标准值创建线路消息。 */
    @Contract(pure = true)
    public static @NotNull NotificationRequested from(@NotNull Notification notification) {
        Notification checked = Objects.requireNonNull(notification, "通知不能为空");
        return new NotificationRequested(
                checked.code().value(),
                checked.recipient().type().value(),
                checked.recipient().id(),
                checked.locale().toLanguageTag(),
                checked.region() == null ? null : checked.region().code(),
                checked.variables(),
                checked.channels().stream().map(NotificationChannel::value).toList());
    }

    /** @return 通知稳定编码 */
    @JsonProperty("code")
    public @NotNull String code() {
        return code;
    }

    /** @return 内部接收人类型编码 */
    @JsonProperty("recipientType")
    public @NotNull String recipientType() {
        return recipientType;
    }

    /** @return 内部接收人标识 */
    @JsonProperty("recipientId")
    public @NotNull String recipientId() {
        return recipientId;
    }

    /** @return BCP 47 语言标签 */
    @JsonProperty("languageTag")
    public @NotNull String languageTag() {
        return languageTag;
    }

    /** @return 可选业务区域编码 */
    @JsonProperty("regionCode")
    public @Nullable String regionCode() {
        return regionCode;
    }

    /** @return 不可修改模板变量 */
    @JsonProperty("variables")
    public @NotNull @Unmodifiable Map<@NotNull String, @NotNull String> variables() {
        return variables;
    }

    /** @return 入队前冻结并稳定排序的渠道编码 */
    @JsonProperty("channels")
    public @NotNull @Unmodifiable List<@NotNull String> channels() {
        return channels;
    }

    /** @return 恢复后的标准通知 */
    @Contract(pure = true)
    public @NotNull Notification toNotification() {
        return new Notification(
                new NotificationCode(code),
                new NotificationRecipient(new NotificationRecipientType(recipientType), recipientId),
                Locale.forLanguageTag(languageTag),
                variables,
                regionCode == null ? null : new Region(regionCode),
                channels.stream()
                        .map(NotificationChannel::new)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }
}
