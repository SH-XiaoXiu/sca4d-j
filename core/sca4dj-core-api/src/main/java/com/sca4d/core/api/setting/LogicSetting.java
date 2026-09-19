package com.sca4d.core.api.setting;

import com.sca4d.core.api.pipeline.Pipeline;
import org.jetbrains.annotations.NotNull;

/** 定义由后端领域 Service 类型化读取和修改的 owner 设置。 */
public final class LogicSetting<T> extends PersonalSetting<T> {

    /**
     * 创建后端逻辑设置定义。
     *
     * @param key 全局稳定设置键
     * @param schemaVersion 当前值结构版本
     * @param defaultValue owner 没有保存值时使用的默认值
     * @param codec 设置值的文本和 JSON 编解码器
     * @param pipeline 对类型值执行规范化和校验的 Pipeline
     * @param secret 是否只保存密文
     */
    public LogicSetting(
            @NotNull String key,
            int schemaVersion,
            @NotNull T defaultValue,
            @NotNull SettingCodec<T> codec,
            @NotNull Pipeline<T, T> pipeline,
            boolean secret) {
        super(key, schemaVersion, defaultValue, codec, pipeline, secret);
    }
}
