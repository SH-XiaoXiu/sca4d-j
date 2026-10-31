package com.sca4d.openapi.web;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** 保存启动期校验完成的 OpenAPI 路由标识和 Client Operation 约束。 */
public class OpenApiRouteDefinition {

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
     * 创建与声明方式无关的 OpenAPI 路由定义。
     *
     * @param method 唯一 HTTP 方法
     * @param path 完整公开路径
     * @param toolGroup 第三方 Tool 与 Swagger 文档使用的分组标识
     * @param toolName Tool 分组内的稳定标识
     * @param operation OperationCatalog 中已经注册的固定 Operation
     */
    public OpenApiRouteDefinition(
            @NotNull RequestMethod method,
            @NotNull String path,
            @NotNull String toolGroup,
            @NotNull String toolName,
            @NotNull TreeCode operation) {
        this.method = requireMethod(method);
        this.path = requirePath(path);
        this.toolGroup = requireJavaIdentifier(toolGroup, "OpenAPI Tool 分组");
        this.toolName = requireJavaIdentifier(toolName, "OpenAPI Tool 名称");
        this.operation = Objects.requireNonNull(operation, "OpenAPI Operation 不能为空");
    }

    /** @return HTTP 方法 */
    public @NotNull RequestMethod method() {
        return method;
    }

    /** @return 完整公开路径 */
    public @NotNull String path() {
        return path;
    }

    /** @return 第三方 Tool 与 Swagger 文档使用的分组标识 */
    public @NotNull String toolGroup() {
        return toolGroup;
    }

    /** @return Tool 分组内的稳定标识 */
    public @NotNull String toolName() {
        return toolName;
    }

    /** @return 写入 OpenAPI 文档的全局唯一操作标识 */
    public @NotNull String openApiOperationId() {
        return toolGroup + toolName.substring(0, 1).toUpperCase(Locale.ROOT) + toolName.substring(1);
    }

    /** @return Client 开放检查使用的 Operation 编码 */
    public @NotNull TreeCode operation() {
        return operation;
    }

    /** 校验并返回唯一 HTTP 方法。 */
    private static @NotNull RequestMethod requireMethod(@NotNull RequestMethod value) {
        if (value == null) {
            throw new IllegalArgumentException("OpenAPI 路由必须声明一个 HTTP 方法");
        }
        return value;
    }

    /** 校验并返回位于配置的 OpenAPI 路径前缀下的公开路径。 */
    private static @NotNull String requirePath(@NotNull String value) {
        String path = requireText(value, "OpenAPI 路径");
        if (!path.startsWith(OpenApiConstants.PATH_PREFIX + "/")) {
            throw new IllegalArgumentException("OpenAPI 路径必须以 " + OpenApiConstants.PATH_PREFIX + "/ 开头: " + path);
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
