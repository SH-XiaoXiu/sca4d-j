package com.sca4d.file;

import com.sca4d.file.storage.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;

/** 创建文件上传、读取授权，并修改文件占用和删除状态。 */
public interface FileService {

    /**
     * 创建属于当前 Principal 的待上传文件资源。
     *
     * @param principalId 文件所有者 Principal 标识
     * @param originalFilename 用户选择的原始文件名
     * @param declaredContentType 客户端声明的内容类型
     * @param sizeBytes 客户端声明的文件大小
     * @return 文件标识和存储系统上传指令
     */
    @NotNull
    FileUpload createUpload(
            @NotNull Long principalId,
            @NotNull String originalFilename,
            @Nullable String declaredContentType,
            long sizeBytes);

    /**
     * 将本地直传请求体写入文件的临时对象。
     *
     * @param fileId 文件标识
     * @param principalId 当前 Principal 标识
     * @param inputStream HTTP 上传内容流
     * @param sizeBytes 实际请求体大小
     * @param contentType 实际请求内容类型
     */
    void writeUpload(
            @NotNull Long fileId,
            @NotNull Long principalId,
            @NotNull InputStream inputStream,
            long sizeBytes,
            @Nullable String contentType);

    /**
     * 校验临时对象并完成上传。
     *
     * @param fileId 文件标识
     * @param principalId 当前 Principal 标识
     * @return 已进入可消费状态的文件资源
     */
    @NotNull
    FileResource completeUpload(@NotNull Long fileId, @NotNull Long principalId);

    /**
     * 读取当前 Principal 所有且等待业务消费的文件。
     *
     * @param fileId 文件标识
     * @param principalId 当前 Principal 标识
     * @return 已完成上传的文件资源
     */
    @NotNull
    FileResource requireReadyFile(@NotNull Long fileId, @NotNull Long principalId);

    /**
     * 使用上传授权把文件原子绑定到一个业务资源。
     *
     * @param fileId 文件标识
     * @param uploadGrant 上传签名阶段签发的短期授权
     * @param expectedScene 消费方声明的固定业务场景
     * @param expectedResourceId 消费方声明的业务资源标识
     * @param principalId 当前 Principal 标识
     * @return 已进入使用中状态的文件资源
     */
    @NotNull
    FileResource consumeFile(
            @NotNull Long fileId,
            @NotNull String uploadGrant,
            @NotNull String expectedScene,
            @Nullable String expectedResourceId,
            @NotNull Long principalId);

    /**
     * 为已经被业务消费的文件批量创建短期访问地址。
     *
     * @param fileIds 业务策略已经授权的文件标识
     * @return 与去重后文件标识顺序一致的访问地址
     */
    @NotNull
    List<FileAccessUrl> createAccessUrls(@NotNull Collection<Long> fileIds);

    /**
     * 将文件标记为等待后台清理任务删除。
     *
     * @param fileId 文件标识；文件不存在时保持幂等
     */
    void requestDelete(@NotNull Long fileId);

    /**
     * 使用短期读取授权打开文件内容。
     *
     * @param fileId 文件标识
     * @param token 访问地址携带的读取授权
     * @return 文件元数据和内容流
     */
    @NotNull
    FileContent openContent(@NotNull Long fileId, @NotNull String token);

    /**
     * 批量标记过期上传，并逐个抢占待删除文件、删除存储对象和记录终态。
     *
     * <p>单个文件失败不影响同批其他文件，失败记录保留为可重试状态。
     */
    void cleanup();
}
