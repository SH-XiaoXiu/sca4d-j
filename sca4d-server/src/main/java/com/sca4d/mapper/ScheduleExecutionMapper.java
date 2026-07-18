package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.entity.ScheduleExecutionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** ScheduleExecution 唯一 fireKey 幂等插入 SQL Mapper。 */
@Mapper
public interface ScheduleExecutionMapper extends BaseMapper<ScheduleExecutionEntity> {

    /** @return fireKey 首次出现并完成插入时返回 1，重复时返回 0 */
    int insertIgnoreDuplicate(@Param("execution") @NotNull ScheduleExecutionEntity execution);

    /** 使用 fireKey 插入或覆盖未成功的执行记录，不覆盖已经成功的记录。 */
    void upsertUnsuccessful(@Param("execution") @NotNull ScheduleExecutionEntity execution);

    /** @return 尚未被 Quartz 消费的人工执行记录 */
    @NotNull
    List<ScheduleExecutionEntity> selectPendingManualExecutions();
}
