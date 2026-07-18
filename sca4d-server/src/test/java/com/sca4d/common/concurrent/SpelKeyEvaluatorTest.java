package com.sca4d.common.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.common.web.ApiResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

/** 验证 SpEL 锁键和限流键的参数解析与空值处理。 */
class SpelKeyEvaluatorTest {

    @Test
    void shouldKeepLiteralTextAndEvaluateImplementationMethodParameters() throws Exception {
        Method method = DemoService.class.getDeclaredMethod("update", Long.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[] {"subjectId"});
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new DemoService());
        when(joinPoint.getArgs()).thenReturn(new Object[] {7L});
        SpelKeyEvaluator evaluator = new SpelKeyEvaluator();

        assertThat(evaluator.evaluate("subject.update", joinPoint)).isEqualTo("subject.update");
        assertThat(evaluator.evaluate("'subject:' + #subjectId", joinPoint)).isEqualTo("subject:7");
        assertThat(evaluator.evaluate("null", joinPoint)).isEqualTo("null");
        assertThat(evaluator.evaluate("#missing", joinPoint)).isEmpty();
    }

    @Test
    void shouldExposeResultErrorArgsMethodAndTargetAfterBusinessCompletion() throws Exception {
        Method method = DemoService.class.getDeclaredMethod("update", Long.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[] {"subjectId"});
        DemoService target = new DemoService();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(new Object[] {7L});
        IllegalStateException error = new IllegalStateException("failed");
        SpelKeyEvaluator evaluator = new SpelKeyEvaluator();

        assertThat(evaluator.evaluateObject("#result.data", joinPoint, ApiResponse.ok(9L), null))
                .isEqualTo(9L);
        assertThat(evaluator.evaluateObject("#error", joinPoint, null, error)).isSameAs(error);
        assertThat(evaluator.evaluateObject("#args[0]", joinPoint, null, error)).isEqualTo(7L);
        assertThat(evaluator.evaluateObject("#methodName", joinPoint, null, error)).isEqualTo("update");
        assertThat(evaluator.evaluateObject("#target", joinPoint, null, error)).isSameAs(target);
    }

    /** 为 SpEL 参数名和属性读取测试提供方法签名。 */
    private static final class DemoService {

        void update(Long subjectId) {
        }
    }
}
