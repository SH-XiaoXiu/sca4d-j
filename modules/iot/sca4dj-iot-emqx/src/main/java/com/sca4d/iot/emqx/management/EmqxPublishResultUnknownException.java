package com.sca4d.iot.emqx.management;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 表示管理端发布请求发生传输中断，调用方无法确认 EMQX 是否已经接受请求。 */
public final class EmqxPublishResultUnknownException extends RuntimeException {

    /**
     * 保留底层传输异常，异常消息不包含请求载荷或 Management API 凭据。
     *
     * @param cause HTTP 请求的底层传输异常
     */
    public EmqxPublishResultUnknownException(@NotNull Throwable cause) {
        super("EMQX 发布请求传输中断，无法确认是否已经接受", Objects.requireNonNull(cause, "传输异常不能为空"));
    }
}
