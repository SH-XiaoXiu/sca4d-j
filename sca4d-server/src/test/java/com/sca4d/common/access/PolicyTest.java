package com.sca4d.common.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cedarpolicy.model.Context;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.PrimLong;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 验证类型安全 Rule 和独立 Cedar 文件产生相同的失败关闭判断。 */
class PolicyTest {

    @Test
    void shouldApplyDefaultDenyAndForbidBeforePermit() {
        Policy<TestResource> policy = Rules.policy(
                "rule-policy-test",
                TestResource.class,
                "/cedar/rule-policy-schema.cedar",
                List.of(Rules.<TestResource>principal(PrincipalType.DEFAULT)),
                List.of(Rules.attribute("status", TestResource::getStatus, "BLOCKED")));
        CurrentRequest defaultRequest = request(PrincipalType.DEFAULT, 10L);

        assertThat(policy.allows(
                defaultRequest, AccessAction.READ, new TestResource("ACTIVE")))
                .isTrue();
        assertThat(policy.allows(
                defaultRequest, AccessAction.READ, new TestResource("BLOCKED")))
                .isFalse();
        assertThat(policy.allows(
                request(PrincipalType.SYSTEM, 1L),
                AccessAction.READ,
                new TestResource("ACTIVE")))
                .isFalse();
    }

    @Test
    void shouldLoadAdvancedPolicyFromClasspathResources() {
        Policy<AdvancedResource> policy = Policy.cedar(new AdvancedPolicy());
        CurrentRequest request = request(PrincipalType.DEFAULT, 10L);

        assertThat(policy.allows(request, AccessAction.WRITE, new AdvancedResource(10L)))
                .isTrue();
        assertThat(policy.allows(request, AccessAction.WRITE, new AdvancedResource(11L)))
                .isFalse();
    }

    @Test
    void shouldRejectDslPolicyThatDriftsFromSchema() {
        assertThatThrownBy(() -> Rules.policy(
                "schema-drift-test",
                TestResource.class,
                "/cedar/rule-policy-schema.cedar",
                List.of(Rules.attribute("unknownStatus", TestResource::getStatus, "ACTIVE")),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cedar Policy 未通过 Schema 校验");
    }

    @Test
    void shouldRejectMissingSchemaResourceBeforeAuthorization() {
        assertThatThrownBy(() -> Rules.policy(
                "missing-schema-test",
                TestResource.class,
                "/cedar/missing-schema.cedar",
                List.of(Rules.<TestResource>principal(PrincipalType.DEFAULT)),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cedar Schema 资源或 DSL Policy 无效");
    }

    @Test
    void shouldEvaluateResourceTimeAgainstRequestStart() {
        Policy<TimedResource> policy = Rules.policy(
                "time-policy-test",
                TimedResource.class,
                "/cedar/time-policy-schema.cedar",
                List.of(Rules.all(
                        Rules.<TimedResource>principal(PrincipalType.DEFAULT),
                        Rules.time(TimedResource::getActiveFrom, TimedResource::getActiveUntil))),
                List.of());
        OffsetDateTime requestTime = OffsetDateTime.of(
                2026, 7, 15, 12, 0, 0, 0, ZoneOffset.UTC);
        CurrentRequest request = request(PrincipalType.DEFAULT, 10L);
        request.setStartedAt(requestTime);

        assertThat(policy.allows(
                request,
                AccessAction.READ,
                new TimedResource(requestTime.minusMinutes(1), requestTime.plusMinutes(1))))
                .isTrue();
        assertThat(policy.allows(
                request,
                AccessAction.READ,
                new TimedResource(requestTime.minusMinutes(2), requestTime.minusMinutes(1))))
                .isFalse();
    }

    /**
     * 创建只包含 Cedar 判断所需 Principal 的请求。
     *
     * @param type 当前测试 Principal 类型
     * @param principalId 当前测试 Principal 主键
     * @return 已选择 Principal 的请求
     */
    private @NotNull CurrentRequest request(
            @NotNull PrincipalType type, @NotNull Long principalId) {
        CurrentRequest request = new CurrentRequest();
        request.setPrincipal(Principal.of(principalId, type, principalId, null));
        return request;
    }

    /** 类型安全 Rule 测试使用的资源。 */
    private static final class TestResource {

        private final String status;

        /** @param status Rule 读取的资源状态 */
        private TestResource(@NotNull String status) {
            this.status = status;
        }

        /** @return Rule 读取的资源状态 */
        private @NotNull String getStatus() {
            return status;
        }
    }

    /** 独立 Cedar 文件测试使用的资源。 */
    private static final class AdvancedResource {

        private final long ownerPrincipalId;

        /** @param ownerPrincipalId 资源所有者 Principal 主键 */
        private AdvancedResource(long ownerPrincipalId) {
            this.ownerPrincipalId = ownerPrincipalId;
        }

        /** @return 资源所有者 Principal 主键 */
        private long getOwnerPrincipalId() {
            return ownerPrincipalId;
        }
    }

    /** Rules.time 测试使用的有效期资源。 */
    private static final class TimedResource {

        private final OffsetDateTime activeFrom;
        private final OffsetDateTime activeUntil;

        /**
         * @param activeFrom 资源生效时间
         * @param activeUntil 资源失效时间
         */
        private TimedResource(
                @NotNull OffsetDateTime activeFrom,
                @NotNull OffsetDateTime activeUntil) {
            this.activeFrom = activeFrom;
            this.activeUntil = activeUntil;
        }

        /** @return 资源生效时间 */
        private @NotNull OffsetDateTime getActiveFrom() {
            return activeFrom;
        }

        /** @return 资源失效时间 */
        private @NotNull OffsetDateTime getActiveUntil() {
            return activeUntil;
        }
    }

    /** 把测试资源转换为独立 Cedar Schema 声明的 Entity。 */
    private static final class AdvancedPolicy implements CedarPolicy<AdvancedResource> {

        private static final EntityTypeName PRINCIPAL = EntityTypeName.parse("Principal").orElseThrow();
        private static final EntityTypeName RESOURCE = EntityTypeName.parse("AdvancedResource").orElseThrow();

        @Override
        public @NotNull Class<AdvancedResource> resourceType() {
            return AdvancedResource.class;
        }

        @Override
        public @NotNull String schemaResource() {
            return "/cedar/advanced-schema.cedar";
        }

        @Override
        public @NotNull String policyResource() {
            return "/cedar/advanced-policy.cedar";
        }

        @Override
        public @NotNull CedarRequest request(
                @NotNull CurrentRequest request,
                @NotNull AccessAction action,
                @NotNull AdvancedResource resource) {
            long principalId = request.requirePrincipal().requirePrincipalId();
            Entity principal = new Entity(
                    PRINCIPAL.of(Long.toString(principalId)),
                    Map.of("principalId", new PrimLong(principalId)),
                    Set.of());
            Entity cedarResource = new Entity(
                    RESOURCE.of("resource"),
                    Map.of("ownerPrincipalId", new PrimLong(resource.getOwnerPrincipalId())),
                    Set.of());
            return new CedarRequest(principal, cedarResource, new Context(), Set.of());
        }
    }
}
