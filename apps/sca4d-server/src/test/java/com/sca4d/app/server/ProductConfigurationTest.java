package com.sca4d.app.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductConfigurationTest {

    @Test
    @DisplayName("Uses App Product Metadata In OpenAPI")
    void usesAppProductMetadataInOpenApi() {
        TestAccess source = mock(TestAccess.class);
        ProductSettings settings = new ProductSettings(source, source);
        when(source.read(settings.DISPLAY_NAME.code(), SettingOwner.system())).thenReturn(
                SettingValue.read(
                        TreeCode.of("SETTING:TEST"),
                        settings.DISPLAY_NAME.code(),
                        SettingOwner.system(),
                        "Example Product",
                        1,
                        new Version(1),
                        false));
        when(source.read(settings.CONTACT_NAME.code(), SettingOwner.system())).thenReturn(
                SettingValue.read(
                        TreeCode.of("SETTING:TEST"),
                        settings.CONTACT_NAME.code(),
                        SettingOwner.system(),
                        "Example Team",
                        1,
                        new Version(1),
                        false));

        OpenAPI openApi = new Sca4dOpenApiConfiguration().sca4dOpenApi("1.2.3", settings);

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Example Product API");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("1.2.3");
        assertThat(openApi.getInfo().getContact().getName()).isEqualTo("Example Team");
    }

    @Test
    @DisplayName("Rejects Invalid Product Metadata")
    void rejectsInvalidProductMetadata() {
        TestAccess source = mock(TestAccess.class);
        ProductSettings settings = new ProductSettings(source, source);
        assertThatThrownBy(() -> settings.DISPLAY_NAME.codec().decode(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings.TOTP_ISSUER.codec().decode("x".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 测试替身同时实现读取和写入契约，避免引入生产组合接口。 */
    private interface TestAccess extends SettingReader<String>, SettingWriter<String> {
    }
}
