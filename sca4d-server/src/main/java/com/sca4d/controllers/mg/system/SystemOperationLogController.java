package com.sca4d.controllers.mg.system;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.PageData;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.system.OperationLogService;
import com.sca4d.system.SystemPermissions;
import com.sca4d.system.request.OperationLogOperatorOptionsRequest;
import com.sca4d.system.request.OperationLogPageRequest;
import com.sca4d.system.view.AuditPayloadViewRegistry;
import com.sca4d.system.view.OperationLogDetailView;
import com.sca4d.system.view.OperationLogModuleView;
import com.sca4d.system.view.OperationLogOperatorOptionView;
import com.sca4d.system.view.OperationLogView;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端系统操作日志接口。
 */
@RestController
@RequestMapping("/mg/system/operation-logs")
@AuthPrincipal(types = PrincipalType.PLATFORM, selection = PlatformPrincipalSelection.class)
@RequirePermission(SystemPermissions.OPERATION_LOGS_READ)
@RequiredArgsConstructor
public class SystemOperationLogController {

    private final OperationLogService service;
    private final AuditPayloadViewRegistry payloadViewRegistry;

    /**
     * 获取操作日志模块与动作筛选目录。
     *
     * @return 当前请求语言下的模块和动作选项
     */
    @GetMapping("/catalog")
    public @NotNull ApiResponse<List<OperationLogModuleView>> catalog() {
        return ApiResponse.ok(service.catalog().stream().map(OperationLogModuleView::from).toList());
    }

    /**
     * 分页查询当前操作日志范围内可筛选的历史操作人。
     *
     * @param request 远程搜索和分页参数
     * @return 使用操作发生时 Subject 名称快照的候选分页
     */
    @GetMapping("/operator-options")
    public @NotNull ApiResponse<PageData<OperationLogOperatorOptionView>> operatorOptions(
            @NotNull OperationLogOperatorOptionsRequest request) {
        return ApiResponse.ok(service
                .pageOperatorOptions(request.getSearch(), request.page(), request.size())
                .map(OperationLogOperatorOptionView::from));
    }

    /**
     * 分页查询系统操作日志。
     *
     * @param request 查询请求
     * @return 操作日志分页
     */
    @GetMapping
    public @NotNull ApiResponse<PageData<OperationLogView>> page(@NotNull OperationLogPageRequest request) {
        return ApiResponse.ok(service
                .page(
                        request.getModuleCode(),
                        request.getActionCode(),
                        request.getResult(),
                        request.getSubjectId(),
                        request.getPrincipalId(),
                        request.getTargetType(),
                        request.getTargetId(),
                        request.getTraceId(),
                        request.getCreatedAtStart(),
                        request.getCreatedAtEnd(),
                        request.page(),
                        request.size())
                .map(OperationLogView::from));
    }

    /**
     * 查询系统操作日志详情。
     *
     * @param id 操作日志主键
     * @return 操作日志详情
     */
    @GetMapping("/{id}")
    public @NotNull ApiResponse<OperationLogDetailView> detail(@PathVariable @NotNull Long id) {
        return ApiResponse.ok(OperationLogDetailView.from(service.detail(id), payloadViewRegistry));
    }
}
