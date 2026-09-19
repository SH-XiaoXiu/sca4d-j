package com.sca4d.app.server.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 把一次调用及其当前执行主体组合为紧凑的对象式日志上下文。 */
public final class LogContextConverter extends ClassicConverter {

    private static final String CALL_ID = "callId";

    private static final String SUBJECT_ID = "subjectId";

    private static final String ACCOUNT_ID = "accountId";

    private static final String PRINCIPAL_ID = "principalId";

    /** 创建日志上下文转换器。 */
    public LogContextConverter() {
    }

    @Override
    public String convert(ILoggingEvent event) {
        Map<String, String> context = event.getMDCPropertyMap();
        String callId = context.get(CALL_ID);
        return "Call[" + valueOrDash(callId) + "] " + actor(context, callId != null);
    }

    private static String actor(Map<String, String> context, boolean requestCall) {
        List<String> identities = new ArrayList<>(3);
        addIdentity(identities, "Subject", context.get(SUBJECT_ID));
        addIdentity(identities, "Account", context.get(ACCOUNT_ID));
        addIdentity(identities, "Principal", context.get(PRINCIPAL_ID));
        if (identities.isEmpty()) {
            return requestCall ? "Actor[anonymous]" : "Actor[system]";
        }
        return "Actor[" + String.join(" / ", identities) + "]";
    }

    private static void addIdentity(List<String> identities, String type, String id) {
        if (id != null && !id.isBlank()) {
            identities.add(type + "#" + id);
        }
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
