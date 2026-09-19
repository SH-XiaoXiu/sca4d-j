package com.sca4d.app.server.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.sca4d.core.api.logging.LogSourceFormatter;

/**
 * 把 Logback 事件中的 logger 名转换为默认应用的模块和类名，并注册为 {@code %source}。
 *
 * <p>依赖 {@link LogSourceFormatter} 解析 {@code com.sca4d} 根包，继承 Logback 的
 * {@link ClassicConverter} 协议但不读取 caller data。
 *
 * @see <a href="https://github.com/qos-ch/logback/blob/v_1.5.18/logback-classic/src/main/java/ch/qos/logback/classic/pattern/ClassicConverter.java">Logback ClassicConverter</a>
 */
public final class LogSourceConverter extends ClassicConverter {

    /** 创建 Logback 日志来源转换器。 */
    public LogSourceConverter() {
    }

    @Override
    public String convert(ILoggingEvent event) {
        return LogSourceFormatter.format(event.getLoggerName());
    }
}
