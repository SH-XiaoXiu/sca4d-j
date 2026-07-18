package com.sca4d.mapper;

import com.sca4d.entity.FileObjectEntity;
import com.sca4d.entity.FileResourceEntity;
import com.sca4d.file.FileResource;
import com.sca4d.file.FileStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

/** 文件资源 Mapper。 */
@Mapper
public interface FileMapper {

    /** 插入文件资源行。 */
    int insertResource(@Param("entity") @NotNull FileResourceEntity entity);

    /** 插入文件存储对象行。 */
    int insertObject(@Param("entity") @NotNull FileObjectEntity entity);

    /** 按文件标识联表查询资源及其存储对象。 */
    @Nullable
    FileResource selectById(@Param("fileId") @NotNull Long fileId);

    /** 按文件标识集合联表查询资源及其存储对象。 */
    @NotNull
    List<FileResource> selectByIds(@Param("fileIds") @NotNull List<Long> fileIds);

    /** 按所有者和当前状态条件固化上传对象元数据。 */
    int updateUploadCompleted(
            @Param("fileId") @NotNull Long fileId,
            @Param("ownerPrincipalId") @NotNull Long ownerPrincipalId,
            @Param("currentStatus") @NotNull FileStatus currentStatus,
            @Param("nextStatus") @NotNull FileStatus nextStatus,
            @Param("contentType") @NotNull String contentType,
            @Param("sizeBytes") long sizeBytes,
            @Param("at") @NotNull OffsetDateTime at);

    /** 按当前状态集合条件更新文件状态及各状态对应的时间。 */
    int updateStatus(
            @Param("fileId") @NotNull Long fileId,
            @Param("currentStatuses") @NotNull List<FileStatus> currentStatuses,
            @Param("nextStatus") @NotNull FileStatus nextStatus,
            @Param("now") @NotNull OffsetDateTime now,
            @Param("deletedStatus") @NotNull FileStatus deletedStatus,
            @Param("inUseStatus") @NotNull FileStatus inUseStatus,
            @Param("deletePendingStatus") @NotNull FileStatus deletePendingStatus,
            @Param("expiredStatus") @NotNull FileStatus expiredStatus);

    /** 受批次上限约束地将已超过上传或待消费期限的文件标记为过期。 */
    int updateExpiredUploads(
            @Param("pendingStatus") @NotNull FileStatus pendingStatus,
            @Param("readyStatus") @NotNull FileStatus readyStatus,
            @Param("expiredStatus") @NotNull FileStatus expiredStatus,
            @Param("now") @NotNull OffsetDateTime now,
            @Param("readyBefore") @NotNull OffsetDateTime readyBefore,
            @Param("limit") int limit);

    /** 原子抢占等待删除或租约已经过期的文件批次，并返回抢占后的文件聚合。 */
    @NotNull
    List<FileResource> claimDeletionBatch(
            @Param("pendingStatuses") @NotNull List<FileStatus> pendingStatuses,
            @Param("deletingStatus") @NotNull FileStatus deletingStatus,
            @Param("leaseExpiredBefore") @NotNull OffsetDateTime leaseExpiredBefore,
            @Param("claimedAt") @NotNull OffsetDateTime claimedAt,
            @Param("limit") int limit);

    /** 仅由持有当前租约的节点写入文件删除终态。 */
    int updateDeletionFinished(
            @Param("fileId") @NotNull Long fileId,
            @Param("deletingStatus") @NotNull FileStatus deletingStatus,
            @Param("claimedAt") @NotNull OffsetDateTime claimedAt,
            @Param("nextStatus") @NotNull FileStatus nextStatus,
            @Param("deletedStatus") @NotNull FileStatus deletedStatus,
            @Param("finishedAt") @NotNull OffsetDateTime finishedAt);
}
