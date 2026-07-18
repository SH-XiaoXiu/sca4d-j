package com.sca4d.scaffold;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.realtime.RealtimeRoutes;
import com.sca4d.config.properties.Sca4djRealtimeProperties;
import com.sca4d.entity.ImConversationEntity;
import com.sca4d.entity.ImConversationMemberEntity;
import com.sca4d.entity.ImMessageEntity;
import com.sca4d.entity.NotificationDeliveryEntity;
import com.sca4d.entity.NotificationMessageEntity;
import com.sca4d.im.ImConversationType;
import com.sca4d.im.ImMemberRole;
import com.sca4d.im.ImMessageContentType;
import com.sca4d.im.ImMessageStatus;
import com.sca4d.notification.NotificationCategory;
import com.sca4d.notification.NotificationDeliveryStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 验证业务模块只能通过规定接口发布实时消息和发送 RabbitMQ 消息。 */
class RealtimeScaffoldArchitectureTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void defaultBusinessServicesShouldNotBypassContextCodecOrRealtimeBoundaries() throws IOException {
        assertBusinessServiceBoundary("com/sca4d/im/ImService.java");
        assertBusinessServiceBoundary("com/sca4d/notification/NotificationService.java");
    }

    @Test
    void realtimeSseShouldNotUseRabbitTemplateDirectly() throws IOException {
        Path realtime = MAIN.resolve("com/sca4d/common/realtime");
        try (var files = Files.walk(realtime)) {
            String source = files.filter(Files::isRegularFile)
                    .map(this::read)
                    .reduce("", String::concat);

            assertThat(source).doesNotContain("RabbitTemplate");
        }
    }

    @Test
    void realtimeSseShouldUseTypedAnnotationDrivenRabbitListener() {
        String listener = read(MAIN.resolve("com/sca4d/common/realtime/sse/SseRealtimeMessageListener.java"));
        String config = read(MAIN.resolve("com/sca4d/config/RealtimeConfig.java"));
        String messagingConfig = read(MAIN.resolve("com/sca4d/config/MessagingConfig.java"));
        String rabbitSender = read(MAIN.resolve("com/sca4d/common/messaging/rabbit/RabbitMessageSender.java"));
        String application = read(RESOURCES.resolve("application.yml"));

        assertThat(listener)
                .contains("@RabbitListener(")
                .contains("Message<MessageEnvelope<RealtimeEnvelope<Object>>>")
                .doesNotContain("ObjectMapper")
                .doesNotContain("readTree(");
        assertThat(MAIN.resolve("com/sca4d/common/realtime/sse/SseRabbitTopology.java")).doesNotExist();
        assertThat(config)
                .doesNotContain("SimpleMessageListenerContainer")
                .doesNotContain("Jackson2JsonMessageConverter")
                .doesNotContain("sseRabbitMessageConverter");
        assertThat(messagingConfig).contains("rabbitMessageConverter").contains("Jackson2JsonMessageConverter");
        assertThat(rabbitSender)
                .contains("rabbitTemplate.convertAndSend(")
                .doesNotContain("ObjectMapper")
                .doesNotContain("writeValueAsBytes(")
                .doesNotContain("MessageBuilder.withBody(");
        assertThat(application).doesNotContain("node-id:").doesNotContain("node-queue-prefix:");
    }

    @Test
    void imShouldUseDomainEnumsInsteadOfConstantsBucket() {
        assertThat(MAIN.resolve("com/sca4d/im/ImConstants.java")).doesNotExist();
    }

    @Test
    void imAndNotificationEntitiesShouldUseDomainEnums() throws NoSuchFieldException {
        assertThat(ImConversationEntity.class.getDeclaredField("conversationType").getType())
                .isEqualTo(ImConversationType.class);
        assertThat(ImConversationMemberEntity.class.getDeclaredField("principalType").getType())
                .isEqualTo(PrincipalType.class);
        assertThat(ImConversationMemberEntity.class.getDeclaredField("memberRole").getType())
                .isEqualTo(ImMemberRole.class);
        assertThat(ImMessageEntity.class.getDeclaredField("senderPrincipalType").getType())
                .isEqualTo(PrincipalType.class);
        assertThat(ImMessageEntity.class.getDeclaredField("contentType").getType()).isEqualTo(ImMessageContentType.class);
        assertThat(ImMessageEntity.class.getDeclaredField("status").getType()).isEqualTo(ImMessageStatus.class);
        assertThat(NotificationMessageEntity.class.getDeclaredField("category").getType())
                .isEqualTo(NotificationCategory.class);
        assertThat(NotificationMessageEntity.class.getDeclaredField("senderPrincipalType").getType())
                .isEqualTo(PrincipalType.class);
        assertThat(NotificationDeliveryEntity.class.getDeclaredField("principalType").getType())
                .isEqualTo(PrincipalType.class);
        assertThat(NotificationDeliveryEntity.class.getDeclaredField("status").getType())
                .isEqualTo(NotificationDeliveryStatus.class);
    }

    @Test
    void realtimeControllerShouldNotWrapSubscriptionTargets() {
        assertThat(read(MAIN.resolve("com/sca4d/controllers/RealtimeController.java"))).doesNotContain("targets(");
    }

    @Test
    void realtimeShouldReuseSharedAuthAndRequestContexts() throws IOException {
        assertThat(readTree(MAIN.resolve("com/sca4d/common/realtime"))).doesNotContain("RealtimeContext");
    }

    @Test
    void websocketDefaultEndpointShouldLiveUnderRealtime() {
        assertThat(new Sca4djRealtimeProperties().getWebsocket().getEndpoint()).isEqualTo(RealtimeRoutes.WS_ENDPOINT);
    }

    @Test
    void realtimeImAndNotificationMainCodeShouldNotUseRecords() throws IOException {
        assertThat(readTree(MAIN.resolve("com/sca4d/common/realtime"))).doesNotContain(" record ");
        assertThat(readTree(MAIN.resolve("com/sca4d/im"))).doesNotContain(" record ");
        assertThat(readTree(MAIN.resolve("com/sca4d/notification"))).doesNotContain(" record ");
    }

    /**
     * 断言业务 Service 只能通过规定的请求、序列化、实时发布和消息发送接口通信。
     *
     * @param relativePath 相对于主源码目录的 Service 源文件路径
     * @throws IOException 源文件无法读取时抛出
     */
    private void assertBusinessServiceBoundary(@NotNull String relativePath) throws IOException {
        String source = read(MAIN.resolve(relativePath));

        assertThat(source)
                .doesNotContain("import com.sca4d.common.request.CurrentRequestHolder")
                .doesNotContain("import com.fasterxml.jackson.databind.ObjectMapper")
                .doesNotContain("import com.sca4d.common.realtime.RealtimePublisher")
                .doesNotContain("import org.springframework.amqp.rabbit.core.RabbitTemplate");
    }

    /**
     * 读取单个架构断言源文件，并把文件系统失败转换为包含路径的测试异常。
     *
     * @param path 待读取文件
     * @return 文件完整文本
     */
    private @NotNull String read(@NotNull Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new IllegalStateException("读取文件失败: " + path, ex);
        }
    }

    /**
     * 顺序拼接目录树中的全部普通文件内容，供包级架构禁用项搜索。
     *
     * @param path 待扫描目录
     * @return 目录树全部普通文件的文本
     * @throws IOException 目录遍历失败时抛出
     */
    private @NotNull String readTree(@NotNull Path path) throws IOException {
        try (var files = Files.walk(path)) {
            return files.filter(Files::isRegularFile)
                    .map(this::read)
                    .reduce("", String::concat);
        }
    }
}
