package com.sca4d.system;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.SysMenuEntity;
import com.sca4d.mapper.SystemMenuMapper;
import com.sca4d.system.repository.MenuRepository;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Map;

/** 验证菜单查询、JSONB 标题读写、排序和乐观锁更新。 */
class MenuRepositoryTest {

    @Test
    void shouldTranslatePostgresqlRouteUniqueViolationAtPersistenceBoundary() {
        SystemMenuMapper mapper = mock(SystemMenuMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(9L);
        when(mapper.insertMenu(any(SysMenuEntity.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate route", uniqueViolation("uk_sys_menu_route")));
        MenuRepository repository = new MenuRepository(mapper, idGenerator, new ObjectMapper());

        assertThatThrownBy(() -> repository.createMenu(
                null,
                "system.menus",
                "system:menus:read",
                new MenuTitles(Map.of("zh-CN", "菜单", "en-US", "Menus")),
                MenuIconName.of("PanelLeft"),
                10,
                true))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> Assertions.assertThat(exception.getMessageKey())
                                .isEqualTo("error.menu.route-occupied"));
    }

    @Test
    void shouldKeepOtherUniqueViolationAsInfrastructureException() {
        SystemMenuMapper mapper = mock(SystemMenuMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(9L);
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "duplicate primary key", uniqueViolation("sys_menu_pkey"));
        when(mapper.insertMenu(any(SysMenuEntity.class))).thenThrow(failure);
        MenuRepository repository = new MenuRepository(mapper, idGenerator, new ObjectMapper());

        assertThatThrownBy(() -> repository.createMenu(
                null,
                null,
                null,
                new MenuTitles(Map.of("zh-CN", "分组", "en-US", "Group")),
                MenuIconName.of("FolderTree"),
                10,
                true))
                .isSameAs(failure);
    }

    @Test
    void shouldKeepNonUniqueIntegrityFailureAsInfrastructureException() {
        SystemMenuMapper mapper = mock(SystemMenuMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(9L);
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "invalid foreign key", new SQLException("invalid foreign key", "23503"));
        when(mapper.insertMenu(any(SysMenuEntity.class))).thenThrow(failure);
        MenuRepository repository = new MenuRepository(mapper, idGenerator, new ObjectMapper());

        assertThatThrownBy(() -> repository.createMenu(
                404L,
                null,
                null,
                new MenuTitles(Map.of("zh-CN", "分组", "en-US", "Group")),
                MenuIconName.of("FolderTree"),
                10,
                true))
                .isSameAs(failure);
    }

    /** @return 携带指定 PostgreSQL 唯一约束名的 23505 异常 */
    private @NotNull PSQLException uniqueViolation(@NotNull String constraint) {
        return new PSQLException(new ServerErrorMessage(
                "SERROR\0C23505\0Mduplicate key\0n" + constraint + "\0\0"));
    }
}
