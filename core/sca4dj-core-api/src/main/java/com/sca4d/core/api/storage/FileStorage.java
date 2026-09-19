package com.sca4d.core.api.storage;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;

/**
 * 对指定容器和对象键执行上传、转正、读取和删除操作。
 */
public interface FileStorage {
    /**
     * 返回实现的稳定标识。
     *
     * @return 文件记录保存的存储实现标识
     */
    @NotNull
    String backendId();

    /**
     * 返回新文件使用的默认容器。
     *
     * @return 新文件默认写入的 Bucket 或本地命名空间
     */
    @NotNull
    String defaultContainer();

    /**
     * 验证连接、凭据和默认容器可用。
     */
    void checkAvailable();

    /**
     * 创建客户端上传临时对象所需的请求。
     *
     * @param fileId 正数文件标识；本地传输签名需要把它写入 Token
     * @param object 临时对象位置
     * @param contentType 申请时声明的媒体类型
     * @param sizeBytes 申请时声明的精确正数字节数
     * @param expiresAt 上传地址失效时间
     * @return 客户端必须原样执行的 PUT 地址和请求头
     */
    @NotNull
    StorageUpload prepareUpload(
            long fileId,
            @NotNull StorageObject object,
            @NotNull String contentType,
            long sizeBytes,
            @NotNull Instant expiresAt);

    /**
     * 读取存储中的实际对象元数据。
     *
     * @param object 需要检查的对象位置
     * @return 对象存在时返回实际元数据，对象不存在时返回空
     */
    @NotNull
    Optional<StorageObjectMetadata> inspect(@NotNull StorageObject object);

    /**
     * 把临时对象复制或移动到不可被上传签名覆盖的正式键。
     *
     * <p>重复调用必须保持幂等；临时对象已经不存在但正式对象存在时按成功处理。
     *
     * @param source 临时对象位置
     * @param target 正式对象位置
     */
    void promote(@NotNull StorageObject source, @NotNull StorageObject target);

    /**
     * 创建短期读取地址。
     *
     * @param fileId 正数文件标识；本地传输签名需要把它写入 Token
     * @param object 正式对象位置
     * @param expiresAt 读取地址失效时间
     * @return 客户端可以访问的绝对地址
     */
    @NotNull
    URI prepareAccess(long fileId, @NotNull StorageObject object, @NotNull Instant expiresAt);

    /**
     * 把服务端持有的内容流写入指定临时对象。
     *
     * <p>实际流长度不等于 {@code sizeBytes} 时抛出 {@link IllegalArgumentException}，不得留下可读取的目标对象。
     *
     * @param object 临时对象位置
     * @param contentType 申请时声明的媒体类型
     * @param sizeBytes 必须写入的精确正数字节数
     * @param content 文件内容流；实现不关闭调用方提供的流
     * @throws IOException 对象无法写入或临时文件无法清理时抛出
     */
    void write(@NotNull StorageObject object, @NotNull String contentType, long sizeBytes, @NotNull InputStream content)
            throws IOException;

    /**
     * 打开 App 签名路由需要返回的对象内容。
     *
     * @param object 正式对象位置
     * @return 由调用方关闭的对象输入流
     * @throws IOException 对象不存在或无法读取时抛出
     */
    @NotNull
    InputStream open(@NotNull StorageObject object) throws IOException;

    /**
     * 幂等删除对象。
     *
     * @param object 需要删除的临时或正式对象位置
     */
    void delete(@NotNull StorageObject object);
}
