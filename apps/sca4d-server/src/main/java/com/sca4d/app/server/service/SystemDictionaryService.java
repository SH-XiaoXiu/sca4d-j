package com.sca4d.app.server.service;

import com.sca4d.app.server.system.ManagedDictionary;
import com.sca4d.app.server.system.ManagedDictionaryOption;
import com.sca4d.app.server.system.SystemDictionaryErrorCode;
import com.sca4d.core.api.dictionary.DictionaryOption;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.core.spring.dictionary.DatabaseDictionaryOption;
import com.sca4d.core.spring.dictionary.DictionaryRegistry;
import com.sca4d.core.spring.dictionary.DictionaryRepository;
import com.sca4d.core.spring.dictionary.DynamicDictionary;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** 管理数据库动态字典和可扩展代码字典的数据库选项。 */
@Service
public class SystemDictionaryService {

    private static final Logger LOG = LoggerFactory.getLogger(SystemDictionaryService.class);

    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+");

    private static final Pattern OPTION_VALUE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,159}");

    private final DictionaryRepository repository;

    private final DictionaryRegistry registry;

    /** 创建系统字典应用服务。 */
    public SystemDictionaryService(
            @NotNull DictionaryRegistry registry,
            @NotNull DictionaryRepository repository) {
        this.registry = Objects.requireNonNull(registry, "Dictionary Registry 不能为空");
        this.repository = Objects.requireNonNull(repository, "Dictionary Repository 不能为空");
    }

    /** @return 按键排序的全部代码字典和动态字典管理结果 */
    @CheckReturnValue
    @Transactional(readOnly = true)
    public @NotNull @Unmodifiable List<@NotNull ManagedDictionary> findAll() {
        Map<String, DynamicDictionary> dynamic = new LinkedHashMap<>();
        for (DynamicDictionary dictionary : repository.findDictionaries()) {
            if (dynamic.putIfAbsent(dictionary.key(), dictionary) != null) {
                throw new IllegalStateException("数据库返回重复 Dictionary 键: " + dictionary.key());
            }
        }
        Set<String> keys = new TreeSet<>(registry.keys());
        keys.addAll(dynamic.keySet());
        return keys.stream().map(key -> managed(key, dynamic.get(key))).toList();
    }

    /** 新增数据库动态字典，键与代码字典或已有动态字典冲突时返回 409。 */
    @Transactional
    public void createDictionary(
            @NotNull String key,
            @NotNull String label,
            boolean enabled,
            int sortOrder) {
        String checkedKey = dictionaryKey(key);
        String checkedLabel = label(label);
        if (registry.contains(checkedKey)) {
            throw new BusinessException(SystemDictionaryErrorCode.DICTIONARY_KEY_EXISTS);
        }
        if (!repository.createDictionary(checkedKey, checkedLabel, enabled, sortOrder)) {
            throw new BusinessException(SystemDictionaryErrorCode.DICTIONARY_KEY_EXISTS);
        }
        LOG.info("动态字典已创建 | dictionaryKey={} enabled={}", checkedKey, enabled);
    }

    /** 按版本修改数据库动态字典，代码字典不可修改。 */
    @Transactional
    public void updateDictionary(
            @NotNull String key,
            @NotNull String label,
            boolean enabled,
            int sortOrder,
            long version) {
        String checkedKey = dictionaryKey(key);
        validateDynamicDictionary(checkedKey);
        if (!repository.updateDictionary(checkedKey, label(label), enabled, sortOrder, version(version))) {
            conflictOrMissingDictionary(checkedKey);
        }
        LOG.info("动态字典已修改 | dictionaryKey={} enabled={}", checkedKey, enabled);
    }

    /** 按版本删除数据库动态字典及对应数据库选项，代码字典不可删除。 */
    @Transactional
    public void deleteDictionary(@NotNull String key, long version) {
        String checkedKey = dictionaryKey(key);
        validateDynamicDictionary(checkedKey);
        if (!repository.deleteDictionary(checkedKey, version(version))) {
            conflictOrMissingDictionary(checkedKey);
        }
        LOG.info("动态字典已删除 | dictionaryKey={}", checkedKey);
    }

    /**
     * 给动态字典或明确可扩展的代码字典新增数据库选项。
     *
     * @param key 字典键
     * @param value 选项稳定值
     * @param label 缺少翻译时使用的回退标签
     * @param i18n 按 Locale 保存的完整多语言 label，可以不包含任何 Locale
     * @param enabled 是否允许用于新的业务输入
     * @param sortOrder 同一字典内的展示顺序
     * @throws BusinessException 字典不可维护或选项值已经存在时抛出
     * @throws IllegalArgumentException label 或 i18n 不符合字典选项限制时抛出
     */
    @Transactional
    public void createOption(
            @NotNull String key,
            @NotNull String value,
            @NotNull String label,
            @NotNull LocalizedFields i18n,
            boolean enabled,
            int sortOrder) {
        String checkedKey = dictionaryKey(key);
        String checkedValue = optionValue(value);
        validateEditableOptions(checkedKey);
        if (registry.contains(checkedKey)
                && registry.options(checkedKey)
                        .stream()
                        .anyMatch(option -> option.value().equalsIgnoreCase(checkedValue))) {
            throw new BusinessException(SystemDictionaryErrorCode.OPTION_VALUE_EXISTS);
        }
        DictionaryOption option = new DictionaryOption(
                checkedValue,
                null,
                label(label),
                Objects.requireNonNull(i18n, "Dictionary 多语言标签不能为空"),
                false,
                enabled,
                sortOrder);
        if (!repository.createOption(checkedKey, option)) {
            throw new BusinessException(SystemDictionaryErrorCode.OPTION_VALUE_EXISTS);
        }
        LOG.info(
                "数据库字典项已创建 | dictionaryKey={} optionValue={} enabled={}",
                checkedKey,
                checkedValue,
                enabled);
    }

    /**
     * 按版本完整修改一个数据库选项；Java 枚举项不会进入此写路径。
     *
     * @param key 字典键
     * @param value 选项稳定值
     * @param label 缺少翻译时使用的回退标签
     * @param i18n 按 Locale 保存的完整多语言 label，可以不包含任何 Locale
     * @param enabled 是否允许用于新的业务输入
     * @param sortOrder 同一字典内的展示顺序
     * @param version 调用方读取到的乐观锁版本
     * @throws BusinessException 字典不可维护、目标不存在或版本变化时抛出
     * @throws IllegalArgumentException label 或 i18n 不符合字典选项限制时抛出
     */
    @Transactional
    public void updateOption(
            @NotNull String key,
            @NotNull String value,
            @NotNull String label,
            @NotNull LocalizedFields i18n,
            boolean enabled,
            int sortOrder,
            long version) {
        String checkedKey = dictionaryKey(key);
        String checkedValue = optionValue(value);
        validateEditableOptions(checkedKey);
        DictionaryOption option = new DictionaryOption(
                checkedValue,
                null,
                label(label),
                Objects.requireNonNull(i18n, "Dictionary 多语言标签不能为空"),
                false,
                enabled,
                sortOrder);
        if (!repository.updateOption(checkedKey, option, version(version))) {
            conflictOrMissingOption(checkedKey, checkedValue);
        }
        LOG.info(
                "数据库字典项已修改 | dictionaryKey={} optionValue={} enabled={}",
                checkedKey,
                checkedValue,
                enabled);
    }

    /** 按版本删除一个数据库选项；Java 枚举项不会进入此写路径。 */
    @Transactional
    public void deleteOption(@NotNull String key, @NotNull String value, long version) {
        String checkedKey = dictionaryKey(key);
        String checkedValue = optionValue(value);
        validateEditableOptions(checkedKey);
        if (!repository.deleteOption(checkedKey, checkedValue, version(version))) {
            conflictOrMissingOption(checkedKey, checkedValue);
        }
        LOG.info("数据库字典项已删除 | dictionaryKey={} optionValue={}", checkedKey, checkedValue);
    }

    /** 把代码 Registry 和数据库配置转换为一个管理结果。 */
    private @NotNull ManagedDictionary managed(
            @NotNull String key,
            DynamicDictionary dynamic) {
        boolean codeDefined = registry.contains(key);
        List<DatabaseDictionaryOption> stored = repository.findOptions(key);
        if (codeDefined && !registry.allowsDatabaseOptions(key) && !stored.isEmpty()) {
            throw new IllegalStateException("封闭代码 Dictionary 存在数据库扩展项: " + key);
        }

        Map<String, ManagedDictionaryOption> options = new LinkedHashMap<>();
        if (codeDefined) {
            for (DictionaryOption option : registry.options(key)) {
                options.put(option.value().toUpperCase(Locale.ROOT), new ManagedDictionaryOption(option, null));
            }
        }
        for (DatabaseDictionaryOption optionState : stored) {
            DictionaryOption option = new DictionaryOption(
                    optionState.value(),
                    null,
                    optionState.label(),
                    optionState.i18n(),
                    false,
                    optionState.enabled(),
                    optionState.sortOrder());
            if (options.putIfAbsent(
                    optionState.value().toUpperCase(Locale.ROOT),
                    new ManagedDictionaryOption(option, optionState.version())) != null) {
                throw new IllegalStateException(
                        "Dictionary 代码项与数据库项值冲突: " + key + "." + optionState.value());
            }
        }
        List<ManagedDictionaryOption> sortedOptions = new ArrayList<>(options.values());
        sortedOptions.sort(
                Comparator.comparingInt((ManagedDictionaryOption value) -> value.option().sortOrder())
                        .thenComparing(value -> value.option().value()));

        if (codeDefined) {
            if (dynamic != null) {
                throw new IllegalStateException("代码 Dictionary 键被数据库动态字典占用: " + key);
            }
            return new ManagedDictionary(
                    key,
                    key,
                    true,
                    registry.allowsDatabaseOptions(key),
                    true,
                    0,
                    null,
                    sortedOptions);
        }
        DynamicDictionary checked = Objects.requireNonNull(dynamic, "动态 Dictionary 配置不能为空");
        return new ManagedDictionary(
                key,
                checked.label(),
                false,
                true,
                checked.enabled(),
                checked.sortOrder(),
                checked.version(),
                sortedOptions);
    }

    /** 校验目标是存在的数据库动态字典。 */
    private void validateDynamicDictionary(@NotNull String key) {
        if (registry.contains(key)) {
            throw new BusinessException(CoreErrorCode.CONFLICT);
        }
        if (repository.findDictionary(key).isEmpty()) {
            throw new BusinessException(CoreErrorCode.NOT_FOUND);
        }
    }

    /** 校验目标允许增加和管理数据库选项。 */
    private void validateEditableOptions(@NotNull String key) {
        if (registry.contains(key)) {
            if (!registry.allowsDatabaseOptions(key)) {
                throw new BusinessException(CoreErrorCode.CONFLICT);
            }
            return;
        }
        if (repository.findDictionary(key).isEmpty()) {
            throw new BusinessException(CoreErrorCode.NOT_FOUND);
        }
    }

    /** 区分动态字典已删除和乐观版本冲突。 */
    private void conflictOrMissingDictionary(@NotNull String key) {
        if (repository.findDictionary(key).isEmpty()) {
            throw new BusinessException(CoreErrorCode.NOT_FOUND);
        }
        throw new BusinessException(CoreErrorCode.CONFLICT);
    }

    /** 区分数据库字典项已删除和乐观版本冲突。 */
    private void conflictOrMissingOption(@NotNull String key, @NotNull String value) {
        boolean exists = repository.findOptions(key)
                .stream()
                .anyMatch(option -> option.value().equals(value));
        if (!exists) {
            throw new BusinessException(CoreErrorCode.NOT_FOUND);
        }
        throw new BusinessException(CoreErrorCode.CONFLICT);
    }

    /** 校验并规范化全局 Dictionary 键。 */
    private static @NotNull String dictionaryKey(@NotNull String key) {
        String checked = TextUtils.requireText(key, "Dictionary 键");
        if (checked.length() > 160 || !KEY.matcher(checked).matches()) {
            throw new BusinessException(CoreErrorCode.VALIDATION_FAILED);
        }
        return checked;
    }

    /** 校验并规范化数据库选项稳定值。 */
    private static @NotNull String optionValue(@NotNull String value) {
        String checked = TextUtils.requireText(value, "Dictionary 选项值");
        if (!OPTION_VALUE.matcher(checked).matches()) {
            throw new BusinessException(CoreErrorCode.VALIDATION_FAILED);
        }
        return checked;
    }

    /** 校验并规范化管理端展示文本。 */
    private static @NotNull String label(@NotNull String label) {
        String checked = TextUtils.requireText(label, "Dictionary 展示文本");
        if (checked.length() > 160) {
            throw new BusinessException(CoreErrorCode.VALIDATION_FAILED);
        }
        return checked;
    }

    /** 校验乐观锁版本。 */
    private static long version(long version) {
        if (version < 0) {
            throw new BusinessException(CoreErrorCode.VALIDATION_FAILED);
        }
        return version;
    }
}
