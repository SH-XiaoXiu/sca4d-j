package com.sca4d.controllers.mg.system;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.system.SystemPermissions;
import com.sca4d.system.SystemScheduledJobService;
import com.sca4d.system.view.SystemScheduledJobView;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 系统 Quartz Job 的只读管理端接口。 */
@RestController
@RequestMapping("/mg/system/scheduled-jobs")
@AuthPrincipal(types = PrincipalType.PLATFORM, selection = PlatformPrincipalSelection.class)
@RequirePermission(SystemPermissions.SCHEDULED_JOBS_READ)
public class SystemScheduledJobController {

    private final SystemScheduledJobService service;

    public SystemScheduledJobController(@NotNull SystemScheduledJobService service) {
        this.service = service;
    }

    /** @return 固定三个系统 Job 的只读状态集合 */
    @GetMapping
    public @NotNull ApiResponse<List<SystemScheduledJobView>> jobs() {
        return ApiResponse.ok(service.jobs().stream().map(SystemScheduledJobView::from).toList());
    }

    /** @param jobGroup Job 分组 @param jobName Job 名称 @return 指定系统 Job 的只读详情 */
    @GetMapping("/{jobGroup}/{jobName}")
    public @NotNull ApiResponse<SystemScheduledJobView> detail(
            @PathVariable @NotNull String jobGroup, @PathVariable @NotNull String jobName) {
        return ApiResponse.ok(SystemScheduledJobView.from(service.require(jobGroup, jobName)));
    }
}
