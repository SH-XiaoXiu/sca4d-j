package com.sca4d.mail;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.CodeRange;
import com.sca4d.common.exception.ResponseCode;
import org.jetbrains.annotations.NotNull;

/** 邮件发送配置和 SMTP 投递使用的错误码。 */
@CodeRange(start = 2200, end = 2299)
public enum MailErrorCode implements BizErrorCode {
    /** 指定邮件发送配置不存在。 */
    SENDER_NOT_FOUND(2200, "error.mail.sender-not-found", ResponseCode.NOT_FOUND),
    /** 邮件发送配置已被其他请求修改。 */
    SENDER_CHANGED(2201, "error.mail.sender-changed", ResponseCode.PRECONDITION_FAILED),
    /** 启用中的邮件发送配置必须先停用才能删除。 */
    SENDER_ACTIVE(2202, "error.mail.sender-active", ResponseCode.CONFLICT),
    /** SMTP 用户名和密码没有按匿名或认证连接成对配置。 */
    SENDER_AUTH_INVALID(2203, "error.mail.sender-auth-invalid", ResponseCode.BAD_REQUEST),
    /** 停用的邮件发送配置不能投递业务邮件。 */
    SENDER_DISABLED(2204, "error.mail.sender-disabled", ResponseCode.CONFLICT),
    /** SMTP 服务器连接测试失败。 */
    CONNECTION_FAILED(2205, "error.mail.connection-failed", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** SMTP 服务器没有接受当前邮件。 */
    SEND_FAILED(2206, "error.mail.send-failed", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** 指定邮件模板不存在。 */
    TEMPLATE_NOT_FOUND(2207, "error.mail.template-not-found", ResponseCode.NOT_FOUND),
    /** 邮件模板已被其他请求修改。 */
    TEMPLATE_CHANGED(2208, "error.mail.template-changed", ResponseCode.PRECONDITION_FAILED),
    /** 同一 family 和 locale 的模板已经存在。 */
    TEMPLATE_EXISTS(2209, "error.mail.template-exists", ResponseCode.CONFLICT),
    /** 启用中的邮件模板必须先停用才能删除。 */
    TEMPLATE_ACTIVE(2210, "error.mail.template-active", ResponseCode.CONFLICT),
    /** 没有找到指定 family 和 locale 下启用的邮件模板。 */
    ACTIVE_TEMPLATE_NOT_FOUND(2211, "error.mail.active-template-not-found", ResponseCode.NOT_FOUND),
    /** 修改或删除邮件发送配置时没有提供 If-Match。 */
    SENDER_IF_MATCH_REQUIRED(2212, "error.mail.sender-if-match-required", ResponseCode.PRECONDITION_REQUIRED),
    /** 修改或删除邮件模板时没有提供 If-Match。 */
    TEMPLATE_IF_MATCH_REQUIRED(2213, "error.mail.template-if-match-required", ResponseCode.PRECONDITION_REQUIRED),
    /** SMTP 名称、主机或端口无效。 */
    SENDER_CONFIGURATION_INVALID(2214, "error.mail.sender-configuration-invalid", ResponseCode.BAD_REQUEST),
    /** SMTP 发件或回复邮箱无效。 */
    SENDER_ADDRESS_INVALID(2215, "error.mail.sender-address-invalid", ResponseCode.BAD_REQUEST),
    /** 测试邮件收件地址无效。 */
    TEMPLATE_RECIPIENT_INVALID(2216, "error.mail.template-recipient-invalid", ResponseCode.BAD_REQUEST),
    /** 邮件模板 family 无效。 */
    TEMPLATE_FAMILY_INVALID(2217, "error.mail.template-family-invalid", ResponseCode.BAD_REQUEST),
    /** 邮件模板 locale 无效。 */
    TEMPLATE_LOCALE_INVALID(2218, "error.mail.template-locale-invalid", ResponseCode.BAD_REQUEST),
    /** 邮件模板必填内容为空或主题包含换行符。 */
    TEMPLATE_CONTENT_INVALID(2219, "error.mail.template-content-invalid", ResponseCode.BAD_REQUEST),
    /** 邮件模板变量不完整或渲染失败。 */
    TEMPLATE_RENDER_INVALID(2220, "error.mail.template-render-invalid", ResponseCode.BAD_REQUEST);

    private final int code;
    private final String messageKey;
    private final ResponseCode category;

    MailErrorCode(int code, @NotNull String messageKey, @NotNull ResponseCode category) {
        this.code = code;
        this.messageKey = messageKey;
        this.category = category;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public @NotNull String getMessageKey() {
        return messageKey;
    }

    @Override
    public @NotNull ResponseCode getCategory() {
        return category;
    }
}
