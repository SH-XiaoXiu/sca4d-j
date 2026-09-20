package com.sca4d.core.spring.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.concurrent.IdempotencyFingerprints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

class IdempotencyKeyResolverTest {

    private final Call call = Call.system("idempotency-test", Instant.EPOCH);

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("HTTP 解析器校验并去除幂等键首尾空白")
    void resolvesHttpHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpIdempotencyKeyResolver.HEADER, "  create-account-1  ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertEquals(
                "create-account-1",
                new HttpIdempotencyKeyResolver().resolve(execution(new Object[0])).value());
    }

    @Test
    @DisplayName("HTTP 解析器拒绝缺失和含控制字符的幂等键")
    void rejectsInvalidHttpHeader() {
        MockHttpServletRequest missing = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(missing));
        assertThrows(
                IllegalArgumentException.class,
                () -> new HttpIdempotencyKeyResolver().resolve(execution(new Object[0])));

        MockHttpServletRequest invalid = new MockHttpServletRequest();
        invalid.addHeader(HttpIdempotencyKeyResolver.HEADER, "create\u0001account");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(invalid));
        assertThrows(
                IllegalArgumentException.class,
                () -> new HttpIdempotencyKeyResolver().resolve(execution(new Object[0])));
    }

    @Test
    @DisplayName("方法参数解析器只返回规范化参数摘要")
    void resolvesMethodArgumentDigest() {
        IdempotencyFingerprints fingerprints = new IdempotencyFingerprints(new ObjectMapper());
        Object[] arguments = {Map.of("name", "account", "enabled", true)};

        assertEquals(
                fingerprints.argumentsDigest(arguments),
                new MethodArgumentsIdempotencyKeyResolver(fingerprints)
                        .resolve(execution(arguments))
                        .value());
    }

    private CallExecution execution(Object[] arguments) {
        try {
            Method method = getClass().getDeclaredMethod("invoke", Object.class);
            return new CallExecution(call, getClass(), method, arguments, method.getGenericReturnType());
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }

    private void invoke(Object value) {
        Objects.requireNonNull(value);
    }
}
