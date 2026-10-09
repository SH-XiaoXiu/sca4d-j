package com.sca4d.core.api.job;

/** 指定 Job 是否保存独立的持久化运行记录。 */
public enum JobRunRecording {
    /** 保存 Run、节点、尝试和结果，支持恢复与查询。 */
    PERSISTED,
    /** 不保存 Job Run；失败由触发方或业务状态再次发现。 */
    NONE
}
