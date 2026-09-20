package com.sca4d.core.api.storage;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;

/** 保存申请上传时已经确认的用途、名称、媒体类型和字节数。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class FileUploadIntent {
    private final @NotNull FileUse use;

    private final @NotNull String filename;

    private final @NotNull String contentType;

    private final long sizeBytes;

    /**
     * 创建上传申请中由调用方声明且后续不能修改的内容信息。
     *
     * @param use 文件准备绑定的业务用途
     * @param filename 调用方提供的原始文件名；只保存为元数据
     * @param contentType 调用方声明的媒体类型
     * @param sizeBytes 调用方声明的精确正数字节数
     * @throws IllegalArgumentException 名称、媒体类型或字节数无效时抛出
     */
    public FileUploadIntent(
            @NotNull FileUse use,
            @NotNull String filename,
            @NotNull String contentType,
            long sizeBytes) {
        this.use = Objects.requireNonNull(use, "文件用途不能为空");
        this.filename = required(filename, 255, "文件名");
        String checkedContentType = required(contentType, 128, "文件媒体类型");
        if (checkedContentType.indexOf('/') <= 0
                || checkedContentType.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("文件媒体类型格式无效");
        }
        this.contentType = checkedContentType.toLowerCase(Locale.ROOT);
        if (sizeBytes <= 0)
            throw new IllegalArgumentException("文件字节数必须为正数");
        this.sizeBytes = sizeBytes;
    }

    /**
     * 返回文件准备绑定的业务用途。
     *
     * @return 文件用途
     */
    public @NotNull FileUse use() {
        return use;
    }

    /**
     * 返回调用方提供、只作为元数据保存的原始文件名。
     *
     * @return 原始文件名
     */
    public @NotNull String filename() {
        return filename;
    }

    /**
     * 返回调用方声明并规范为小写的媒体类型。
     *
     * @return 声明媒体类型
     */
    public @NotNull String contentType() {
        return contentType;
    }

    /**
     * 返回申请上传的精确字节数。
     *
     * @return 正数字节数
     */
    public long sizeBytes() {
        return sizeBytes;
    }

    /** 校验并返回去除首尾空白后的必填文本。 */
    private static @NotNull String required(@NotNull String value, int maxLength, @NotNull String name) {
        String checked = Objects.requireNonNull(value, name + "不能为空").trim();
        if (checked.isEmpty() || checked.length() > maxLength)
            throw new IllegalArgumentException(name + "长度必须在 1 到 " + maxLength + " 之间");
        return checked;
    }
}
