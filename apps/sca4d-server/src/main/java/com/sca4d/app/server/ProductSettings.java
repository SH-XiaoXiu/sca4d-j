package com.sca4d.app.server;

import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.SettingCodec;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.SystemSetting;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 声明 Sca4d Server 可以由客户管理的产品名称、联系人和 TOTP issuer。 */
@Configuration(proxyBeanMethods = false)
public class ProductSettings {

    private static final SettingGroup GROUP = new SettingGroup("product", "setting.group.product", 10);

    /** 页面和 OpenAPI 使用的产品展示名称。 */
    public static final @NotNull SystemSetting<String> DISPLAY_NAME = new SystemSetting<>(
            "product.display-name",
            1,
            GROUP,
            "setting.product.display-name",
            "setting.product.display-name.description",
            "APP_PRODUCT_DISPLAY_NAME",
            "Sca4d-J",
            SettingCodec.text(100),
            Pipeline.identity(String.class),
            false,
            100);

    /** OpenAPI 显示的联系人名称。 */
    public static final @NotNull SystemSetting<String> CONTACT_NAME = new SystemSetting<>(
            "product.contact-name",
            1,
            GROUP,
            "setting.product.contact-name",
            "setting.product.contact-name.description",
            "APP_PRODUCT_CONTACT_NAME",
            "XiaoXiu",
            SettingCodec.text(100),
            Pipeline.identity(String.class),
            false,
            200);

    /** 身份验证器中显示的 TOTP 发行者。 */
    public static final @NotNull SystemSetting<String> TOTP_ISSUER = new SystemSetting<>(
            "product.totp-issuer",
            1,
            GROUP,
            "setting.product.totp-issuer",
            "setting.product.totp-issuer.description",
            "APP_PRODUCT_TOTP_ISSUER",
            "Sca4d-J",
            SettingCodec.text(64),
            Pipeline.identity(String.class),
            false,
            300);

    /** 创建产品设置注册配置。 */
    public ProductSettings() {
    }

    /** @return 产品展示名称设置 */
    @Bean
    SystemSetting<String> productDisplayNameSetting() {
        return DISPLAY_NAME;
    }

    /** @return 产品联系人设置 */
    @Bean
    SystemSetting<String> productContactNameSetting() {
        return CONTACT_NAME;
    }

    /** @return TOTP issuer 设置 */
    @Bean
    SystemSetting<String> productTotpIssuerSetting() {
        return TOTP_ISSUER;
    }
}
