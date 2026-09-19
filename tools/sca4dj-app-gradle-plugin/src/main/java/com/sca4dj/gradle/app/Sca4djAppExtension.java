package com.sca4dj.gradle.app;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/** 配置 Sca4d-J App 自己拥有的构建结果。 */
public abstract class Sca4djAppExtension {

    /**
     * 返回 Flyway 初始迁移文件使用的稳定名称。
     *
     * @return 不包含版本和扩展名的迁移名称
     */
    public abstract Property<String> getMigrationName();

    /**
     * 返回可选的 App 自有数据库结构片段。
     *
     * @return 生成 V1 时最后写入的 SQL 文件
     */
    public abstract RegularFileProperty getSchemaFragment();
}
