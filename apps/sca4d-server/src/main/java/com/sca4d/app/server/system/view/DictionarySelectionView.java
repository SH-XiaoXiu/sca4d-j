package com.sca4d.app.server.system.view;

import com.sca4d.core.api.dictionary.DictionaryOption;
import com.sca4d.core.api.i18n.LocalizableText;
import com.sca4d.core.spring.web.view.BaseView;
import com.sca4d.core.spring.web.view.Localize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 返回一个字典键及代码项和数据库项聚合后的选择项。 */
@SuppressWarnings({"ClassCanBeRecord", "unused"})
public final class DictionarySelectionView {

    private final String key;

    private final List<Option> options;

    /** 从聚合字典结果创建 HTTP 投影。 */
    public DictionarySelectionView(
            @NotNull String key,
            @NotNull List<@NotNull DictionaryOption> options) {
        this.key = Objects.requireNonNull(key, "Dictionary 键不能为空");
        this.options = List.copyOf(options.stream().map(Option::new).toList());
    }

    /** @return 字典键 */
    public @NotNull String getKey() {
        return key;
    }

    /** @return 代码项和数据库项聚合后的选择项 */
    public @NotNull @Unmodifiable List<@NotNull Option> getOptions() {
        return options;
    }

    /** 一个已经可以直接展示的字典选择项。 */
    public static final class Option extends BaseView {
        private final String value;

        private final String labelKey;

        private final LocalizableText localizedLabel;

        @Localize(source = "labelText")
        private final String label;

        private final boolean codeDefined;

        private final boolean disabled;

        private Option(@NotNull DictionaryOption option) {
            this.value = option.value();
            this.labelKey = option.labelKey();
            this.label = option.label() == null ? option.labelKey() : option.label();
            this.localizedLabel = option.labelText();
            this.codeDefined = option.codeDefined();
            this.disabled = !option.enabled();
        }

        /** @return 提交给业务接口的值 */
        public @NotNull String getValue() {
            return value;
        }

        /** @return 代码选项的消息键；数据库选项返回空 */
        public @Nullable String getLabelKey() {
            return labelKey;
        }

        /** @return 当前请求语言的代码标签或数据库维护的标签 */
        public @NotNull String getLabel() {
            return label;
        }

        /** @return 选项由 Java 枚举声明时返回 {@code true} */
        public boolean isCodeDefined() {
            return codeDefined;
        }

        /** @return 选项不能用于新输入时返回 {@code true} */
        public boolean isDisabled() {
            return disabled;
        }

        /** @return 代码消息或数据库标签的本地化文本 */
        @SuppressWarnings("ProtectedMemberInFinalClass") // @Localize 要求 source 是 protected 方法。
        protected @NotNull LocalizableText labelText() {
            return localizedLabel;
        }
    }
}
