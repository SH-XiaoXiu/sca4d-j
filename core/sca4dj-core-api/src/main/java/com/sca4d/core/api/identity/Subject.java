package com.sca4d.core.api.identity;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 表示完成认证或建立受控调用的具体接入者。 */
public final class Subject {

    private static final Object SYSTEM_ID_LOCK = new Object();

    /** 整个业务系统唯一的 SYSTEM Subject；各实例和模块共享此身份，不对应用户 Session。启动时由 IAM 配置发布 ID。 */
    public static final @NotNull Subject SYSTEM = new Subject(1, SubjectType.SYSTEM);

    /**
     * 发布启动配置确认的 SYSTEM Subject ID；只能把默认值替换为一次正数配置。
     *
     * @param id 数据库已确认的 SYSTEM Subject ID
     */
    public static void configureSystemId(long id) {
        if (id <= 0)
            throw new IllegalArgumentException("SYSTEM Subject ID 必须为正数");
        synchronized (SYSTEM_ID_LOCK) {
            if (SYSTEM.id != 1 && SYSTEM.id != id) {
                throw new IllegalStateException("SYSTEM Subject ID 不能在运行中改变");
            }
            SYSTEM.id = id;
        }
    }

    private volatile long id;

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
        this.type = Objects.requireNonNull(type, "Subject 类型不能为空");
    }

    /**
     * Subject 标识。
     *
     * @return Subject 标识
     */
    @Contract(pure = true)
    public long subjectId() {
        return id;
    }

    /**
     * 创建 Subject 时确定的接入者类型。
     *
     * @return 接入者使用的固定 Subject 类型
     */
    @Contract(pure = true)
    public @NotNull SubjectType type() {
        return type;
    }
}
