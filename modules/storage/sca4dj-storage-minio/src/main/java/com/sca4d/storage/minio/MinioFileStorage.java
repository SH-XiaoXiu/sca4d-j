package com.sca4d.storage.minio;

import com.sca4d.core.api.storage.FileStorage;
import com.sca4d.core.api.storage.StorageObject;
import com.sca4d.core.api.storage.StorageObjectMetadata;
import com.sca4d.core.api.storage.StorageUpload;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http.Method;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SourceObject;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.MinioException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** 使用 MinIO 保存临时和正式对象，并生成浏览器直接使用的预签名地址。 */
public class MinioFileStorage implements FileStorage {
    private static final Logger LOG = LoggerFactory.getLogger(MinioFileStorage.class);

    private final @NotNull MinioClient client;

    private final @NotNull MinioClient publicClient;

    private final @NotNull MinioStorageProperties properties;

    private final @NotNull Clock clock;

    /**
     * 根据内部端点和公开端点创建两个使用相同凭据的 MinIO 客户端。
     *
     * @param properties MinIO 端点、凭据、Bucket 和实现标识配置
     */
    public MinioFileStorage(@NotNull MinioStorageProperties properties) {
        this(
                properties,
                client(properties.getEndpoint(), properties),
                client(properties.getPublicEndpoint(), properties),
                Clock.systemUTC());
    }

    /** 创建使用指定客户端的 MinIO 存储，供隔离测试验证对象操作。 */
    MinioFileStorage(
            @NotNull MinioStorageProperties properties,
            @NotNull MinioClient client,
            @NotNull MinioClient publicClient,
            @NotNull Clock clock) {
        this.properties = Objects.requireNonNull(properties, "MinIO 配置不能为空");
        this.client = Objects.requireNonNull(client, "MinIO 内部客户端不能为空");
        this.publicClient = Objects.requireNonNull(publicClient, "MinIO 公开客户端不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    @Override
    public @NotNull String backendId() {
        return properties.getBackendId();
    }

    @Override
    public @NotNull String defaultContainer() {
        return properties.getBucket();
    }

    @Override
    public void checkAvailable() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build());
            if (!exists && properties.isCreateBucket()) {
                try {
                    client.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
                } catch (Exception createFailure) {
                    exists = client.bucketExists(
                            BucketExistsArgs.builder().bucket(properties.getBucket()).build());
                    if (!exists)
                        throw createFailure;
                }
                exists = true;
            }
            if (!exists)
                throw new IllegalStateException("MinIO Bucket 不存在: " + properties.getBucket());
        } catch (Exception exception) {
            throw new IllegalStateException("MinIO Bucket 不可用", exception);
        }
    }

    @Override
    public @NotNull StorageUpload prepareUpload(
            long fileId,
            @NotNull StorageObject object,
            @NotNull String contentType,
            long sizeBytes,
            @NotNull Instant expiresAt) {
        try {
            int seconds = expirySeconds(expiresAt);
            String url = publicClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(object.container())
                            .object(object.objectKey())
                            .expiry(seconds, TimeUnit.SECONDS)
                            .extraHeaders(Map.of("Content-Type", contentType))
                            .build());
            return new StorageUpload(URI.create(url), Map.of("Content-Type", contentType));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成 MinIO 上传地址", exception);
        }
    }

    @Override
    public @NotNull Optional<StorageObjectMetadata> inspect(@NotNull StorageObject object) {
        try {
            StatObjectResponse response = client.statObject(
                    StatObjectArgs.builder()
                            .bucket(object.container())
                            .object(object.objectKey())
                            .build());
            String contentType = response.contentType() == null ? "application/octet-stream" : response.contentType();
            return Optional.of(new StorageObjectMetadata(contentType, response.size()));
        } catch (ErrorResponseException exception) {
            String code = exception.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
                return Optional.empty();
            }
            throw new IllegalStateException("无法读取 MinIO 对象元数据", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取 MinIO 对象元数据", exception);
        }
    }

    @Override
    public void promote(@NotNull StorageObject source, @NotNull StorageObject target) {
        try {
            client.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(target.container())
                            .object(target.objectKey())
                            .source(
                                    SourceObject.builder()
                                            .bucket(source.container())
                                            .object(source.objectKey())
                                            .build())
                            .build());
        } catch (Exception copyFailure) {
            boolean sourceExists;
            boolean targetExists;
            try {
                sourceExists = inspect(source).isPresent();
                targetExists = inspect(target).isPresent();
            } catch (RuntimeException recoveryFailure) {
                recoveryFailure.addSuppressed(copyFailure);
                throw recoveryFailure;
            }
            if (!sourceExists && targetExists)
                return;
            throw new IllegalStateException("无法转正 MinIO 对象", copyFailure);
        }
        try {
            client.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(source.container())
                            .object(source.objectKey())
                            .build());
        } catch (Exception deleteFailure) {
            LOG.warn(
                    "MinIO 临时对象首次删除失败，将由到期清理重试 | backendId={} container={} errorType={}",
                    backendId(),
                    source.container(),
                    deleteFailure.getClass().getSimpleName());
        }
    }

    @Override
    public @NotNull URI prepareAccess(
            long fileId,
            @NotNull StorageObject object,
            @NotNull Instant expiresAt) {
        try {
            String url = publicClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(object.container())
                            .object(object.objectKey())
                            .expiry(expirySeconds(expiresAt), TimeUnit.SECONDS)
                            .build());
            return URI.create(url);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成 MinIO 读取地址", exception);
        }
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
        CountingInputStream checkedContent = new CountingInputStream(
                Objects.requireNonNull(content, "文件内容不能为空"));
        try {
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(object.container())
                            .object(object.objectKey())
                            .stream(checkedContent, sizeBytes, -1L)
                            .contentType(contentType)
                            .build());
            if (checkedContent.bytesRead > sizeBytes
                    || checkedContent.bytesRead == sizeBytes && checkedContent.read() != -1) {
                throw new IllegalArgumentException("上传内容字节数与申请不一致");
            }
        } catch (InsufficientDataException exception) {
            deleteFailedWrite(object, exception);
            throw new IllegalArgumentException("上传内容字节数与申请不一致", exception);
        } catch (IllegalArgumentException | IOException exception) {
            deleteFailedWrite(object, exception);
            throw exception;
        } catch (MinioException exception) {
            deleteFailedWrite(object, exception);
            if ("unexpected EOF".equals(exception.getMessage())) {
                throw new IllegalArgumentException("上传内容字节数与申请不一致", exception);
            }
            throw new IOException("无法写入 MinIO 对象", exception);
        } catch (Exception exception) {
            deleteFailedWrite(object, exception);
            throw new IOException("无法写入 MinIO 对象", exception);
        }
    }

    /** 写入失败或长度不匹配时删除可能已经创建的目标对象。 */
    private void deleteFailedWrite(@NotNull StorageObject object, @NotNull Exception failure) {
        try {
            delete(object);
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    @Override
    public @NotNull InputStream open(@NotNull StorageObject object) throws IOException {
        try {
            return client.getObject(
                    GetObjectArgs.builder()
                            .bucket(object.container())
                            .object(object.objectKey())
                            .build());
        } catch (Exception exception) {
            throw new IOException("无法打开 MinIO 对象", exception);
        }
    }

    @Override
    public void delete(@NotNull StorageObject object) {
        try {
            client.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(object.container())
                            .object(object.objectKey())
                            .build());
        } catch (Exception exception) {
            throw new IllegalStateException("无法删除 MinIO 对象", exception);
        }
    }

    /** 把签名到期时间转换成 MinIO 接受的正整数秒。 */
    private int expirySeconds(@NotNull Instant expiresAt) {
        long seconds = Duration.between(clock.instant(), expiresAt).toSeconds();
        if (seconds <= 0 || seconds > 604800)
            throw new IllegalArgumentException("MinIO 签名到期时间超出范围");
        return Math.toIntExact(seconds);
    }

    /** 创建使用配置凭据的 MinIO 客户端。 */
    private static @NotNull MinioClient client(
            @NotNull String endpoint,
            @NotNull MinioStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .region(properties.getRegion())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    /** 统计 MinIO SDK 实际从调用方内容流读取的字节数。 */
    private static class CountingInputStream extends InputStream {
        private final @NotNull InputStream delegate;

        private long bytesRead;

        private CountingInputStream(@NotNull InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                bytesRead++;
            }
            return value;
        }

        @Override
        public int read(byte @NotNull [] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                bytesRead += count;
            }
            return count;
        }

    }
}
