package com.sca4d.app.server.system.view;

import com.sca4d.app.server.system.ManagedDictionary;
import com.sca4d.app.server.system.ManagedDictionaryOption;
import com.sca4d.core.api.dictionary.DictionaryOption;
import com.sca4d.core.api.i18n.LocalizableText;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.spring.web.view.BaseView;
import com.sca4d.core.spring.web.view.Localize;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 返回管理端可查看的代码字典、动态字典、代码项和数据库项。 */
@SuppressWarnings("unused")
public class SystemDictionariesView {

    private final List<Entry> dictionaries;

    private SystemDictionariesView(@NotNull List<Entry> dictionaries) {
        this.dictionaries = List.copyOf(dictionaries);
    }

    /** 从字典管理结果创建 HTTP 投影。 */
    @CheckReturnValue
    public static @NotNull SystemDictionariesView from(
            @NotNull List<@NotNull ManagedDictionary> dictionaries) {
        return new SystemDictionariesView(
                Objects.requireNonNull(dictionaries, "Dictionary 管理结果不能为空")
                        .stream()
                        .map(Entry::new)
                        .toList());
    }

    /** @return 按键排序的代码字典和动态字典 */
    public @NotNull @Unmodifiable List<@NotNull Entry> getDictionaries() {
        return dictionaries;
    }

    /** 管理端中的一个代码字典或动态字典。 */
    public static class Entry {
        private final String key;

        private final String label;

        private final boolean codeDefined;

        private final boolean optionsEditable;

        private final boolean enabled;

        private final int sortOrder;

        private final Long version;

        private final List<Option> options;

        private Entry(@NotNull ManagedDictionary dictionary) {
            this.key = dictionary.key();
            this.label = dictionary.label();
            this.codeDefined = dictionary.codeDefined();
            this.optionsEditable = dictionary.optionsEditable();
            this.enabled = dictionary.enabled();
            this.sortOrder = dictionary.sortOrder();
            this.version = dictionary.version();
            this.options = List.copyOf(dictionary.options().stream().map(Option::new).toList());
        }

        /** @return 字典稳定键 */
        public @NotNull String getKey() {
            return key;
        }

        /** @return 动态字典名称；代码字典返回稳定键 */
        public @NotNull String getLabel() {
            return label;
        }

        /** @return 字典由 Java 枚举声明时返回 {@code true} */
        public boolean isCodeDefined() {
            return codeDefined;
        }

        /** @return 管理端可以管理数据库选项时返回 {@code true} */
        public boolean isOptionsEditable() {
            return optionsEditable;
        }

        /** @return 动态字典可供业务查询时返回 {@code true} */
        public boolean isEnabled() {
            return enabled;
        }

        /** @return 动态字典展示顺序；代码字典返回 {@code 0} */
        public int getSortOrder() {
            return sortOrder;
        }

        /** @return 动态字典乐观锁版本；代码字典返回空 */
        public @Nullable Long getVersion() {
            return version;
        }

        /** @return 代码项与数据库项 */
        public @NotNull @Unmodifiable List<@NotNull Option> getOptions() {
            return options;
        }
    }

    /** 管理端中的一个代码字典项或数据库字典项。 */
    public static class Option extends BaseView {
        private final String value;

        private final String labelKey;

        private final LocalizedFields i18n;

        private final LocalizableText localizedLabel;

        @Localize(source = "labelText")
        private final String label;

        private final String fallbackLabel;

        private final boolean codeDefined;

        private final boolean enabled;

        private final int sortOrder;

        private final Long version;

        private Option(@NotNull ManagedDictionaryOption managed) {
            DictionaryOption option = managed.option();
            this.value = option.value();
            this.labelKey = option.labelKey();
            this.label = option.label() == null ? option.labelKey() : option.label();
            this.fallbackLabel = option.label();
            this.i18n = option.i18n();
            this.localizedLabel = option.labelText();
            this.codeDefined = option.codeDefined();
            this.enabled = option.enabled();
            this.sortOrder = option.sortOrder();
            this.version = managed.version();
        }

        /** @return 提交给业务接口的稳定值 */
        public @NotNull String getValue() {
            return value;
        }

        /** @return 代码项的国际化消息键；数据库项返回空 */
        public @Nullable String getLabelKey() {
            return labelKey;
        }

        /** @return 数据库项完整多语言 label；代码项返回空 */
        public @Nullable LocalizedFields getI18n() {
            return i18n;
        }

        /** @return 数据库项缺少翻译时使用的回退标签；代码项返回空 */
        public @Nullable String getFallbackLabel() {
            return fallbackLabel;
        }

        /** @return 当前请求语言的代码标签或数据库维护的标签 */
        public @NotNull String getLabel() {
            return label;
        }

        /** @return 选项由 Java 枚举声明时返回 {@code true} */
        public boolean isCodeDefined() {
            return codeDefined;
        }

        /** @return 选项允许用于新的业务输入时返回 {@code true} */
        public boolean isEnabled() {
            return enabled;
        }

        /** @return 同一字典内的展示顺序 */
        public int getSortOrder() {
            return sortOrder;
        }

        /** @return 数据库项乐观锁版本；代码项返回空 */
        public @Nullable Long getVersion() {
            return version;
        }

        /** @return 代码消息或数据库标签的本地化文本 */
        @SuppressWarnings("ProtectedMemberInFinalClass") // @Localize 要求 source 是 protected 方法。
        protected @NotNull LocalizableText labelText() {
            return localizedLabel;
        }
    }
}
