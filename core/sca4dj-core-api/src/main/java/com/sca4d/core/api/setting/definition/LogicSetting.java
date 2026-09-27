package com.sca4d.core.api.setting.definition;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.codec.SettingCodec;
import org.jetbrains.annotations.NotNull;

/** 定义由后端领域 Service 类型化读取和修改的 owner 设置。 */
public class LogicSetting<T> extends PersonalSetting<T> {

    /**
     * 创建后端逻辑设置定义。
     *
     * @param reader 读取当前 owner 外部文本的 `SettingReader`
     * @param writer 按版本条件保存当前 owner 外部文本的 `SettingWriter`
     * @param code 设置稳定编码
     * @param defaultValue owner 没有保存值时使用的默认值
     * @param codec 设置值的文本和 JSON 编解码器
     * @param pipeline 对类型值执行规范化和校验的 Pipeline
     * @param secret 是否只保存密文
     */
    public LogicSetting(
            @NotNull SettingReader<String> reader,
            @NotNull SettingWriter<String> writer,
            @NotNull TreeCode code,
            @NotNull T defaultValue,
            @NotNull SettingCodec<T> codec,
            @NotNull Pipeline<T, T> pipeline,
            boolean secret) {
        super(reader, writer, code, defaultValue, codec, pipeline, secret);
    }
}
