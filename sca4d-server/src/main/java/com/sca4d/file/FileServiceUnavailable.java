package com.sca4d.file;

import com.sca4d.common.exception.BizException;
import com.sca4d.file.error.FileErrorCode;
import com.sca4d.file.storage.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;

/**
 * 文件模块关闭时的失败关闭占位实现。
 *
 * <p>该实现只用于保持依赖文件契约的用户、IM 等模块可启动；任何实际文件动作都会明确失败。
 */
public class FileServiceUnavailable implements FileService {

    @Override
    public @NotNull FileUpload createUpload(
            @NotNull Long principalId,
            @NotNull String originalFilename,
            @Nullable String declaredContentType,
            long sizeBytes) {
        throw new BizException(FileErrorCode.FILE_DISABLED);
    }

    @Override
    public void writeUpload(
            @NotNull Long fileId,
            @NotNull Long principalId,
            @NotNull InputStream inputStream,
            long sizeBytes,
            @Nullable String contentType) {
        throw new BizException(FileErrorCode.FILE_DISABLED);
    }

    @Override
    public @NotNull FileResource completeUpload(@NotNull Long fileId, @NotNull Long principalId) {
        throw new BizException(FileErrorCode.FILE_DISABLED);
    }

    @Override
    public @NotNull FileResource requireReadyFile(@NotNull Long fileId, @NotNull Long principalId) {
        throw new BizException(FileErrorCode.FILE_DISABLED);
    }

    @Override
    public @NotNull FileResource consumeFile(
            @NotNull Long fileId,
            @NotNull String uploadGrant,
            @NotNull String expectedScene,
            @Nullable String expectedResourceId,
            @NotNull Long principalId) {
        throw new BizException(FileErrorCode.FILE_DISABLED);
    }

    @Override
    public @NotNull List<FileAccessUrl> createAccessUrls(@NotNull Collection<Long> fileIds) {
        throw new BizException(FileErrorCode.FILE_DISABLED);
    }

    @Override
    public void requestDelete(@NotNull Long fileId) {
        throw new BizException(FileErrorCode.FILE_DISABLED);
    }

    @Override
    public @NotNull FileContent openContent(@NotNull Long fileId, @NotNull String token) {
        throw new BizException(FileErrorCode.FILE_DISABLED);
    }

    @Override
    public void cleanup() {
        throw new BizException(FileErrorCode.FILE_DISABLED);
    }
}
