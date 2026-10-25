package com.sca4d.storage.local;

import com.sca4d.core.api.storage.FileStorage;
import com.sca4d.core.api.storage.StorageObject;
import com.sca4d.core.api.storage.StorageObjectMetadata;
import com.sca4d.core.api.storage.StorageTransferSigner;
import com.sca4d.core.api.storage.StorageUpload;
import com.sca4d.storage.local.exception.StorageLocalException;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 把对象限制在配置根目录内，并使用 App 签名 URL 接收和返回内容。 */
public class LocalFileStorage implements FileStorage {
    private final @NotNull LocalStorageProperties properties;

    private final @NotNull StorageTransferSigner signer;

    private @NotNull Path root;

    /**
     * 创建本地目录存储。
     *
     * @param properties 本地目录、实现标识和 App 隔离名称配置
     * @param signer App 文件传输路由的短期地址签名器
     */
    public LocalFileStorage(@NotNull LocalStorageProperties properties, @NotNull StorageTransferSigner signer) {
        this.properties = Objects.requireNonNull(properties, "本地存储配置不能为空");
        this.signer = Objects.requireNonNull(signer, "传输签名器不能为空");
        this.root = properties.getRoot().toAbsolutePath().normalize();
    }

    @Override
    public @NotNull String backendId() {
        return properties.getBackendId();
    }

    @Override
    public @NotNull String defaultContainer() {
        return properties.getContainer();
    }

    @Override
    public void checkAvailable() {
        try {
            Files.createDirectories(root);
            root = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
                throw new StorageLocalException("本地存储根目录不可用");
            }
        } catch (IOException exception) {
            throw new StorageLocalException("无法初始化本地存储根目录", exception);
        }
    }

    @Override
    public @NotNull StorageUpload prepareUpload(
            long fileId,
            @NotNull StorageObject object,
            @NotNull String contentType,
            long sizeBytes,
            @NotNull Instant expiresAt) {
        return new StorageUpload(signer.uploadUrl(fileId, expiresAt), Map.of("Content-Type", contentType));
    }

    @Override
    public @NotNull Optional<StorageObjectMetadata> inspect(@NotNull StorageObject object) {
        Path path = resolve(object);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new StorageObjectMetadata(readContentType(path), Files.size(path)));
        } catch (IOException exception) {
            throw new StorageLocalException("无法读取本地对象元数据", exception);
        }
    }

    @Override
    public void promote(@NotNull StorageObject source, @NotNull StorageObject target) {
        Path sourcePath = resolve(source);
        Path targetPath = resolve(target);
        try {
            createSafeParents(targetPath);
            Path sourceMetadata = metadataPath(sourcePath);
            Path targetMetadata = metadataPath(targetPath);
            if (Files.exists(sourceMetadata, LinkOption.NOFOLLOW_LINKS)) {
                moveReplacing(sourceMetadata, targetMetadata);
            }
            if (!Files.exists(sourcePath, LinkOption.NOFOLLOW_LINKS)
                    && Files.isRegularFile(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            moveReplacing(sourcePath, targetPath);
        } catch (IOException exception) {
            throw new StorageLocalException("无法转正本地对象", exception);
        }
    }

    @Override
    public @NotNull URI prepareAccess(
            long fileId,
            @NotNull StorageObject object,
            @NotNull Instant expiresAt) {
        return signer.accessUrl(fileId, expiresAt);
    }

    @Override
    public void write(
            @NotNull StorageObject object,
            @NotNull String contentType,
            long sizeBytes,
            @NotNull InputStream content)
            throws IOException {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("文件字节数必须为正数");
        }
        Objects.requireNonNull(content, "文件内容不能为空");
        Path target = resolve(object);
        createSafeParents(target);
        Path temporary = target.resolveSibling("." + target.getFileName() + "." + UUID.randomUUID() + ".part");
        Path metadata = metadataPath(target);
        Path temporaryMetadata = metadata.resolveSibling(
                metadata.getFileName() + "." + UUID.randomUUID() + ".part");
        try {
            long copied = Files.copy(new BoundedInputStream(content, sizeBytes + 1), temporary);
            if (copied != sizeBytes) {
                throw new IllegalArgumentException("上传内容字节数与申请不一致");
            }
            Files.writeString(temporaryMetadata, contentType, StandardCharsets.UTF_8);
            moveReplacing(temporaryMetadata, metadata);
            moveReplacing(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
            Files.deleteIfExists(temporaryMetadata);
        }
    }

    @Override
    public @NotNull InputStream open(@NotNull StorageObject object) throws IOException {
        return Files.newInputStream(resolve(object), LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public void delete(@NotNull StorageObject object) {
        try {
            Path path = resolve(object);
            Files.deleteIfExists(path);
            Files.deleteIfExists(metadataPath(path));
        } catch (IOException exception) {
            throw new StorageLocalException("无法删除本地对象", exception);
        }
    }

    @SuppressWarnings("ExtractMethodRecommender") // 路径校验必须连续展示容器、逃逸和符号链接约束。
    private @NotNull Path resolve(@NotNull StorageObject object) {
        if (!properties.getContainer().equals(object.container())) {
            throw new IllegalArgumentException("本地存储命名空间不匹配");
        }
        Path relative = Path.of(object.objectKey());
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("对象键不能是绝对路径");
        }
        for (Path segment : relative) {
            if (segment.toString().equals("..")) {
                throw new IllegalArgumentException("对象键不能包含上级目录");
            }
        }
        Path resolved = root.resolve(relative).normalize();
        if (resolved.equals(root) || !resolved.startsWith(root)) {
            throw new IllegalArgumentException("对象键超出本地存储根目录");
        }
        try {
            rejectSymbolicLinks(resolved);
        } catch (IOException exception) {
            throw new IllegalArgumentException("本地存储路径不可用", exception);
        }
        return resolved;
    }

    private void createSafeParents(@NotNull Path target) throws IOException {
        Path parent = Objects.requireNonNull(target.getParent(), "对象必须位于存储根目录内");
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IOException("本地存储根目录不可用");
        }
        Path current = root;
        for (Path segment : root.relativize(parent)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("本地存储路径包含符号链接或非目录节点");
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    /** 拒绝根目录到目标路径之间已经存在的符号链接。 */
    private void rejectSymbolicLinks(@NotNull Path target) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IOException("本地存储根目录不可用");
        }
        Path current = root;
        for (Path segment : root.relativize(target)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IOException("本地存储路径包含符号链接");
            }
        }
    }

    private @NotNull String readContentType(@NotNull Path path) throws IOException {
        Path metadata = metadataPath(path);
        rejectSymbolicLinks(metadata);
        if (Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)) {
            try (InputStream input = Files.newInputStream(metadata, LinkOption.NOFOLLOW_LINKS)) {
                String contentType = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!contentType.isEmpty())
                    return contentType;
            }
        }
        return detectContentType(path);
    }

    private static @NotNull Path metadataPath(@NotNull Path objectPath) {
        return objectPath.resolveSibling("." + objectPath.getFileName() + ".content-type");
    }

    private static void moveReplacing(@NotNull Path source, @NotNull Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static @NotNull String detectContentType(@NotNull Path path) throws IOException {
        byte[] bytes = new byte[12];
        int count;
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS))) {
            count = input.read(bytes);
        }
        if (count >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff)
            return "image/jpeg";
        if (count >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G')
            return "image/png";
        if (count >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P')
            return "image/webp";
        String detected = Files.probeContentType(path);
        return detected == null ? "application/octet-stream" : detected;
    }

    /** 限制单次本地上传最多读取给定字节数。 */
    private static class BoundedInputStream extends InputStream {
        private final InputStream delegate;

        private long remaining;

        private BoundedInputStream(InputStream delegate, long limit) {
            this.delegate = Objects.requireNonNull(delegate, "文件内容不能为空");
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0)
                return -1;
            int value = delegate.read();
            if (value >= 0)
                remaining--;
            return value;
        }

        @Override
        public int read(byte @NotNull [] bytes, int offset, int length) throws IOException {
            if (length == 0)
                return 0;
            if (remaining <= 0)
                return -1;
            int count = delegate.read(bytes, offset, (int) Math.min(length, remaining));
            if (count > 0)
                remaining -= count;
            return count;
        }
    }
}
