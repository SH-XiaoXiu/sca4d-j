package com.sca4d.auth.federation;

/** 区分联邦授权结果要用于登录还是绑定外部身份。 */
public enum FederatedAuthorizationPurpose {
    /** 只允许认证引擎把结果用于登录。 */
    LOGIN,

    /** 只允许当前账号绑定流程把结果用于绑定。 */
    LINK
}
