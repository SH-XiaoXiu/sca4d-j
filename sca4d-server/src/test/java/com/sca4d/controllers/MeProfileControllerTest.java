package com.sca4d.controllers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.iam.profile.request.DefaultProfileAvatarUpdateRequest;
import com.sca4d.iam.profile.service.DefaultProfileService;
import org.junit.jupiter.api.Test;

/** 验证当前账号头像绑定沿用上传文件所属的最终操作主体。 */
class MeProfileControllerTest {

    @Test
    void shouldConsumeAvatarAsCurrentPlatformPrincipal() {
        DefaultProfileService profiles = mock(DefaultProfileService.class);
        MeProfileController controller = new MeProfileController(profiles);
        DefaultProfileAvatarUpdateRequest request = new DefaultProfileAvatarUpdateRequest();
        request.setFileId(90L);
        request.setUploadGrant("upload-grant");
        request.setProfileVersion(3L);
        Subject subject = new Subject(70L, "Administrator");
        Principal platformPrincipal = Principal.of(80L, PrincipalType.PLATFORM, 70L, null);

        controller.replaceAvatar(request, subject, platformPrincipal);

        verify(profiles).replaceAvatar(70L, 90L, "upload-grant", 3L, 80L);
    }
}
