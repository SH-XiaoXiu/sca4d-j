package com.sca4d.im;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;

/**
 * IM 消息内容类型。
 */
public enum ImMessageContentType implements BaseEnum {

    /** 文本消息。 */
    TEXT("文本消息"),
    /** 图片消息。 */
    IMAGE("图片消息");

    private final String value;
    private final String description;

    ImMessageContentType(@NotNull String description) {
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
     * 解析消息内容类型，未声明时默认文本。
     *
     * @param value 请求声明的消息类型
     * @return 消息内容类型
     */
    public static @NotNull ImMessageContentType resolve(@Nullable ImMessageContentType value) {
        return value != null ? value : TEXT;
    }

    /**
     * 当前类型是否要求文本内容。
     *
     * @return true 表示必须提交文本内容
     */
    public boolean requiresTextContent() {
        return this == TEXT;
    }

    /**
     * 当前类型是否要求附件文件。
     *
     * @return true 表示必须提交附件文件主键
     */
    public boolean requiresAttachmentFile() {
        return this == IMAGE;
    }

    /**
     * 生成会话最后消息预览。
     *
     * @param content 消息文本内容
     * @return 消息预览
     */
    public @NotNull String preview(@Nullable String content) {
        if (this == IMAGE) {
            return description;
        }
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String value = content.trim();
        return value.length() <= 120 ? value : value.substring(0, 120);
    }

    /**
     * 转换消息文本内容。
     *
     * @param content 原始文本内容
     * @return 转换后的文本；空白文本返回 null
     */
    public @Nullable String normalizeContent(@Nullable String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        return content.trim();
    }
}
