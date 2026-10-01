package com.sca4d.core.spring.setting;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.jar.JarFile;

/** 只从指定 JAR 读取配置资源，避免多个插件的同名资源在全局 classpath 中混淆。 */
public final class JarSettingReader implements SettingReader<byte[]> {
    private final Path artifact;

    private final SettingOwner owner;

    private final String resource;

    /** 固定不可变构件、资源路径和所属；读取其他 owner 时失败。 */
    public JarSettingReader(@NotNull Path artifact, @NotNull String resource, @NotNull SettingOwner owner) {
        this.artifact = Objects.requireNonNull(artifact);
        this.resource = Objects.requireNonNull(resource);
        this.owner = Objects.requireNonNull(owner);
    }

    @Override
    public @NotNull TreeCode code() {
        return TreeCode.of("SETTING:JAR");
    }

    @Override
    public @NotNull SettingValue<byte[]> read(@NotNull TreeCode setting, @NotNull SettingOwner requested) {
        if (!owner.equals(requested))
            throw new IllegalArgumentException("JAR 配置 owner 不一致");
        try (JarFile jar = new JarFile(artifact.toFile())) {
            var entry = jar.getJarEntry(resource);
            if (entry == null)
                return SettingValue.read(code(), setting, owner, null, 0, null, false);
            if (entry.getSize() > 1_048_576)
                throw new IllegalArgumentException("配置资源超过 1 MiB");
            try (var input = jar.getInputStream(entry)) {
                byte[] bytes = input.readNBytes(1_048_577);
                if (bytes.length > 1_048_576)
                    throw new IllegalArgumentException("配置资源超过 1 MiB");
                return SettingValue.read(code(), setting, owner, bytes, 0, null, false);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("插件配置资源读取失败", failure);
        }
    }
}
