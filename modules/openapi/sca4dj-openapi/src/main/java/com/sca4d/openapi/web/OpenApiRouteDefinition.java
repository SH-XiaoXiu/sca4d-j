package com.sca4d.openapi.web;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.openapi.web.annotation.OpenApiRoute;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** 保存已经校验的 OpenAPI 路由标识和 Client Operation 约束。 */
public final class OpenApiRouteDefinition {

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract",
            "assert",
            "boolean",
            "break",
            "byte",
            "case",
            "catch",
            "char",
            "class",
            "const",
            "continue",
            "default",
            "do",
            "double",
            "else",
            "enum",
            "extends",
            "final",
            "finally",
            "float",
            "for",
            "goto",
            "if",
            "implements",
            "import",
            "instanceof",
            "int",
            "interface",
            "long",
            "native",
            "new",
            "package",
            "private",
            "protected",
            "public",
            "return",
            "short",
            "static",
            "strictfp",
            "super",
            "switch",
            "synchronized",
            "this",
            "throw",
            "throws",
            "transient",
            "try",
            "void",
            "volatile",
            "while",
            "true",
            "false",
            "null",
            "var",
            "yield",
            "record",
            "sealed",
            "permits",
            "non-sealed");

    private final @NotNull RequestMethod method;

    private final @NotNull String path;

    private final @NotNull String toolGroup;

    private final @NotNull String toolName;

    private final @NotNull TreeCode operation;

    /**
     * 创建路由定义。
     *
     * @param annotation 路由声明
     */
    public OpenApiRouteDefinition(@NotNull OpenApiRoute annotation) {
        OpenApiRoute checked = Objects.requireNonNull(annotation, "OpenAPI 路由声明不能为空");
        this.method = requireMethod(checked.method());
        this.path = requirePath(checked.path());
        this.toolGroup = requireJavaIdentifier(checked.toolGroup(), "OpenAPI Tool 分组");
        this.toolName = requireJavaIdentifier(checked.toolName(), "OpenAPI Tool 名称");
        this.operation = TreeCode.ofColon(requireText(checked.operation(), "OpenAPI Operation"));
    }

    /** @return HTTP 方法 */
    public @NotNull RequestMethod method() {
        return method;
    }

    /** @return 完整公开路径 */
    public @NotNull String path() {
        return path;
    }

    /** @return 第三方 Tool 和 Swagger 分组名称 */
    public @NotNull String toolGroup() {
        return toolGroup;
    }

    /** @return Tool 分组内的稳定名称 */
    public @NotNull String toolName() {
        return toolName;
    }

    /** @return 写入 OpenAPI 标准文档的全局唯一操作标识 */
    public @NotNull String openApiOperationId() {
        return toolGroup + toolName.substring(0, 1).toUpperCase(Locale.ROOT) + toolName.substring(1);
    }

    /** @return Client 开放检查使用的 Operation 编码 */
    public @NotNull TreeCode operation() {
        return operation;
    }

    /** 校验并返回路由声明中的唯一 HTTP 方法。 */
    private static @NotNull RequestMethod requireMethod(RequestMethod @NotNull [] values) {
        Objects.requireNonNull(values, "OpenAPI HTTP 方法不能为空");
        if (values.length != 1 || values[0] == null) {
            throw new IllegalArgumentException("OpenAPI 路由必须声明一个 HTTP 方法");
        }
        return values[0];
    }

    /** 校验并返回以 {@code /openapi/} 开头的唯一公开路径。 */
    private static @NotNull String requirePath(String @NotNull [] values) {
        Objects.requireNonNull(values, "OpenAPI 路径不能为空");
        if (values.length != 1) {
            throw new IllegalArgumentException("OpenAPI 路由必须声明一个路径");
        }
        String path = requireText(values[0], "OpenAPI 路径");
        if (!path.startsWith("/openapi/")) {
            throw new IllegalArgumentException("OpenAPI 路径必须以 /openapi/ 开头: " + path);
        }
        if (path.contains("//") || path.contains("?") || path.contains("#")) {
            throw new IllegalArgumentException("OpenAPI 路径不能包含重复斜杠、查询参数或片段: " + path);
        }
        return path;
    }

    /** 校验并返回去除首尾空白的必填文本。 */
    private static @NotNull String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value.trim();
    }

    /** 校验并返回可以组成标准 OpenAPI 操作标识的 Java 标识符。 */
    private static @NotNull String requireJavaIdentifier(String value, String name) {
        String checked = requireText(value, name);
        if (!Character.isJavaIdentifierStart(checked.charAt(0))
                || checked.chars().skip(1).anyMatch(character -> !Character.isJavaIdentifierPart(character))
                || JAVA_KEYWORDS.contains(checked)) {
            throw new IllegalArgumentException(name + "必须是合法 Java 标识符: " + checked);
        }
        return checked;
    }
}
