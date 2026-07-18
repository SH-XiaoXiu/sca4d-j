package com.sca4d.file.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.FileObjectEntity;
import com.sca4d.entity.FileResourceEntity;
import com.sca4d.file.FileResource;
import com.sca4d.file.FileStatus;
import com.sca4d.mapper.FileMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/** 验证文件元数据写入、状态条件更新和清理任务领取。 */
class FileRepositoryTest {

    @Test
    void shouldAllocateStorageObjectIdInsideRepository() {
        FileMapper mapper = mock(FileMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(2L);
        FileRepository repository = new FileRepository(mapper, idGenerator);
        FileResourceEntity resource = new FileResourceEntity().setId(1L);
        FileObjectEntity object = new FileObjectEntity().setFileId(1L);

        repository.createUpload(resource, object);

        assertThat(object.getId()).isEqualTo(2L);
        verify(mapper).insertResource(resource);
        verify(mapper).insertObject(object);
    }

    @Test
    void shouldConvertCompletedUploadAffectedRowsToPersistenceResult() {
        FileMapper mapper = mock(FileMapper.class);
        FileRepository repository = new FileRepository(mapper, mock(IdGenerator.class));
        OffsetDateTime at = OffsetDateTime.now();
        when(mapper.updateUploadCompleted(
                1L, 2L, FileStatus.PENDING, FileStatus.READY, "image/png", 10L, at))
                .thenReturn(1);
        when(mapper.updateUploadCompleted(
                3L, 2L, FileStatus.PENDING, FileStatus.READY, "image/png", 10L, at))
                .thenReturn(0);

        assertThat(repository.completeUpload(1L, 2L, "image/png", 10L, at)).isTrue();
        assertThat(repository.completeUpload(3L, 2L, "image/png", 10L, at)).isFalse();
    }

    @Test
    void shouldUseSingleConditionalStatusUpdateCapability() {
        FileMapper mapper = mock(FileMapper.class);
        FileRepository repository = new FileRepository(mapper, mock(IdGenerator.class));
        OffsetDateTime at = OffsetDateTime.now();
        when(mapper.updateStatus(
                eq(1L),
                any(),
                eq(FileStatus.DELETING),
                eq(at),
                eq(FileStatus.DELETED),
                eq(FileStatus.IN_USE),
                eq(FileStatus.DELETE_PENDING),
                eq(FileStatus.EXPIRED)))
                .thenReturn(1);

        boolean updated = repository.updateStatus(
                1L,
                Set.of(FileStatus.DELETE_PENDING, FileStatus.DELETE_FAILED),
                FileStatus.DELETING,
                at);

        assertThat(updated).isTrue();
        verify(mapper).updateStatus(
                eq(1L),
                any(),
                eq(FileStatus.DELETING),
                eq(at),
                eq(FileStatus.DELETED),
                eq(FileStatus.IN_USE),
                eq(FileStatus.DELETE_PENDING),
                eq(FileStatus.EXPIRED));
    }

    @Test
    void shouldExpireUploadBatchWithOneMapperCall() {
        FileMapper mapper = mock(FileMapper.class);
        FileRepository repository = new FileRepository(mapper, mock(IdGenerator.class));
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime readyBefore = now.minusHours(24);
        when(mapper.updateExpiredUploads(
                FileStatus.PENDING, FileStatus.READY, FileStatus.EXPIRED, now, readyBefore, 50))
                .thenReturn(37);

        assertThat(repository.expireUploads(now, readyBefore, 50)).isEqualTo(37);
        verify(mapper).updateExpiredUploads(
                FileStatus.PENDING, FileStatus.READY, FileStatus.EXPIRED, now, readyBefore, 50);
    }

    @Test
    void shouldRejectNonPositiveCleanupBatchSize() {
        FileRepository repository = new FileRepository(mock(FileMapper.class), mock(IdGenerator.class));
        OffsetDateTime now = OffsetDateTime.now();

        assertThatThrownBy(() -> repository.expireUploads(now, now, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文件清理批次大小必须大于 0");
        assertThatThrownBy(() -> repository.claimDeletionBatch(now, now, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文件清理批次大小必须大于 0");
    }

    @Test
    void shouldHideDeletionLeaseSqlResultsBehindPersistenceSemantics() {
        FileMapper mapper = mock(FileMapper.class);
        FileRepository repository = new FileRepository(mapper, mock(IdGenerator.class));
        OffsetDateTime leaseExpiredBefore = OffsetDateTime.now().minusMinutes(30);
        OffsetDateTime claimedAt = OffsetDateTime.now();
        OffsetDateTime finishedAt = claimedAt.plusSeconds(2);
        FileResource claimed = new FileResource().setFileId(1L);
        when(mapper.claimDeletionBatch(
                any(), eq(FileStatus.DELETING), eq(leaseExpiredBefore), eq(claimedAt), eq(25)))
                .thenReturn(List.of(claimed));
        when(mapper.updateDeletionFinished(
                1L, FileStatus.DELETING, claimedAt, FileStatus.DELETED, FileStatus.DELETED, finishedAt))
                .thenReturn(1);

        assertThat(repository.claimDeletionBatch(leaseExpiredBefore, claimedAt, 25)).containsExactly(claimed);
        assertThat(repository.finishDeletion(1L, claimedAt, FileStatus.DELETED, finishedAt)).isTrue();

        verify(mapper).claimDeletionBatch(
                eq(List.of(FileStatus.DELETE_PENDING, FileStatus.DELETE_FAILED, FileStatus.EXPIRED)),
                eq(FileStatus.DELETING),
                eq(leaseExpiredBefore),
                eq(claimedAt),
                eq(25));
        verify(mapper).updateDeletionFinished(
                1L, FileStatus.DELETING, claimedAt, FileStatus.DELETED, FileStatus.DELETED, finishedAt);
    }

    @Test
    void shouldRejectNonTerminalDeletionResult() {
        FileRepository repository = new FileRepository(mock(FileMapper.class), mock(IdGenerator.class));
        OffsetDateTime now = OffsetDateTime.now();

        assertThatThrownBy(() -> repository.finishDeletion(1L, now, FileStatus.READY, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文件删除结果状态无效");
    }
}
