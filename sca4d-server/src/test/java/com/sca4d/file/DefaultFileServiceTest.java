package com.sca4d.file;

import static java.time.Duration.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.config.properties.Sca4djFileProperties;
import com.sca4d.entity.FileObjectEntity;
import com.sca4d.entity.FileResourceEntity;
import com.sca4d.file.error.FileErrorCode;
import com.sca4d.file.repository.FileRepository;
import com.sca4d.file.service.DefaultFileService;
import com.sca4d.file.signature.FileGrantSigner;
import com.sca4d.file.storage.FileStorage;
import com.sca4d.file.storage.FileStorageMetadata;
import com.sca4d.file.storage.FileStorageProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 验证文件上传、完成、占用、授权读取和删除状态变化。 */
class DefaultFileServiceTest {

    @Test
    void shouldPreserveSignedContentTypeWhenLocalTemporaryPathCannotProbeMimeType() {
        FileRepository repository = mock(FileRepository.class);
        FileStorage storage = mock(FileStorage.class);
        when(storage.provider()).thenReturn(FileStorageProvider.LOCAL);
        FileResource file = new FileResource()
                .setFileId(10L)
                .setOwnerPrincipalId(1L)
                .setContentType("image/webp")
                .setSizeBytes(46L)
                .setStatus(FileStatus.PENDING)
                .setProvider(FileStorageProvider.LOCAL)
                .setTemporaryObjectKey("temporary/10")
                .setObjectKey("objects/10")
                .setUploadExpiresAt(OffsetDateTime.now().plusMinutes(5));
        when(repository.find(10L)).thenReturn(Optional.of(file));
        when(storage.stat("temporary/10"))
                .thenReturn(new FileStorageMetadata().setContentType("application/octet-stream").setSizeBytes(46L));
        when(repository.completeUpload(
                ArgumentMatchers.eq(10L),
                ArgumentMatchers.eq(1L),
                ArgumentMatchers.eq("image/webp"),
                ArgumentMatchers.eq(46L),
                ArgumentMatchers.any()))
                .thenReturn(true);
        DefaultFileService service = new DefaultFileService(
                repository,
                mock(IdGenerator.class),
                new Sca4djFileProperties(),
                mock(FileGrantSigner.class),
                List.of(storage));

        service.completeUpload(10L, 1L);

        verify(storage).promote("temporary/10", "objects/10");
        verify(repository).completeUpload(
                ArgumentMatchers.eq(10L),
                ArgumentMatchers.eq(1L),
                ArgumentMatchers.eq("image/webp"),
                ArgumentMatchers.eq(46L),
                ArgumentMatchers.any());
    }

    @Test
    void shouldCompleteFromFinalObjectAfterStoragePromotionWasAlreadyCommitted() {
        FileRepository repository = mock(FileRepository.class);
        FileStorage storage = mock(FileStorage.class);
        when(storage.provider()).thenReturn(FileStorageProvider.MINIO);
        FileResource file = new FileResource()
                .setFileId(10L)
                .setOwnerPrincipalId(1L)
                .setContentType("image/webp")
                .setSizeBytes(46L)
                .setStatus(FileStatus.PENDING)
                .setProvider(FileStorageProvider.MINIO)
                .setTemporaryObjectKey("temporary/10")
                .setObjectKey("objects/10")
                .setUploadExpiresAt(OffsetDateTime.now().plusMinutes(5));
        when(repository.find(10L)).thenReturn(Optional.of(file));
        when(storage.stat("temporary/10"))
                .thenThrow(new BizException(FileErrorCode.FILE_UPLOAD_INCOMPLETE));
        when(storage.stat("objects/10"))
                .thenReturn(new FileStorageMetadata().setContentType("image/webp").setSizeBytes(46L));
        when(repository.completeUpload(
                ArgumentMatchers.eq(10L),
                ArgumentMatchers.eq(1L),
                ArgumentMatchers.eq("image/webp"),
                ArgumentMatchers.eq(46L),
                ArgumentMatchers.any()))
                .thenReturn(true);
        DefaultFileService service = new DefaultFileService(
                repository,
                mock(IdGenerator.class),
                new Sca4djFileProperties(),
                mock(FileGrantSigner.class),
                List.of(storage));

        service.completeUpload(10L, 1L);

        verify(storage).promote("temporary/10", "objects/10");
        verify(repository).completeUpload(
                ArgumentMatchers.eq(10L),
                ArgumentMatchers.eq(1L),
                ArgumentMatchers.eq("image/webp"),
                ArgumentMatchers.eq(46L),
                ArgumentMatchers.any());
    }

    @Test
    void shouldUseUuidForFinalStorageObjectKey() {
        FileRepository repository = mock(FileRepository.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(10L);
        FileStorage storage = mock(FileStorage.class);
        when(storage.provider()).thenReturn(FileStorageProvider.MINIO);
        DefaultFileService service = new DefaultFileService(
                repository,
                idGenerator,
                new Sca4djFileProperties(),
                mock(FileGrantSigner.class),
                List.of(storage));

        service.createUpload(1L, "avatar.png", "image/png", 10L);

        ArgumentCaptor<FileResourceEntity> resourceCaptor = ArgumentCaptor.forClass(FileResourceEntity.class);
        ArgumentCaptor<FileObjectEntity> objectCaptor = ArgumentCaptor.forClass(FileObjectEntity.class);
        verify(repository).createUpload(resourceCaptor.capture(), objectCaptor.capture());
        assertThat(resourceCaptor.getValue().getId()).isEqualTo(10L);
        assertThat(objectCaptor.getValue().getTemporaryObjectKey()).endsWith("/10");
        assertThat(objectCaptor.getValue().getObjectKey())
                .matches("objects/\\d{4}/\\d{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void shouldRejectInvalidPlatformPathAsBusinessInput() {
        FileRepository repository = mock(FileRepository.class);
        FileStorage storage = mock(FileStorage.class);
        when(storage.provider()).thenReturn(FileStorageProvider.MINIO);
        DefaultFileService service = new DefaultFileService(
                repository,
                mock(IdGenerator.class),
                new Sca4djFileProperties(),
                mock(FileGrantSigner.class),
                List.of(storage));

        assertThatThrownBy(() -> service.createUpload(1L, "bad\0name.png", "image/png", 10))
                .isInstanceOfSatisfying(BizException.class, exception -> assertThat(exception.getMessageKey())
                        .isEqualTo("error.file.filename-invalid"));
        verify(repository, never()).createUpload(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void shouldRemoveWindowsDirectoryFromDisplayFilename() {
        FileRepository repository = mock(FileRepository.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(10L);
        FileStorage storage = mock(FileStorage.class);
        when(storage.provider()).thenReturn(FileStorageProvider.MINIO);
        DefaultFileService service = new DefaultFileService(
                repository,
                idGenerator,
                new Sca4djFileProperties(),
                mock(FileGrantSigner.class),
                List.of(storage));

        service.createUpload(1L, "C:\\fakepath\\avatar.png", "image/png", 10L);

        ArgumentCaptor<FileResourceEntity> resourceCaptor = ArgumentCaptor.forClass(FileResourceEntity.class);
        verify(repository).createUpload(resourceCaptor.capture(), ArgumentMatchers.any());
        assertThat(resourceCaptor.getValue().getOriginalFilename()).isEqualTo("avatar.png");
    }

    @Test
    void shouldRejectDuplicateStorageProviderImplementations() {
        FileStorage first = mock(FileStorage.class);
        FileStorage second = mock(FileStorage.class);
        when(first.provider()).thenReturn(FileStorageProvider.MINIO);
        when(second.provider()).thenReturn(FileStorageProvider.MINIO);

        assertThatThrownBy(() -> new DefaultFileService(
                mock(FileRepository.class),
                mock(IdGenerator.class),
                new Sca4djFileProperties(),
                mock(FileGrantSigner.class),
                List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复文件存储实现");
    }

    @Test
    void shouldRejectNonPositiveConfiguredCleanupBatchSize() {
        Sca4djFileProperties properties = new Sca4djFileProperties();

        assertThatThrownBy(() -> properties.setCleanupBatchSize(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文件清理批次大小必须大于 0");
        assertThatThrownBy(() -> properties.setCleanupLease(ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文件清理租约必须大于 0");
    }

    @Test
    void shouldUseConfiguredBatchAndKeepPerFileDeletionState() {
        FileRepository repository = mock(FileRepository.class);
        FileStorage storage = mock(FileStorage.class);
        when(storage.provider()).thenReturn(FileStorageProvider.MINIO);
        Sca4djFileProperties properties = new Sca4djFileProperties();
        properties.setCleanupBatchSize(25);
        FileResource file = new FileResource()
                .setFileId(10L)
                .setProvider(FileStorageProvider.MINIO)
                .setTemporaryObjectKey("temporary/10")
                .setObjectKey("objects/10");
        when(repository.claimDeletionBatch(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(25)))
                .thenReturn(List.of(file));
        when(repository.finishDeletion(
                ArgumentMatchers.eq(10L),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(FileStatus.DELETED),
                ArgumentMatchers.any()))
                .thenReturn(true);
        DefaultFileService service = new DefaultFileService(
                repository,
                mock(IdGenerator.class),
                properties,
                mock(FileGrantSigner.class),
                List.of(storage));

        service.cleanup();

        verify(repository).expireUploads(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.eq(25));
        verify(storage).delete("temporary/10", "objects/10");
        verify(repository).finishDeletion(
                ArgumentMatchers.eq(10L),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(FileStatus.DELETED),
                ArgumentMatchers.any());
    }

    @Test
    void shouldMarkDeleteFailureForRetryAndCompleteItOnTheNextCleanup() {
        FileRepository repository = mock(FileRepository.class);
        FileStorage storage = mock(FileStorage.class);
        when(storage.provider()).thenReturn(FileStorageProvider.MINIO);
        FileResource file = new FileResource()
                .setFileId(10L)
                .setProvider(FileStorageProvider.MINIO)
                .setTemporaryObjectKey("temporary/10")
                .setObjectKey("objects/10");
        when(repository.claimDeletionBatch(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(100)))
                .thenReturn(List.of(file));
        when(repository.finishDeletion(
                ArgumentMatchers.eq(10L),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()))
                .thenReturn(true);
        Mockito.doThrow(new IllegalStateException("storage unavailable"))
                .doNothing()
                .when(storage)
                .delete("temporary/10", "objects/10");
        DefaultFileService service = new DefaultFileService(
                repository,
                mock(IdGenerator.class),
                new Sca4djFileProperties(),
                mock(FileGrantSigner.class),
                List.of(storage));

        service.cleanup();
        service.cleanup();

        InOrder transitions = inOrder(repository);
        transitions.verify(repository).claimDeletionBatch(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(100));
        transitions.verify(repository).finishDeletion(
                ArgumentMatchers.eq(10L),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(FileStatus.DELETE_FAILED),
                ArgumentMatchers.any());
        transitions.verify(repository).claimDeletionBatch(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(100));
        transitions.verify(repository).finishDeletion(
                ArgumentMatchers.eq(10L),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(FileStatus.DELETED),
                ArgumentMatchers.any());
    }

    @Test
    void shouldAllowOnlyOneNodeToDeleteTheSameCandidate() {
        FileRepository repository = mock(FileRepository.class);
        FileStorage storage = mock(FileStorage.class);
        when(storage.provider()).thenReturn(FileStorageProvider.MINIO);
        FileResource file = new FileResource()
                .setFileId(10L)
                .setProvider(FileStorageProvider.MINIO)
                .setTemporaryObjectKey("temporary/10")
                .setObjectKey("objects/10");
        when(repository.claimDeletionBatch(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(100)))
                .thenReturn(List.of(file), List.of());
        when(repository.finishDeletion(
                ArgumentMatchers.eq(10L),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(FileStatus.DELETED),
                ArgumentMatchers.any()))
                .thenReturn(true);
        Sca4djFileProperties properties = new Sca4djFileProperties();
        DefaultFileService firstNode = new DefaultFileService(
                repository, mock(IdGenerator.class), properties, mock(FileGrantSigner.class), List.of(storage));
        DefaultFileService secondNode = new DefaultFileService(
                repository, mock(IdGenerator.class), properties, mock(FileGrantSigner.class), List.of(storage));

        firstNode.cleanup();
        secondNode.cleanup();

        verify(storage).delete("temporary/10", "objects/10");
    }

    @Test
    void shouldReclaimDeletionAfterTheOwningNodeStops() {
        FileRepository repository = mock(FileRepository.class);
        FileStorage storage = mock(FileStorage.class);
        when(storage.provider()).thenReturn(FileStorageProvider.MINIO);
        FileResource file = new FileResource()
                .setFileId(10L)
                .setProvider(FileStorageProvider.MINIO)
                .setTemporaryObjectKey("temporary/10")
                .setObjectKey("objects/10");
        when(repository.claimDeletionBatch(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(100)))
                .thenReturn(List.of(file));
        when(repository.finishDeletion(
                ArgumentMatchers.eq(10L),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(FileStatus.DELETED),
                ArgumentMatchers.any()))
                .thenReturn(true);
        Mockito.doThrow(new AssertionError("node stopped"))
                .doNothing()
                .when(storage)
                .delete("temporary/10", "objects/10");
        DefaultFileService service = new DefaultFileService(
                repository,
                mock(IdGenerator.class),
                new Sca4djFileProperties(),
                mock(FileGrantSigner.class),
                List.of(storage));

        assertThatThrownBy(service::cleanup).isInstanceOf(AssertionError.class).hasMessage("node stopped");
        service.cleanup();

        verify(storage, Mockito.times(2)).delete("temporary/10", "objects/10");
        verify(repository).finishDeletion(
                ArgumentMatchers.eq(10L),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(FileStatus.DELETED),
                ArgumentMatchers.any());
    }

    @Test
    void shouldRetryIdempotentObjectDeletionWhenTheTerminalWriteWasLost() {
        FileRepository repository = mock(FileRepository.class);
        FileStorage storage = mock(FileStorage.class);
        when(storage.provider()).thenReturn(FileStorageProvider.MINIO);
        FileResource file = new FileResource()
                .setFileId(10L)
                .setProvider(FileStorageProvider.MINIO)
                .setTemporaryObjectKey("temporary/10")
                .setObjectKey("objects/10");
        when(repository.claimDeletionBatch(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(100)))
                .thenReturn(List.of(file));
        when(repository.finishDeletion(
                ArgumentMatchers.eq(10L),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(FileStatus.DELETED),
                ArgumentMatchers.any()))
                .thenReturn(false, true);
        DefaultFileService service = new DefaultFileService(
                repository,
                mock(IdGenerator.class),
                new Sca4djFileProperties(),
                mock(FileGrantSigner.class),
                List.of(storage));

        service.cleanup();
        service.cleanup();

        verify(storage, Mockito.times(2)).delete("temporary/10", "objects/10");
    }
}
