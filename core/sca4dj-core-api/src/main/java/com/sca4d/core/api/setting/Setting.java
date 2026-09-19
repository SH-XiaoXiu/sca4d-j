package com.sca4d.core.api.setting;

import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 定义一个由 Java Bean 声明并按 owner 保存完整类型化值的设置。 */
public abstract class Setting<T> {

    private final String key;

    private final T defaultValue;

    private final SettingCodec<T> codec;

    private final Pipeline<T, T> pipeline;

    private final int schemaVersion;

    private final boolean secret;

    /**
     * 创建设置定义。
     *
     * @param key 数据库中长期稳定的点分小写键
     * @param schemaVersion 当前 Codec 能够读取和值结构版本
     * @param defaultValue 数据库没有值时使用的 Java 默认值
     * @param codec 文本编解码和取值校验
     * @param pipeline 对已经解码的值执行 owner 无关规范化和校验
     * @param secret 是否只以密文保存
     */
    protected Setting(
            @NotNull String key,
            int schemaVersion,
            @NotNull T defaultValue,
            @NotNull SettingCodec<T> codec,
            @NotNull Pipeline<T, T> pipeline,
            boolean secret) {
        this.key = TextUtils.requireText(key, "设置键");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("设置结构版本必须为正数");
        }
        this.schemaVersion = schemaVersion;
        this.codec = Objects.requireNonNull(codec, "设置编解码器不能为空");
        this.pipeline = Objects.requireNonNull(pipeline, "设置处理 Pipeline 不能为空");
        T normalized = this.pipeline.run(Objects.requireNonNull(defaultValue, "设置默认值不能为空"));
        this.defaultValue = this.codec.decode(this.codec.encode(normalized));
        this.secret = secret;
    }

    /** @return 数据库中长期稳定的设置键 */
    @Contract(pure = true)
    public @NotNull String key() {
        return key;
    }

    /** @return 当前 Codec 能够读取和值结构版本 */
    @Contract(pure = true)
    public int schemaVersion() {
        return schemaVersion;
    }

    /** @return 数据库没有值时使用的 Java 默认值 */
    @Contract(pure = true)
    public @NotNull T defaultValue() {
        return defaultValue;
    }

    /** @return 当前设置使用的文本编解码器 */
    @Contract(pure = true)
    public @NotNull SettingCodec<T> codec() {
        return codec;
    }

    /** @return 对已经解码的值执行 owner 无关规范化和校验的 Pipeline */
    @Contract(pure = true)
    public @NotNull Pipeline<T, T> pipeline() {
        return pipeline;
    }

    /** @return 数据库必须保存密文且管理查询不得返回当前值时返回 {@code true} */
    @Contract(pure = true)
    public boolean secret() {
        return secret;
    }

}
