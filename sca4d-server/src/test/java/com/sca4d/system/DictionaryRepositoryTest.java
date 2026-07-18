package com.sca4d.system;

import static com.sca4d.common.dictionary.DictionaryTone.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.access.AccessAction;
import com.sca4d.common.access.ResourceAccess;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.dictionary.CodeDictionaryCatalog;
import com.sca4d.common.dictionary.DictionarySource;
import com.sca4d.common.dictionary.DictionaryTone;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.SysDictionaryItemEntity;
import com.sca4d.entity.SysDictionaryTypeEntity;
import com.sca4d.mapper.SysDictionaryItemMapper;
import com.sca4d.mapper.SystemDictionaryMapper;
import com.sca4d.system.repository.DictionaryRepository;
import com.sca4d.tenant.policy.TenantAccessCriteria;
import dictionaryfixture.sync.ExtensibleDictionary;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 验证字典类型、字典项、代码同步和唯一性锁的数据库操作。 */
class DictionaryRepositoryTest {

    @Test
    void shouldPreserveEditableTypeAndItemSortingDuringCodeSynchronization() {
        SystemDictionaryMapper mapper = mock(SystemDictionaryMapper.class);
        SysDictionaryItemMapper itemMapper = mock(SysDictionaryItemMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        DictionaryRepository repository = new DictionaryRepository(mapper, itemMapper, idGenerator, new DictionaryLabelCodec(new ObjectMapper()), access());
        SysDictionaryTypeEntity existingType = new SysDictionaryTypeEntity()
                .setId(11L)
                .setCode("system.dictionary-status")
                .setKind(DictionaryKind.CODE_ENUM)
                .setSortOrder(91)
                .setDeleted(1);
        when(mapper.selectGlobalDictionaryTypes()).thenReturn(List.of(existingType));
        when(mapper.selectDictionaryItems(List.of(11L)))
                .thenReturn(List.of(
                        new SysDictionaryItemEntity()
                                .setId(21L)
                                .setDictTypeId(11L)
                                .setItemValue("ACTIVE")
                                .setSortOrder(42)
                                .setDeleted(1),
                        new SysDictionaryItemEntity()
                                .setId(22L)
                                .setDictTypeId(11L)
                                .setItemValue("DISABLED")
                                .setSortOrder(7)
                                .setDeleted(0)));

        repository.synchronizeCodeCatalog(new CodeDictionaryCatalog(List.of(DictionaryStatus.class)));

        ArgumentCaptor<List<SysDictionaryTypeEntity>> typeCaptor = dictionaryTypeListCaptor();
        ArgumentCaptor<List<SysDictionaryItemEntity>> itemCaptor = dictionaryItemListCaptor();
        verify(mapper).lockCodeDictionaryCatalog();
        verify(mapper).selectGlobalDictionaryTypes();
        verify(mapper).selectDictionaryItems(List.of(11L));
        verify(mapper).upsertCodeDictionaryTypes(typeCaptor.capture());
        verify(mapper).upsertCodeDictionaryItems(itemCaptor.capture());
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(idGenerator);

        assertThat(typeCaptor.getValue().getFirst().getSortOrder()).isEqualTo(91);
        assertThat(typeCaptor.getValue().getFirst().getDeleted()).isZero();
        assertThat(itemCaptor.getValue())
                .extracting(item -> item.getItemValue() + ":" + item.getSortOrder() + ":" + item.getDeleted())
                .containsExactly("ACTIVE:42:0", "DISABLED:7:0");
    }

    @Test
    void shouldUseCodeSortingOnlyForFirstProjectionAndKeepMapperCallsConstant() {
        SystemDictionaryMapper mapper = mock(SystemDictionaryMapper.class);
        SysDictionaryItemMapper itemMapper = mock(SysDictionaryItemMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        DictionaryRepository repository = new DictionaryRepository(mapper, itemMapper, idGenerator, new DictionaryLabelCodec(new ObjectMapper()), access());
        when(mapper.selectGlobalDictionaryTypes()).thenReturn(List.of());
        when(idGenerator.nextId()).thenReturn(101L, 201L, 202L);

        repository.synchronizeCodeCatalog(new CodeDictionaryCatalog(List.of(DictionaryStatus.class)));

        ArgumentCaptor<List<SysDictionaryTypeEntity>> typeCaptor = dictionaryTypeListCaptor();
        ArgumentCaptor<List<SysDictionaryItemEntity>> itemCaptor = dictionaryItemListCaptor();
        verify(mapper).lockCodeDictionaryCatalog();
        verify(mapper).selectGlobalDictionaryTypes();
        verify(mapper, never()).selectDictionaryItems(ArgumentMatchers.anyList());
        verify(mapper).upsertCodeDictionaryTypes(typeCaptor.capture());
        verify(mapper).upsertCodeDictionaryItems(itemCaptor.capture());
        verifyNoMoreInteractions(mapper);

        assertThat(typeCaptor.getValue().getFirst().getId()).isEqualTo(101L);
        assertThat(itemCaptor.getValue())
                .extracting(SysDictionaryItemEntity::getSortOrder)
                .containsExactly(10, 20);
    }

    @Test
    void shouldClaimExtensibleReservedValueWithoutOverwritingManagedPresentation() {
        SystemDictionaryMapper mapper = mock(SystemDictionaryMapper.class);
        SysDictionaryItemMapper itemMapper = mock(SysDictionaryItemMapper.class);
        DictionaryRepository repository = new DictionaryRepository(
                mapper,
                itemMapper,
                mock(IdGenerator.class),
                new DictionaryLabelCodec(new ObjectMapper()),
                access());
        SysDictionaryTypeEntity existingType = new SysDictionaryTypeEntity()
                .setId(11L)
                .setCode("fixture.extensible")
                .setKind(DictionaryKind.BUSINESS_CODE)
                .setSource(DictionarySource.CONFIGURATION)
                .setNameLabels("{\"zh-CN\":\"后台名称\"}")
                .setStatus(DictionaryStatus.DISABLED)
                .setSortOrder(91)
                .setDeleted(0);
        SysDictionaryItemEntity reserved = new SysDictionaryItemEntity()
                .setId(21L)
                .setDictTypeId(11L)
                .setItemValue("RESERVED")
                .setSource(DictionarySource.CONFIGURATION)
                .setLabelLabels("{\"zh-CN\":\"后台标签\"}")
                .setTone(DictionaryTone.DANGER)
                .setSelectable(false)
                .setStatus(DictionaryStatus.DISABLED)
                .setSortOrder(42)
                .setDeleted(0);
        SysDictionaryItemEntity ordinary = new SysDictionaryItemEntity()
                .setId(22L)
                .setDictTypeId(11L)
                .setItemValue("ORDINARY")
                .setSource(DictionarySource.CONFIGURATION)
                .setLabelLabels("{\"zh-CN\":\"普通编码\"}")
                .setTone(DictionaryTone.DEFAULT)
                .setSelectable(true)
                .setStatus(DictionaryStatus.ACTIVE)
                .setSortOrder(50)
                .setDeleted(0);
        when(mapper.selectGlobalDictionaryTypes()).thenReturn(List.of(existingType));
        when(mapper.selectDictionaryItems(List.of(11L))).thenReturn(List.of(reserved, ordinary));

        repository.synchronizeCodeCatalog(new CodeDictionaryCatalog(List.of(ExtensibleDictionary.class)));

        ArgumentCaptor<List<SysDictionaryTypeEntity>> typeCaptor = dictionaryTypeListCaptor();
        ArgumentCaptor<List<SysDictionaryItemEntity>> itemCaptor = dictionaryItemListCaptor();
        verify(mapper).upsertCodeDictionaryTypes(typeCaptor.capture());
        verify(mapper).upsertCodeDictionaryItems(itemCaptor.capture());
        assertThat(typeCaptor.getValue().getFirst())
                .satisfies(type -> {
                    assertThat(type.getKind()).isEqualTo(DictionaryKind.BUSINESS_CODE);
                    assertThat(type.getSource()).isEqualTo(DictionarySource.CODE);
                    assertThat(type.getNameLabels()).contains("后台名称");
                    assertThat(type.getStatus()).isEqualTo(DictionaryStatus.DISABLED);
                    assertThat(type.getSortOrder()).isEqualTo(91);
                });
        assertThat(itemCaptor.getValue()).hasSize(1);
        assertThat(itemCaptor.getValue().getFirst())
                .satisfies(item -> {
                    assertThat(item.getItemValue()).isEqualTo("RESERVED");
                    assertThat(item.getSource()).isEqualTo(DictionarySource.CODE);
                    assertThat(item.getSourceRef()).endsWith("ExtensibleDictionary#RESERVED");
                    assertThat(item.getLabelLabels()).contains("后台标签");
                    assertThat(item.getTone()).isEqualTo(DictionaryTone.DANGER);
                    assertThat(item.getStatus()).isEqualTo(DictionaryStatus.DISABLED);
                    assertThat(item.getSortOrder()).isEqualTo(42);
                });
    }

    @Test
    void shouldReleaseRemovedExtensibleDefinitionAsConfiguration() {
        SystemDictionaryMapper mapper = mock(SystemDictionaryMapper.class);
        DictionaryRepository repository = new DictionaryRepository(
                mapper,
                mock(SysDictionaryItemMapper.class),
                mock(IdGenerator.class),
                new DictionaryLabelCodec(new ObjectMapper()),
                access());
        SysDictionaryTypeEntity existingType = new SysDictionaryTypeEntity()
                .setId(11L)
                .setCode("fixture.extensible")
                .setKind(DictionaryKind.BUSINESS_CODE)
                .setSource(DictionarySource.CODE)
                .setNameKey("fixture.extensible")
                .setNameLabels("{}")
                .setStatus(DictionaryStatus.ACTIVE)
                .setSortOrder(10)
                .setSourceRef(ExtensibleDictionary.class.getName())
                .setDeleted(0);
        SysDictionaryItemEntity reserved = new SysDictionaryItemEntity()
                .setId(21L)
                .setDictTypeId(11L)
                .setItemValue("RESERVED")
                .setSource(DictionarySource.CODE)
                .setSourceRef(ExtensibleDictionary.class.getName() + "#RESERVED")
                .setLabelKey("fixture.extensible.reserved")
                .setLabelLabels("{}")
                .setTone(DictionaryTone.DEFAULT)
                .setSelectable(true)
                .setStatus(DictionaryStatus.ACTIVE)
                .setSortOrder(10)
                .setDeleted(0);
        when(mapper.selectGlobalDictionaryTypes()).thenReturn(List.of(existingType));
        when(mapper.selectDictionaryItems(List.of(11L))).thenReturn(List.of(reserved));

        repository.synchronizeCodeCatalog(new CodeDictionaryCatalog(List.of()));

        ArgumentCaptor<List<SysDictionaryTypeEntity>> typeCaptor = dictionaryTypeListCaptor();
        ArgumentCaptor<List<SysDictionaryItemEntity>> itemCaptor = dictionaryItemListCaptor();
        verify(mapper).upsertCodeDictionaryTypes(typeCaptor.capture());
        verify(mapper).upsertCodeDictionaryItems(itemCaptor.capture());
        assertThat(typeCaptor.getValue().getFirst().getSource()).isEqualTo(DictionarySource.CONFIGURATION);
        assertThat(typeCaptor.getValue().getFirst().getSourceRef()).isNull();
        assertThat(itemCaptor.getValue().getFirst().getSource()).isEqualTo(DictionarySource.CONFIGURATION);
        assertThat(itemCaptor.getValue().getFirst().getSourceRef()).isNull();
        assertThat(itemCaptor.getValue().getFirst().getDeleted()).isZero();
    }

    @Test
    void shouldLogicallyDeleteRemovedClosedDefinition() {
        SystemDictionaryMapper mapper = mock(SystemDictionaryMapper.class);
        DictionaryRepository repository = new DictionaryRepository(
                mapper,
                mock(SysDictionaryItemMapper.class),
                mock(IdGenerator.class),
                new DictionaryLabelCodec(new ObjectMapper()),
                access());
        SysDictionaryTypeEntity existingType = new SysDictionaryTypeEntity()
                .setId(11L)
                .setCode("fixture.closed")
                .setKind(DictionaryKind.CODE_ENUM)
                .setSource(DictionarySource.CODE)
                .setNameKey("fixture.closed")
                .setNameLabels("{}")
                .setStatus(DictionaryStatus.ACTIVE)
                .setSortOrder(10)
                .setSourceRef("fixture.Closed")
                .setDeleted(0);
        SysDictionaryItemEntity existingItem = new SysDictionaryItemEntity()
                .setId(21L)
                .setDictTypeId(11L)
                .setItemValue("ACTIVE")
                .setSource(DictionarySource.CODE)
                .setSourceRef("fixture.Closed#ACTIVE")
                .setLabelKey("fixture.closed.active")
                .setLabelLabels("{}")
                .setTone(DictionaryTone.DEFAULT)
                .setSelectable(true)
                .setStatus(DictionaryStatus.ACTIVE)
                .setSortOrder(10)
                .setDeleted(0);
        when(mapper.selectGlobalDictionaryTypes()).thenReturn(List.of(existingType));
        when(mapper.selectDictionaryItems(List.of(11L))).thenReturn(List.of(existingItem));

        repository.synchronizeCodeCatalog(new CodeDictionaryCatalog(List.of()));

        ArgumentCaptor<List<SysDictionaryTypeEntity>> typeCaptor = dictionaryTypeListCaptor();
        ArgumentCaptor<List<SysDictionaryItemEntity>> itemCaptor = dictionaryItemListCaptor();
        verify(mapper).upsertCodeDictionaryTypes(typeCaptor.capture());
        verify(mapper).upsertCodeDictionaryItems(itemCaptor.capture());
        assertThat(typeCaptor.getValue().getFirst().getStatus()).isEqualTo(DictionaryStatus.DISABLED);
        assertThat(typeCaptor.getValue().getFirst().getDeleted()).isEqualTo(1);
        assertThat(itemCaptor.getValue().getFirst().getStatus()).isEqualTo(DictionaryStatus.DISABLED);
        assertThat(itemCaptor.getValue().getFirst().getSelectable()).isFalse();
        assertThat(itemCaptor.getValue().getFirst().getDeleted()).isEqualTo(1);
    }

    @Test
    void shouldBuildTenantVisibilityWithGroupedTypeSafeColumns() {
        SystemDictionaryMapper mapper = mock(SystemDictionaryMapper.class);
        SysDictionaryItemMapper itemMapper = mock(SysDictionaryItemMapper.class);
        DictionaryRepository repository = new DictionaryRepository(
                mapper, itemMapper, mock(IdGenerator.class), new DictionaryLabelCodec(new ObjectMapper()), access());
        when(mapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        repository.findVisibleSelections(99L, List.of("system.dictionary-status"));

        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectListByQuery(queryCaptor.capture());
        verifyNoInteractions(itemMapper);
    }

    @Test
    void shouldKeepManagedReadsInsideTheExactTenantScope() {
        SystemDictionaryMapper mapper = mock(SystemDictionaryMapper.class);
        DictionaryRepository repository = new DictionaryRepository(
                mapper,
                mock(SysDictionaryItemMapper.class),
                mock(IdGenerator.class),
                new DictionaryLabelCodec(new ObjectMapper()),
                access());
        when(mapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        repository.findManagedType(99L, 11L);

        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectOneByQuery(queryCaptor.capture());
    }

    @Test
    void shouldIsolateAndCleanupTenantDictionariesWithFixedBatchCalls() {
        SystemDictionaryMapper mapper = mock(SystemDictionaryMapper.class);
        DictionaryRepository repository = new DictionaryRepository(
                mapper,
                mock(SysDictionaryItemMapper.class),
                mock(IdGenerator.class),
                new DictionaryLabelCodec(new ObjectMapper()),
                access());

        repository.isolateTenantDictionaries(20L);
        repository.deleteIsolatedTenantDictionaries(20L);

        verify(mapper).isolateTenantDictionaryItems(20L);
        verify(mapper).isolateTenantDictionaryTypes(20L);
        verify(mapper).deleteIsolatedTenantDictionaries(20L);
    }

    @Test
    void shouldWriteCompleteTypeAndItemOrdersWithOneMapperCallEach() {
        SystemDictionaryMapper mapper = mock(SystemDictionaryMapper.class);
        DictionaryRepository repository = new DictionaryRepository(
                mapper,
                mock(SysDictionaryItemMapper.class),
                mock(IdGenerator.class),
                new DictionaryLabelCodec(new ObjectMapper()),
                access());
        List<DictionaryOrder> typeOrders = List.of(
                new DictionaryOrder(11L, 3L, 10),
                new DictionaryOrder(12L, 4L, 20));
        List<DictionaryOrder> itemOrders = List.of(
                new DictionaryOrder(21L, 5L, 10),
                new DictionaryOrder(22L, 6L, 20));
        when(mapper.reorderDictionaryTypes(null, typeOrders)).thenReturn(2);
        when(mapper.reorderDictionaryItems(null, 11L, itemOrders)).thenReturn(2);

        repository.reorderTypes(null, typeOrders);
        repository.reorderItems(null, 11L, itemOrders);

        verify(mapper).reorderDictionaryTypes(null, typeOrders);
        verify(mapper).reorderDictionaryItems(null, 11L, itemOrders);
    }

    @Test
    void shouldReturnConflictWhenSortScopeOrVersionDoesNotMatch() {
        SystemDictionaryMapper mapper = mock(SystemDictionaryMapper.class);
        DictionaryRepository repository = new DictionaryRepository(
                mapper,
                mock(SysDictionaryItemMapper.class),
                mock(IdGenerator.class),
                new DictionaryLabelCodec(new ObjectMapper()),
                access());

        List<DictionaryOrder> orders = List.of(new DictionaryOrder(11L, 2L, 10));

        assertThatThrownBy(() -> repository.reorderTypes(9L, orders))
                .isInstanceOfSatisfying(
                        ConcurrencyConflictException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(SystemErrorCode.DICTIONARY_SORT_CHANGED));
        assertThatThrownBy(() -> repository.reorderItems(9L, 11L, orders))
                .isInstanceOfSatisfying(
                        ConcurrencyConflictException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(SystemErrorCode.DICTIONARY_ITEM_SORT_CHANGED));
    }

    @Test
    void shouldInsertConfigurableTypeWithPreallocatedIdAndEncodedLabels() {
        SystemDictionaryMapper mapper = mock(SystemDictionaryMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        DictionaryRepository repository = new DictionaryRepository(
                mapper,
                mock(SysDictionaryItemMapper.class),
                idGenerator,
                new DictionaryLabelCodec(new ObjectMapper()),
                access());
        when(idGenerator.nextId()).thenReturn(101L);
        when(mapper.selectMaxDictionaryTypeSortOrder(null)).thenReturn(40);
        when(mapper.insertConfigurableDictionaryType(any(SysDictionaryTypeEntity.class))).thenReturn(1);

        Long dictionaryId = repository.createConfigurableType(
                null,
                "verification.vehicle-brand",
                DictionaryKind.BUSINESS_CODE,
                Map.of("zh-CN", "车辆品牌", "en-US", "Vehicle brand"));

        ArgumentCaptor<SysDictionaryTypeEntity> entityCaptor = ArgumentCaptor.forClass(SysDictionaryTypeEntity.class);
        verify(mapper).lockDictionaryTypeCode("verification.vehicle-brand");
        verify(mapper).selectListByQuery(any(QueryWrapper.class));
        verify(mapper).selectMaxDictionaryTypeSortOrder(null);
        verify(mapper).insertConfigurableDictionaryType(entityCaptor.capture());
        assertThat(dictionaryId).isEqualTo(101L);
        assertThat(entityCaptor.getValue().getId()).isEqualTo(101L);
        assertThat(entityCaptor.getValue().getSortOrder()).isEqualTo(50);
        assertThat(entityCaptor.getValue().getNameLabels()).contains("车辆品牌", "Vehicle brand");
    }

    @Test
    void shouldInsertConfigurableItemWithPreallocatedIdAndEncodedLabels() {
        SystemDictionaryMapper mapper = mock(SystemDictionaryMapper.class);
        SysDictionaryItemMapper itemMapper = mock(SysDictionaryItemMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        DictionaryRepository repository = new DictionaryRepository(
                mapper, itemMapper, idGenerator, new DictionaryLabelCodec(new ObjectMapper()), access());
        when(idGenerator.nextId()).thenReturn(202L);
        when(mapper.selectMaxDictionaryItemSortOrder(101L)).thenReturn(null);
        when(mapper.insertConfigurableDictionaryItem(any(SysDictionaryItemEntity.class))).thenReturn(1);

        Long itemId = repository.createConfigurableItem(
                101L,
                "BYD",
                Map.of("zh-CN", "比亚迪", "en-US", "BYD"),
                DEFAULT,
                true);

        ArgumentCaptor<SysDictionaryItemEntity> entityCaptor = ArgumentCaptor.forClass(SysDictionaryItemEntity.class);
        verify(mapper).lockDictionaryItemValue(101L, "BYD");
        verify(itemMapper).selectCountByQuery(any(QueryWrapper.class));
        verify(mapper).selectMaxDictionaryItemSortOrder(101L);
        verify(mapper).insertConfigurableDictionaryItem(entityCaptor.capture());
        assertThat(itemId).isEqualTo(202L);
        assertThat(entityCaptor.getValue().getId()).isEqualTo(202L);
        assertThat(entityCaptor.getValue().getSortOrder()).isEqualTo(10);
        assertThat(entityCaptor.getValue().getLabelLabels()).contains("比亚迪", "BYD");
    }

    /**
     * 创建提供全量租户范围的访问入口，保证租户字典 SQL 测试仍经过最终访问校验。
     *
     * @return 允许当前测试执行全部租户管理操作的访问入口
     */
    private static ResourceAccess access() {
        ResourceAccess resourceAccess = mock(ResourceAccess.class);
        when(resourceAccess.require(TenantAccessCriteria.class))
                .thenReturn(new TenantAccessCriteria(true, AccessAction.MANAGE, Set.of(), Set.of(), null));
        return resourceAccess;
    }

    /**
     * 创建用于断言一次批量字典类型写入内容的参数捕获器。
     *
     * @return 保留字典类型实体列表泛型的 Mockito 捕获器
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @NotNull ArgumentCaptor<List<SysDictionaryTypeEntity>> dictionaryTypeListCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    /**
     * 创建用于断言一次批量字典项写入内容的参数捕获器。
     *
     * @return 保留字典项实体列表泛型的 Mockito 捕获器
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @NotNull ArgumentCaptor<List<SysDictionaryItemEntity>> dictionaryItemListCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

}
