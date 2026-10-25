package com.sca4d.core.spring.version;

import com.sca4d.core.api.version.ClientVersion;
import com.sca4d.core.api.version.exception.VersionCompatibilityException;
import com.sca4d.core.spring.web.HttpConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

/** 严格解析一次 HTTP 客户端版本声明，结果不作为可信身份。 */
public final class ClientVersionResolver {
    /**
     * 读取五个客户端版本请求头并校验组合关系。
     *
     * <p>所有版本头都不存在时返回未声明的 ClientVersion。声明了任一版本头但缺少客户端名称或版本，
     * 或契约号不是正整数时，抛出 VersionCompatibilityException。任一头重复或格式不合法时同样抛出该异常。
     * 解析结果只用于版本兼容判断，不作为身份依据。
     *
     * @param request 当前 HTTP 请求
     * @return 客户端版本声明
     * @throws VersionCompatibilityException 版本头格式或组合不合法时抛出
     */
    public ClientVersion resolve(HttpServletRequest request) {
        try {
            String name = header(request, HttpConstants.CLIENT_NAME_HEADER);
            String version = header(request, HttpConstants.CLIENT_RELEASE_VERSION_HEADER);
            String release = header(request, HttpConstants.CLIENT_RELEASE_ID_HEADER);
            String build = header(request, HttpConstants.CLIENT_BUILD_HEADER);
            String contract = header(request, HttpConstants.CLIENT_API_CONTRACT_HEADER);
            var client = new ClientVersion(
                    name,
                    version,
                    release,
                    build,
                    contract == null ? null : Integer.valueOf(contract));
            if (!client.unspecified()) {
                if (name == null || version == null)
                    throw new IllegalArgumentException("缺少客户端名称或版本");
                if (contract != null && !contract.matches("[1-9][0-9]*"))
                    throw new IllegalArgumentException("无效契约");
            }
            return client;
        } catch (IllegalArgumentException exception) {
            throw new VersionCompatibilityException(null);
        }
    }

    /**
     * 读取单个请求头的值。
     *
     * @param request 当前 HTTP 请求
     * @param name 请求头名称
     * @return 请求头的值；请求头不存在时返回 {@code null}
     * @throws IllegalArgumentException 同名请求头出现多次，或值不是 1 到 240 个字母、数字、点、下划线或中划线时抛出
     */
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
