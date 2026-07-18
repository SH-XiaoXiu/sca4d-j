package com.sca4d.controllers.mg.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sca4d.common.concurrent.IdempotentRequest;
import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.exception.BizException;
import com.sca4d.mail.MailErrorCode;
import com.sca4d.mail.MailSenderService;
import com.sca4d.mail.MailService;
import com.sca4d.mail.MailTemplateService;
import com.sca4d.mail.request.MailSenderCreateRequest;
import com.sca4d.mail.request.MailSenderStatusRequest;
import com.sca4d.mail.request.MailTemplateCreateRequest;
import com.sca4d.mail.request.MailTemplatePreviewRequest;
import com.sca4d.mail.request.MailTemplateStatusRequest;
import com.sca4d.mail.request.MailTemplateTestSendRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;

/** 验证邮件资源使用 header 前置条件和名词资源路径。 */
class MailControllerRestContractTest {

    @Test
    void shouldUseMainResourcesForStatusAndNounResourcesForOperations() throws Exception {
        PatchMapping senderPatch = SystemMailSenderController.class
                .getDeclaredMethod("changeStatus", Long.class, MailSenderStatusRequest.class, String.class)
                .getAnnotation(PatchMapping.class);
        PatchMapping templatePatch = SystemMailTemplateController.class
                .getDeclaredMethod("changeStatus", Long.class, MailTemplateStatusRequest.class, String.class)
                .getAnnotation(PatchMapping.class);
        PostMapping preview = SystemMailTemplateController.class
                .getDeclaredMethod("preview", MailTemplatePreviewRequest.class)
                .getAnnotation(PostMapping.class);
        PostMapping testMessage = SystemMailTemplateController.class
                .getDeclaredMethod("sendTest", Long.class, MailTemplateTestSendRequest.class)
                .getAnnotation(PostMapping.class);

        assertThat(senderPatch.value()).containsExactly("/{senderId}");
        assertThat(templatePatch.value()).containsExactly("/mail-templates/{templateId}");
        assertThat(preview.value()).containsExactly("/mail-template-previews");
        assertThat(testMessage.value()).containsExactly("/mail-templates/{templateId}/test-messages");
        assertThat(IdempotentRequest.class.isAssignableFrom(MailSenderCreateRequest.class)).isFalse();
        assertThat(IdempotentRequest.class.isAssignableFrom(MailTemplateCreateRequest.class)).isFalse();
        assertThat(VersionedRequest.class.isAssignableFrom(MailSenderStatusRequest.class)).isFalse();
        assertThat(VersionedRequest.class.isAssignableFrom(MailTemplateStatusRequest.class)).isFalse();
    }

    @Test
    void shouldRequireIfMatchBeforeDeletingMailResources() throws Exception {
        MailSenderService senderService = mock(MailSenderService.class);
        MailTemplateService templateService = mock(MailTemplateService.class);

        assertThatThrownBy(() -> new SystemMailSenderController(senderService, mock(MailService.class))
                .delete(1L, null))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MailErrorCode.SENDER_IF_MATCH_REQUIRED));
        assertThatThrownBy(() -> new SystemMailTemplateController(templateService).delete(1L, null))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MailErrorCode.TEMPLATE_IF_MATCH_REQUIRED));
        verifyNoInteractions(senderService, templateService);

        DeleteMapping senderDelete = SystemMailSenderController.class
                .getDeclaredMethod("delete", Long.class, String.class)
                .getAnnotation(DeleteMapping.class);
        DeleteMapping templateDelete = SystemMailTemplateController.class
                .getDeclaredMethod("delete", Long.class, String.class)
                .getAnnotation(DeleteMapping.class);
        assertThat(senderDelete.value()).containsExactly("/{senderId}");
        assertThat(templateDelete.value()).containsExactly("/mail-templates/{templateId}");
    }
}
