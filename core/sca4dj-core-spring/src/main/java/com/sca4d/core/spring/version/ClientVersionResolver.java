package com.sca4d.core.spring.version;

import com.sca4d.core.api.version.ClientVersion;
import com.sca4d.core.api.version.CompatibilityRule;
import com.sca4d.core.api.version.VersionCompatibilityException;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

/** 严格解析一次 HTTP 客户端版本声明，结果不作为可信身份。 */
public final class ClientVersionResolver {
    /** @return 客户端声明；畸形或重复头返回版本输入异常 */
    public ClientVersion resolve(HttpServletRequest request) {
        try {
            String name = header(request, "X-Client-Name");
            String version = header(request, "X-Client-Release-Version");
            String release = header(request, "X-Client-Release-Id");
            String build = header(request, "X-Client-Build");
            String contract = header(request, "X-Client-Api-Contract");
            var client = new ClientVersion(
                    name,
                    version,
                    release,
                    build,
                    contract == null ? null : Integer.valueOf(contract));
            if (!client.unspecified()) {
                if (name == null || version == null)
                    throw new IllegalArgumentException("缺少客户端名称或版本");
                CompatibilityRule.compareVersions(version, version);
                if (contract != null && !contract.matches("[1-9][0-9]*"))
                    throw new IllegalArgumentException("无效契约");
            }
            return client;
        } catch (IllegalArgumentException exception) {
            throw new VersionCompatibilityException(null);
        }
    }

    private static @Nullable String header(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        if (values.isEmpty())
            return null;
        String value = values.getFirst();
        if (values.size() != 1 || !value.matches("[A-Za-z0-9._-]{1,240}"))
            throw new IllegalArgumentException("无效版本头");
        return value;
    }
}
