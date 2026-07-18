package com.sca4d.system;

import com.sca4d.common.audit.AuditResult;
import com.sca4d.common.web.PageData;
import com.sca4d.system.audit.OperationLog;
import com.sca4d.system.audit.OperationLogModuleDescription;
import com.sca4d.system.audit.OperationLogOperatorOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 分页查询操作日志并读取单条日志详情。
 */
public interface OperationLogService {

    /**
     * 获取当前应用可产生的操作日志筛选目录。
     *
     * @return 已按当前请求语言解析并按展示顺序排列的模块与动作
     */
    @NotNull
    List<OperationLogModuleDescription> catalog();

    /**
     * 分页查询操作日志中已经出现且具有名称快照的操作人。
     *
     * @param search Subject 展示名称搜索；空白时读取全部
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 由操作日志业务模块拥有的操作人候选分页
     */
    @NotNull
    PageData<OperationLogOperatorOption> pageOperatorOptions(@Nullable String search, long page, long size);

    /**
     * 分页查询符合筛选条件的操作日志。
     *
     * @param moduleCode 模块编码筛选；缺失时查询全部模块
     * @param actionCode 动作编码筛选；缺失时查询全部动作
     * @param result 执行结果筛选；缺失时查询全部结果
     * @param subjectId 操作身份筛选；缺失时不限制 Subject
     * @param principalId 操作主体筛选；缺失时不限制 Principal
     * @param targetType 目标资源类型筛选；缺失时不限制目标类型
     * @param targetId 目标资源主键筛选；缺失时不限制目标主键
     * @param traceId 请求追踪标识；缺失时不按追踪标识筛选
     * @param createdAtStart 创建时间下界；缺失时不限制最早时间
     * @param createdAtEnd 创建时间上界；缺失时不限制最晚时间
     * @param page 页码，从 1 开始
     * @param size 每页记录数
     * @return 已解码审计载荷的操作日志分页
     */
    @NotNull
    PageData<OperationLog> page(
            @Nullable String moduleCode,
            @Nullable String actionCode,
            @Nullable AuditResult result,
            @Nullable Long subjectId,
            @Nullable Long principalId,
            @Nullable String targetType,
            @Nullable String targetId,
            @Nullable String traceId,
            @Nullable OffsetDateTime createdAtStart,
            @Nullable OffsetDateTime createdAtEnd,
            long page,
            long size);

    /**
     * 查询一条操作日志及其完整审计载荷。
     *
     * @param id 操作日志主键
     * @return 已解码审计载荷的操作日志
     * @throws com.sca4d.common.exception.BizException 操作日志不存在时抛出
     */
    @NotNull
    OperationLog detail(@NotNull Long id);
}
