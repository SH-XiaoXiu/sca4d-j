package com.sca4d.app.server.platform;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.spring.identity.PrincipalSelection;
import com.sca4d.iam.base.PlatformIdentityService;
import com.sca4d.iam.base.PlatformPrincipal;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 为已认证平台账号选择明确关联的平台业务主体。 */
@Component
public final class PlatformPrincipalSelection implements PrincipalSelection<Map<String, @Nullable Object>> {

    /** 创建平台业务主体选择器。 */
    public PlatformPrincipalSelection() {
    }

    /**
     * Platform Principal 只根据当前 Session 和 path、query、header 中的可选标识选择。
     *
     * @return 始终返回 {@code true}
     */
    @Override
    public boolean supportsSelectionBeforeArgumentBinding() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull Class<Map<String, @Nullable Object>> parameterType() {
        return PrincipalSelection.mapParameterType();
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @Nullable Principal select(
            @Nullable Session session,
            @Nullable Subject subject,
            @Nullable Account account,
            @NotNull Map<String, @Nullable Object> parameters) {
        if (session == null
                || account == null
                || !PlatformIdentityService.PLATFORM_REALM.equals(account.realmKey())) {
            return null;
        }
        List<PlatformPrincipal> principals = session.principals()
                .stream()
                .filter(PlatformPrincipal.class::isInstance)
                .map(PlatformPrincipal.class::cast)
                .toList();
        if (principals.isEmpty()) {
            throw new IllegalStateException("Platform Account Session 必须包含 PlatformPrincipal");
        }
        if (principals.size() == 1) {
            return principals.getFirst();
        }
        Long selectedId = selectedPrincipalId(parameters);
        return principals.stream()
                .filter(principal -> principal.principalId() == selectedId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("选择的 PlatformPrincipal 不在当前 Session 中"));
    }

    /**
     * 按显式参数、路径、查询参数、请求头的固定优先级解析 Principal 选择值。
     *
     * @param parameters 当前调用已经归集的参数
     * @return 调用方明确选择的 Principal 标识
     */
    @CheckReturnValue
    private static @NotNull Long selectedPrincipalId(
            @NotNull Map<String, @Nullable Object> parameters) {
        // 固定优先级避免同一请求在多个位置携带值时依赖 Map 遍历顺序。
        Object value = parameters.get("principalId");
        if (value == null && parameters.get("path") instanceof Map<?, ?> path) {
            value = path.get("principalId");
        }
        if (value == null && parameters.get("query") instanceof Map<?, ?> query) {
            value = query.get("principalId");
        }
        if (value == null && parameters.get("header") instanceof Map<?, ?> headers) {
            value = headers.get("x-principal-id");
        }
        if (value instanceof Long id) {
            return id;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("PlatformPrincipal 选择值必须是正整数", exception);
            }
        }
        throw new IllegalArgumentException("多个 PlatformPrincipal 时必须提供 principalId 选择输入");
    }
}
