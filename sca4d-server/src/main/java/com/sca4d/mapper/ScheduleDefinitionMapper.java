package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.entity.ScheduleDefinitionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

/** Schedule 定义 JSONB 写入和乐观更新 SQL Mapper。 */
@Mapper
public interface ScheduleDefinitionMapper extends BaseMapper<ScheduleDefinitionEntity> {

    /** @return 插入 Schedule 定义的行数 */
    int insertDefinition(@Param("schedule") @NotNull ScheduleDefinitionEntity schedule);

    /** @return 版本匹配并完成更新时返回 1，否则返回 0 */
    int updateDefinition(
            @Param("schedule") @NotNull ScheduleDefinitionEntity schedule,
            @Param("expectedVersion") long expectedVersion);

    /** @return Schedule 仍存在并写入 Quartz 同步错误时返回 1，否则返回 0 */
    int updateSynchronizationError(
            @Param("scheduleId") @NotNull Long scheduleId,
            @Param("synchronizationError") String synchronizationError,
            @Param("updatedAt") @NotNull OffsetDateTime updatedAt);
}
