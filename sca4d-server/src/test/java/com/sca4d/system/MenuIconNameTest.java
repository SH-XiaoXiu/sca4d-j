package com.sca4d.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 验证菜单图标名称只接受长度受限的 Lucide PascalCase 导出名称。 */
class MenuIconNameTest {

    @Test
    void shouldTrimAndAcceptLucideExportName() {
        assertThat(MenuIconName.of("  LayoutDashboard  ").value()).isEqualTo("LayoutDashboard");
    }

    @Test
    void shouldRejectLegacyCodeAndInvalidCharacters() {
        assertThatThrownBy(() -> MenuIconName.of("DASHBOARD_ICON"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MenuIconName.of("layout-dashboard"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MenuIconName.of("A".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
