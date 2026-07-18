package com.sca4d.common.dictionary;

import com.sca4d.common.enums.BaseEnum;
import com.sca4d.common.validation.ValidationPatterns;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 代码枚举形成的不可变字典目录。
 *
 * <p>目录在应用启动阶段一次性校验类型编码、固定值、消息键和重复定义，运行期间只提供不可变快照查询。
 */
public final class CodeDictionaryCatalog {

    private final Map<String, CodeDictionaryDefinition> definitions;

    /**
     * 从扫描得到的枚举构建完整代码字典目录。
     *
     * <p>每个枚举和枚举常量各遍历一次，反射只读取声明字段上的静态元数据，不执行数据库或外部调用。
     *
     * @param enumTypes 已确认实现 BaseEnum 的代码字典枚举集合
     */
    public CodeDictionaryCatalog(@NotNull List<Class<? extends Enum<?>>> enumTypes) {
        Map<String, CodeDictionaryDefinition> checked = new LinkedHashMap<>();
        for (Class<? extends Enum<?>> enumType : enumTypes) {
            CodeDictionary annotation = enumType.getAnnotation(CodeDictionary.class);
            if (annotation == null) {
                throw new IllegalArgumentException("代码字典枚举缺少 @CodeDictionary: " + enumType.getName());
            }
            String code = annotation.code();
            if (!ValidationPatterns.isNamespacedCode(code)) {
                throw new IllegalArgumentException("代码字典类型编码格式错误: " + enumType.getName() + "." + code);
            }
            if (annotation.nameKey().isBlank()) {
                throw new IllegalArgumentException("代码字典名称消息键不能为空: " + enumType.getName());
            }
            CodeDictionaryDefinition definition = new CodeDictionaryDefinition(
                    code,
                    annotation.mode(),
                    DictionarySource.CODE,
                    annotation.nameKey(),
                    enumType.getName(),
                    options(enumType, code));
            CodeDictionaryDefinition existing = checked.putIfAbsent(code, definition);
            if (existing != null) {
                throw new IllegalArgumentException(
                        "重复代码字典类型编码: " + code + ", " + existing.getSourceRef() + ", " + enumType.getName());
            }
        }
        this.definitions = Map.copyOf(checked);
    }

    /**
     * 获取全部代码字典定义。
     *
     * @return 按编码固定排序的不可变定义集合
     */
    public @NotNull List<CodeDictionaryDefinition> definitions() {
        return definitions.values().stream().sorted(Comparator.comparing(CodeDictionaryDefinition::getCode)).toList();
    }

    /**
     * 按固定类型编码读取代码字典。
     *
     * @param code 调用方持有的字典类型编码
     * @return 匹配定义；未知编码返回 {@code null}
     */
    public @Nullable CodeDictionaryDefinition find(@NotNull String code) {
        return definitions.get(code);
    }

    /**
     * 从一个枚举读取并校验全部字典项。
     *
     * @param enumType 当前代码字典枚举
     * @param dictionaryCode 已校验的字典类型编码
     * @return 保持声明顺序并包含发布状态的枚举常量定义
     */
    private @NotNull List<CodeDictionaryOption> options(
            @NotNull Class<? extends Enum<?>> enumType, @NotNull String dictionaryCode) {
        Enum<?>[] constants = enumType.getEnumConstants();
        if (constants.length == 0) {
            throw new IllegalArgumentException("代码字典至少需要一个枚举项: " + enumType.getName());
        }
        Map<String, String> values = new LinkedHashMap<>();
        List<CodeDictionaryOption> options = new ArrayList<>(constants.length);
        for (int index = 0; index < constants.length; index++) {
            Enum<?> constant = constants[index];
            BaseEnum item = (BaseEnum) constant;
            String value = item.value();
            if (!ValidationPatterns.isBizCode(value)) {
                throw new IllegalArgumentException(
                        "代码字典枚举值格式错误: " + enumType.getName() + "." + constant.name() + "=" + value);
            }
            String existing = values.putIfAbsent(value.toUpperCase(Locale.ROOT), constant.name());
            if (existing != null) {
                throw new IllegalArgumentException("代码字典枚举值重复: " + enumType.getName() + "." + existing + ", "
                        + constant.name() + "=" + value);
            }
            CodeDictionaryItem metadata = itemMetadata(enumType, constant);
            boolean published = metadata == null || metadata.published();
            String labelKey = !published
                    ? null
                    : metadata == null || metadata.labelKey().isBlank()
                            ? "dictionary." + dictionaryCode + "." + value.toLowerCase(Locale.ROOT).replace('_', '-')
                            : metadata.labelKey();
            DictionaryTone tone = metadata == null ? DictionaryTone.DEFAULT : metadata.tone();
            boolean selectable = metadata == null || metadata.selectable();
            options.add(new CodeDictionaryOption(
                    value,
                    labelKey,
                    tone,
                    selectable,
                    (index + 1) * 10,
                    DictionarySource.CODE,
                    enumType.getName() + "#" + constant.name(),
                    published));
        }
        return List.copyOf(options);
    }

    /**
     * 读取枚举常量字段上的可选展示元数据。
     *
     * @param enumType 枚举声明类型
     * @param constant 当前枚举常量
     * @return 显式项元数据；未声明时返回 {@code null}
     * @throws IllegalStateException Java 枚举常量与声明字段不一致时抛出
     */
    private @Nullable CodeDictionaryItem itemMetadata(
            @NotNull Class<? extends Enum<?>> enumType, @NotNull Enum<?> constant) {
        try {
            Field field = enumType.getField(constant.name());
            return field.getAnnotation(CodeDictionaryItem.class);
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException("代码字典枚举常量字段不存在: " + enumType.getName() + "." + constant.name(), exception);
        }
    }
}
