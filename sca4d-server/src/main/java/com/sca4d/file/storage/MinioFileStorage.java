package com.sca4d.file.storage;

import com.sca4d.common.exception.BizException;
import com.sca4d.config.properties.Sca4djMinioProperties;
import com.sca4d.file.FileAccessUrl;
import com.sca4d.file.FileUploadMethod;
import com.sca4d.file.FileUploadMode;
import com.sca4d.file.error.FileErrorCode;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** MinIO 文件存储。 */
public class MinioFileStorage implements FileStorage {

    private final MinioClient client;
    private final MinioClient publicClient;
    private final String bucket;
    private volatile boolean bucketReady;

    /**
     * 根据 MinIO 连接配置创建内部访问客户端和面向浏览器的签名客户端。
     *
     * @param properties MinIO 内部端点、公开端点、凭据和 Bucket 配置
     */
    public MinioFileStorage(@NotNull Sca4djMinioProperties properties) {
        this.client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
        this.publicClient = MinioClient.builder()
                .endpoint(properties.getPublicEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
        this.bucket = properties.getBucket();
    }

    MinioFileStorage(@NotNull MinioClient client, @NotNull MinioClient publicClient, @NotNull String bucket) {
        this.client = client;
        this.publicClient = publicClient;
        this.bucket = bucket;
    }

    @Override
    public @NotNull FileStorageProvider provider() {
        return FileStorageProvider.MINIO;
    }

    @Override
    public @NotNull String bucket() {
        return bucket;
    }

    /**
     * 验证 MinIO 服务及目标 Bucket 可用。
     *
     * <p>与首次文件操作保持一致：不存在的 Bucket 会在校验期间创建。
     */
    public void verifyAvailable() {
        ensureBucket();
    }

    @Override
    public @NotNull FileUploadInstruction prepareUpload(
            @NotNull Long fileId,
            @NotNull String temporaryObjectKey,
            @NotNull String contentType,
            @NotNull Duration ttl,
            @NotNull OffsetDateTime expiresAt) {
        ensureBucket();
        try {
            String url = publicClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(temporaryObjectKey)
                    .expiry(Math.toIntExact(ttl.toSeconds()), TimeUnit.SECONDS)
                    .build());
            return new FileUploadInstruction()
                    .setMode(FileUploadMode.DIRECT)
                    .setMethod(FileUploadMethod.PUT)
                    .setUrl(url)
                    .setHeaders(Map.of("Content-Type", contentType))
                    .setExpiresAt(expiresAt);
        } catch (Exception exception) {
            throw storageError(FileErrorCode.FILE_MINIO_UPLOAD_SIGN, exception);
        }
    }

    @Override
    public void writeTemporary(
            @NotNull String temporaryObjectKey,
            @NotNull InputStream inputStream,
            long sizeBytes,
            @NotNull String contentType) {
        ensureBucket();
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(temporaryObjectKey)
                    .stream(inputStream, sizeBytes, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception exception) {
            throw storageError(FileErrorCode.FILE_MINIO_WRITE, exception);
        }
    }

    @Override
    public @NotNull FileStorageMetadata stat(@NotNull String objectKey) {
        ensureBucket();
        try {
            StatObjectResponse response = client.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            String contentType = response.contentType();
            return new FileStorageMetadata()
                    .setSizeBytes(response.size())
                    .setContentType(contentType == null ? "application/octet-stream" : contentType);
        } catch (ErrorResponseException exception) {
            if (isMissingObject(exception)) {
                throw new BizException(FileErrorCode.FILE_UPLOAD_INCOMPLETE);
            }
            throw storageError(FileErrorCode.FILE_MINIO_METADATA, exception);
        } catch (Exception exception) {
            throw storageError(FileErrorCode.FILE_MINIO_METADATA, exception);
        }
    }

    @Override
    public void promote(@NotNull String temporaryObjectKey, @NotNull String objectKey) {
        ensureBucket();
        try {
            if (!exists(objectKey)) {
                client.copyObject(CopyObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .source(CopySource.builder().bucket(bucket).object(temporaryObjectKey).build())
                        .build());
            }
            // S3 DeleteObject 对不存在对象幂等；直接清理避免额外 Stat 往返和检查后删除竞态。
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(temporaryObjectKey).build());
        } catch (Exception exception) {
            throw storageError(FileErrorCode.FILE_MINIO_PROMOTE, exception);
        }
    }

    @Override
    public @NotNull FileAccessUrl createAccessUrl(
            @NotNull Long fileId,
            @NotNull String objectKey,
            @NotNull String filename,
            @NotNull Duration ttl,
            @NotNull OffsetDateTime expiresAt) {
        ensureBucket();
        try {
            String url = publicClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(Math.toIntExact(ttl.toSeconds()), TimeUnit.SECONDS)
                    .build());
            return new FileAccessUrl().setFileId(fileId).setUrl(url).setExpiresAt(expiresAt);
        } catch (Exception exception) {
            throw storageError(FileErrorCode.FILE_MINIO_READ_SIGN, exception);
        }
    }

    @Override
    public @NotNull FileContent open(
            @NotNull String objectKey, @NotNull String filename, @NotNull String contentType, long sizeBytes) {
        ensureBucket();
        try {
            return new FileContent()
                    .setFilename(filename)
                    .setContentType(contentType)
                    .setSizeBytes(sizeBytes)
                    .setInputStream(client.getObject(
                            GetObjectArgs.builder().bucket(bucket).object(objectKey).build()));
        } catch (Exception exception) {
            throw storageError(FileErrorCode.FILE_MINIO_READ, exception);
        }
    }

    @Override
    public void delete(@NotNull String temporaryObjectKey, @NotNull String objectKey) {
        ensureBucket();
        try {
            // S3 DeleteObject 对不存在的对象保持幂等，直接删除可以避免每个对象额外一次 StatObject 往返和检查后删除竞态。
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(temporaryObjectKey).build());
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw storageError(FileErrorCode.FILE_MINIO_DELETE, exception);
        }
    }

    /**
     * 在首次对象操作前确认 Bucket 可用，并在当前实例内缓存成功结果。
     *
     * <p>多个节点同时创建同名 Bucket 时由 MinIO 保证最终结果；失败不会设置缓存标记，后续调用仍可重试。
     */
    private synchronized void ensureBucket() {
        if (bucketReady) {
            return;
        }
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                try {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                } catch (ErrorResponseException exception) {
                    if (!isExistingBucket(exception)
                            || !client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                        throw exception;
                    }
                }
            }
            bucketReady = true;
        } catch (Exception exception) {
            throw storageError(FileErrorCode.FILE_MINIO_BUCKET_INIT, exception);
        }
    }

    /**
     * 区分对象缺失和存储故障地判断对象是否存在。
     *
     * @param objectKey 待检查的对象键
     * @return MinIO 明确返回对象缺失时为 false，元数据读取成功时为 true
     * @throws Exception 网络、鉴权和服务端故障原样交给上层操作转换
     */
    private boolean exists(@NotNull String objectKey) throws Exception {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return true;
        } catch (ErrorResponseException exception) {
            if (isMissingObject(exception)) {
                return false;
            }
            throw exception;
        }
    }

    /**
     * 判断 MinIO 错误是否明确表达对象不存在。
     *
     * @param exception MinIO 返回的结构化 S3 错误
     * @return 错误码为 NoSuchKey 或 NoSuchObject 时返回 true
     */
    private boolean isMissingObject(@NotNull ErrorResponseException exception) {
        String code = exception.errorResponse().code();
        return "NoSuchKey".equals(code) || "NoSuchObject".equals(code);
    }

    /**
     * 判断建桶失败是否由另一节点已完成同名 Bucket 创建引起。
     *
     * <p>只接受 S3 明确的已存在错误码，并由调用方再次读取 Bucket 存在数据；鉴权、网络和其他服务端故障不得被吞掉。
     *
     * @param exception MinIO 建桶请求返回的结构化错误
     * @return 错误码明确表示 Bucket 已存在时返回 {@code true}
     */
    private boolean isExistingBucket(@NotNull ErrorResponseException exception) {
        String code = exception.errorResponse().code();
        return "BucketAlreadyOwnedByYou".equals(code) || "BucketAlreadyExists".equals(code);
    }

    /**
     * 将 MinIO SDK 异常收敛为文件模块共用存储错误。
     *
     * @param errorCode 当前失败操作的文件错误码
     * @param exception MinIO SDK 或网络层原始异常
     * @return 保留原始异常链的业务异常
     */
    private @NotNull BizException storageError(@NotNull FileErrorCode errorCode, @NotNull Exception exception) {
        return new BizException(errorCode, exception);
    }
}
