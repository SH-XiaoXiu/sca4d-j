package com.sca4d.iot.persistence;

import com.sca4d.core.api.i18n.LocalizedFields;
import org.jetbrains.annotations.NotNull;

/**
 * 保存数据库物模型的规范 JSON 和摘要。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class EncodedThingModel {
    private final int version;

    private final @NotNull String modelJson;

    private final @NotNull String contentHash;

    private final @NotNull LocalizedFields i18n;

    /**
     * 创建一个已经完成 JSON 编码的物模型版本。
     *
     * @param version 版本
     * @param modelJson 规范 JSON
     * @param contentHash SHA-256 摘要
     * @param i18n 与技术模型分开保存的完整动态翻译
     */
    public EncodedThingModel(
            int version,
            @NotNull String modelJson,
            @NotNull String contentHash,
            @NotNull LocalizedFields i18n) {
        this.version = version;
        this.modelJson = modelJson;
        this.contentHash = contentHash;
        this.i18n = i18n;
    }

    /**
     * @return 版本
     */
    public int version() {
        return version;
    }

    /**
     * @return 规范 JSON
     */
    public @NotNull String modelJson() {
        return modelJson;
    }

    /**
     * @return 内容摘要
     */
    public @NotNull String contentHash() {
        return contentHash;
    }

    /** @return 与技术模型分开保存的完整动态翻译 */
    public @NotNull LocalizedFields i18n() {
        return i18n;
    }
}
