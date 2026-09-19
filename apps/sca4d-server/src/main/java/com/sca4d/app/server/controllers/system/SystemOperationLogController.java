package com.sca4d.app.server.controllers.system;

import com.sca4d.app.server.audit.SystemOperations;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.service.OperationLogService;
import com.sca4d.app.server.system.view.OperationLogView;
import com.sca4d.audit.query.AuditQuery;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.audit.AuditResult;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.core.spring.web.annotation.PageOptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Objects;

/** 按身份、操作、结果、目标和时间查询管理端操作日志。 */
@RestController
@RequestMapping("/system/audit/records")
@Tag(name = "System Audit")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class SystemOperationLogController {
    private final @NotNull OperationLogService logs;

    /**
     * 创建系统操作日志查询入口。
     *
     * @param logs 操作日志应用服务
     */
    public SystemOperationLogController(@NotNull OperationLogService logs) {
        this.logs = Objects.requireNonNull(logs, "操作日志服务不能为空");
    }

    /**
     * 按可选条件查询一页操作日志。
     *
     * @param requestId 可选精确调用标识
     * @param accountId 可选操作 Account 标识
     * @param actorPrincipalId 可选操作 Principal 标识；避免与当前管理身份选择参数重名
     * @param operation 可选精确操作编码
     * @param result 可选执行结果
     * @param targetType 可选目标 Resource 类型；与 targetId 同时提供
     * @param targetId 可选目标 Resource 标识；与 targetType 同时提供
     * @param occurredFrom 可选发生时间下界，包含该时间
     * @param occurredTo 可选发生时间上界，不包含该时间
     * @param page 从 1 开始且每页数量不超过 100 的分页要求
     * @return 按发生时间和标识倒序排列的操作日志
     */
    @GetMapping
    @Operation(summary = "查询操作日志")
    @RequirePermission(operation = SystemOperations.AUDIT_READ)
    public @NotNull ApiResponse<Page<OperationLogView>> find(
            @RequestParam(required = false) @Nullable String requestId,
            @RequestParam(required = false) @Nullable Long accountId,
            @RequestParam(required = false) @Nullable Long actorPrincipalId,
            @RequestParam(required = false) @Nullable String operation,
            @RequestParam(required = false) @Nullable AuditResult result,
            @RequestParam(required = false) @Nullable String targetType,
            @RequestParam(required = false) @Nullable String targetId,
            @RequestParam(required = false) @Nullable Instant occurredFrom,
            @RequestParam(required = false) @Nullable Instant occurredTo,
            @PageOptions(maxSize = 100) @NotNull PageRequest page) {
        AuditQuery query = new AuditQuery(
                requestId,
                accountId,
                actorPrincipalId,
                operation,
                result,
                targetType,
                targetId,
                occurredFrom,
                occurredTo,
                page);
        return ApiResponse.ok(logs.page(query).map(OperationLogView::from));
    }

    /**
     * 读取一条必须存在的操作日志。
     *
     * @param id 正数审计记录标识
     * @return 完整操作日志
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询操作日志详情")
    @RequirePermission(operation = SystemOperations.AUDIT_READ)
    public @NotNull ApiResponse<OperationLogView> get(@PathVariable long id) {
        return ApiResponse.ok(OperationLogView.from(logs.loadById(id)));
    }
}
