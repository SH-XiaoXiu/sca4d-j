package com.sca4d.common.dictionary;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 一个由 Java 枚举声明的代码字典定义。
 *
 * <p>类型编码、模式、名称消息键、Java 来源和全部枚举常量共同构成启动同步使用的完整快照。枚举项同时保留发布状态，使目录可以
 * 校验隐藏常量而不把它们写入数据库。
 */
public final class CodeDictionaryDefinition {

    private final String code;
    private final CodeDictionaryMode mode;
    private final DictionarySource source;
    private final String nameKey;
    private final String sourceRef;
    private final List<CodeDictionaryOption> options;

    /**
     * 创建已经完成类型和值校验的代码字典定义。
     *
     * @param code 全局唯一的字典类型编码
     * @param mode 数据库是否可以维护枚举之外的业务编码
     * @param source 字典类型由代码同步或由管理端维护
     * @param nameKey 字典名称消息键
     * @param sourceRef 定义该字典的 Java 枚举完整类名
     * @param options 保持声明顺序的全部枚举常量
     */
    public CodeDictionaryDefinition(
            @NotNull String code,
            @NotNull CodeDictionaryMode mode,
            @NotNull DictionarySource source,
            @NotNull String nameKey,
            @NotNull String sourceRef,
            @NotNull List<CodeDictionaryOption> options) {
        this.code = code;
        this.mode = mode;
        this.source = source;
        this.nameKey = nameKey;
        this.sourceRef = sourceRef;
        this.options = List.copyOf(options);
    }

    /** @return 全局唯一的字典类型编码 */
    public @NotNull String getCode() {
        return code;
    }

    /** @return 数据库对普通业务编码的开放方式 */
    public @NotNull CodeDictionaryMode getMode() {
        return mode;
    }

    /** @return 字典类型由代码同步或由管理端维护 */
    public @NotNull DictionarySource getSource() {
        return source;
    }

    /** @return 字典名称消息键 */
    public @NotNull String getNameKey() {
        return nameKey;
    }

    /** @return 定义该字典的 Java 枚举完整类名 */
    public @NotNull String getSourceRef() {
        return sourceRef;
    }

    /** @return 保持声明顺序并包含发布状态的全部枚举常量 */
    public @NotNull List<CodeDictionaryOption> getOptions() {
        return options;
    }
}
