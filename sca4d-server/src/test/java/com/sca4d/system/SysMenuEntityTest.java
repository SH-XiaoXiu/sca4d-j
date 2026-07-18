package com.sca4d.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybatisflex.annotation.Table;
import com.sca4d.entity.SysMenuEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

/** 验证菜单实体保存多语言标题和固定路由字段。 */
class SysMenuEntityTest {

    @Test
    void shouldMapNavigationMetadataToSystemTable() throws NoSuchFieldException {
        Table table = SysMenuEntity.class.getAnnotation(Table.class);

        assertThat(table).isNotNull();
        assertThat(table.value()).isEqualTo("sys_menu");
        assertThat(SysMenuEntity.class.getDeclaredFields())
                .extracting(Field::getName)
                .contains("permissionCode", "routeCode", "titleLabels", "iconCode")
                .doesNotContain("path", "title")
                .doesNotContain("roleId", "permissionId");
        assertThat(SysMenuEntity.class.getDeclaredField("visible").getType()).isEqualTo(Boolean.class);
        assertThat(SysMenuEntity.class.getDeclaredField("iconCode").getType()).isEqualTo(String.class);
    }
}
