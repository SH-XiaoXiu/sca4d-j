package com.sca4d.storage.file.persistence.mapper;

import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.storage.FileStatus;
import com.sca4d.storage.file.persistence.FileRecord;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** 查询 storage_file 字段，并按期望状态更新对象位置、状态和删除租约。 */
public interface FileMapper {
    /**
     * 插入一行文件字段。
     *
     * @param row 待插入的全部列值
     * @return 影响行数
     */
    int insert(@Param("row") @NotNull FileRecord row);

    /**
     * 按主键集合读取文件字段。
     *
     * @param ids 文件主键集合
     * @return 数据库中存在的文件列值
     */
    @NotNull
    List<@NotNull FileRecord> selectByIds(@Param("ids") @NotNull Collection<@NotNull Long> ids);

    /**
     * 按期望状态写入实际内容、正式对象键、保留期限和目标状态。
     *
     * @param id 文件主键
     * @param expectedStatus 更新前必须匹配的状态
     * @param targetStatus 更新后的状态
     * @param contentType 存储对象实际媒体类型
     * @param sizeBytes 存储对象实际字节数
     * @param objectKey 正式对象键
     * @param readyExpiresAt 未绑定文件失效时间
     * @return 影响行数
     */
    int updateUploadedObject(
            @Param("id") long id,
            @Param("expectedStatus") @NotNull FileStatus expectedStatus,
            @Param("targetStatus") @NotNull FileStatus targetStatus,
            @Param("contentType") @NotNull String contentType,
            @Param("sizeBytes") long sizeBytes,
            @Param("objectKey") @NotNull String objectKey,
            @Param("readyExpiresAt") @NotNull Instant readyExpiresAt);

    /**
     * 按期望状态写入目标状态及其到期或删除时间。
     *
     * @param id 文件主键
     * @param expectedStatus 更新前必须匹配的状态
     * @param targetStatus 更新后的状态
     * @param readyExpiresAt 未绑定失效时间的明确更新意图
     * @param nextDeleteAt 最早删除时间的明确更新意图
     * @return 影响行数
     */
    int updateStatus(
            @Param("id") long id,
            @Param("expectedStatus") @NotNull FileStatus expectedStatus,
            @Param("targetStatus") @NotNull Patch<FileStatus> targetStatus,
            @Param("readyExpiresAt") @NotNull Patch<Instant> readyExpiresAt,
            @Param("nextDeleteAt") @NotNull Patch<Instant> nextDeleteAt);

    /**
     * 把 PENDING 上传或 READY 保留期限到期的文件改为给定状态。
     *
     * @param targetStatus 更新后的状态
     * @param now 包含在到期范围内的时间上限
     * @return 影响行数
     */
    int updateStatusWhereExpired(
            @Param("targetStatus") @NotNull FileStatus targetStatus,
            @Param("now") @NotNull Instant now);

    /**
     * 读取签名期限不晚于给定时间且尚未清理临时对象的行。
     *
     * @param now 包含在到期范围内的时间上限
     * @param limit 最大返回行数
     * @return 按主键排序的文件列值
     */
    @NotNull
    List<@NotNull FileRecord> selectUploadObjectsBefore(
            @Param("now") @NotNull Instant now,
            @Param("limit") int limit);

    /**
     * 把指定行的临时对象清理标记改为已清理。
     *
     * @param id 文件主键
     * @return 影响行数
     */
    int updateUploadObjectCleaned(@Param("id") long id);

    /**
     * 按状态、重试时间和租约到期条件领取文件，并在同一 SQL 中写入新租约。
     *
     * @param statuses 可以领取的状态集合
     * @param now 判断重试时间和租约到期的当前时间
     * @param limit 最大领取行数
     * @return 已成功写入新租约的文件列值
     */
    @NotNull
    List<@NotNull FileRecord> updateDeletionLeases(
            @Param("statuses") @NotNull Collection<@NotNull FileStatus> statuses,
            @Param("now") @NotNull Instant now,
            @Param("limit") int limit,
            @Param("targetStatus") @NotNull FileStatus targetStatus,
            @Param("leaseOwner") @NotNull String leaseOwner,
            @Param("leaseUntil") @NotNull Instant leaseUntil);

    /**
     * 仅由当前删除租约 owner 写回成功或失败状态。
     *
     * @param id 文件主键
     * @param expectedStatus 写回前必须匹配的处理中状态
     * @param leaseOwner 必须匹配数据库当前值的租约 owner
     * @param targetStatus 删除成功或失败状态
     * @param retryAt 删除失败后的下次领取时间；成功时使用 CLEAR
     * @param incrementAttempts 是否增加删除失败次数
     * @return 影响行数
     */
    int updateDeletionResult(
            @Param("id") long id,
            @Param("expectedStatus") @NotNull FileStatus expectedStatus,
            @Param("leaseOwner") @NotNull String leaseOwner,
            @Param("targetStatus") @NotNull Patch<FileStatus> targetStatus,
            @Param("retryAt") @NotNull Patch<Instant> retryAt,
            @Param("incrementAttempts") boolean incrementAttempts);
}
