package com.sca4d.schedule.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.exception.BizException;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.iam.resource.PrincipalReference;
import com.sca4d.iam.resource.SubjectReference;
import com.sca4d.notification.NotificationRecipient;
import com.sca4d.notification.NotificationService;
import com.sca4d.schedule.ScheduleErrorCode;
import com.sca4d.schedule.ScheduleTask;
import com.sca4d.schedule.ScheduleTaskExecution;
import com.sca4d.schedule.ScheduleTaskResult;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 通过 NotificationService 创建站内通知，并在事务提交后通过 SSE 发送给在线接收人。 */
@Component
public class NotificationScheduleTask implements ScheduleTask<NotificationSchedulePayload> {

    /** 创建站内通知并通过 SSE 发送的 ScheduleTask 类型。 */
    public static final String TASK_TYPE = "notification.sse";
    private static final int PAYLOAD_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final IamRepository iamRepository;
    private final NotificationService notificationService;

    public NotificationScheduleTask(
            @NotNull ObjectMapper objectMapper,
            @NotNull IamRepository iamRepository,
            @NotNull NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.iamRepository = iamRepository;
        this.notificationService = notificationService;
    }

    @Override
    public @NotNull String taskType() {
        return TASK_TYPE;
    }

    @Override
    public int payloadVersion() {
        return PAYLOAD_VERSION;
    }

    @Override
    public @NotNull Class<NotificationSchedulePayload> payloadClass() {
        return NotificationSchedulePayload.class;
    }

    @Override
    public @NotNull String encode(@NotNull NotificationSchedulePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("notification.sse Payload 编码失败", exception);
        }
    }

    @Override
    public @NotNull NotificationSchedulePayload decode(int version, @NotNull String json) {
        if (version != PAYLOAD_VERSION) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_PAYLOAD_VERSION_UNKNOWN);
        }
        try {
            return objectMapper.readValue(json, NotificationSchedulePayload.class);
        } catch (JsonProcessingException exception) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_INVALID);
        }
    }

    @Override
    public void validate(@NotNull NotificationSchedulePayload payload) {
        // 保存前校验通知字段长度并固定接收人列表，后续执行不能通过动态条件增加接收人。
        if (!StringUtils.hasText(payload.getTitle()) || payload.getTitle().trim().length() > 200) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_INVALID);
        }
        if (!StringUtils.hasText(payload.getContent()) || payload.getContent().trim().length() > 2000) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_INVALID);
        }
        if (payload.getCategory() == null || payload.getRecipients().isEmpty()) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_INVALID);
        }
        Set<Long> subjectIds = payload.getRecipients().stream().map(recipient -> {
            if (recipient == null
                    || recipient.getSubjectId() == null
                    || recipient.getPrincipalType() != PrincipalType.DEFAULT
                    || recipient.getPrincipalId() == null) {
                throw new BizException(ScheduleErrorCode.SCHEDULE_INVALID);
            }
            return recipient.getSubjectId();
        }).collect(Collectors.toSet());
        Set<Long> principalIds = payload.getRecipients().stream()
                .map(NotificationScheduleRecipient::getPrincipalId)
                .collect(Collectors.toSet());

        // 固定执行一条 Subject 集合查询；缺失或已删除用户使整个创建或更新请求失败。
        Map<Long, SubjectReference> identities = iamRepository.findSubjectReferences(subjectIds).stream()
                .collect(Collectors.toMap(SubjectReference::getSubjectId, Function.identity()));
        if (identities.size() != subjectIds.size()
                || identities.values().stream().anyMatch(SubjectReference::isDeleted)) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_INVALID);
        }
        Map<Long, PrincipalReference> principals = iamRepository.findPrincipalReferences(principalIds).stream()
                .collect(Collectors.toMap(PrincipalReference::getPrincipalId, Function.identity()));
        if (principals.size() != principalIds.size()
                || payload.getRecipients().stream().anyMatch(recipient -> {
                    PrincipalReference principal = principals.get(recipient.getPrincipalId());
                    return principal == null
                            || principal.isDeleted()
                            || principal.getPrincipalType() != PrincipalType.DEFAULT
                            || !recipient.getSubjectId().equals(principal.getSubjectId());
                })) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_INVALID);
        }
    }

    @Override
    public @NotNull ScheduleTaskResult execute(
            @NotNull NotificationSchedulePayload payload, @NotNull ScheduleTaskExecution execution) {
        // 执行时固定一次批量读取接收人；只跳过已经删除的 Subject，停用但未删除者仍保留站内通知。
        Set<Long> subjectIds = payload.getRecipients().stream()
                .map(NotificationScheduleRecipient::getSubjectId)
                .collect(Collectors.toSet());
        Map<Long, SubjectReference> identities = iamRepository.findSubjectReferences(subjectIds).stream()
                .collect(Collectors.toMap(SubjectReference::getSubjectId, Function.identity()));
        List<NotificationRecipient> recipients = payload.getRecipients().stream()
                .filter(recipient -> {
                    SubjectReference subject = identities.get(recipient.getSubjectId());
                    return subject != null && !subject.isDeleted();
                })
                .map(recipient -> NotificationRecipient.of(
                        recipient.getSubjectId(), recipient.getPrincipalType(), recipient.getPrincipalId()))
                .toList();
        int skippedRecipientCount = payload.getRecipients().size() - recipients.size();
        if (recipients.isEmpty()) {
            return new ScheduleTaskResult("recipientCount=0, skippedRecipientCount=" + skippedRecipientCount);
        }

        // NotificationService 在当前事务中写入一条消息和全部 delivery，提交后再通过现有 SSE 发布器发送。
        notificationService.create(
                payload.getTitle().trim(),
                payload.getContent().trim(),
                payload.getCategory(),
                null,
                null,
                Map.of("scheduleId", execution.getScheduleId(), "executionId", execution.getExecutionId()),
                recipients);
        return new ScheduleTaskResult(
                "recipientCount=" + recipients.size() + ", skippedRecipientCount=" + skippedRecipientCount);
    }

    @Override
    public @NotNull String displayNameKey() {
        return "schedule.task.notification-sse";
    }
}
