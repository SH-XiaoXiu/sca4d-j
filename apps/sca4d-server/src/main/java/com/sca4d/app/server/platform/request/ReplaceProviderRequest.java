package com.sca4d.app.server.platform.request;

import com.sca4d.auth.federation.FederatedUnlinkedIdentityAction;
import com.sca4d.core.spring.validation.Req;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 接收后台替换联邦 Provider 展示和未绑定身份策略的请求。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 和 setter 绑定 HTTP 请求字段。
public final class ReplaceProviderRequest {
    private boolean loginPageVisible = true;

    @PositiveOrZero
    private int displayOrder;

    @Req.NotNull
    private FederatedUnlinkedIdentityAction unlinkedIdentityAction;

    private boolean autoLinkVerifiedEmail;

    private Map<String, Map<String, String>> i18n = new LinkedHashMap<>();

    private String iconReference;

    /** 创建空请求供 JSON 绑定。 */
    public ReplaceProviderRequest() {
    }

    /** @return 是否显示在登录页 */
    public boolean isLoginPageVisible() {
        return loginPageVisible;
    }

    /** @param value 是否显示在登录页 */
    public void setLoginPageVisible(boolean value) {
        loginPageVisible = value;
    }

    /** @return 登录页排序 */
    public int getDisplayOrder() {
        return displayOrder;
    }

    /** @param value 登录页排序 */
    public void setDisplayOrder(int value) {
        displayOrder = value;
    }

    /** @return 未绑定身份策略 */
    public FederatedUnlinkedIdentityAction getUnlinkedIdentityAction() {
        return Objects.requireNonNull(unlinkedIdentityAction, "未绑定身份策略不能为空");
    }

    /** @param value 未绑定身份策略 */
    public void setUnlinkedIdentityAction(FederatedUnlinkedIdentityAction value) {
        unlinkedIdentityAction = value;
    }

    /** @return 是否按已验证邮箱自动关联 */
    public boolean isAutoLinkVerifiedEmail() {
        return autoLinkVerifiedEmail;
    }

    /** @param value 是否按已验证邮箱自动关联 */
    public void setAutoLinkVerifiedEmail(boolean value) {
        autoLinkVerifiedEmail = value;
    }

    /** @return 多语言名称和按钮文案 */
    public Map<String, Map<String, String>> getI18n() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        i18n.forEach((locale, fields) -> result.put(locale, new LinkedHashMap<>(fields)));
        return result;
    }

    /** @param value 多语言名称和按钮文案 */
    public void setI18n(Map<String, Map<String, String>> value) {
        i18n = new LinkedHashMap<>();
        if (value != null) {
            value.forEach((locale, fields) -> i18n.put(locale, new LinkedHashMap<>(fields)));
        }
    }

    /** @return 图标引用 */
    public String getIconReference() {
        return iconReference;
    }

    /** @param value 图标引用 */
    public void setIconReference(String value) {
        iconReference = value;
    }
}
