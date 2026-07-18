package com.sca4d.common.boot;

import cn.hutool.core.util.URLUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 应用启动环境、依赖检查与访问入口日志。 */
@Component
@Slf4j
public class BootUtils implements ApplicationRunner {

    private final Environment environment;
    private final List<BootCheck> checks;
    private static volatile BootEnvironment currentEnvironment;
    private static volatile Map<String, BootCheckResult> latestCheckResults = Map.of();

    /**
     * 创建应用启动检查协调器，并按检查名称固化固定执行顺序。
     *
     * @param environment 当前 Spring 运行环境
     * @param checks 当前应用提供的全部启动依赖检查
     */
    public BootUtils(@NotNull Environment environment, @NotNull List<BootCheck> checks) {
        this.environment = environment;
        this.checks = checks.stream().sorted(Comparator.comparing(BootCheck::name)).toList();
        currentEnvironment = resolveEnvironment(environment);
    }

    /**
     * 获取当前运行环境。
     *
     * @return 当前环境
     */
    public static @NotNull BootEnvironment getEnv() {
        if (currentEnvironment == null) {
            throw new IllegalStateException("BootUtils 尚未初始化");
        }
        return currentEnvironment;
    }

    /**
     * 判断当前是否为预发布或生产环境。
     *
     * @return 预发布或生产环境时返回 {@code true}
     */
    public static boolean isProductionLike() {
        return getEnv().isProductionLike();
    }

    /**
     * 在容器就绪前执行全部启用的启动检查。
     *
     * <p>检查结果只进行一次内存遍历，用于收集关键失败并记录非关键失败，不产生额外依赖调用。
     *
     * @param args 启动参数
     */
    @Override
    public void run(@NotNull ApplicationArguments args) {
        Map<String, BootCheckResult> results = executeChecks();
        latestCheckResults = Map.copyOf(results);
        List<String> requiredFailures = new ArrayList<>();
        for (BootCheck check : checks) {
            BootCheckPolicy policy = check.policy();
            if (!policy.isEnabled()) {
                continue;
            }
            BootCheckResult result = results.get(check.name());
            if (result.isAvailable()) {
                continue;
            }
            if (policy.getCriticality() == DependencyCriticality.REQUIRED && isProductionLike()) {
                requiredFailures.add(check.name());
            } else {
                log.warn(
                        "启动检查未通过，当前环境继续启动 | check={} criticality={} environment={}",
                        check.name(),
                        policy.getCriticality(),
                        getEnv());
            }
        }
        if (!requiredFailures.isEmpty()) {
            throw new IllegalStateException("关键启动检查失败: " + String.join(",", requiredFailures));
        }
    }

    /**
     * 执行当前检查并返回供运行时健康端点使用的结果。
     *
     * @return 检查名称到结果的映射
     */
    public @NotNull Map<String, BootCheckResult> healthResults() {
        Map<String, BootCheckResult> results = executeChecks();
        latestCheckResults = Map.copyOf(results);
        return results;
    }

    /**
     * 获取最新一次启动或健康检查结果。
     *
     * @return 检查名称到结果的映射
     */
    public static @NotNull Map<String, BootCheckResult> latestResults() {
        return latestCheckResults;
    }

    /**
     * 在 Spring 容器就绪后输出本地访问入口与启动检查摘要。
     *
     * @param event 容器就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logApplicationStartup(@NotNull ApplicationReadyEvent event) {
        String applicationName = environment.getProperty("spring.application.name", "sca4d-j");
        String baseUrl = resolveBaseUrl();
        boolean apiDocsEnabled = environment.getProperty("springdoc.api-docs.enabled", Boolean.class, true);
        boolean swaggerUiEnabled = environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, true);
        String apiDocsPath = environment.getProperty("springdoc.api-docs.path", "/v3/api-docs");

        log.info(
                """

                        {} started successfully
                        ----------------------------------------------------------
                        Environment : {}
                        Service     : {}
                        Swagger UI  : {}
                        OpenAPI     : {}
                        Checks      : {}
                        ----------------------------------------------------------""",
                applicationName,
                getEnv(),
                baseUrl,
                swaggerUiEnabled ? baseUrl + "/swagger-ui/index.html" : "disabled",
                apiDocsEnabled ? baseUrl + (apiDocsPath.startsWith("/") ? apiDocsPath : "/" + apiDocsPath) : "disabled",
                latestCheckResults.isEmpty() ? "none" : summarizeChecks());
    }

    /**
     * 顺序执行全部启用的依赖检查并生成不可变结果快照。
     *
     * <p>启用 N 个检查时创建 N 个只执行本次检查的虚拟线程；每个检查独立使用自己的超时时间。
     *
     * @return 检查名称到检查结果的不可变映射
     */
    private @NotNull Map<String, BootCheckResult> executeChecks() {
        Map<String, BootCheckResult> results = new LinkedHashMap<>();
        BootEnvironment bootEnvironment = getEnv();
        for (BootCheck check : checks) {
            BootCheckPolicy policy = check.policy();
            if (policy.isEnabled()) {
                results.put(check.name(), executeCheck(check, bootEnvironment, policy.getTimeout()));
            }
        }
        return Map.copyOf(results);
    }

    /**
     * 在独立虚拟线程中执行一次依赖检查，并把超时、中断和执行异常转换为不可用结果。
     *
     * @param check 当前依赖检查实现
     * @param bootEnvironment 当前运行环境类型
     * @param timeout 当前检查允许的最长等待时间
     * @return 检查结果；实现返回 {@code null} 或执行失败时返回不可用结果
     */
    private @NotNull BootCheckResult executeCheck(
            @NotNull BootCheck check, @NotNull BootEnvironment bootEnvironment, @NotNull Duration timeout) {
        FutureTask<BootCheckResult> task = new FutureTask<>(() -> check.check(bootEnvironment));
        Thread.ofVirtual().name("boot-check-" + check.name()).start(task);
        try {
            BootCheckResult result = task.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            return result == null ? BootCheckResult.unavailable("检查未返回结果") : result;
        } catch (TimeoutException exception) {
            return BootCheckResult.unavailable("检查超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return BootCheckResult.unavailable("检查被中断");
        } catch (ExecutionException exception) {
            return BootCheckResult.unavailable("检查执行异常: " + exception.getCause().getClass().getSimpleName());
        } finally {
            task.cancel(true);
        }
    }

    /**
     * 将最新检查快照转换为按名称排序的单行状态摘要。
     *
     * @return 例如 {@code minio=UP, redis=UP} 的启动日志文本
     */
    private @NotNull String summarizeChecks() {
        return latestCheckResults.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + (entry.getValue().isAvailable() ? "UP" : "DOWN"))
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }

    /**
     * 根据实际监听端口和 Servlet context path 构造本机访问基址。
     *
     * @return 不以斜杠结尾的 HTTP 本机地址
     */
    private @NotNull String resolveBaseUrl() {
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String baseUrl = "http://localhost:" + port;
        if (contextPath.isBlank() || "/".equals(contextPath)) {
            return baseUrl;
        }
        return URLUtil.normalize(baseUrl + "/" + contextPath).replaceFirst("/$", "");
    }

    /**
     * 从 Spring active profiles 或 default profiles 解析项目运行环境。
     *
     * @param environment 当前 Spring 环境
     * @return 与当前 profile 集合匹配的环境类型
     */
    private static @NotNull BootEnvironment resolveEnvironment(@NotNull Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        return BootEnvironment.resolve(activeProfiles.length == 0 ? environment.getDefaultProfiles() : activeProfiles);
    }
}
