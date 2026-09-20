package com.sca4d.core.spring.dictionary;

import com.sca4d.core.api.dictionary.Dictionary;
import com.sca4d.core.api.dictionary.DictionaryOption;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 校验并保存实现 `Dictionary` 的枚举类型及其不可变代码选项。 */
public final class DictionaryRegistry {

    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+");

    private final Map<String, List<DictionaryOption>> options;

    private final Set<String> extensibleKeys;

    /**
     * 从扫描结果创建代码字典目录。
     *
     * @param enumTypes 实现 `Dictionary` 的枚举类型
     */
    public DictionaryRegistry(@NotNull Collection<Class<? extends Enum<?>>> enumTypes) {
        Map<String, List<DictionaryOption>> byKey = new LinkedHashMap<>();
        Set<String> extensible = new LinkedHashSet<>();
        for (Class<? extends Enum<?>> enumType : Objects.requireNonNull(enumTypes, "Dictionary 枚举不能为空")) {
            Enum<?>[] constants = enumType.getEnumConstants();
            if (constants.length == 0) {
                throw new IllegalArgumentException("Dictionary 枚举至少需要一个常量: " + enumType.getName());
            }
            Dictionary first = (Dictionary) constants[0];
            String key = first.dictionaryKey();
            if (!KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("Dictionary 键格式错误: " + enumType.getName() + "." + key);
            }
            if (byKey.containsKey(key)) {
                throw new IllegalArgumentException("重复 Dictionary 键: " + key);
            }
            boolean allowsDatabaseOptions = first.allowsDatabaseOptions();
            Map<String, DictionaryOption> enumOptions = new LinkedHashMap<>();
            for (int index = 0; index < constants.length; index++) {
                Dictionary item = (Dictionary) constants[index];
                if (!key.equals(item.dictionaryKey())) {
                    throw new IllegalArgumentException("同一 Dictionary 枚举返回了不同键: " + enumType.getName());
                }
                if (allowsDatabaseOptions != item.allowsDatabaseOptions()) {
                    throw new IllegalArgumentException("同一 Dictionary 枚举返回了不同扩展规则: " + enumType.getName());
                }
                String normalized = item.value().toUpperCase(Locale.ROOT);
                DictionaryOption option = new DictionaryOption(
                        item.value(),
                        item.labelKey(),
                        null,
                        null,
                        true,
                        true,
                        (index + 1) * 10);
                if (enumOptions.putIfAbsent(normalized, option) != null) {
                    throw new IllegalArgumentException("Dictionary 枚举值重复: " + enumType.getName() + "." + item.value());
                }
            }
            byKey.put(key, List.copyOf(enumOptions.values()));
            if (allowsDatabaseOptions) {
                extensible.add(key);
            }
        }
        this.options = Map.copyOf(byKey);
        this.extensibleKeys = Set.copyOf(extensible);
    }

    /** @return 按键排序的全部代码字典键 */
    @CheckReturnValue
    public @NotNull @Unmodifiable Set<@NotNull String> keys() {
        Set<String> sorted = new LinkedHashSet<>(options.keySet().stream().sorted().toList());
        return Collections.unmodifiableSet(sorted);
    }

    /** @return 当前键是否由 Java 枚举声明 */
    @CheckReturnValue
    public boolean contains(@NotNull String key) {
        return options.containsKey(Objects.requireNonNull(key, "Dictionary 键不能为空"));
    }

    /** @return 代码字典明确允许数据库扩展时返回 {@code true} */
    @CheckReturnValue
    public boolean allowsDatabaseOptions(@NotNull String key) {
        return extensibleKeys.contains(Objects.requireNonNull(key, "Dictionary 键不能为空"));
    }

    /** @return 指定代码字典按枚举声明顺序生成的选项 */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull DictionaryOption> options(@NotNull String key) {
        List<DictionaryOption> result = options.get(Objects.requireNonNull(key, "Dictionary 键不能为空"));
        if (result == null) {
            throw new IllegalArgumentException("代码 Dictionary 不存在: " + key);
        }
        return result.stream()
                .sorted(Comparator.comparingInt(DictionaryOption::sortOrder).thenComparing(DictionaryOption::value))
                .toList();
    }
}
