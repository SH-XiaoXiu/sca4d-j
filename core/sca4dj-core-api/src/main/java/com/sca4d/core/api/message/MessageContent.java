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

    public static @NotNull MessageContent text(@NotNull String mediaType, @NotNull String value) {
        return new MessageContent(mediaType, ContentDisposition.INLINE, new TextContent(value), null);
    }

    public static @NotNull MessageContent attachment(
            @NotNull String mediaType,
            byte @NotNull [] value,
            @NotNull String name) {
        return new MessageContent(mediaType, ContentDisposition.ATTACHMENT, new BinaryContent(value), name);
    }

    public static @NotNull MessageContent reference(
            @NotNull String mediaType,
            @NotNull ContentDisposition disposition,
            @NotNull String scheme,
            @NotNull String key,
            @Nullable String name) {
        return new MessageContent(mediaType, disposition, new ReferencedContent(scheme, key), name);
    }

    public @NotNull String mediaType() {
        return mediaType;
    }

    public @NotNull ContentDisposition disposition() {
        return disposition;
    }

    public @NotNull ContentBody body() {
        return body;
    }

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
