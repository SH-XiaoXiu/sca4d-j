package com.sca4d.core.spring.dictionary;

import com.sca4d.core.api.dictionary.Dictionaries;
import com.sca4d.core.api.dictionary.DictionaryOption;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** 聚合 Java 枚举和数据库动态项，并拒绝数据库扩展封闭代码字典。 */
public class DatabaseDictionaries implements Dictionaries {

    private final DictionaryRepository repository;

    private final DictionaryRegistry registry;

    /** 创建聚合字典查询入口。 */
    public DatabaseDictionaries(
            @NotNull DictionaryRegistry registry,
            @NotNull DictionaryRepository repository) {
        this.registry = Objects.requireNonNull(registry, "Dictionary Registry 不能为空");
        this.repository = Objects.requireNonNull(repository, "Dictionary Repository 不能为空");
    }

    @Override
    @Transactional(readOnly = true)
    public @NotNull Set<@NotNull String> keys() {
        Set<String> keys = new TreeSet<>(registry.keys());
        repository.findDictionaries()
                .stream()
                .filter(DynamicDictionary::enabled)
                .map(DynamicDictionary::key)
                .forEach(keys::add);
        return Collections.unmodifiableSet(keys);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean allowsDatabaseOptions(@NotNull String key) {
        String checked = Objects.requireNonNull(key, "Dictionary 键不能为空");
        if (registry.contains(checked)) {
            return registry.allowsDatabaseOptions(checked);
        }
        if (repository.findDictionary(checked).filter(DynamicDictionary::enabled).isPresent()) {
            return false;
        }
        throw new IllegalArgumentException("Dictionary 不存在: " + checked);
    }

    @Override
    @Transactional(readOnly = true)
    public @NotNull List<@NotNull DictionaryOption> options(@NotNull String key) {
        String checked = Objects.requireNonNull(key, "Dictionary 键不能为空");
        boolean codeDictionary = registry.contains(checked);
        if (!codeDictionary && repository.findDictionary(checked).filter(DynamicDictionary::enabled).isEmpty()) {
            throw new IllegalArgumentException("Dictionary 不存在: " + checked);
        }

        List<DatabaseDictionaryOption> stored = repository.findOptions(checked);
        if (codeDictionary && !registry.allowsDatabaseOptions(checked) && !stored.isEmpty()) {
            throw new IllegalStateException("封闭代码 Dictionary 存在数据库扩展项: " + checked);
        }

        Map<String, DictionaryOption> options = new LinkedHashMap<>();
        if (codeDictionary) {
            for (DictionaryOption option : registry.options(checked)) {
                options.put(option.value().toUpperCase(Locale.ROOT), option);
            }
        }
        for (DatabaseDictionaryOption optionValue : stored) {
            DictionaryOption option = new DictionaryOption(
                    optionValue.value(),
                    null,
                    optionValue.label(),
                    optionValue.i18n(),
                    false,
                    optionValue.enabled(),
                    optionValue.sortOrder());
            if (options.putIfAbsent(optionValue.value().toUpperCase(Locale.ROOT), option) != null) {
                throw new IllegalStateException("代码项与数据库项使用了重复 Dictionary 值: " + checked + "." + optionValue.value());
            }
        }
        List<DictionaryOption> result = new ArrayList<>(options.values());
        result.sort(Comparator.comparingInt(DictionaryOption::sortOrder).thenComparing(DictionaryOption::value));
        return List.copyOf(result);
    }
}
