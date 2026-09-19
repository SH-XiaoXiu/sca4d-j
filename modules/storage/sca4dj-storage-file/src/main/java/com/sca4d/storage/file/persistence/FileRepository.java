package com.sca4d.storage.file.persistence;

import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.api.storage.FileStatus;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.storage.file.persistence.mapper.FileMapper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 读取并修改 storage_file 中的上传状态、对象位置、绑定状态和删除租约。 */
@ApiStatus.Internal
public class FileRepository {
    private static final List<FileStatus> DELETION_CANDIDATES = List
            .of(FileStatus.DELETE_PENDING, FileStatus.DELETE_FAILED, FileStatus.DELETING);

    private final @NotNull FileMapper mapper;

    private final @NotNull SqlBatches sqlBatches;

    /** 创建使用指定 Mapper 的文件持久化边界。 */
    public FileRepository(@NotNull FileMapper mapper, @NotNull SqlBatches sqlBatches) {
        this.mapper = Objects.requireNonNull(mapper, "文件 Mapper 不能为空");
        this.sqlBatches = Objects.requireNonNull(sqlBatches, "SQL 批处理器不能为空");
    }

    /** 保存一条等待上传的文件记录。 */
    @AffectedRows(target = "新增文件记录")
    public void createPending(@NotNull FileRecord record) {
        mapper.insert(record);
    }

    /** 按标识读取文件记录。 */
    @NotNull
    public Optional<FileRecord> find(long id) {
        return mapper.selectByIds(List.of(id)).stream().findFirst();
    }

    /** 按标识批量读取文件记录。 */
    @NotNull
    public List<@NotNull FileRecord> findAll(@NotNull Collection<@NotNull Long> ids) {
        List<Long> ordered = ids.stream().distinct().sorted().toList();
        return sqlBatches.query(ordered, 1, 0, mapper::selectByIds);
    }

    /** 保存上传完成后的正式对象位置和实际元数据。 */
    public boolean saveCompleted(@NotNull FileRecord completed) {
        String objectKey = completed.objectKey();
        String contentType = completed.contentType();
        Long sizeBytes = completed.sizeBytes();
        Instant readyExpiresAt = completed.readyExpiresAt();
        if (objectKey == null || contentType == null || sizeBytes == null || readyExpiresAt == null) {
            throw new IllegalArgumentException("完成文件缺少正式对象信息");
        }
        return AffectedRowCounts.changed(
                mapper.updateUploadedObject(
                        completed.id(),
                        FileStatus.PENDING,
                        FileStatus.READY,
                        contentType,
                        sizeBytes,
                        objectKey,
                        readyExpiresAt),
                "完成文件上传");
    }

    /** 按原状态保存业务层生成的文件状态和清理时间；并发已推进时返回 false。 */
    public boolean saveState(@NotNull FileRecord current, @NotNull FileRecord changed, @Nullable Instant nextDeleteAt) {
        if (current.id() != changed.id()) {
            throw new IllegalArgumentException("文件状态必须属于同一文件");
        }
        return AffectedRowCounts.changed(
                mapper.updateStatus(
                        current.id(),
                        current.status(),
                        Patch.set(changed.status()),
                        changed.readyExpiresAt() == null ? Patch.clear() : Patch.set(changed.readyExpiresAt()),
                        nextDeleteAt == null ? Patch.clear() : Patch.set(nextDeleteAt)),
                "保存文件状态");
    }

    /** 把到期上传和未绑定文件登记为等待删除。 */
    public void requestExpiredDeletion(@NotNull Instant now) {
        AffectedRowCounts
                .requireAtLeast(mapper.updateStatusWhereExpired(FileStatus.DELETE_PENDING, now), 0, "登记过期文件删除");
    }

    /** 读取签名到期后仍需清理临时对象的文件。 */
    @NotNull
    public List<@NotNull FileRecord> findExpiredUploadObjects(@NotNull Instant now) {
        return mapper.selectUploadObjectsBefore(now, 100);
    }

    /** 记录临时对象已在上传签名到期后删除。 */
    @AffectedRows(target = "文件临时对象清理标记", whenZero = ZeroRowPolicy.ALLOW)
    public void markUploadObjectCleaned(long id) {
        mapper.updateUploadObjectCleaned(id);
    }

    /** 按状态、重试时间和租约条件领取一批删除记录。 */
    @NotNull
    public List<@NotNull FileRecord> claimDeletion(
            @NotNull Instant now,
            @NotNull Instant leaseUntil,
            @NotNull String owner) {
        return mapper.updateDeletionLeases(
                DELETION_CANDIDATES,
                now,
                50,
                FileStatus.DELETING,
                owner,
                leaseUntil);
    }

    /** 按当前租约保存删除结果及重试时间；过期持有者不能覆盖新租约的结果。 */
    public boolean saveDeletionResult(
            long id,
            @NotNull String owner,
            @NotNull FileStatus status,
            @Nullable Instant retryAt) {
        return AffectedRowCounts.changed(
                mapper.updateDeletionResult(
                        id,
                        FileStatus.DELETING,
                        owner,
                        Patch.set(status),
                        retryAt == null ? Patch.clear() : Patch.set(retryAt),
                        status == FileStatus.DELETE_FAILED),
                "保存文件删除结果");
    }

}
