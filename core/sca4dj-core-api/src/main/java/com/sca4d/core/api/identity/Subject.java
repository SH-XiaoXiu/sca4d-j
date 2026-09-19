package com.sca4d.core.api.identity;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** 表示完成认证或建立受控调用的具体接入者。 */
public final class Subject {

    /** 系统任务使用的固定 Subject，不对应用户 Session 或 IAM 数据行。 */
    public static final @NotNull Subject SYSTEM = new Subject(1, SubjectType.SYSTEM);

    private final long id;

    private final @NotNull SubjectType type;

    /**
     * 创建接入者。
     *
     * @param id Subject 标识
     * @param type 固定接入者类型
     */
    public Subject(long id, @NotNull SubjectType type) {
        if (id <= 0) {
            throw new IllegalArgumentException("Subject 标识必须为正数");
        }
        this.id = id;
        this.type = java.util.Objects.requireNonNull(type, "Subject 类型不能为空");
    }

    /**
     * 读取接入者的标识。
     *
     * @return Subject 标识
     */
    @Contract(pure = true)
    public long subjectId() {
        return id;
    }

    /**
     * 读取创建 Subject 时确定的接入者类型。
     *
     * @return 接入者使用的固定 Subject 类型
     */
    @Contract(pure = true)
    public @NotNull SubjectType type() {
        return type;
    }
}
