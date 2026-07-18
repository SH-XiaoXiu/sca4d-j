package com.sca4d.controllers;

import com.sca4d.authentication.me.MePrincipalSelection;
import com.sca4d.authentication.me.MeService;
import com.sca4d.authentication.view.MeView;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.common.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户接口。
 */
@RestController
@RequestMapping("/me")
@AuthPrincipal(selection = MePrincipalSelection.class, tenantId = "#query.tenantId", selectionKey = "#query.principalType")
@RequiredArgsConstructor
public class MeController {

    private final MeService service;

    /**
     * 查询当前登录用户完整本地状态。
     *
     * @param principalType 需要进入的主体类型；缺失时使用 DEFAULT
     * @param tenantId TENANT Principal 所属租户；其他主体类型不提供
     * @return 当前登录用户完整本地状态
     */
    @GetMapping
    public @NotNull ApiResponse<MeView> current(
            @RequestParam(required = false) PrincipalType principalType,
            @RequestParam(required = false) Long tenantId) {
        return ApiResponse.ok(MeView.from(service.snapshot(CurrentRequestHolder.current())));
    }

}
