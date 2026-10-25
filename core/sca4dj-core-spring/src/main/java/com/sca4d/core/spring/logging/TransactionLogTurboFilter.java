package com.sca4d.core.spring.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import com.sca4d.core.api.logging.LogMark;
import com.sca4d.core.spring.transaction.TransactionCallbacks;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.slf4j.event.LoggingEvent;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 把带 {@link LogMark#AFTER_COMMIT} 的 INFO 交给当前事务，并在提交后按原 logger 输出。 */
public class TransactionLogTurboFilter extends TurboFilter {

    /**
     * 处理带 {@link LogMark#AFTER_COMMIT} 的 Marker 日志。Marker 与其他 Marker 同时使用时抛出 IllegalArgumentException；
     * 日志被登记到当前事务后返回 DENY，因此原 logger 不会立即输出。
     *
     * @param marker 日志调用携带的 Marker
     * @param logger 发起日志调用的 logger
     * @param level 日志级别
     * @param format 日志消息模板；只用于级别探测时为空
     * @param params 日志参数
     * @param throwable 日志携带的异常
     * @return 非提交后日志返回 NEUTRAL，已登记或已被级别过滤的提交后日志返回 DENY
     */
    @Override
    public FilterReply decide(
            Marker marker,
            Logger logger,
            Level level,
            String format,
            Object[] params,
            Throwable throwable) {
        if (!containsAfterCommit(marker)) {
            return FilterReply.NEUTRAL;
        }
        if (!LogMark.AFTER_COMMIT.equals(marker) || marker.hasReferences()) {
            throw new IllegalArgumentException("提交后日志不能同时使用其他 Marker");
        }
        // 级别探测没有消息，此时还不能登记提交后动作。
        if (format == null) {
            return FilterReply.NEUTRAL;
        }
        if (!enabled(logger, level)) {
            return FilterReply.DENY;
        }
        defer(logger, level, format, params, throwable, List.of());
        return FilterReply.DENY;
    }

    /**
     * 处理 SLF4J fluent API 产生的提交后日志事件，逻辑与 {@link #decide(Marker, Logger, Level, String, Object[], Throwable)} 相同。
     *
     * @param logger 发起日志调用的 logger
     * @param event SLF4J 日志事件
     * @return 非提交后日志返回 NEUTRAL，已登记或已被级别过滤的提交后日志返回 DENY
     */
    @Override
    public FilterReply decide(Logger logger, LoggingEvent event) {
        List<Marker> markers = event.getMarkers();
        if (markers == null || markers.stream().noneMatch(TransactionLogTurboFilter::containsAfterCommit)) {
            return FilterReply.NEUTRAL;
        }
        if (markers.size() != 1
                || !LogMark.AFTER_COMMIT.equals(markers.getFirst())
                || markers.getFirst().hasReferences()) {
            throw new IllegalArgumentException("提交后日志不能同时使用其他 Marker");
        }
        Level level = Level.convertAnSLF4JLevel(event.getLevel());
        if (!enabled(logger, level)) {
            return FilterReply.DENY;
        }
        defer(
                logger,
                level,
                event.getMessage(),
                arguments(event.getArguments()),
                event.getThrowable(),
                event.getKeyValuePairs());
        return FilterReply.DENY;
    }

    /**
     * 校验提交后日志的级别、消息、异常和参数，复制参数与 MDC，并把输出动作登记到当前事务的提交回调。
     * 校验失败时抛出 IllegalArgumentException 或 NullPointerException，不登记任何输出；提交后写入失败只通过 addError 记录，不影响已提交的数据库结果。
     *
     * @param logger 原始 logger
     * @param level 日志级别，只允许 INFO
     * @param message 日志消息模板，不能为空或空白
     * @param arguments 日志参数，只允许 null 和基本类型包装类、String、BigInteger、BigDecimal、Enum
     * @param throwable 必须为空，成功日志不携带异常
     * @param keyValuePairs 必须为空，成功日志不携带结构化字段
     */
    private void defer(
            Logger logger,
            Level level,
            String message,
            Object[] arguments,
            Throwable throwable,
            List<KeyValuePair> keyValuePairs) {
        if (level != Level.INFO) {
            throw new IllegalArgumentException("LogMark.AFTER_COMMIT 只允许用于 INFO");
        }
        String checkedMessage = Objects.requireNonNull(message, "提交后日志消息不能为空");
        if (checkedMessage.isBlank()) {
            throw new IllegalArgumentException("提交后日志消息不能为空白");
        }
        if (throwable != null || (keyValuePairs != null && !keyValuePairs.isEmpty())) {
            throw new IllegalArgumentException("提交后成功日志不能携带异常或额外结构化字段");
        }
        Object[] snapshot = arguments == null ? new Object[0] : arguments.clone();
        for (Object value : snapshot) {
            if (!stable(value)) {
                throw new IllegalArgumentException("提交后日志参数只接受稳定标量");
            }
        }
        Map<String, String> context = MDC.getCopyOfContextMap();
        TransactionCallbacks.afterCommit(() -> {
            try {
                write(logger, checkedMessage, snapshot, context);
            } catch (RuntimeException exception) {
                addError("事务提交后日志写入失败，数据库已提交", exception);
            }
        });
    }

    /** 在提交线程临时恢复登记时的 MDC，写入后恢复原值。 */
    private static void write(Logger logger, String message, Object[] arguments, Map<String, String> context) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            if (context == null || context.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(context);
            }
            logger.info(message, arguments);
        } finally {
            if (previous == null || previous.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }

    /** @return 当前 logger 是否允许指定级别输出 */
    private static boolean enabled(Logger logger, Level level) {
        return level.levelInt >= logger.getEffectiveLevel().levelInt;
    }

    /** @return Marker 本身或引用中是否包含提交后标记 */
    private static boolean containsAfterCommit(Marker marker) {
        return marker != null
                && (LogMark.AFTER_COMMIT.equals(marker) || marker.contains(LogMark.AFTER_COMMIT));
    }

    /** @return 参数是否可以安全保留到提交时输出 */
    private static boolean stable(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal
                || value instanceof Enum<?>;
    }

    /** 把 fluent 日志参数复制为标准数组。 */
    private static Object[] arguments(List<Object> arguments) {
        return arguments == null || arguments.isEmpty() ? new Object[0] : arguments.toArray();
    }
}
