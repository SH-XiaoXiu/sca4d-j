package com.sca4d.core.api.setting;

import org.jetbrains.annotations.NotNull;

/** 表示数据库设置值结构版本与当前 Java 定义不一致且没有声明转换规则。 */
public final class SettingSchemaVersionException extends IllegalStateException {

    /**
     * 创建不支持的设置结构版本异常。
     *
     * @param settingKey 设置键
     * @param storedVersion 数据库存储的结构版本
     * @param expectedVersion Java 定义要求的结构版本
     */
    public SettingSchemaVersionException(
            @NotNull String settingKey,
            int storedVersion,
            int expectedVersion) {
        super("设置结构版本不受支持: " + settingKey + ", stored=" + storedVersion + ", expected=" + expectedVersion);
    }
}
