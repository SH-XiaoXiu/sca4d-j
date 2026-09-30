package com.sca4d.core.api.setting.definition;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.Setting;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.WritableSetting;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.codec.SettingCodec;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Function;

/** 定义固定归属 `Subject.SYSTEM`、可以由系统设置管理接口维护的设置。 */
public final class SystemSetting<T> extends WritableSetting<T> {

    private final SettingGroup group;

    private final String descriptionKey;

    private final String environmentVariableSuffix;

    private final String labelKey;

    private final int sortOrder;

    /**
     * 创建系统设置定义。
     *
     * @param reader 读取 `Subject.SYSTEM` 外部文本的 `SettingReader`
     * @param writer 按版本条件保存 `Subject.SYSTEM` 外部文本的 `SettingWriter`
     * @param code 设置稳定编码
     * @param group 管理端所属分组
     * @param labelKey 设置名称的国际化消息键
     * @param descriptionKey 设置说明的国际化消息键
     * @param environmentVariableSuffix 初始化节点读取的 ENV 后缀，不包含产品前缀
     * @param defaultValue 数据库没有覆盖值时使用的默认值
     * @param codec 设置值的文本和 JSON 编解码器
     * @param pipeline 对类型值执行规范化和校验的 Pipeline
     * @param secret 是否只保存密文且不向管理查询返回当前值
     * @param sortOrder 分组内展示顺序
     */
    public SystemSetting(
            @NotNull SettingReader<String> reader,
            @NotNull SettingWriter<String> writer,
            @NotNull TreeCode code,
            @NotNull SettingGroup group,
            @NotNull String labelKey,
            @NotNull String descriptionKey,
            @NotNull String environmentVariableSuffix,
            @NotNull T defaultValue,
            @NotNull SettingCodec<T> codec,
            @NotNull Pipeline<T, T> pipeline,
            boolean secret,
            int sortOrder) {
        super(
                Setting.builder(code, codec)
                        .from(reader)
                        .defaultValue(defaultValue)
                        .validate(pipeline)
                        .secret(secret)
                        .owner(SettingOwner::system),
                reader,
                writer,
                Function.identity());
        this.group = Objects.requireNonNull(group, "设置分组不能为空");
        this.labelKey = TextUtils.requireText(labelKey, "设置标签消息键");
        this.descriptionKey = TextUtils.requireText(descriptionKey, "设置说明消息键");
        this.environmentVariableSuffix = TextUtils.requireText(environmentVariableSuffix, "设置初始化 ENV 后缀");
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

    /** @return 初始化节点读取的 ENV 后缀 */
    @Contract(pure = true)
    public @NotNull String environmentVariableSuffix() {
        return environmentVariableSuffix;
    }

    /** @return 分组内展示顺序 */
    @Contract(pure = true)
    public int sortOrder() {
        return sortOrder;
    }

    /** 返回支持管理界面 JSON 表示的当前版本 Codec。 */
    @Override
    public @NotNull SettingCodec<T> codec() {
        return (SettingCodec<T>) super.codec();
    }
}
