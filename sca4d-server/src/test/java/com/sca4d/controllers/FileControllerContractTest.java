package com.sca4d.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.auth.Principal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PutMapping;

/** 验证文件传输接口使用文件上传资源路径。 */
class FileControllerContractTest {

    @Test
    void shouldUseUploadContentAndCompletionResources() throws Exception {
        PutMapping content = FileTransportController.class
                .getDeclaredMethod("upload", Long.class, Principal.class, HttpServletRequest.class)
                .getAnnotation(PutMapping.class);
        PutMapping completion = FileTransportController.class
                .getDeclaredMethod("completeUpload", Long.class, Principal.class)
                .getAnnotation(PutMapping.class);

        assertThat(content.value()).containsExactly("/uploads/{fileId}/content");
        assertThat(completion.value()).containsExactly("/uploads/{fileId}/completion");
    }
}
