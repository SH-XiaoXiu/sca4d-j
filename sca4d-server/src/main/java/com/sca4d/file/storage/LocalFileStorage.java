package com.sca4d.file.storage;

import com.sca4d.common.exception.BizException;
import com.sca4d.config.properties.Sca4djFileProperties;
import com.sca4d.file.FileAccessUrl;
import com.sca4d.file.FileUploadMethod;
import com.sca4d.file.FileUploadMode;
import com.sca4d.file.error.FileErrorCode;
import com.sca4d.file.signature.FileGrantSigner;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

/** 本地文件系统存储。 */
public class LocalFileStorage implements FileStorage {

    private final Path root;
    private final FileGrantSigner grantSigner;

    /**
     * 创建限制在配置根目录内的本地文件存储。
     *
     * @param properties 本地对象根目录配置
     * @param grantSigner 本地下载接口使用的读取授权签发器
     */
    public LocalFileStorage(@NotNull Sca4djFileProperties properties, @NotNull FileGrantSigner grantSigner) {
        this.root = properties.getLocalRoot().toAbsolutePath().normalize();
        this.grantSigner = grantSigner;
    }

    @Override
    public @NotNull FileStorageProvider provider() {
        return FileStorageProvider.LOCAL;
    }

    @Override
    public @NotNull String bucket() {
        return "local";
    }

    @Override
    public @NotNull FileUploadInstruction prepareUpload(
            @NotNull Long fileId,
            @NotNull String temporaryObjectKey,
            @NotNull String contentType,
            @NotNull Duration ttl,
            @NotNull OffsetDateTime expiresAt) {
        return new FileUploadInstruction()
                .setMode(FileUploadMode.PROXY)
                .setMethod(FileUploadMethod.PUT)
                .setUrl("/files/uploads/" + fileId + "/content")
                .setHeaders(Map.of("Content-Type", contentType))
                .setExpiresAt(expiresAt);
    }

    @Override
    public void writeTemporary(
            @NotNull String temporaryObjectKey,
            @NotNull InputStream inputStream,
            long sizeBytes,
            @NotNull String contentType) {
        Path target = path(temporaryObjectKey);
        try {
            Files.createDirectories(target.getParent());
            long written = Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            if (written != sizeBytes) {
                Files.deleteIfExists(target);
                throw new BizException(FileErrorCode.FILE_UPLOAD_INCOMPLETE);
            }
        } catch (IOException exception) {
            throw new BizException(FileErrorCode.FILE_LOCAL_WRITE, exception);
        }
    }

    @Override
    public @NotNull FileStorageMetadata stat(@NotNull String objectKey) {
        Path target = path(objectKey);
        try {
            if (!Files.isRegularFile(target)) {
                throw new BizException(FileErrorCode.FILE_UPLOAD_INCOMPLETE);
            }
            String contentType = Files.probeContentType(target);
            return new FileStorageMetadata()
                    .setSizeBytes(Files.size(target))
                    .setContentType(contentType == null ? "application/octet-stream" : contentType);
        } catch (IOException exception) {
            throw new BizException(FileErrorCode.FILE_LOCAL_INSPECT, exception);
        }
    }

    @Override
    public void promote(@NotNull String temporaryObjectKey, @NotNull String objectKey) {
        Path source = path(temporaryObjectKey);
        Path target = path(objectKey);
        try {
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) {
                Files.deleteIfExists(source);
                return;
            }
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(source, target);
            }
        } catch (IOException exception) {
            throw new BizException(FileErrorCode.FILE_LOCAL_PROMOTE, exception);
        }
    }

    @Override
    public @NotNull FileAccessUrl createAccessUrl(
            @NotNull Long fileId,
            @NotNull String objectKey,
            @NotNull String filename,
            @NotNull Duration ttl,
            @NotNull OffsetDateTime expiresAt) {
        String token = grantSigner.issueRead(fileId, expiresAt);
        return new FileAccessUrl()
                .setFileId(fileId)
                .setUrl("/files/" + fileId + "/content?token=" + token)
                .setExpiresAt(expiresAt);
    }

    @Override
    public @NotNull FileContent open(
            @NotNull String objectKey, @NotNull String filename, @NotNull String contentType, long sizeBytes) {
        try {
            return new FileContent()
                    .setFilename(filename)
                    .setContentType(contentType)
                    .setSizeBytes(sizeBytes)
                    .setInputStream(Files.newInputStream(path(objectKey)));
        } catch (IOException exception) {
            throw new BizException(FileErrorCode.FILE_LOCAL_READ, exception);
        }
    }

    @Override
    public void delete(@NotNull String temporaryObjectKey, @NotNull String objectKey) {
        try {
            Files.deleteIfExists(path(temporaryObjectKey));
            Files.deleteIfExists(path(objectKey));
        } catch (IOException exception) {
            throw new BizException(FileErrorCode.FILE_LOCAL_DELETE, exception);
        }
    }

    /**
     * 将服务端对象键解析为配置根目录内的绝对路径。
     *
     * @param objectKey 服务端生成或数据库持久化的对象键
     * @return 位于配置根目录内且已移除 {@code .} 和 {@code ..} 的绝对路径
     * @throws BizException 对象键试图逃逸配置根目录时抛出
     */
    private @NotNull Path path(@NotNull String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(FileErrorCode.FILE_FORBIDDEN);
        }
        return target;
    }
}
