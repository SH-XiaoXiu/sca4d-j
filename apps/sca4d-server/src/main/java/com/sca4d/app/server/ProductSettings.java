package com.sca4d.app.server;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.codec.SettingCodec;
import com.sca4d.core.api.setting.definition.SystemSetting;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 声明 Sca4d Server 可以由客户管理的产品名称、联系人和 TOTP issuer。 */
@Configuration(proxyBeanMethods = false)
public class ProductSettings {

    private static final SettingGroup GROUP = new SettingGroup(TreeCode.of("PRODUCT"), "setting.group.product", 10);

    /** 页面和 OpenAPI 使用的产品展示名称。 */
    public final @NotNull SystemSetting<String> DISPLAY_NAME;

    /** OpenAPI 显示的联系人名称。 */
    public final @NotNull SystemSetting<String> CONTACT_NAME;

    /** 身份验证器中显示的 TOTP 发行者。 */
    public final @NotNull SystemSetting<String> TOTP_ISSUER;

    /** 创建产品设置注册配置。 */
    public ProductSettings(@NotNull SettingReader<String> reader, SettingWriter<String> writer) {
        this.DISPLAY_NAME = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("PRODUCT:DISPLAY_NAME"),
                GROUP,
                "setting.product.display-name",
                "setting.product.display-name.description",
                "PRODUCT_DISPLAY_NAME",
                "Sca4d-J",
                SettingCodec.text(100),
                Pipeline.identity(String.class),
                false,
                100);
        this.CONTACT_NAME = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("PRODUCT:CONTACT_NAME"),
                GROUP,
                "setting.product.contact-name",
                "setting.product.contact-name.description",
                "PRODUCT_CONTACT_NAME",
                "XiaoXiu",
                SettingCodec.text(100),
                Pipeline.identity(String.class),
                false,
                200);
        this.TOTP_ISSUER = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("PRODUCT:TOTP_ISSUER"),
                GROUP,
                "setting.product.totp-issuer",
                "setting.product.totp-issuer.description",
                "PRODUCT_TOTP_ISSUER",
                "Sca4d-J",
                SettingCodec.text(64),
                Pipeline.identity(String.class),
                false,
                300);
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
