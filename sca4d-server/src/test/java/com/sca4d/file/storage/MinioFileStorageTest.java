package com.sca4d.file.storage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.common.exception.BizException;
import com.sca4d.file.error.FileErrorCode;
import io.minio.CopyObjectArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import okhttp3.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;

/** 验证 MinIO 文件上传、读取、移动和删除请求。 */
class MinioFileStorageTest {

    @Test
    void shouldAcceptBucketCreatedByAnotherNodeAfterConcurrentMake() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenReturn(false, true);
        Mockito.doThrow(new ErrorResponseException(
                new ErrorResponse(
                        "BucketAlreadyOwnedByYou", "exists", "files", null, null, null, null),
                mock(Response.class),
                null))
                .when(client)
                .makeBucket(any());
        MinioFileStorage storage = new MinioFileStorage(client, mock(MinioClient.class), "files");

        assertThatCode(storage::verifyAvailable).doesNotThrowAnyException();

        verify(client, times(2)).bucketExists(any());
        verify(client).makeBucket(any());
        verifyNoMoreInteractions(client);
    }

    @Test
    void shouldPromoteAndDeleteTemporaryObjectWithoutSecondStat() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenReturn(true);
        when(client.statObject(any(StatObjectArgs.class)))
                .thenThrow(new ErrorResponseException(
                        new ErrorResponse("NoSuchKey", "missing", "files", "objects/1", null, null, null),
                        mock(Response.class),
                        null));
        MinioFileStorage storage = new MinioFileStorage(client, mock(MinioClient.class), "files");

        storage.promote("temporary/1", "objects/1");

        verify(client).bucketExists(any());
        verify(client).statObject(any(StatObjectArgs.class));
        verify(client).copyObject(any(CopyObjectArgs.class));
        verify(client).removeObject(any(RemoveObjectArgs.class));
        verifyNoMoreInteractions(client);
    }

    @Test
    void shouldDeleteBothKeysWithoutExistenceChecks() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenReturn(true);
        MinioFileStorage storage = new MinioFileStorage(client, mock(MinioClient.class), "files");

        storage.delete("temporary/1", "objects/1");

        verify(client).bucketExists(any());
        verify(client, times(2)).removeObject(any(RemoveObjectArgs.class));
        verifyNoMoreInteractions(client);
    }

    @Test
    void shouldReportMissingObjectAsIncompleteUpload() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenReturn(true);
        when(client.statObject(any(StatObjectArgs.class)))
                .thenThrow(new ErrorResponseException(
                        new ErrorResponse("NoSuchKey", "missing", "files", "temporary/1", null, null, null),
                        mock(Response.class),
                        null));
        MinioFileStorage storage = new MinioFileStorage(client, mock(MinioClient.class), "files");

        assertThatThrownBy(() -> storage.stat("temporary/1"))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(FileErrorCode.FILE_UPLOAD_INCOMPLETE));
    }

    @Test
    void shouldPreserveMetadataStorageFailure() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenReturn(true);
        IOException outage = new IOException("connection reset");
        when(client.statObject(any(StatObjectArgs.class))).thenThrow(outage);
        MinioFileStorage storage = new MinioFileStorage(client, mock(MinioClient.class), "files");

        assertThatThrownBy(() -> storage.stat("temporary/1"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    Assertions.assertThat(exception.getErrorCode())
                            .isEqualTo(FileErrorCode.FILE_MINIO_METADATA);
                    Assertions.assertThat(exception.getCause()).isSameAs(outage);
                });
    }

    @Test
    void shouldNotTreatStorageFailureAsSuccessfulDelete() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenReturn(true);
        Mockito.doThrow(new IOException("connection reset"))
                .when(client)
                .removeObject(any(RemoveObjectArgs.class));
        MinioFileStorage storage = new MinioFileStorage(client, mock(MinioClient.class), "files");

        assertThatThrownBy(() -> storage.delete("temporary/1", "objects/1"))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(FileErrorCode.FILE_MINIO_DELETE));
        verify(client).removeObject(any(RemoveObjectArgs.class));
    }
}
