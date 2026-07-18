package com.sca4d.file.repository;

import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.FileObjectEntity;
import com.sca4d.entity.FileResourceEntity;
import com.sca4d.file.FileResource;
import com.sca4d.file.FileStatus;
import com.sca4d.mapper.FileMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 文件资源仓储。 */
@Repository
@RequiredArgsConstructor
public class FileRepository {

    private final FileMapper mapper;
    private final IdGenerator idGenerator;

    /** 创建一次等待上传的文件资源。 */
    public void createUpload(@NotNull FileResourceEntity resource, @NotNull FileObjectEntity object) {
        if (object.getId() == null) {
            object.setId(idGenerator.nextId());
        }
        mapper.insertResource(resource);
        mapper.insertObject(object);
    }

    /**
     * 按文件标识读取包含存储对象信息的文件聚合。
     *
     * @param fileId 文件标识
     * @return 文件不存在时返回空结果
     */
    public @NotNull Optional<FileResource> find(@NotNull Long fileId) {
        return Optional.ofNullable(mapper.selectById(fileId));
    }

    /**
     * 批量读取文件聚合，结果只包含数据库中实际存在的文件。
     *
     * @param fileIds 待读取的文件标识
     * @return 文件资源与存储对象组合后的聚合集合
     */
    public @NotNull List<FileResource> findAll(@NotNull List<Long> fileIds) {
        return fileIds.isEmpty() ? List.of() : mapper.selectByIds(fileIds);
    }

    /**
     * 在文件仍属于指定 Principal 且等待上传时固化对象元数据。
     *
     * @param fileId 文件标识
     * @param ownerPrincipalId 文件所有者 Principal 标识
     * @param contentType 存储系统确认的内容类型
     * @param sizeBytes 存储系统确认的文件大小
     * @param at 完成上传的时间
     * @return 条件更新命中唯一文件时返回 true
     */
    public boolean completeUpload(
            @NotNull Long fileId,
            @NotNull Long ownerPrincipalId,
            @NotNull String contentType,
            long sizeBytes,
            @NotNull OffsetDateTime at) {
        return mapper.updateUploadCompleted(
                fileId, ownerPrincipalId, FileStatus.PENDING, FileStatus.READY, contentType, sizeBytes, at) == 1;
    }

    /**
     * 按允许的当前状态原子更新文件状态。
     *
     * @param fileId 文件主键
     * @param currentStatuses 允许迁移的当前状态，不能为空
     * @param nextStatus 目标状态
     * @param at 状态变化时间
     * @return 状态更新成功时返回 true
     */
    public boolean updateStatus(
            @NotNull Long fileId,
            @NotNull Set<FileStatus> currentStatuses,
            @NotNull FileStatus nextStatus,
            @NotNull OffsetDateTime at) {
        if (currentStatuses.isEmpty()) {
            throw new IllegalArgumentException("文件当前状态集合不能为空");
        }
        return mapper.updateStatus(
                fileId,
                List.copyOf(currentStatuses),
                nextStatus,
                at,
                FileStatus.DELETED,
                FileStatus.IN_USE,
                FileStatus.DELETE_PENDING,
                FileStatus.EXPIRED) > 0;
    }

    /**
     * 受批次上限约束地将超过上传或待消费期限的文件标记为等待删除。
     *
     * @param now 当前清理时间
     * @param readyBefore READY 文件允许保留的最晚时间
     * @param limit 本轮最多更新的文件数
     * @return 实际进入 EXPIRED 状态的文件数
     */
    public int expireUploads(@NotNull OffsetDateTime now, @NotNull OffsetDateTime readyBefore, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("文件清理批次大小必须大于 0");
        }
        return mapper.updateExpiredUploads(
                FileStatus.PENDING, FileStatus.READY, FileStatus.EXPIRED, now, readyBefore, limit);
    }

    /**
     * 原子抢占等待删除和处理租约已经过期的文件批次。
     *
     * <p>数据库使用跳过锁定行的批量更新完成节点竞争；一次调用最多返回 {@code limit} 个已归当前租约所有的文件。
     *
     * @param leaseExpiredBefore `DELETING` 文件可以被其他节点重领的最晚抢占时间
     * @param claimedAt 当前节点取得整批处理权的时间，也是终态写入必须匹配的租约标识
     * @param limit 本轮最多抢占的文件数
     * @return 按删除请求时间排序、已由当前节点取得处理权的文件
     */
    public @NotNull List<FileResource> claimDeletionBatch(
            @NotNull OffsetDateTime leaseExpiredBefore,
            @NotNull OffsetDateTime claimedAt,
            int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("文件清理批次大小必须大于 0");
        }
        return mapper.claimDeletionBatch(
                List.of(FileStatus.DELETE_PENDING, FileStatus.DELETE_FAILED, FileStatus.EXPIRED),
                FileStatus.DELETING,
                leaseExpiredBefore,
                claimedAt,
                limit);
    }

    /**
     * 写入当前删除租约的处理结果。
     *
     * <p>终态更新同时校验 `DELETING` 状态和抢占时间，已经被其他节点重领的任务不能覆盖新租约结果。
     *
     * @param fileId 已处理文件标识
     * @param claimedAt 当前节点取得处理权时保存的租约标识
     * @param nextStatus `DELETED` 或 `DELETE_FAILED`
     * @param finishedAt 对象删除完成或失败的时间
     * @return 当前租约仍有效且结果成功写入时返回 true
     * @throws IllegalArgumentException 目标状态不是删除终态或重试状态时抛出
     */
    public boolean finishDeletion(
            @NotNull Long fileId,
            @NotNull OffsetDateTime claimedAt,
            @NotNull FileStatus nextStatus,
            @NotNull OffsetDateTime finishedAt) {
        if (nextStatus != FileStatus.DELETED && nextStatus != FileStatus.DELETE_FAILED) {
            throw new IllegalArgumentException("文件删除结果状态无效");
        }
        return mapper.updateDeletionFinished(
                fileId,
                FileStatus.DELETING,
                claimedAt,
                nextStatus,
                FileStatus.DELETED,
                finishedAt) == 1;
    }
}
