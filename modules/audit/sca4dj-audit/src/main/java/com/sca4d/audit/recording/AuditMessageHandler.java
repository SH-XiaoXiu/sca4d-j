package com.sca4d.audit.recording;

import com.sca4d.core.api.messaging.HandlingDecision;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.messaging.MessageHandler;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.retry.Backoff;
import com.sca4d.core.api.retry.RetryRule;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Objects;

/** 独立事务追加审计，成功提交后确认消息；技术失败由 Messaging 重试并最终进入 DLQ。 */
public class AuditMessageHandler implements MessageHandler<AuditMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(AuditMessageHandler.class);

    private final AuditRepository repository;

    private final AuditMessageCodec codec;

    /** 使用 Audit 模块的持久化入口和消息编码器消费可靠审计。 */
    public AuditMessageHandler(AuditRepository repository, AuditMessageCodec codec) {
        this.repository = Objects.requireNonNull(repository);
        this.codec = Objects.requireNonNull(codec);
    }

    @Override
    public @NotNull TreeCode code() {
        return TreeCode.of("AUDIT/RECORD/STORE");
    }

    @Override
    public @NotNull MessageCodec<AuditMessage> codec() {
        return codec;
    }

    @Override
    public @NotNull RetryRule retryRule() {
        return RetryRule.atMost(5, Backoff.fixed(Duration.ofSeconds(5)));
    }

    @Override
    @Transactional
    public @NotNull HandlingDecision handle(@NotNull IncomingMessage<AuditMessage> incoming) {
        repository.appendDelivery(incoming.messageId().value(), incoming.message());
        LOG.trace("审计消费写入已执行 | messageId={} attempt={}", incoming.messageId(), incoming.attempt());
        return HandlingDecision.acknowledge();
    }
}
