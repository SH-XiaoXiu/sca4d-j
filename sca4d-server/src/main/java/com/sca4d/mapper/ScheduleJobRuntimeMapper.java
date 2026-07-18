package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.entity.ScheduleJobRuntimeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

/** 系统 Job 最近运行快照原子 UPSERT SQL Mapper。 */
@Mapper
public interface ScheduleJobRuntimeMapper extends BaseMapper<ScheduleJobRuntimeEntity> {

    /** 按 Job group 和 name 插入或覆盖最近一次运行结果。 */
    void upsert(@Param("runtime") @NotNull ScheduleJobRuntimeEntity runtime);
}
