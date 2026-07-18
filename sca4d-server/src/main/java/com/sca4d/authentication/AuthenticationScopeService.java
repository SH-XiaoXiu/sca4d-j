package com.sca4d.authentication;

import com.sca4d.common.auth.AccountScopeType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 将匿名认证入口提交的 GLOBAL 或租户业务编码转换为后端 Account 作用域。 */
public interface AuthenticationScopeService {

    /**
     * 解析匿名认证使用的 Account 作用域。
     *
     * @param scopeType GLOBAL 或 TENANT；缺失时拒绝请求
     * @param tenantCode TENANT 的当前业务编码；GLOBAL 必须为空
     * @return 只在后端携带租户主键的认证作用域
     */
    @NotNull
    AuthenticationScope resolve(
            @Nullable AccountScopeType scopeType, @Nullable String tenantCode);
}
