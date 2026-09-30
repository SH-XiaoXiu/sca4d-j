package com.sca4d.core.spring.dictionary;

import com.sca4d.core.api.i18n.LocalizedFields;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存动态字典选项或代码字典扩展项的配置。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class DatabaseDictionaryOption {

    private final LocalizedFields i18n;

    private final String label;

    private final String value;

    private final boolean enabled;

    private final int sortOrder;

    private final long version;

    /**
     * 创建数据库字典选项。
     *
     * @param value 提交给业务接口的稳定值
     * @param label 所有目标 Locale 都没有翻译时使用的回退标签
     * @param i18n 按 Locale 保存的完整多语言 label，可以不包含任何 Locale
     * @param enabled 是否允许用于新的业务输入
     * @param sortOrder 同一字典内的展示顺序
     * @param version 当前乐观锁版本
     */
    public DatabaseDictionaryOption(
            @NotNull String value,
            @NotNull String label,
            @NotNull LocalizedFields i18n,
            boolean enabled,
            int sortOrder,
            long version) {
        this.value = Objects.requireNonNull(value, "字典值不能为空");
        this.label = Objects.requireNonNull(label, "字典标签不能为空");
        this.i18n = Objects.requireNonNull(i18n, "字典多语言标签不能为空");
        this.enabled = enabled;
        this.sortOrder = sortOrder;
        this.version = version;
    }

    /** @return 提交给业务接口的值 */
    public @NotNull String value() {
        return value;
    }

    /** @return 管理端维护的展示文本 */
    public @NotNull String label() {
        return label;
    }

    /** @return 按 Locale 保存的完整多语言标签 */
    public @NotNull LocalizedFields i18n() {
        return i18n;
    }

    /** @return 允许用于新的业务输入时返回 {@code true} */
    public boolean enabled() {
        return enabled;
    }

    /** @return 同一字典内的展示顺序 */
    public int sortOrder() {
        return sortOrder;
    }

    /** @return 当前乐观锁版本 */
    public long version() {
        return version;
    }
}
