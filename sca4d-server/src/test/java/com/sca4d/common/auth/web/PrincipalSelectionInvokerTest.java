package com.sca4d.common.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalSelection;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.test.TestSession;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.Map;

/** 验证指定 PrincipalSelection 的查找、调用和异常处理。 */
class PrincipalSelectionInvokerTest {

    private final PrincipalSelectionInvoker invoker = new PrincipalSelectionInvoker(new ObjectMapper());

    @Test
    void shouldConvertMapToTypedParameters() {
        TypedPrincipalSelection selection = new TypedPrincipalSelection();
        CurrentRequest auth = CurrentRequest.authenticated(TestSession.of(1L));
        PrincipalSelectionInput input = new PrincipalSelectionInput(9L, 7L, "scene-a", Map.of("tenantId", 7L, "scene", "scene-a"));

        Principal principal = invoker.select(selection, auth, new PrincipalType[] {PrincipalType.PLATFORM}, input);

        assertThat(principal.getPrincipalId()).isEqualTo(9L);
        assertThat(selection.parameters.getTenantId()).isEqualTo(7L);
        assertThat(selection.parameters.getScene()).isEqualTo("scene-a");
    }

    /** 记录类型化参数并返回固定 Principal 的选择实现。 */
    private static final class TypedPrincipalSelection implements PrincipalSelection<TypedPrincipalParameters> {

        private TypedPrincipalParameters parameters;

        @Override
        public @NonNull Class<TypedPrincipalParameters> parameterType() {
            return TypedPrincipalParameters.class;
        }

        @Override
        public @NonNull Principal select(
                @NonNull CurrentRequest auth,
                PrincipalType[] allowedTypes,
                Long principalId,
                Long tenantId,
                String selectionKey,
                @NonNull TypedPrincipalParameters parameters) {
            this.parameters = parameters;
            return Principal.of(principalId, allowedTypes[0], auth.getSubject().getSubjectId(), tenantId);
        }
    }

    /** PrincipalSelectionInvoker 从选择输入构造的类型化参数。 */
    private static final class TypedPrincipalParameters {

        private Long tenantId;
        private String scene;

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

        public String getScene() {
            return scene;
        }

        public void setScene(String scene) {
            this.scene = scene;
        }
    }
}
