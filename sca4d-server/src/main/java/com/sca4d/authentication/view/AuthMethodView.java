package com.sca4d.authentication.view;

import com.sca4d.authentication.AuthMethod;
import com.sca4d.authentication.AuthMethodCapability;
import com.sca4d.authentication.AuthMethodType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

/** 登录入口可使用的认证方式及其 认证目的。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthMethodView {

    private AuthMethodType type;
    private List<AuthMethodCapability> capabilities;

    public static @NotNull AuthMethodView from(@NotNull AuthMethod<?> method) {
        return new AuthMethodView(
                method.type(),
                method.capabilities().stream()
                        .sorted(Comparator.comparing(AuthMethodCapability::value))
                        .toList());
    }
}
