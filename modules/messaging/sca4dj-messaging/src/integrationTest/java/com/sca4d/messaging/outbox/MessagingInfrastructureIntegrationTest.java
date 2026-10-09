package com.sca4d.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.concurrent.OptimisticLockException;
import com.sca4d.core.api.concurrent.VersionCondition;
import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.messaging.MessageConsumer;
import com.sca4d.core.api.messaging.MessageDelivery;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.scheduling.JobSchedule;
import com.sca4d.core.spring.config.CoreJacksonAutoConfiguration;
import com.sca4d.core.spring.config.CoreSpringAutoConfiguration;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

/** 使用 PostgreSQL 和 RabbitMQ 验证业务消息发布、消费、DLQ 与 Outbox。 */
@Testcontainers
@SpringBootTest(classes = MessagingInfrastructureIntegrationTest.TestApplication.class, properties = {
        "spring.application.name=messaging-test",
        "spring.autoconfigure.exclude="
                + "com.sca4d.core.spring.config.CoreSpringAutoConfiguration,"
                + "com.sca4d.core.spring.config.CoreJacksonAutoConfiguration,"
                + "com.sca4d.core.spring.gateway.GatewaySpringAutoConfiguration,"
                + "com.sca4d.core.spring.openapi.CoreOpenApiConfiguration",
        "sca4dj.messaging.dispatch-interval=PT1H",
        "sca4dj.messaging.cleanup-interval=PT1H"
})
@SuppressWarnings({
        "rawtypes",
        "SqlNoDataSourceInspection",
        "SpringJavaInjectionPointsAutowiringInspection"
})
class MessagingInfrastructureIntegrationTest {

    private static final String COPY_QUEUE = "messaging-test:sca4dj:messaging:consumer:invoice-copy";

    private static final String FAILURE_QUEUE = "messaging-test:sca4dj:messaging:consumer:invoice-failure";

    private static final String SUCCESS_QUEUE = "messaging-test:sca4dj:messaging:consumer:invoice-primary";

    private static final TreeCode EVENT_CODE = TreeCode.of("INVOICE/CREATED");

    private static final EventType<Map> EVENT_TYPE = new EventType<>(EVENT_CODE, 1, Map.class);

    private static final String FAILURE_DLQ = FAILURE_QUEUE + ":dead-letter";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    private static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4-alpine")
            .withAdminUser("sca4dj")
            .withAdminPassword("sca4dj");

    @Autowired
    private MessageBus publisher;

    @Autowired
    private MessageOutboxDispatcher dispatcher;

    @Autowired
    private MessageOutboxRepository outbox;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private PrimaryConsumer primaryConsumer;

    @Autowired
    private CopyConsumer copyConsumer;

    @Autowired
    private FailureConsumer failureConsumer;

    @Autowired
    private ApplicationContext applicationContext;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.application.name", () -> "messaging-test");
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.dynamic.primary", () -> "master");
        registry.add("spring.datasource.dynamic.datasource.master.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.dynamic.datasource.master.username", POSTGRES::getUsername);
        registry.add("spring.datasource.dynamic.datasource.master.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "sca4dj");
        registry.add("spring.rabbitmq.password", () -> "sca4dj");
    }

    @BeforeEach
    void resetState() {
        Boolean schemaExists = jdbc.queryForObject(
                "SELECT to_regclass('public.messaging_outbox') IS NOT NULL",
                Boolean.class);
        if (!Boolean.TRUE.equals(schemaExists)) {
            DataSource dataSource = Objects.requireNonNull(jdbc.getDataSource());
            ResourceDatabasePopulator coreSchema = new ResourceDatabasePopulator(
                    new ClassPathResource("META-INF/sca4dj/schema/dictionary-settings.sql"));
            coreSchema.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
            coreSchema.execute(dataSource);
            new ResourceDatabasePopulator(
                    new ClassPathResource("META-INF/sca4dj/schema/messaging.sql"))
                    .execute(dataSource);
        }
        jdbc.execute("TRUNCATE TABLE messaging_outbox");

        primaryConsumer.reset();
        copyConsumer.reset();
        failureConsumer.reset();
        for (String queue : List.of(SUCCESS_QUEUE, COPY_QUEUE, FAILURE_QUEUE, FAILURE_DLQ)) {
            if (rabbitAdmin.getQueueProperties(queue) != null) {
                rabbitAdmin.purgeQueue(queue, false);
            }
        }
    }

    /** Messaging 登记不保留 Run 的派发与清理 Job 计划。 */
    @Test
    void registersOutboxJobsWithoutJobRuntime() {
        assertThat(applicationContext.getBeansOfType(JobSchedule.class).values())
                .extracting(schedule -> schedule.key().format(NameFormat.SLASH))
                .containsExactlyInAnyOrder("MESSAGING/OUTBOX/DISPATCH", "MESSAGING/OUTBOX/CLEANUP");
    }

    @Test
    @DisplayName("Publisher Confirm Requires A Route And Each Consumer Receives A Copy")
    void publisherConfirmRequiresRouteAndEachConsumerReceivesCopy() throws InterruptedException {
        assertThat(rabbit.isMandatoryFor(MessageBuilder.withBody(new byte[0]).build())).isFalse();

        Id messageId = publisher.dispatch(message(), DispatchPolicy.immediate()).messageId();

        assertThat(primaryConsumer.receivedMessage()).isEqualTo(messageId);
        assertThat(copyConsumer.receivedMessage()).isEqualTo(messageId);
        assertThat(failureConsumer.receivedMessage()).isEqualTo(messageId);

        Message deadLetter = rabbit.receive(FAILURE_DLQ, 5_000);
        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.getMessageProperties().getReceivedDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(deadLetter.getMessageProperties().getMessageId())
                .isEqualTo(messageId.toString());
        assertThat(rabbit.receive(FAILURE_QUEUE, 500)).isNull();
        assertThat(failureConsumer.attempts).hasValue(1);

        assertThatThrownBy(() -> {
            Id unexpected = publisher.dispatch(
                    new Event<>(
                            new EventType<>(TreeCode.of("INVOICE/UNROUTED"), 1, Map.class),
                            "invoice:42",
                            Instant.now(),
                            Map.of("invoiceId", 42)),
                    DispatchPolicy.immediate()).messageId();
            assertThat(unexpected).isNotNull();
        })
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有匹配消息路由");
    }

    @Test
    @DisplayName("AMQP Message Id Must Match The Envelope")
    void amqpMessageIdMustMatchEnvelope() throws InterruptedException {
        Id envelopeMessageId = Objects.requireNonNull(
                transactions.execute(
                        status -> publisher.dispatch(message(), DispatchPolicy.outbox()).messageId()));
        byte[] envelope = jdbc.queryForObject(
                "SELECT envelope FROM messaging_outbox WHERE message_id = ?",
                byte[].class,
                envelopeMessageId.value());
        rabbit.send(
                FAILURE_QUEUE,
                MessageBuilder.withBody(envelope)
                        .setMessageId(Long.toString(envelopeMessageId.value() + 1))
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                        .build());

        Message deadLetter = rabbit.receive(FAILURE_DLQ, 5_000);
        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.getMessageProperties().getMessageId())
                .isEqualTo(Long.toString(envelopeMessageId.value() + 1));
        assertThat(failureConsumer.receivedMessage()).isNull();
        assertThat(failureConsumer.attempts).hasValue(0);
    }

    @Test
    @DisplayName("Outbox Follows Transaction Delay Lease And Retention Rules")
    void outboxFollowsTransactionDelayLeaseAndRetentionRules() {
        Id committed = transactions.execute(
                status -> publisher.dispatch(message(), DispatchPolicy.outbox()).messageId());
        assertThat(committed).isNotNull();
        assertThat(status(committed)).isEqualTo(OutboxStatus.PENDING);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            Id rolledBack = publisher.dispatch(message(), DispatchPolicy.outbox()).messageId();
            assertThat(rolledBack).isNotNull();
            throw new IllegalStateException("rollback");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("rollback");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messaging_outbox", Integer.class))
                .isEqualTo(1);

        dispatcher.dispatch();
        assertThat(status(committed)).isEqualTo(OutboxStatus.SENT);

        Instant delayedUntil = Instant.now().plus(Duration.ofHours(1));
        Id delayed = transactions.execute(
                status -> publisher.dispatch(message(), DispatchPolicy.outboxNotBefore(delayedUntil)).messageId());
        assertThat(delayed).isNotNull();
        dispatcher.dispatch();
        assertThat(status(delayed)).isEqualTo(OutboxStatus.PENDING);

        Id takeover = transactions.execute(
                status -> publisher.dispatch(message(), DispatchPolicy.outbox()).messageId());
        assertThat(takeover).isNotNull();
        transactions.executeWithoutResult(
                status -> assertThat(
                        outbox.claim(
                                Instant.now(),
                                1,
                                "abandoned-lease",
                                Instant.now().plusSeconds(60)))
                        .extracting(OutboxClaim::messageId)
                        .containsExactly(takeover));
        jdbc.update(
                "UPDATE messaging_outbox SET lease_until = ? WHERE message_id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)),
                takeover.value());
        dispatcher.dispatch();
        assertThat(status(takeover)).isEqualTo(OutboxStatus.SENT);
        assertThat(
                jdbc.queryForObject(
                        "SELECT attempt_count FROM messaging_outbox WHERE message_id = ?",
                        Integer.class,
                        takeover.value()))
                .isEqualTo(2);

        jdbc.update(
                "UPDATE messaging_outbox SET sent_at = ? WHERE message_id IN (?, ?)",
                Timestamp.from(Instant.now().minus(Duration.ofDays(8))),
                committed.value(),
                takeover.value());
        dispatcher.cleanup();
        assertThat(
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM messaging_outbox WHERE message_id IN (?, ?)",
                        Integer.class,
                        committed.value(),
                        takeover.value()))
                .isZero();
        assertThat(status(delayed)).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("Repository Affected Rows Uses The MyBatis Update Count")
    void repositoryAffectedRowsUsesTheMyBatisUpdateCount() {
        OutboxClaim missing = new OutboxClaim(
                Id.of(Long.MAX_VALUE),
                EVENT_CODE,
                new byte[0],
                1,
                "missing-lease",
                0);

        assertThatThrownBy(() -> outbox.saveProgress(missing.sent(Instant.now())))
                .isInstanceOfSatisfying(OptimisticLockException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CoreErrorCode.CONFLICT);
                    assertThat(exception.messageArguments()).containsExactly("Outbox 消息派发结果");
                });

        Id id = transactions.execute(
                status -> publisher.dispatch(message(), DispatchPolicy.outbox()).messageId());
        Instant now = Instant.now().plusSeconds(1);
        OutboxClaim first = transactions
                .execute(status -> outbox.claim(now, 1, "first", now.plusSeconds(10)).getFirst());
        transactions.executeWithoutResult(status -> outbox.saveProgress(first.failed(now, "test failure")));
        assertThat(status(id)).isEqualTo(OutboxStatus.FAILED);
        OutboxClaim next = transactions.execute(status -> outbox.claim(now, 1, "next", now.plusSeconds(10)).getFirst());
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> outbox.saveProgress(first.sent(now))))
                .isInstanceOf(OptimisticLockException.class);
        transactions.executeWithoutResult(status -> outbox.saveProgress(next.sent(now)));
        assertThat(status(id)).isEqualTo(OutboxStatus.SENT);
        assertThat(
                jdbc.queryForObject(
                        "SELECT last_error IS NULL AND lease_id IS NULL FROM messaging_outbox WHERE message_id = ?",
                        Boolean.class,
                        id.value()))
                .isTrue();
    }

    /** 读取并校验数据库中的 Outbox 状态名称。 */
    private OutboxStatus status(Id messageId) {
        return OutboxStatus.valueOf(
                jdbc.queryForObject(
                        "SELECT status FROM messaging_outbox WHERE message_id = ?",
                        String.class,
                        messageId.value()));
    }

    private static Event<Map> message() {
        return new Event<>(
                EVENT_TYPE,
                "invoice:42",
                Instant.now(),
                Map.of("invoiceId", 42));
    }

    /** 保存成功 Consumer 收到的 Id。 */
    private abstract static class SuccessfulConsumer implements MessageConsumer<Map> {
        private final BlockingQueue<Id> messages = new LinkedBlockingQueue<>();

        @Override
        public @NotNull EventType<Map> eventType() {
            return EVENT_TYPE;
        }

        @Override
        public void consume(@NotNull MessageDelivery<Map> delivery) {
            messages.add(delivery.messageId());
        }

        Id receivedMessage() throws InterruptedException {
            return messages.poll(5, TimeUnit.SECONDS);
        }

        void reset() {
            messages.clear();
        }
    }

    /** 消费 invoice.created 的主 Consumer。 */
    private static class PrimaryConsumer extends SuccessfulConsumer {
        @Override
        public @NotNull String consumerCode() {
            return "invoice-primary";
        }
    }

    /** 独立消费同一事件并验证 RabbitMQ 为不同 Consumer 各保留一份消息。 */
    private static class CopyConsumer extends SuccessfulConsumer {
        @Override
        public @NotNull String consumerCode() {
            return "invoice-copy";
        }
    }

    /** 每次都失败，用于验证消息直接进入当前 Consumer 的 DLQ。 */
    private static class FailureConsumer implements MessageConsumer<Map> {
        private final BlockingQueue<Id> messages = new LinkedBlockingQueue<>();

        private final AtomicInteger attempts = new AtomicInteger();

        @Override
        public @NotNull String consumerCode() {
            return "invoice-failure";
        }

        @Override
        public @NotNull EventType<Map> eventType() {
            return EVENT_TYPE;
        }

        @Override
        public void consume(@NotNull MessageDelivery<Map> delivery) {
            messages.add(delivery.messageId());
            attempts.incrementAndGet();
            throw new IllegalStateException("consumer failure");
        }

        private Id receivedMessage() throws InterruptedException {
            return messages.poll(5, TimeUnit.SECONDS);
        }

        private void reset() {
            messages.clear();
            attempts.set(0);
        }
    }

    /** 为 Messaging 自动配置提供测试依赖和三个 Consumer。 */
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            CoreSpringAutoConfiguration.class,
            CoreJacksonAutoConfiguration.class
    })
    static class TestApplication {

        /** 非 HTTP 测试使用 Repository 当前版本作为乐观锁条件。 */
        @Bean
        VersionConditions versionConditions() {
            return VersionCondition::optimistic;
        }

        @Bean
        @Sca4djJson
        Sca4djMapper sca4djObjectMapper() {
            return new Sca4djMapper();
        }

        @Bean
        SqlBatches sqlBatches() {
            return new SqlBatches(new SqlBatchProperties());
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        IdGenerator idGenerator() {
            AtomicLong ids = new AtomicLong(10_000);
            return () -> Id.of(ids.incrementAndGet());
        }

        @Bean
        PrimaryConsumer primaryConsumer() {
            return new PrimaryConsumer();
        }

        @Bean
        CopyConsumer copyConsumer() {
            return new CopyConsumer();
        }

        @Bean
        FailureConsumer failureConsumer() {
            return new FailureConsumer();
        }
    }
}
