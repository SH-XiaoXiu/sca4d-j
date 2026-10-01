package com.sca4d.core.api.setting.definition;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.Setting;
import com.sca4d.core.api.setting.WritableSetting;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.codec.SettingCodec;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/** 定义需要调用方明确提供 Account、Principal 或 Resource owner 的设置。 */
public abstract class PersonalSetting<T> extends WritableSetting<T> {

    /** 创建个人设置定义。 */
    protected PersonalSetting(
            @NotNull SettingReader<String> reader,
            @NotNull SettingWriter<String> writer,
            @NotNull TreeCode code,
            @NotNull T defaultValue,
            @NotNull SettingCodec<T> codec,
            @NotNull Pipeline<T, T> pipeline,
            boolean secret) {
        super(
                Setting.builder(code, codec).from(reader).defaultValue(defaultValue).validate(pipeline).secret(secret),
                reader,
                writer,
                Function.identity());
    }

    /** 返回支持管理界面 JSON 表示的当前版本 Codec。 */
    @Override
    public @NotNull SettingCodec<T> codec() {
        return (SettingCodec<T>) super.codec();
    }
}
