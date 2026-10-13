package com.sca4d.core.spring.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sca4d.core.api.call.*;
import com.sca4d.core.api.i18n.TextLocalizer;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.version.*;
import com.sca4d.core.spring.call.CallLifecycleAdvisor;
import com.sca4d.core.spring.web.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** 验证 HTTP 路由、类型化参数、统一 Call 拒绝和两种版本入口。 */
class VersionHttpContractTest {
    private AnnotationConfigWebApplicationContext context;

    private MockMvc mvc;

    @BeforeAll
    static void constants() {
        com.sca4d.core.api.setting.ConfiguredConstants.initialize(HttpConstants.class, ignored -> null);
        com.sca4d.core.api.setting.ConfiguredConstants
                .initialize(com.sca4d.core.api.gateway.GatewayConstants.class, ignored -> null);
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment()
                .getPropertySources()
                .addFirst(
                        new org.springframework.core.env.MapPropertySource(
                                "version-test",
                                Map.of("sca4dj.versioning.allow-unspecified", "true")));
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new HttpCallFilter(Clock.systemUTC(), context.getBean(CallInvoker.class)))
                .build();
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void samePathAndVersionedPathsUseSameService() throws Exception {
        mvc.perform(
                get("/projects/1").header("X-Client-Name", "app")
                        .header("X-Client-Release-Version", "1.3.0")
                        .header("X-Client-Api-Contract", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.name").value("Example"))
                .andExpect(header().string("X-Compatibility-Status", "deprecated"));
        mvc.perform(get("/v2/projects/1").header("X-Client-Name", "app").header("X-Client-Release-Version", "1.3.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.displayName").value("Example"))
                .andExpect(header().string("X-Api-Contract", "2"));
        assertThat(context.getBean(Projects.class).calls.get()).isEqualTo(2);
        assertThat(org.slf4j.MDC.get("clientBuild")).isNull();
        assertThat(org.slf4j.MDC.get("serverBuild")).isNull();
    }

    @Test
    void rejectsBeforeBusinessAndRecordsDeniedCallWithCallId() throws Exception {
        mvc.perform(
                get("/v2/projects/1").header("X-Client-Name", "app")
                        .header("X-Client-Release-Version", "1.3.0")
                        .header("X-Client-Api-Contract", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("1009"))
                .andExpect(jsonPath("callId").isNotEmpty())
                .andExpect(jsonPath("data.reason").value("contract-conflict"));
        assertThat(context.getBean(Projects.class).calls.get()).isZero();
        assertThat(context.getBean(Outcomes.class).last).isEqualTo(CallOutcome.Status.DENIED);
    }

    @Test
    void malformedHeaderUsesUnifiedErrorAndDiscoveryRemainsAvailable() throws Exception {
        mvc.perform(
                get("/projects/1").header("X-Client-Name", "app")
                        .header("X-Client-Release-Version", "1.3.0")
                        .header("X-Client-Api-Contract", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("1008"));
        mvc.perform(get("/public/version").header("X-Client-Api-Contract", "bad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.releaseVersion").value("1.3.0"));
        assertThat(context.getBean(Projects.class).calls.get()).isZero();
    }

    @Test
    void permitsLegacyUnversionedEndpointButRejectsUnsupportedEventStream() throws Exception {
        mvc.perform(get("/legacy"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Compatibility-Status", "unknown"));
        mvc.perform(
                get("/projects/1").header("X-Client-Name", "app")
                        .header("X-Client-Release-Version", "1.3.0")
                        .header("X-Client-Api-Contract", "3")
                        .accept("text/event-stream"))
                .andExpect(status().isConflict())
                .andExpect(content().string(""));
        assertThat(context.getBean(Outcomes.class).last).isEqualTo(CallOutcome.Status.DENIED);
    }

    /** 测试用实际 MVC 和 AOP 装配。 */
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import(VersionWebConfiguration.class)
    static class Config {
        @Bean
        ApplicationVersion version() {
            return new ApplicationVersion(
                    Map.of(
                            "product",
                            "test",
                            "releaseVersion",
                            "1.3.0",
                            "releaseId",
                            "release",
                            "component",
                            "server",
                            "componentBuildId",
                            "build",
                            "sourceRevision",
                            "sha",
                            "builtAt",
                            "2026-01-01T00:00:00Z",
                            "buildMode",
                            "dev",
                            "buildVersion",
                            "sha-time",
                            "apiContractVersion",
                            "2"));
        }

        @Bean
        CompatibilityResolver compatibility() {
            return new CompatibilityResolver(
                    List.of(
                            new CompatibilityRule(
                                    "app",
                                    "projects",
                                    "1.0.0",
                                    "2.0.0",
                                    1,
                                    CompatibilityStatus.DEPRECATED,
                                    "v1",
                                    "/projects/{id}"),
                            new CompatibilityRule(
                                    "app",
                                    "projects",
                                    "1.0.0",
                                    "2.0.0",
                                    2,
                                    CompatibilityStatus.SUPPORTED,
                                    "v2",
                                    "/v2/projects/{id}")));
        }

        @Bean
        Projects projects() {
            return new Projects();
        }

        @Bean
        ProjectController controller(Projects projects, ApplicationVersion version) {
            return new ProjectController(projects, version);
        }

        @Bean
        GlobalExceptionHandler errors() {
            return new GlobalExceptionHandler(mock(TextLocalizer.class));
        }

        @Bean
        ApiResponseAdvice responses() {
            return new ApiResponseAdvice();
        }

        @Bean
        Outcomes outcomes() {
            return new Outcomes();
        }

        @Bean
        CallInvoker invoker(VersionCallInterceptor version, Outcomes outcomes) {
            return new CallInvoker(List.of(version, outcomes));
        }

        @Bean
        static CallLifecycleAdvisor advisor(CallInvoker invoker, VersionCallBinding binding) {
            return new CallLifecycleAdvisor(invoker, List.of(), List.of(binding), Clock.systemUTC());
        }
    }

    /** 观察统一 Call 的最终状态。 */
    static class Outcomes implements CallLifecycle.Completion {
        CallOutcome.Status last;

        @Override
        public TreeCode code() {
            return TreeCode.of("TEST:OUTCOME");
        }

        @Override
        public void complete(CallExecution execution, CallOutcome outcome) {
            last = outcome.status();
        }
    }

    /** 两种版本入口共用的业务查询。 */
    static class Projects {
        final AtomicInteger calls = new AtomicInteger();

        String load() {
            calls.incrementAndGet();
            return "Example";
        }
    }

    /** 测试用有不同字段的两个 HTTP 契约。 */
    @RestController
    static class ProjectController {
        private final ApplicationVersion version;

        private final Projects projects;

        ProjectController(Projects projects, ApplicationVersion version) {
            this.projects = projects;
            this.version = version;
        }

        @GetMapping("/projects/{id}")
        @ApiCapability("projects")
        public ApiResponse<?> negotiated(CompatibilityDecision decision) {
            String result = projects.load();
            return decision.contract() == 1 ? ApiResponse.ok(new V1(result)) : ApiResponse.ok(new V2(result));
        }

        @GetMapping("/v2/projects/{id}")
        @ApiCapability(value = "projects", contract = 2)
        public ApiResponse<V2> fixed() {
            return ApiResponse.ok(new V2(projects.load()));
        }

        @GetMapping("/legacy")
        public ApiResponse<String> legacy() {
            return ApiResponse.ok("legacy");
        }

        @GetMapping("/public/version")
        @VersionDiscovery
        public ApiResponse<ApplicationVersion> version() {
            return ApiResponse.ok(version);
        }
    }

    /** 契约 1 响应。 */
    static class V1 {
        private final String name;

        V1(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /** 契约 2 响应。 */
    static class V2 {
        private final String displayName;

        V2(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
