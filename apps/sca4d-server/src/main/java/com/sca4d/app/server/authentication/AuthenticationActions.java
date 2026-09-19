package com.sca4d.app.server.authentication;

import com.sca4d.core.api.authentication.AuthenticationActionCode;

/** 定义示例应用要求认证的稳定动作编码。 */
public final class AuthenticationActions {

    /** 建立 Session 前必须完成的登录认证动作编码。 */
    public static final String LOGIN_CODE = "account.login";

    /** 修改当前账号认证方式配置的动作编码。 */
    public static final String AUTHENTICATION_SETTINGS_REPLACE_CODE = "authentication.settings.replace";

    /** 注册新 TOTP 凭据的动作编码。 */
    public static final String TOTP_ENROLL_CODE = "authentication.totp.enroll";

    /** 删除或替换 TOTP 凭据的动作编码。 */
    public static final String TOTP_REPLACE_CODE = "authentication.totp.replace";

    /** 注册新 Passkey 凭据的动作编码。 */
    public static final String PASSKEY_REGISTER_CODE = "authentication.passkey.register";

    /** 删除 Passkey 凭据的动作编码。 */
    public static final String PASSKEY_DELETE_CODE = "authentication.passkey.delete";

    /** 替换当前账号密码凭据的动作编码。 */
    public static final String PASSWORD_REPLACE_CODE = "authentication.password.replace";

    /** 验证并绑定当前 Profile 手机号的动作编码。 */
    public static final String PHONE_VERIFY_CODE = "authentication.phone.verify";

    /** 绑定当前账号联邦身份的动作编码。 */
    public static final String FEDERATED_LINK_CODE = "authentication.federated.link";

    /** 解绑当前账号联邦身份的动作编码。 */
    public static final String FEDERATED_UNLINK_CODE = "authentication.federated.unlink";

    /** 删除平台账号的动作编码。 */
    public static final String ACCOUNT_DELETE_CODE = "account.delete";

    /** 登录服务和认证策略使用的类型化登录动作。 */
    public static final AuthenticationActionCode LOGIN_ACTION = new AuthenticationActionCode(LOGIN_CODE);

    private AuthenticationActions() {
    }
}
