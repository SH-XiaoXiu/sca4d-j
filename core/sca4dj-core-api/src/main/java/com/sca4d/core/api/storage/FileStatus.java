package com.sca4d.core.api.storage;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 表示文件从等待上传到对象删除完成的持久化状态。 */
public enum FileStatus implements BaseEnum {
    /** 已创建文件记录，等待客户端上传临时对象。 */
    PENDING("等待上传"),

    /** 上传内容已经校验并转正，等待业务绑定。 */
    READY("等待绑定"),

    /** 文件已经绑定到声明的业务对象。 */
    IN_USE("使用中"),

    /** 文件已到期或被业务释放，等待清理任务领取。 */
    DELETE_PENDING("等待删除"),

    /** 清理任务已经取得删除租约并正在删除对象。 */
    DELETING("正在删除"),

    /** 临时对象和正式对象已经完成幂等删除。 */
    DELETED("已删除"),

    /** 最近一次删除失败，等待下次领取重试。 */
    DELETE_FAILED("删除失败");

    private final @NotNull String description;

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
