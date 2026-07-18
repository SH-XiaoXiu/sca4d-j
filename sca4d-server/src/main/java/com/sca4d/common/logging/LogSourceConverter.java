package com.sca4d.common.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.jetbrains.annotations.NotNull;

/** 将项目 logger 名转换为稳定的模块名和实际类名。 */
public final class LogSourceConverter extends ClassicConverter {

    private static final String PROJECT_PACKAGE = "com.sca4d.";

    /**
     * 从 logger 名提取项目模块和类名，不读取调用栈。
     *
     * @param event Logback 已创建的日志事件
     * @return 项目日志的模块与类名；第三方日志保留完整 logger 名
     */
    @Override
    public @NotNull String convert(@NotNull ILoggingEvent event) {
        String loggerName = event.getLoggerName();
        if (loggerName == null || loggerName.isBlank()) {
            return "logger=unknown";
        }
        if (!loggerName.startsWith(PROJECT_PACKAGE)) {
            return "logger=" + loggerName;
        }

        String projectSource = loggerName.substring(PROJECT_PACKAGE.length());
        int firstSeparator = projectSource.indexOf('.');
        if (firstSeparator < 0) {
            return "module=server class=" + projectSource;
        }

        String module = projectSource.substring(0, firstSeparator);
        if ("common".equals(module)) {
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
