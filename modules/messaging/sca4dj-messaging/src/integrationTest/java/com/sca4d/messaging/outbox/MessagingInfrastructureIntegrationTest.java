package com.sca4d.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.messaging.EventHandler;
import com.sca4d.core.api.messaging.HandlingDecision;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.retry.Backoff;
import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.persistence.mybatis.CoreEnumTypeHandler;
import com.sca4d.core.spring.persistence.mybatis.StringValueTypeHandler;
import com.sca4d.core.spring.persistence.mybatis.TreeCodeTypeHandler;
import com.sca4d.core.spring.retry.RetryRuleCodec;
import com.sca4d.messaging.config.MessagingProperties;
import com.sca4d.messaging.outbox.mapper.OutboxMapper;
import com.sca4d.messaging.rabbit.RabbitHandlerContainers;
import com.sca4d.messaging.rabbit.RabbitMessageBus;
import com.sca4d.messaging.rabbit.RabbitMessageEnvelopeCodec;
import com.sca4d.messaging.rabbit.RabbitMessageTopology;
import com.sca4d.messaging.rabbit.RabbitPublisher;
import org.apache.ibatis.session.Configuration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javax.sql.DataSource;

/** 使用真实 PostgreSQL 和 RabbitMQ 验证定向重试交接、恢复、原消息身份和发布次数隔离。 */
@Testcontainers
class MessagingInfrastructureIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-alpine")
            .withAdminUser("retry")
            .withAdminPassword("retry-test");

    private static final EventType<String> TYPE = new EventType<>("TEST/RETRY/EVENT", 1, String.class);

    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    static {
        AppName.publish("RETRY_TEST");
    }

    private final AtomicLong sequence = new AtomicLong(100);

    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);

    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    private JdbcTemplate jdbc;

    private final Sca4djMapper mapper = new Sca4djMapper();

    private final RetryRuleCodec retryRules = new RetryRuleCodec(mapper);

    private FaultTransactions manager;

    private TransactionTemplate transactions;

    private CachingConnectionFactory connection;

    private RabbitAdmin admin;

    private RabbitTemplate rabbit;

    private RabbitMessageTopology topology;

    private RabbitMessageEnvelopeCodec codec;

    private MessageOutboxRepository outbox;

    private MessageOutboxDispatcher dispatcher;

    private RabbitMessageBus bus;

    private RabbitHandlerContainers consumers;

    private TestHandler primary;

    private TestHandler copy;

    private final IdGenerator ids = () -> Id.of(sequence.incrementAndGet());

    @SuppressWarnings("resource") // SqlSessionTemplate 按调用管理会话，不允许手动关闭。
    @BeforeEach
    void infrastructure() throws Exception {
        DataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP TABLE IF EXISTS messaging_outbox");
        jdbc.execute(
                "CREATE OR REPLACE FUNCTION sca4dj_set_updated_at() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END; $$");
        new ResourceDatabasePopulator(new ClassPathResource("META-INF/sca4dj/schema/messaging.sql")).execute(source);
        manager = new FaultTransactions(source);
        manager.setRollbackOnCommitFailure(true);
        transactions = new TransactionTemplate(manager);
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setArgNameBasedConstructorAutoMapping(true);
        configuration.setDefaultEnumTypeHandler(CoreEnumTypeHandler.class);
        configuration.getTypeHandlerRegistry()
                .register(RetryRule.class, new StringValueTypeHandler<>(retryRules::decode, retryRules::encode));
        configuration.getTypeHandlerRegistry().register(TreeCode.class, new TreeCodeTypeHandler.Colon());
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/messaging/OutboxMapper.xml"));
        factory.afterPropertiesSet();
        outbox = new MessageOutboxRepository(
                new SqlSessionTemplate(Objects.requireNonNull(factory.getObject())).getMapper(OutboxMapper.class));
        connection = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connection.setUsername("retry");
        connection.setPassword("retry-test");
        connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connection.setPublisherReturns(true);
        rabbit = new RabbitTemplate(connection);
        admin = new RabbitAdmin(connection);
        topology = new RabbitMessageTopology();
        primary = new TestHandler(
                "TEST/PRIMARY",
                RetryRule.atMost(
                        3,
                        Backoff.exponential(Duration.ofSeconds(1), 2, Duration.ofSeconds(10))));
        primary.action = incoming -> incoming.attempt() < 3
                ? HandlingDecision.retry("TEMPORARY")
                : HandlingDecision.acknowledge();
        copy = new TestHandler("TEST/COPY", RetryRule.once());
        for (Declarable declaration : topology.declarables(List.of(primary, copy), List.of()).getDeclarables()) {
            if (declaration instanceof Exchange exchange)
                admin.declareExchange(exchange);
            if (declaration instanceof Queue queue) {
                admin.declareQueue(queue);
                admin.purgeQueue(queue.getName());
            }
            if (declaration instanceof Binding binding)
                admin.declareBinding(binding);
        }
        MessagingProperties properties = new MessagingProperties();
        codec = new RabbitMessageEnvelopeCodec(mapper, List.of());
        RabbitPublisher sender = new RabbitPublisher(rabbit, topology, properties, retryRules);
        dispatcher = new MessageOutboxDispatcher(outbox, sender, transactions, properties, clock);
        bus = new RabbitMessageBus(ids, codec, sender, outbox, clock);
        startConsumers();
    }

    private void startConsumers() {
        consumers = new RabbitHandlerContainers(
                connection,
                topology,
                codec,
                outbox,
                ids,
                manager,
                clock,
                retryRules,
                List.of(primary, copy),
                List.of());
        consumers.start();
    }

    @AfterEach
    void close() {
        if (consumers != null)
            consumers.stop();
        if (connection != null)
            connection.destroy();
    }

    @Test
    @DisplayName("指数退避定向返回原 Handler，重启后沿用原规则与业务标识")
    void targetsOnlyOriginalHandlerAndRestoresFrozenRule() {
        Id id = bus.dispatch(event(), DispatchPolicy.immediate()).messageId();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(retryRows()).isEqualTo(1));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(copy.calls).hasValue(1));
        assertThat(nextAt(2)).isEqualTo(NOW.plusSeconds(1));
        dispatcher.dispatch();
        assertThat(
                jdbc.queryForObject("SELECT attempt_count FROM messaging_outbox WHERE handler_attempt = 2", Long.class))
                .isZero();
        consumers.stop();
        primary.rule = RetryRule.once();
        startConsumers();
        now.set(nextAt(2));
        dispatcher.dispatch();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(retryRows()).isEqualTo(2));
        assertThat(nextAt(3)).isEqualTo(NOW.plusSeconds(3));
        now.set(nextAt(3));
        dispatcher.dispatch();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(primary.calls).hasValue(3));
        assertThat(primary.ids).containsOnly(id);
        assertThat(primary.attempts).containsExactly(1L, 2L, 3L);
        assertThat(copy.calls).hasValue(1);
        assertThat(jdbc.queryForList("SELECT DISTINCT message_id FROM messaging_outbox", Long.class))
                .containsExactly(id.value());
    }

    @Test
    @DisplayName("交接提交后 ACK 连接中断，原消息重投不重复登记或执行已知旧尝试")
    void survivesAckFailureAfterCommit() {
        manager.afterCommit.set(connection::resetConnection);
        Id messageId = bus.dispatch(event(), DispatchPolicy.immediate()).messageId();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(retryRows()).isEqualTo(1);
            assertThat(admin.getQueueInfo(queue(primary)).getMessageCount()).isZero();
        });
        now.set(nextAt(2));
        dispatcher.dispatch();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(retryRows()).isEqualTo(2));
        assertThat(primary.attempts).containsExactly(1L, 2L);
        assertThat(primary.ids).containsOnly(messageId);
    }

    @Test
    @DisplayName("交接事务失败时保留 Broker 消息并最终恢复登记")
    void retainsOriginalMessageWhenCommitFails() {
        manager.beforeCommit.set(() -> {
            throw new TransactionSystemException("injected commit failure");
        });
        Id messageId = bus.dispatch(event(), DispatchPolicy.immediate()).messageId();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(retryRows()).isEqualTo(1));
        assertThat(primary.calls.get()).isGreaterThanOrEqualTo(2);
        assertThat(primary.ids).containsOnly(messageId);
        assertThat(jdbc.queryForObject("SELECT handler_attempt FROM messaging_outbox", Long.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("不可路由的定向发布持续重试，发布次数与消费次数分离")
    void publisherFailuresDoNotConsumeHandlerAttempts() {
        consumers.stop();
        Id id = Objects.requireNonNull(
                transactions.execute(status -> bus.dispatch(event(), DispatchPolicy.outbox()).messageId()));
        byte[] body = Objects.requireNonNull(
                jdbc
                        .queryForObject(
                                "SELECT envelope FROM messaging_outbox WHERE message_id = ?",
                                byte[].class,
                                id.value()));
        jdbc.update("DELETE FROM messaging_outbox");
        TreeCode handler = TreeCode.of("TEST/MISSING");
        String target = topology.handlerQueueName(handler).format(NameFormat.COLON);
        admin.deleteQueue(target);
        RetryRule rule = RetryRule.unlimited(Backoff.sequence(Duration.ofSeconds(1), Duration.ofSeconds(3)));
        transactions.executeWithoutResult(
                status -> outbox.scheduleHandlerRetry(ids.nextId(), id, TYPE.code(), body, handler, 2, rule, NOW));
        dispatcher.dispatch();
        assertThat(jdbc.queryForObject("SELECT status FROM messaging_outbox", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM messaging_outbox", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT handler_attempt FROM messaging_outbox", Long.class)).isEqualTo(2);
        admin.declareQueue(new Queue(target, true));
        now.set(NOW.plusSeconds(1));
        dispatcher.dispatch();
        Message received = rabbit.receive(target, 5000);
        assertThat(received).isNotNull();
        assertThat(received.getMessageProperties().getMessageId()).isEqualTo(id.toString());
        assertThat(received.getMessageProperties().getHeaders()).containsEntry("HANDLER:ATTEMPT", 2L);
        assertThat(
                retryRules.decode(received.getMessageProperties().getHeader("HANDLER:RETRY_RULE").toString())
                        .maximumAttempts())
                .isEmpty();
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM messaging_outbox", Long.class)).isEqualTo(2);
        admin.deleteQueue(target);
    }

    @Test
    @DisplayName("有限消费规则耗尽进入独立 DLQ，不影响其他 Handler")
    void exhaustedHandlerGoesToItsDlq() {
        consumers.stop();
        primary.rule = RetryRule.atMost(2, Backoff.fixed(Duration.ofSeconds(1)));
        primary.action = incoming -> HandlingDecision.retry("TEMPORARY");
        startConsumers();
        Id id = bus.dispatch(event(), DispatchPolicy.immediate()).messageId();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(retryRows()).isEqualTo(1));
        now.set(nextAt(2));
        dispatcher.dispatch();
        Message dead = rabbit.receive(queue(primary) + ":DEAD_LETTER", 10000);
        assertThat(dead).isNotNull();
        assertThat(dead.getMessageProperties().getMessageId()).isEqualTo(id.toString());
        assertThat(primary.attempts).containsExactly(1L, 2L);
        assertThat(retryRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("同一消费尝试幂等登记，无效消息内容或信封冲突直接拒绝")
    void rejectsCorruptMetadataAndDeduplicatesHandoff() throws Exception {
        consumers.stop();
        primary.rule = RetryRule.unlimited(Backoff.fixed(Duration.ofSeconds(1)));
        Id id = Objects.requireNonNull(
                transactions.execute(status -> bus.dispatch(event(), DispatchPolicy.outbox()).messageId()));
        byte[] body = Objects.requireNonNull(
                jdbc
                        .queryForObject(
                                "SELECT envelope FROM messaging_outbox WHERE message_id = ?",
                                byte[].class,
                                id.value()));
        for (int index = 0; index < 2; index++) {
            transactions.executeWithoutResult(
                    status -> outbox.scheduleHandlerRetry(
                            ids.nextId(),
                            id,
                            TYPE.code(),
                            body,
                            primary.code(),
                            2,
                            primary.rule,
                            NOW.plusSeconds(5)));
        }
        assertThat(retryRows()).isEqualTo(1);
        startConsumers();
        rabbit.send(
                "",
                queue(primary),
                MessageBuilder.withBody(body).setMessageId(id.toString()).setHeader("HANDLER:ATTEMPT", -1).build());
        assertThat(rabbit.receive(queue(primary) + ":DEAD_LETTER", 10000)).isNotNull();
        ObjectNode invalidVersion = (ObjectNode) mapper.readTree(body);
        Id invalidId = ids.nextId();
        invalidVersion.put("messageId", invalidId.toString());
        invalidVersion.put("protocolVersion", 2);
        rabbit.send(
                "",
                queue(primary),
                MessageBuilder.withBody(mapper.writeValueAsBytes(invalidVersion))
                        .setMessageId(invalidId.toString())
                        .build());
        assertThat(rabbit.receive(queue(primary) + ":DEAD_LETTER", 10000)).isNotNull();
        ObjectNode conflictingEnvelope = (ObjectNode) mapper.readTree(body);
        conflictingEnvelope.put("publishedAt", NOW.plusSeconds(1).toString());
        rabbit.send(
                "",
                queue(primary),
                MessageBuilder.withBody(mapper.writeValueAsBytes(conflictingEnvelope))
                        .setMessageId(id.toString())
                        .build());
        assertThat(rabbit.receive(queue(primary) + ":DEAD_LETTER", 10000)).isNotNull();
        assertThat(primary.calls).hasValue(0);
        assertThat(retryRows()).isEqualTo(1);
    }

    /** 无限消费规则按序列等待，末项持续复用，成功后停止登记。 */
    @Test
    void repeatsLastSequenceIntervalUntilSuccessfulConsumption() {
        consumers.stop();
        primary.rule = RetryRule.unlimited(Backoff.sequence(Duration.ofSeconds(2), Duration.ofSeconds(5)));
        primary.action = incoming -> incoming.attempt() < 4
                ? HandlingDecision.retry("TEMPORARY")
                : HandlingDecision.acknowledge();
        startConsumers();
        Id messageId = bus.dispatch(event(), DispatchPolicy.immediate()).messageId();
        Instant expected = NOW;
        for (long attempt = 2; attempt <= 4; attempt++) {
            long targetAttempt = attempt;
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(retryRows()).isEqualTo((int) targetAttempt - 1));
            expected = expected.plusSeconds(attempt == 2 ? 2 : 5);
            assertThat(nextAt(attempt)).isEqualTo(expected);
            now.set(expected);
            dispatcher.dispatch();
        }
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(primary.calls).hasValue(4));
        assertThat(retryRows()).isEqualTo(3);
        assertThat(primary.attempts).containsExactly(1L, 2L, 3L, 4L);
        assertThat(primary.ids).containsOnly(messageId);
    }

    /** 旧发布租约不能覆盖接管后的状态；当前持有者仍可写回成功。 */
    @Test
    void rejectsStaleOutboxLeaseWriteback() {
        consumers.stop();
        Id messageId = Objects.requireNonNull(
                transactions.execute(status -> bus.dispatch(event(), DispatchPolicy.outbox()).messageId()));
        OutboxClaim first = Objects
                .requireNonNull(transactions.execute(status -> outbox.claim(NOW, 1, "first", NOW.plusSeconds(1))))
                .getFirst();
        assertThat(first.messageId()).isEqualTo(messageId);
        OutboxClaim next = Objects.requireNonNull(
                transactions.execute(status -> outbox.claim(NOW.plusSeconds(2), 1, "next", NOW.plusSeconds(10))))
                .getFirst();
        transactions.executeWithoutResult(status -> outbox.saveProgress(first.sent(NOW.plusSeconds(2))));
        assertThat(jdbc.queryForObject("SELECT lease_id FROM messaging_outbox", String.class)).isEqualTo("next");
        assertThat(jdbc.queryForObject("SELECT status FROM messaging_outbox", String.class)).isEqualTo("SENDING");
        transactions.executeWithoutResult(status -> outbox.saveProgress(next.sent(NOW.plusSeconds(3))));
        assertThat(jdbc.queryForObject("SELECT status FROM messaging_outbox", String.class)).isEqualTo("SENT");
    }

    private Event<String> event() {
        return new Event<>(TYPE, "event", NOW, "payload");
    }

    private int retryRows() {
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        "SELECT count(*) FROM messaging_outbox WHERE target_handler_code IS NOT NULL",
                        Integer.class));
    }

    private Instant nextAt(long attempt) {
        return Objects
                .requireNonNull(
                        jdbc.queryForObject(
                                "SELECT available_at FROM messaging_outbox WHERE handler_attempt = ?",
                                Timestamp.class,
                                attempt))
                .toInstant();
    }

    private String queue(TestHandler handler) {
        return topology.handlerQueueName(handler.code()).format(NameFormat.COLON);
    }

    /** 在真实提交前后注入一次故障，验证跨数据库与 Broker 的交接边界。 */
    private static class FaultTransactions extends DataSourceTransactionManager {
        private final AtomicReference<Runnable> beforeCommit = new AtomicReference<>();

        private final AtomicReference<Runnable> afterCommit = new AtomicReference<>();

        private FaultTransactions(DataSource source) {
            super(source);
        }

        @Override
        protected void doCommit(@NotNull DefaultTransactionStatus status) {
            Runnable before = beforeCommit.getAndSet(null);
            if (before != null)
                before.run();
            super.doCommit(status);
            Runnable after = afterCommit.getAndSet(null);
            if (after != null)
                after.run();
        }
    }

    /** 保存实际消费身份和尝试序号，并返回可配置业务决定。 */
    private static class TestHandler implements EventHandler<String> {
        private final TreeCode code;

        private volatile RetryRule rule;

        private volatile Function<IncomingMessage<Event<String>>, HandlingDecision> action = incoming -> HandlingDecision
                .acknowledge();

        private final AtomicInteger calls = new AtomicInteger();

        private final List<Id> ids = new CopyOnWriteArrayList<>();

        private final List<Long> attempts = new CopyOnWriteArrayList<>();

        private TestHandler(String code, RetryRule rule) {
            this.code = TreeCode.of(code);
            this.rule = rule;
        }

        @Override
        public @NotNull TreeCode code() {
            return code;
        }

        @Override
        public @NotNull EventType<String> eventType() {
            return TYPE;
        }

        @Override
        public @NotNull RetryRule retryRule() {
            return rule;
        }

        @Override
        public void handleEvent(@NotNull IncomingMessage<Event<String>> incoming) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull HandlingDecision handle(@NotNull IncomingMessage<Event<String>> incoming) {
            ids.add(incoming.messageId());
            attempts.add(incoming.attempt());
            calls.incrementAndGet();
            return action.apply(incoming);
        }
    }
}
