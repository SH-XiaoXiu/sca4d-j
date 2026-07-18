package com.sca4d.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.common.resource.ResourceNavigationState;
import com.sca4d.common.resource.ResourceReferenceContext;
import com.sca4d.common.resource.ResourceReferenceKey;
import com.sca4d.entity.SysDictionaryItemEntity;
import com.sca4d.entity.SysDictionaryTypeEntity;
import com.sca4d.entity.SysMenuEntity;
import com.sca4d.system.repository.DictionaryRepository;
import com.sca4d.system.repository.MenuRepository;
import com.sca4d.system.resource.MenuResourceReferenceContributor;
import com.sca4d.system.resource.SystemResourceReferenceContributor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 验证系统菜单和字典资源的名称、存在状态与导航地址解析。 */
class SystemResourceReferenceContributorTest {

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void shouldResolveTypeAndItemWithTwoFixedBatchQueries() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("zh-CN"));
        DictionaryRepository repository = mock(DictionaryRepository.class);
        SysDictionaryTypeEntity type = new SysDictionaryTypeEntity()
                .setId(10L)
                .setCode("vehicle.brand")
                .setNameLabels("{\"zh-CN\":\"车辆品牌\",\"en-US\":\"Vehicle brand\"}")
                .setDeleted(0);
        SysDictionaryItemEntity item = new SysDictionaryItemEntity()
                .setId(20L)
                .setDictTypeId(10L)
                .setItemValue("BYD")
                .setLabelLabels("{\"zh-CN\":\"比亚迪\",\"en-US\":\"BYD\"}")
                .setDeleted(0);
        when(repository.findItemReferences(Set.of(20L))).thenReturn(List.of(item));
        when(repository.findTypeReferences(Set.of(10L))).thenReturn(List.of(type));
        SystemResourceReferenceContributor contributor = new SystemResourceReferenceContributor(
                repository,
                new DictionaryLabelCodec(new ObjectMapper()),
                LocalizedMessagesFixture.create());
        ResourceReferenceKey typeKey = new ResourceReferenceKey(SystemResourceTypes.DICTIONARY, "10");
        ResourceReferenceKey itemKey = new ResourceReferenceKey(SystemResourceTypes.DICTIONARY_ITEM, "20");

        var references = contributor.resolve(
                Set.of(typeKey, itemKey),
                new ResourceReferenceContext(
                        PrincipalType.PLATFORM, 100L, null, Set.of(SystemPermissions.DICTIONARIES_READ)));

        assertThat(references.get(typeKey).getLabel()).isEqualTo("车辆品牌");
        assertThat(references.get(typeKey).getNavigationState()).isEqualTo(ResourceNavigationState.NAVIGABLE);
        assertThat(references.get(typeKey).getPath()).isEqualTo("/system/dictionaries/10");
        assertThat(references.get(itemKey).getLabel()).isEqualTo("比亚迪");
        assertThat(references.get(itemKey).getPath()).isEqualTo("/system/dictionaries/10?itemId=20");
        verify(repository).findItemReferences(Set.of(20L));
        verify(repository).findTypeReferences(Set.of(10L));
    }

    @Test
    void shouldResolveMenuWithOneBatchQueryAndReadPermission() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("en-US"));
        MenuRepository repository = mock(MenuRepository.class);
        SysMenuEntity menu = new SysMenuEntity()
                .setId(30L)
                .setTitleLabels("{\"zh-CN\":\"菜单管理\",\"en-US\":\"Menus\"}")
                .setDeleted(0);
        when(repository.findMenuReferences(Set.of(30L))).thenReturn(List.of(menu));
        when(repository.restoreTitles(menu.getTitleLabels()))
                .thenReturn(new MenuTitles(Map.of("zh-CN", "菜单管理", "en-US", "Menus")));
        MenuResourceReferenceContributor contributor = new MenuResourceReferenceContributor(
                repository, LocalizedMessagesFixture.create());
        ResourceReferenceKey key = new ResourceReferenceKey(SystemResourceTypes.MENU, "30");

        var references = contributor.resolve(
                Set.of(key),
                new ResourceReferenceContext(
                        PrincipalType.PLATFORM, 100L, null, Set.of(SystemPermissions.MENUS_READ)));

        assertThat(references.get(key).getLabel()).isEqualTo("Menus");
        assertThat(references.get(key).getNavigationState()).isEqualTo(ResourceNavigationState.NAVIGABLE);
        assertThat(references.get(key).getPath()).isEqualTo("/system/menus/30");
        verify(repository).findMenuReferences(Set.of(30L));
    }
}
