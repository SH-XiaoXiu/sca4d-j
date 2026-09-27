package com.sca4d.notification.email;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/** 保存一封同步 Email 要附带的文件名、媒体类型和完整内容。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class EmailAttachment {
    private final @NotNull String filename;

    private final @NotNull String contentType;

    private final byte @NotNull [] content;

    /** 创建一个不可修改的 Email 附件。 */
    public EmailAttachment(@NotNull String filename, @NotNull String contentType, byte @NotNull [] content) {
        this.filename = required(filename, 255, "附件名");
        this.contentType = required(contentType, 128, "附件媒体类型");
        byte[] checked = Objects.requireNonNull(content, "附件内容不能为空");
        if (checked.length == 0)
            throw new IllegalArgumentException("附件内容不能为空");
        this.content = Arrays.copyOf(checked, checked.length);
    }

    /** @return 附件文件名 */
    public @NotNull String filename() {
        return filename;
    }

    /** @return 附件媒体类型 */
    public @NotNull String contentType() {
        return contentType;
    }

    /** @return 附件内容的独立副本 */
    public byte @NotNull [] content() {
        return Arrays.copyOf(content, content.length);
    }

    /** @return 附件内容字节数；不会复制内容数组 */
    public int size() {
        return content.length;
    }

    private static @NotNull String required(@NotNull String value, int maximum, @NotNull String name) {
        String checked = Objects.requireNonNull(value, name + "不能为空").strip();
        if (checked.isEmpty() || checked.length() > maximum || checked.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + "不合法");
        }
        return checked;
    }
}
