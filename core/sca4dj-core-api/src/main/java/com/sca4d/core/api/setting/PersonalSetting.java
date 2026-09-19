package com.sca4d.core.api.setting;

import com.sca4d.core.api.pipeline.Pipeline;
import org.jetbrains.annotations.NotNull;

/** 定义需要调用方明确提供 Account、Principal 或 Resource owner 的设置。 */
public abstract class PersonalSetting<T> extends Setting<T> {

    /** 创建个人设置定义。 */
    protected PersonalSetting(
            @NotNull String key,
            int schemaVersion,
            @NotNull T defaultValue,
            @NotNull SettingCodec<T> codec,
            @NotNull Pipeline<T, T> pipeline,
            boolean secret) {
        super(key, schemaVersion, defaultValue, codec, pipeline, secret);
    }
}
