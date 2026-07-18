package com.sca4d.common.auth;

import com.sca4d.common.exception.CommonErrorCode;

/**
 * 当前请求缺少认证授权内容异常。
 */
public class MissingAuthException extends ForbiddenException {

    /**
     * 创建当前请求缺少认证授权内容异常。
     *
     */
    public MissingAuthException() {
        super(CommonErrorCode.AUTHENTICATION_CONTEXT_INCOMPLETE);
    }
}
