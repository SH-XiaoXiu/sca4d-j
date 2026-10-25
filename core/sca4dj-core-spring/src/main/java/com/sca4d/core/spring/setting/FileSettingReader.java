package com.sca4d.core.spring.setting;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
import com.sca4d.core.spring.exception.CoreSpringException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiFunction;

/** 按明确所属读取手工维护的文件字节；字节内容由设置声明的 Codec 解释。 */
public final class FileSettingReader implements SettingReader<byte[]> {
    private final BiFunction<TreeCode, SettingOwner, Path> location;

    private final Path root;

    /** location 按设置及归属定位文件；读取时拒绝根目录外的路径和符号链接。 */
    public FileSettingReader(@NotNull Path root, @NotNull BiFunction<TreeCode, SettingOwner, Path> location) {
        this.root = root.toAbsolutePath().normalize();
        this.location = Objects.requireNonNull(location);
    }

    @Override
    public @NotNull TreeCode code() {
        return TreeCode.of("SETTING:FILE");
    }

    /**
     * 读取 `owner` 映射到的设置文件内容；文件不存在时返回值为 null 的 SettingValue。
     *
     * @throws IllegalArgumentException 定位路径超出根目录、路径中包含符号链接或文件超过 1 MiB 时抛出
     * @throws IllegalStateException 文件读取发生 IO 错误时抛出，异常消息只包含设置键
     */
    @Override
    public @NotNull SettingValue<byte[]> read(@NotNull TreeCode setting, @NotNull SettingOwner owner) {
        Path path = checkedPath(root, location.apply(setting, owner));
        try {
            if (!Files.exists(path))
                return SettingValue.read(code(), setting, owner, null, 0, null, false);
            return SettingValue.read(code(), setting, owner, readBytes(path), 0, null, false);
        } catch (IOException failure) {
            throw new CoreSpringException("设置文件读取失败: " + setting, failure);
        }
    }

    /** 限制配置字节数，避免在检查大小之前无界分配内存。 */
    static byte[] readBytes(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            byte[] content = input.readNBytes(1_048_577);
            if (content.length > 1_048_576)
                throw new IllegalArgumentException("设置文件超过 1 MiB");
            return content;
        }
    }

    /** 拒绝根目录外的定位和符号链接，防止 owner 映射越过声明目录。 */
    static Path checkedPath(Path root, Path location) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path path = normalizedRoot.resolve(location).normalize();
        if (!path.startsWith(normalizedRoot) || path.equals(normalizedRoot))
            throw new IllegalArgumentException("设置文件必须位于声明根目录内");
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current))
                throw new IllegalArgumentException("设置路径不能包含符号链接");
        }
        return path;
    }
}
