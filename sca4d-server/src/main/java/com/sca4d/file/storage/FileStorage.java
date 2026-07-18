package com.sca4d.file.storage;

import com.sca4d.file.FileAccessUrl;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;

/** 保存、读取、移动和删除文件字节。 */
public interface FileStorage {

    /**
     * 返回当前实现对应的固定存储提供方。
     *
     * @return 用于配置选择和持久化路由的提供方
     */
    @NotNull
    FileStorageProvider provider();

    /**
     * 返回当前实现使用的存储桶。
     *
     * @return 与对象键共同定位存储对象的 Bucket 名称
     */
    @NotNull
    String bucket();

    /**
     * 创建客户端上传到临时对象的限时指令。
     *
     * @param fileId 文件资源标识
     * @param temporaryObjectKey 服务端生成的临时对象键
     * @param contentType 上传内容类型
     * @param ttl 上传指令有效期
     * @param expiresAt 上传指令的绝对过期时间
     * @return 客户端可直接执行的上传方式、地址和必要请求头
     */
    @NotNull
    FileUploadInstruction prepareUpload(
            @NotNull Long fileId,
            @NotNull String temporaryObjectKey,
            @NotNull String contentType,
            @NotNull Duration ttl,
            @NotNull OffsetDateTime expiresAt);

    /**
     * 将请求内容写入临时对象，调用结束前消费输入流。
     *
     * @param temporaryObjectKey 服务端生成的临时对象键
     * @param inputStream 本次上传的内容流，由调用方负责关闭
     * @param sizeBytes 实际内容字节数
     * @param contentType 实际内容类型
     */
    void writeTemporary(
            @NotNull String temporaryObjectKey,
            @NotNull InputStream inputStream,
            long sizeBytes,
            @NotNull String contentType);

    /**
     * 读取存储系统确认的对象元数据。
     *
     * @param objectKey 待读取的对象键
     * @return 对象实际大小和内容类型
     * @throws com.sca4d.common.exception.BizException 对象不存在或存储服务不可用时按准确错误码抛出
     */
    @NotNull
    FileStorageMetadata stat(@NotNull String objectKey);

    /**
     * 幂等地将临时对象转为正式对象，并清除残留临时对象。
     *
     * @param temporaryObjectKey 临时对象键
     * @param objectKey 正式对象键
     */
    void promote(@NotNull String temporaryObjectKey, @NotNull String objectKey);

    /**
     * 为正式对象创建限时读取地址。
     *
     * @param fileId 文件资源标识
     * @param objectKey 正式对象键
     * @param filename 下载时展示的文件名
     * @param ttl 读取地址有效期
     * @param expiresAt 读取地址绝对过期时间
     * @return 文件标识、访问地址和过期时间
     */
    @NotNull
    FileAccessUrl createAccessUrl(
            @NotNull Long fileId,
            @NotNull String objectKey,
            @NotNull String filename,
            @NotNull Duration ttl,
            @NotNull OffsetDateTime expiresAt);

    /**
     * 打开正式对象的内容流。
     *
     * @param objectKey 正式对象键
     * @param filename 下载时展示的文件名
     * @param contentType 已固化的内容类型
     * @param sizeBytes 已固化的内容字节数
     * @return 包含调用方负责关闭的内容流及响应元数据
     */
    @NotNull
    FileContent open(@NotNull String objectKey, @NotNull String filename, @NotNull String contentType, long sizeBytes);

    /**
     * 幂等删除临时对象和正式对象；任一存储故障均作为整次删除失败上抛。
     *
     * @param temporaryObjectKey 临时对象键
     * @param objectKey 正式对象键
     */
    void delete(@NotNull String temporaryObjectKey, @NotNull String objectKey);
}
