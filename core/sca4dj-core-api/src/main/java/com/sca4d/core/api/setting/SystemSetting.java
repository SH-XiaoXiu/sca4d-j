package com.sca4d.core.api.setting;

import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 定义固定属于 `Subject.SYSTEM` 且可以由系统设置管理接口维护的设置。 */
public final class SystemSetting<T> extends Setting<T> {

    private final SettingGroup group;

    private final String labelKey;

    private final String descriptionKey;

    private final String environmentVariable;

    private final int sortOrder;

    /**
     * 创建系统设置定义。
     *
     * @param key 数据库中长期稳定的点分小写键
     * @param schemaVersion 当前值结构版本
     * @param group 管理端所属分组
     * @param labelKey 设置名称的国际化消息键
     * @param descriptionKey 设置说明的国际化消息键
     * @param environmentVariable 初始化节点读取的 ENV 名称
     * @param defaultValue 数据库没有覆盖值时使用的默认值
     * @param codec 设置值的文本和 JSON 编解码器
     * @param pipeline 对类型值执行规范化和校验的 Pipeline
     * @param secret 是否只保存密文且不向管理查询返回当前值
     * @param sortOrder 分组内展示顺序
     */
    public SystemSetting(
            @NotNull String key,
            int schemaVersion,
            @NotNull SettingGroup group,
            @NotNull String labelKey,
            @NotNull String descriptionKey,
            @NotNull String environmentVariable,
            @NotNull T defaultValue,
            @NotNull SettingCodec<T> codec,
            @NotNull Pipeline<T, T> pipeline,
            boolean secret,
            int sortOrder) {
        super(key, schemaVersion, defaultValue, codec, pipeline, secret);
        this.group = Objects.requireNonNull(group, "设置分组不能为空");
        this.labelKey = TextUtils.requireText(labelKey, "设置标签消息键");
        this.descriptionKey = TextUtils.requireText(descriptionKey, "设置说明消息键");
        this.environmentVariable = TextUtils.requireText(environmentVariable, "设置初始化 ENV");
        this.sortOrder = sortOrder;
    }

    /** @return 管理端所属分组 */
    @Contract(pure = true)
    public @NotNull SettingGroup group() {
        return group;
    }

    /** @return 设置名称的国际化消息键 */
    @Contract(pure = true)
    public @NotNull String labelKey() {
        return labelKey;
    }

    /** @return 设置说明的国际化消息键 */
    @Contract(pure = true)
    public @NotNull String descriptionKey() {
        return descriptionKey;
    }

    /** @return 初始化节点读取的 ENV 名称 */
    @Contract(pure = true)
    public @NotNull String environmentVariable() {
        return environmentVariable;
    }

    /** @return 分组内展示顺序 */
    @Contract(pure = true)
    public int sortOrder() {
        return sortOrder;
    }
}
