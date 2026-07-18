package com.sca4d.iam.profile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.ForbiddenException;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.file.FileSignatureOperation;
import com.sca4d.file.signature.FileSignatureItem;
import com.sca4d.iam.profile.repository.DefaultProfileRepository;
import com.sca4d.iam.profile.signature.DefaultProfileAvatarFileSignaturePolicy;
import com.sca4d.platform.PlatformPermissions;
import com.sca4d.test.TestSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 验证 DEFAULT Profile 头像不再依赖业务 User。 */
class DefaultProfileAvatarFileSignaturePolicyTest {

    @Test
    void shouldRejectAnonymousRequestsWithoutQueryingProfiles() {
        DefaultProfileRepository repository = mock(DefaultProfileRepository.class);
        DefaultProfileAvatarFileSignaturePolicy policy = new DefaultProfileAvatarFileSignaturePolicy(repository);

        assertThatThrownBy(() -> policy.authorize(new CurrentRequest(), List.of(read(10L, 20L))))
                .isInstanceOf(UnauthenticatedException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void shouldAuthorizeReadAndSelfUploadUsingDefaultProfile() {
        DefaultProfileRepository repository = mock(DefaultProfileRepository.class);
        when(repository.findBySubjectIds(Set.of(10L)))
                .thenReturn(Map.of(10L, new DefaultProfile().setSubjectId(10L).setAvatarFileId(20L)));
        DefaultProfileAvatarFileSignaturePolicy policy = new DefaultProfileAvatarFileSignaturePolicy(repository);

        assertThatCode(() -> policy.authorize(auth(10L, PrincipalType.DEFAULT, Set.of()), List.of(read(10L, 20L))))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.authorize(auth(10L, PrincipalType.TENANT, Set.of()), List.of(upload("10", "image/webp", 1024L))))
                .doesNotThrowAnyException();
        verify(repository, times(2)).findBySubjectIds(Set.of(10L));
    }

    @Test
    void shouldAllowInitialAvatarOnlyForAccountOrMemberCreation() {
        DefaultProfileRepository repository = mock(DefaultProfileRepository.class);
        DefaultProfileAvatarFileSignaturePolicy policy = new DefaultProfileAvatarFileSignaturePolicy(repository);

        assertThatCode(() -> policy.authorize(
                auth(1L, PrincipalType.PLATFORM, Set.of(PlatformPermissions.ACCOUNTS_CREATE)),
                List.of(upload(null, "image/jpeg", 1024L))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.authorize(
                auth(1L, PrincipalType.DEFAULT, Set.of()), List.of(upload(null, "image/jpeg", 1024L))))
                .isInstanceOf(ForbiddenException.class);
    }

    private CurrentRequest auth(Long subjectId, PrincipalType type, Set<String> permissions) {
        Long principalId = subjectId + 100L;
        Principal principal = Principal.of(principalId, type, subjectId, null);
        CurrentRequest request = CurrentRequest.authenticated(TestSession.withAuthorization(
                new Subject(subjectId, "测试"),
                List.of(principal),
                Map.of(principalId, new SessionPermission(principalId, Set.of(), permissions))));
        request.setPrincipal(principal);
        return request;
    }

    private FileSignatureItem read(Long subjectId, Long fileId) {
        FileSignatureItem item = new FileSignatureItem();
        item.setOperation(FileSignatureOperation.READ);
        item.setResourceId(subjectId.toString());
        item.setFileId(fileId);
        return item;
    }

    private FileSignatureItem upload(String subjectId, String contentType, long sizeBytes) {
        FileSignatureItem item = new FileSignatureItem();
        item.setOperation(FileSignatureOperation.UPLOAD);
        item.setResourceId(subjectId);
        item.setContentType(contentType);
        item.setSizeBytes(sizeBytes);
        return item;
    }
}
