package com.sca4d.common.dictionary;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 代码字典中的一个不可变枚举常量定义。
 *
 * <p>该对象保存固定值、Java 来源、发布状态和发布时使用的展示元数据，不保存已经本地化的标签。未发布常量没有标签消息键，
 * 但仍参与目录级固定值校验。
 */
public final class CodeDictionaryOption {

    private final String value;
    private final String labelKey;
    private final DictionaryTone tone;
    private final boolean selectable;
    private final int sortOrder;
    private final DictionarySource source;
    private final String sourceRef;
    private final boolean published;

    /**
     * 创建一个已经通过代码目录校验的枚举项定义。
     *
     * @param value HTTP、数据库和业务判断共用的固定值
     * @param labelKey 后端国际化资源中的标签消息键；未发布常量为空
     * @param tone 管理端共用组件使用的含义色调
     * @param selectable 是否允许用于新的业务输入
     * @param sortOrder 按枚举声明顺序生成的首次写入排序值
     * @param source 字典项由代码同步或由管理端维护
     * @param sourceRef 枚举类型和常量组成的固定代码来源
     * @param published 是否形成数据库和共用选项接口投影
     */
    public CodeDictionaryOption(
            @NotNull String value,
            @Nullable String labelKey,
            @NotNull DictionaryTone tone,
            boolean selectable,
            int sortOrder,
            @NotNull DictionarySource source,
            @NotNull String sourceRef,
            boolean published) {
        this.value = value;
        this.labelKey = labelKey;
        this.tone = tone;
        this.selectable = selectable;
        this.sortOrder = sortOrder;
        this.source = source;
        this.sourceRef = sourceRef;
        this.published = published;
    }

    /** @return HTTP、数据库和业务判断共用的固定值 */
    public @NotNull String getValue() {
        return value;
    }

    /** @return 后端国际化资源中的标签消息键 */
    public @Nullable String getLabelKey() {
        return labelKey;
    }

    /** @return 管理端共用组件使用的含义色调 */
    public @NotNull DictionaryTone getTone() {
        return tone;
    }

    /** @return 是否允许用于新的业务输入 */
    public boolean isSelectable() {
        return selectable;
    }

    /** @return 首次同步使用的默认排序值 */
    public int getSortOrder() {
        return sortOrder;
    }

    /** @return 字典项由代码同步或由管理端维护 */
    public @NotNull DictionarySource getSource() {
        return source;
    }

    /** @return 枚举类型和常量组成的固定代码来源 */
    public @NotNull String getSourceRef() {
        return sourceRef;
    }

    /** @return 是否形成数据库和共用选项接口投影 */
    public boolean isPublished() {
        return published;
    }
}
