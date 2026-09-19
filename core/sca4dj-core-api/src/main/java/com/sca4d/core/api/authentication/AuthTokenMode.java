package com.sca4d.core.api.authentication;

/** 表示普通 HTTP 请求默认使用的认证 Token 类型。 */
public enum AuthTokenMode {
    /** 每次请求在线读取 Sa-Token Token-Session。 */
    SESSION,

    /** 普通请求验证短效 JWT 投影，标注接口仍可强制使用 Session Token。 */
    JWT
}
