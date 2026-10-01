package com.sca4d.core.api.logging;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 从 SLF4J logger 名提取稳定的模块和类名，供应用层 Logback 转换器输出日志来源。
 *
 * <p>只依赖 logger 名，不读取 Logback 调用栈或运行时上下文；具体应用可以传入自己的根包，复用同一套规则。
 */
public class LogSourceFormatter {

    private LogSourceFormatter() {
    }

    /**
     * 格式化日志来源。
     *
     * @param loggerName 日志名称，可以为空
     * @return 项目日志的模块和类名，或第三方完整日志名称
     */
    @Contract(pure = true)
    public static String format(@Nullable String loggerName) {
        return format("com.sca4d", loggerName);
    }

    /**
     * 按指定根包格式化日志来源。
     *
     * @param projectPackage 应用或脚手架代码的根包，可以包含末尾的 {@code .}
     * @param loggerName 日志名称，可以为空
     * @return 根包内日志的模块和类名，或第三方完整日志名称
     */
    @Contract(pure = true)
    public static String format(@NotNull String projectPackage, @Nullable String loggerName) {
        if (loggerName == null || loggerName.isBlank()) {
            return "logger=unknown";
        }
        String packagePrefix = projectPackage.endsWith(".") ? projectPackage : projectPackage + ".";
        if (!loggerName.startsWith(packagePrefix)) {
            return "logger=" + loggerName;
        }
        String projectSource = loggerName.substring(packagePrefix.length());
        int firstSeparator = projectSource.indexOf('.');
        if (firstSeparator < 0) {
            return "module=server class=" + projectSource;
        }
        String module = projectSource.substring(0, firstSeparator);
        if ("core".equals(module)) {
            int capabilitySeparator = projectSource.indexOf('.', firstSeparator + 1);
            if (capabilitySeparator > firstSeparator + 1) {
                module = projectSource.substring(0, capabilitySeparator);
            }
        }
        int classSeparator = projectSource.lastIndexOf('.');
        String className = projectSource.substring(classSeparator + 1);
        return "module=" + module + " class=" + className;
    }
}
