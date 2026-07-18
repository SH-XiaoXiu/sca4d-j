package com.sca4d.file;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 文件当前处于上传、可用、已占用、待删除或已删除状态。 */
public enum FileStatus implements BaseEnum {
    /** 等待客户端上传。 */
    PENDING("等待上传"),
    /** 上传完成，等待业务消费。 */
    READY("等待业务消费"),
    /** 已被业务字段消费。 */
    IN_USE("业务使用中"),
    /** 等待删除存储对象。 */
    DELETE_PENDING("等待删除"),
    /** 正在删除存储对象。 */
    DELETING("删除中"),
    /** 删除失败，等待重试。 */
    DELETE_FAILED("删除失败"),
    /** 文件已删除。 */
    DELETED("已删除"),
    /** 临时上传已过期。 */
    EXPIRED("已过期");

    private final String description;

    FileStatus(@NotNull String description) {
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
