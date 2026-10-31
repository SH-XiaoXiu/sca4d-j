package com.sca4d.open.exception;

/** 平台开放模块注册或恢复运行组件时的技术异常。 */
public class OpenException extends RuntimeException {

    /** 创建带原始原因的技术异常。 */
    public OpenException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 创建只带失败说明的技术异常。 */
    public OpenException(String message) {
        super(message);
    }
}
