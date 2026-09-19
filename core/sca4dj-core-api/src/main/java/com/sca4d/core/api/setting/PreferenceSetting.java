package com.sca4d.core.api.setting;

import com.sca4d.core.api.pipeline.Pipeline;
import org.jetbrains.annotations.NotNull;

/** 定义由前端完整读取和替换、后端不解释具体展示字段的个人偏好。 */
public final class PreferenceSetting<T> extends PersonalSetting<T> {

    /**
     * 创建只能以明文 JSON 传输和保存的个人偏好定义。
     *
     * @param key 全局稳定设置键
     * @param schemaVersion 当前值结构版本
     * @param defaultValue owner 没有保存值时使用的默认值
     * @param codec 设置值的文本和 JSON 编解码器
     * @param pipeline 对类型值执行规范化和校验的 Pipeline
     */
    public PreferenceSetting(
            @NotNull String key,
            int schemaVersion,
            @NotNull T defaultValue,
            @NotNull SettingCodec<T> codec,
            @NotNull Pipeline<T, T> pipeline) {
        super(key, schemaVersion, defaultValue, codec, pipeline, false);
    }
}
