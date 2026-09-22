package com.sca4d.core.api.message;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/** 保存 Message 携带的一项媒体内容及其使用方式。 */
public final class MessageContent {

    private final @NotNull String mediaType;

    private final @NotNull ContentDisposition disposition;

    private final @NotNull ContentBody body;

    private final @Nullable String name;

    /** 创建一项消息内容。 */
    public MessageContent(
            @NotNull String mediaType,
            @NotNull ContentDisposition disposition,
            @NotNull ContentBody body,
            @Nullable String name) {
        this.mediaType = mediaType(mediaType);
        this.disposition = Objects.requireNonNull(disposition, "内容使用方式不能为空");
        this.body = Objects.requireNonNull(body, "内容正文不能为空");
        this.name = name == null || name.isBlank() ? null : name.strip();
    }

    /** @return 创建一项内联文本内容 */
    public static @NotNull MessageContent text(@NotNull String mediaType, @NotNull String value) {
        return new MessageContent(mediaType, ContentDisposition.INLINE, new TextContent(value), null);
    }

    /** @return 创建一项内联二进制附件 */
    public static @NotNull MessageContent attachment(
            @NotNull String mediaType,
            byte @NotNull [] value,
            @NotNull String name) {
        return new MessageContent(mediaType, ContentDisposition.ATTACHMENT, new BinaryContent(value), name);
    }

    /** @return 创建一项外部内容引用 */
    public static @NotNull MessageContent reference(
            @NotNull String mediaType,
            @NotNull ContentDisposition disposition,
            @NotNull String scheme,
            @NotNull String key,
            @Nullable String name) {
        return new MessageContent(mediaType, disposition, new ReferencedContent(scheme, key), name);
    }

    /** @return 标准化后的媒体类型 */
    public @NotNull String mediaType() {
        return mediaType;
    }

    /** @return 内容在消息中的使用方式 */
    public @NotNull ContentDisposition disposition() {
        return disposition;
    }

    /** @return 内容正文或外部引用 */
    public @NotNull ContentBody body() {
        return body;
    }

    /** @return 可选的内容名称 */
    public @Nullable String name() {
        return name;
    }

    private static @NotNull String mediaType(@NotNull String value) {
        String checked = Objects.requireNonNull(value, "媒体类型不能为空").strip().toLowerCase(Locale.ROOT);
        if (checked.indexOf('/') <= 0 || checked.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("媒体类型格式无效");
        }
        return checked;
    }
}
