package com.sca4d.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.dictionary.DictionarySource;
import com.sca4d.common.dictionary.DictionaryTone;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.SysDictionaryItemEntity;
import com.sca4d.entity.SysDictionaryTypeEntity;
import com.sca4d.system.dictionary.DictionaryItemSnapshot;
import com.sca4d.system.dictionary.DictionarySnapshot;
import com.sca4d.system.repository.DictionaryRepository;
import com.sca4d.system.service.DefaultDictionaryService;
import com.sca4d.tenant.TenantService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/** 验证字典查询、创建、修改、停用和引用检查。 */
class DefaultDictionaryServiceTest {

    @AfterEach
    void clearLocaleContext() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void shouldLocalizeCodeDictionaryAndKeepUnavailableHistoryVisible() {
        DictionaryRepository repository = mock(DictionaryRepository.class);
        LocalizedMessages messages = mock(LocalizedMessages.class);
        DefaultDictionaryService service = new DefaultDictionaryService(
                repository,
                messages,
                new DictionaryLabelCodec(new ObjectMapper()),
                new DictionaryReferenceRegistry(List.of()),
                mock(TenantService.class));
        DictionarySnapshot snapshot = new DictionarySnapshot(
                "fixture.status",
                DictionaryKind.CODE_ENUM,
                DictionaryStatus.ACTIVE,
                "fixture.status",
                new DictionaryLabels(Map.of()),
                List.of(
                        new DictionaryItemSnapshot(
                                "ACTIVE",
                                "fixture.status.active",
                                new DictionaryLabels(Map.of()),
                                DictionaryTone.SUCCESS,
                                true,
                                DictionaryStatus.ACTIVE),
                        new DictionaryItemSnapshot(
                                "LEGACY",
                                "fixture.status.legacy",
                                new DictionaryLabels(Map.of()),
                                DictionaryTone.WARNING,
                                false,
                                DictionaryStatus.DISABLED)));
        when(repository.findVisibleSelections(null, List.of("fixture.status"))).thenReturn(List.of(snapshot));
        when(messages.get(Locale.US, "fixture.status")).thenReturn("Status");
        when(messages.get(Locale.US, "fixture.status.active")).thenReturn("Active");
        when(messages.get(Locale.US, "fixture.status.legacy")).thenReturn("Legacy");
        LocaleContextHolder.setLocale(Locale.US);

        var selection = service.selections(null, List.of("fixture.status")).getFirst();

        assertThat(selection.getLabel()).isEqualTo("Status");
        assertThat(selection.getOptions())
                .extracting(option -> option.getValue() + ":" + option.getLabel() + ":" + option.isDisabled())
                .containsExactly("ACTIVE:Active:false", "LEGACY:Legacy:true");
    }

    @Test
    void shouldKeepUserContentAndApplyDeterministicLanguageFallback() {
        DictionaryRepository repository = mock(DictionaryRepository.class);
        DefaultDictionaryService service = new DefaultDictionaryService(
                repository,
                mock(LocalizedMessages.class),
                new DictionaryLabelCodec(new ObjectMapper()),
                new DictionaryReferenceRegistry(List.of()),
                mock(TenantService.class));
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("en-US", "Vehicle brand");
        labels.put("zh-CN", "车辆品牌");
        DictionarySnapshot snapshot = new DictionarySnapshot(
                "vehicle.brand",
                DictionaryKind.INPUT_SUGGESTION,
                DictionaryStatus.DISABLED,
                null,
                new DictionaryLabels(labels),
                List.of(new DictionaryItemSnapshot(
                        "BYD",
                        null,
                        new DictionaryLabels(Map.of("zh-CN", "比亚迪")),
                        DictionaryTone.DEFAULT,
                        true,
                        DictionaryStatus.ACTIVE)));
        when(repository.findVisibleSelections(9L, List.of("vehicle.brand"))).thenReturn(List.of(snapshot));
        LocaleContextHolder.setLocale(Locale.FRANCE);

        var selection = service.selections(9L, List.of("vehicle.brand")).getFirst();

        assertThat(selection.getLabel()).isEqualTo("车辆品牌");
        assertThat(selection.getOptions().getFirst().getLabel()).isEqualTo("比亚迪");
        assertThat(selection.getOptions().getFirst().isDisabled()).isTrue();
    }

    @Test
    void shouldPreferManagedLanguageOverrideAndFallbackToCodeMessage() {
        DictionaryRepository repository = mock(DictionaryRepository.class);
        LocalizedMessages messages = mock(LocalizedMessages.class);
        DefaultDictionaryService service = new DefaultDictionaryService(
                repository,
                messages,
                new DictionaryLabelCodec(new ObjectMapper()),
                new DictionaryReferenceRegistry(List.of()),
                mock(TenantService.class));
        DictionarySnapshot snapshot = new DictionarySnapshot(
                "fixture.status",
                DictionaryKind.BUSINESS_CODE,
                DictionaryStatus.ACTIVE,
                "fixture.status",
                new DictionaryLabels(Map.of("en-US", "Managed status")),
                List.of(new DictionaryItemSnapshot(
                        "RESERVED",
                        "fixture.status.reserved",
                        new DictionaryLabels(Map.of("en-US", "Managed reserved")),
                        DictionaryTone.INFO,
                        true,
                        DictionaryStatus.ACTIVE)));
        when(repository.findVisibleSelections(null, List.of("fixture.status"))).thenReturn(List.of(snapshot));
        when(messages.get(Locale.FRANCE, "fixture.status")).thenReturn("Statut");
        when(messages.get(Locale.FRANCE, "fixture.status.reserved")).thenReturn("Reserve");

        LocaleContextHolder.setLocale(Locale.US);
        var english = service.selections(null, List.of("fixture.status")).getFirst();
        assertThat(english.getLabel()).isEqualTo("Managed status");
        assertThat(english.getOptions().getFirst().getLabel()).isEqualTo("Managed reserved");

        LocaleContextHolder.setLocale(Locale.FRANCE);
        var french = service.selections(null, List.of("fixture.status")).getFirst();
        assertThat(french.getLabel()).isEqualTo("Statut");
        assertThat(french.getOptions().getFirst().getLabel()).isEqualTo("Reserve");
    }

    @Test
    void shouldRejectInvalidOrOversizedSelectionRequestsWithStableMessageKeys() {
        DefaultDictionaryService service = service(
                mock(DictionaryRepository.class), new DictionaryReferenceRegistry(List.of()));

        assertThatThrownBy(() -> service.selections(null, List.of("invalid code")))
                .isInstanceOfSatisfying(BizException.class, exception -> assertThat(exception.getMessageKey())
                        .isEqualTo("error.dictionary.code-invalid"));

        List<String> codes = IntStream.range(0, 51)
                .mapToObj(index -> "fixture.dictionary-" + index)
                .toList();
        assertThatThrownBy(() -> service.selections(null, codes))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getMessageKey()).isEqualTo("error.dictionary.batch-size-exceeded");
                    assertThat(exception.getMessageArgs()).containsExactly(50);
                });
    }

    @Test
    void shouldRejectCodeOwnedContentMutation() {
        DictionaryRepository repository = mock(DictionaryRepository.class);
        when(repository.findManagedTypeForUpdate(null, 11L))
                .thenReturn(dictionaryType(DictionaryKind.CODE_ENUM));
        DefaultDictionaryService service = service(repository, new DictionaryReferenceRegistry(List.of()));

        assertThatThrownBy(() -> service.updateType(
                null,
                11L,
                Map.of("zh-CN", "状态"),
                DictionaryStatus.ACTIVE,
                0L))
                .isInstanceOfSatisfying(BizException.class, exception -> assertThat(exception.getMessageKey())
                        .isEqualTo("error.dictionary.code-enum-type-readonly"));
    }

    @Test
    void shouldKeepBusinessValueImmutableAndRejectReferencedDeletion() {
        DictionaryRepository repository = mock(DictionaryRepository.class);
        when(repository.findManagedTypeForUpdate(null, 11L))
                .thenReturn(dictionaryType(DictionaryKind.BUSINESS_CODE));
        when(repository.findManagedItem(11L, 21L)).thenReturn(new SysDictionaryItemEntity()
                .setId(21L)
                .setDictTypeId(11L)
                .setItemValue("BYD")
                .setDeleted(0));
        DictionaryValueReference reference = new DictionaryValueReference() {
            @Override
            public boolean supports(@NotNull String dictionaryCode) {
                return "fixture.dictionary".equals(dictionaryCode);
            }

            @Override
            public @NotNull Set<String> findReferencedValues(
                    Long tenantId, @NotNull String dictionaryCode, @NotNull Set<String> values) {
                return Set.of("BYD");
            }
        };
        DefaultDictionaryService service = service(
                repository, new DictionaryReferenceRegistry(List.of(reference)));

        assertThatThrownBy(() -> service.updateItem(
                null,
                11L,
                21L,
                "TESLA",
                Map.of("zh-CN", "特斯拉"),
                DictionaryTone.DEFAULT,
                true,
                DictionaryStatus.ACTIVE,
                0L))
                .isInstanceOfSatisfying(BizException.class, exception -> assertThat(exception.getMessageKey())
                        .isEqualTo("error.dictionary.business-value-immutable"));
        assertThatThrownBy(() -> service.deleteItem(null, 11L, 21L, 0L))
                .isInstanceOfSatisfying(BizException.class, exception -> assertThat(exception.getMessageKey())
                        .isEqualTo("error.dictionary.item-referenced"));
    }

    @Test
    void shouldAllowReservedItemPresentationUpdateAndRejectItsDeletion() {
        DictionaryRepository repository = mock(DictionaryRepository.class);
        SysDictionaryTypeEntity type = dictionaryType(DictionaryKind.BUSINESS_CODE)
                .setSource(DictionarySource.CODE)
                .setNameKey("fixture.dictionary")
                .setSourceRef("fixture.ExtensibleCodes");
        SysDictionaryItemEntity item = new SysDictionaryItemEntity()
                .setId(21L)
                .setDictTypeId(11L)
                .setItemValue("RESERVED")
                .setSource(DictionarySource.CODE)
                .setSourceRef("fixture.ExtensibleCodes#RESERVED")
                .setDeleted(0);
        when(repository.findManagedTypeForUpdate(null, 11L)).thenReturn(type);
        when(repository.findManagedItem(11L, 21L)).thenReturn(item);
        DefaultDictionaryService service = service(repository, new DictionaryReferenceRegistry(List.of()));

        service.updateItem(
                null,
                11L,
                21L,
                "RESERVED",
                Map.of("zh-CN", "保留值"),
                DictionaryTone.WARNING,
                false,
                DictionaryStatus.DISABLED,
                3L);

        verify(repository).updateConfigurableItem(
                null,
                11L,
                21L,
                DictionaryKind.BUSINESS_CODE,
                "RESERVED",
                Map.of("zh-CN", "保留值"),
                DictionaryTone.WARNING,
                false,
                DictionaryStatus.DISABLED,
                3L);
        assertThatThrownBy(() -> service.deleteItem(null, 11L, 21L, 3L))
                .isInstanceOfSatisfying(BizException.class, exception -> assertThat(exception.getMessageKey())
                        .isEqualTo("error.dictionary.code-item-delete-forbidden"));
    }

    @Test
    void shouldReturnCompleteLocalizedTypeAndItemOrders() {
        DictionaryRepository repository = mock(DictionaryRepository.class);
        LocalizedMessages messages = mock(LocalizedMessages.class);
        SysDictionaryTypeEntity type = dictionaryType(DictionaryKind.BUSINESS_CODE)
                .setNameLabels("{\"en-US\":\"Vehicle brands\"}")
                .setVersion(7L);
        SysDictionaryItemEntity item = new SysDictionaryItemEntity()
                .setId(21L)
                .setDictTypeId(11L)
                .setItemValue("BYD")
                .setLabelLabels("{\"en-US\":\"BYD\"}")
                .setVersion(8L);
        when(repository.findManagedTypesForOrdering(null)).thenReturn(List.of(type));
        when(repository.findManagedType(null, 11L)).thenReturn(type);
        when(repository.findManagedItemsForOrdering(11L)).thenReturn(List.of(item));
        LocaleContextHolder.setLocale(Locale.US);
        DefaultDictionaryService service = new DefaultDictionaryService(
                repository,
                messages,
                new DictionaryLabelCodec(new ObjectMapper()),
                new DictionaryReferenceRegistry(List.of()),
                mock(TenantService.class));

        assertThat(service.typeOrder(null)).singleElement().satisfies(order -> {
            assertThat(order.getId()).isEqualTo(11L);
            assertThat(order.getLabel()).isEqualTo("Vehicle brands");
            assertThat(order.getDescription()).isEqualTo("fixture.dictionary");
            assertThat(order.getVersion()).isEqualTo(7L);
        });
        assertThat(service.itemOrder(null, 11L)).singleElement().satisfies(order -> {
            assertThat(order.getId()).isEqualTo(21L);
            assertThat(order.getLabel()).isEqualTo("BYD");
            assertThat(order.getDescription()).isEqualTo("BYD");
            assertThat(order.getVersion()).isEqualTo(8L);
        });
    }

    @Test
    void shouldNormalizeAndWriteACompleteTypeOrderOnce() {
        DictionaryRepository repository = mock(DictionaryRepository.class);
        SysDictionaryTypeEntity first = dictionaryType(DictionaryKind.BUSINESS_CODE)
                .setId(11L)
                .setVersion(3L);
        SysDictionaryTypeEntity second = dictionaryType(DictionaryKind.INPUT_SUGGESTION)
                .setId(12L)
                .setVersion(4L);
        when(repository.pageManagedTypes(null, null, null, null, 1, Integer.MAX_VALUE))
                .thenReturn(new PageData<>(List.of(first, second), 1, 2, 2));
        DefaultDictionaryService service = service(repository, new DictionaryReferenceRegistry(List.of()));
        LinkedHashMap<Long, Long> versions = new LinkedHashMap<>();
        versions.put(12L, 4L);
        versions.put(11L, 3L);

        service.reorderTypes(null, versions);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<DictionaryOrder>> captor = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(repository).lockOrdering("types:platform");
        verify(repository).reorderTypes(ArgumentMatchers.isNull(), captor.capture());
        assertThat(captor.getValue())
                .extracting(order -> order.getId() + ":" + order.getVersion() + ":" + order.getSortOrder())
                .containsExactly("12:4:10", "11:3:20");
    }

    @Test
    void shouldRejectAStaleTypeOrderBeforeWriting() {
        DictionaryRepository repository = mock(DictionaryRepository.class);
        when(repository.pageManagedTypes(null, null, null, null, 1, Integer.MAX_VALUE))
                .thenReturn(new PageData<>(List.of(dictionaryType(DictionaryKind.BUSINESS_CODE)), 1, 1, 1));
        DefaultDictionaryService service = service(repository, new DictionaryReferenceRegistry(List.of()));

        assertThatThrownBy(() -> service.reorderTypes(null, new LinkedHashMap<>()))
                .isInstanceOfSatisfying(
                        ConcurrencyConflictException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(SystemErrorCode.DICTIONARY_SORT_CHANGED));
    }

    /**
     * 用指定持久化和引用检查替身创建字典 Service。
     *
     * @param repository 字典持久化测试替身
     * @param registry 业务引用检查测试替身
     * @return 使用真实标签 Codec 且不访问外部资源的字典 Service
     */
    private static @NotNull DefaultDictionaryService service(
            @NotNull DictionaryRepository repository, @NotNull DictionaryReferenceRegistry registry) {
        return new DefaultDictionaryService(
                repository,
                mock(LocalizedMessages.class),
                new DictionaryLabelCodec(new ObjectMapper()),
                registry,
                mock(TenantService.class));
    }

    /**
     * 创建可被管理结果转换读取的完整字典类型实体。
     *
     * @param kind 当前用例要验证的字典含义类型
     * @return 包含主键、双语标签、状态和版本的字典实体
     */
    private static @NotNull SysDictionaryTypeEntity dictionaryType(@NotNull DictionaryKind kind) {
        return new SysDictionaryTypeEntity()
                .setId(11L)
                .setCode("fixture.dictionary")
                .setKind(kind)
                .setSource(kind == DictionaryKind.CODE_ENUM
                        ? DictionarySource.CODE
                        : DictionarySource.CONFIGURATION)
                .setNameLabels("{\"zh-CN\":\"测试字典\"}")
                .setStatus(DictionaryStatus.ACTIVE)
                .setSortOrder(10)
                .setVersion(0L)
                .setCreatedAt(OffsetDateTime.now())
                .setUpdatedAt(OffsetDateTime.now())
                .setDeleted(0);
    }
}
