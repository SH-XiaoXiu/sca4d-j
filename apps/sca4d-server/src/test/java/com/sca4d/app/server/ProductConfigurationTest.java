package com.sca4d.app.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.setting.SettingOwner;
import com.sca4d.core.api.setting.SettingValue;
import com.sca4d.core.api.setting.SettingValues;
import com.sca4d.core.api.setting.Settings;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class ProductConfigurationTest {

    @Test
    @DisplayName("Uses App Product Metadata In OpenAPI")
    void usesAppProductMetadataInOpenApi() {
        Settings settings = mock(Settings.class);
        when(settings.get(List.of(ProductSettings.DISPLAY_NAME, ProductSettings.CONTACT_NAME)))
                .thenReturn(
                        new SettingValues(
                                List.of(
                                        new SettingValue<>(
                                                ProductSettings.DISPLAY_NAME,
                                                SettingOwner.system(),
                                                "Example Product",
                                                true,
                                                false,
                                                new Version(1)),
                                        new SettingValue<>(
                                                ProductSettings.CONTACT_NAME,
                                                SettingOwner.system(),
                                                "Example Team",
                                                true,
                                                false,
                                                new Version(1)))));

        OpenAPI openApi = new Sca4dOpenApiConfiguration().sca4dOpenApi("1.2.3", settings);

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Example Product API");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("1.2.3");
        assertThat(openApi.getInfo().getContact().getName()).isEqualTo("Example Team");
    }

    @Test
    @DisplayName("Rejects Invalid Product Metadata")
    void rejectsInvalidProductMetadata() {
        assertThatThrownBy(() -> ProductSettings.DISPLAY_NAME.codec().decode(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductSettings.TOTP_ISSUER.codec().decode("x".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
