package com.sca4d.file.signature;

import com.sca4d.common.auth.ForbiddenException;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.config.properties.Sca4djFileProperties;
import com.sca4d.file.FileAccessUrl;
import com.sca4d.file.FileService;
import com.sca4d.file.FileSignatureOperation;
import com.sca4d.file.FileUpload;
import com.sca4d.file.error.FileErrorCode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 批量选择文件签名规则、校验请求并签发授权。
 */
public class DefaultFileSignatureService implements FileSignatureService {

    private static final Logger log = LoggerFactory.getLogger(DefaultFileSignatureService.class);

    private final FileSignatureRegistry registry;
    private final FileService fileService;
    private final FileGrantSigner grantSigner;
    private final Sca4djFileProperties properties;

    /**
     * 创建默认批量文件签名服务。
     *
     * @param registry 业务签名场景注册表
     * @param fileService 创建文件上传并读取文件信息的服务
     * @param grantSigner 文件短期授权签发器
     * @param properties 文件授权时效配置
     */
    public DefaultFileSignatureService(
            @NotNull FileSignatureRegistry registry,
            @NotNull FileService fileService,
            @NotNull FileGrantSigner grantSigner,
            @NotNull Sca4djFileProperties properties) {
        this.registry = registry;
        this.fileService = fileService;
        this.grantSigner = grantSigner;
        this.properties = properties;
    }

    @Override
    @Transactional
    public @NotNull FileSignatureResult sign(
            @NotNull List<FileSignatureItem> requestedItems,
            @NotNull CurrentRequest currentRequest) {
        long startedAt = System.nanoTime();
        // 对最多 100 项执行一次纯内存结构校验和 requestId 去重，固定为 0 条 SQL、0 次外部调用。
        if (requestedItems.isEmpty() || requestedItems.size() > 100) {
            throw new BizException(FileErrorCode.FILE_SIGNATURE_BATCH_INVALID);
        }
        Set<String> requestIds = new LinkedHashSet<>();
        for (FileSignatureItem item : requestedItems) {
            if (item == null
                    || !StringUtils.hasText(item.getRequestId())
                    || item.getOperation() == null
                    || !StringUtils.hasText(item.getScene())) {
                throw new BizException(FileErrorCode.FILE_SIGNATURE_BATCH_INVALID);
            }
            item.setRequestId(item.getRequestId().trim());
            item.setScene(item.getScene().trim());
            if (!requestIds.add(item.getRequestId())) {
                throw new BizException(FileErrorCode.FILE_REQUEST_ID_DUPLICATE);
            }
            if (item.getOperation() == FileSignatureOperation.READ && item.getFileId() == null) {
                throw new BizException(FileErrorCode.FILE_READ_FILE_ID_REQUIRED);
            }
            if (item.getOperation() == FileSignatureOperation.UPLOAD
                    && (!StringUtils.hasText(item.getOriginalFilename())
                            || item.getSizeBytes() == null
                            || item.getSizeBytes() <= 0)) {
                throw new BizException(FileErrorCode.FILE_UPLOAD_METADATA_INCOMPLETE);
            }
        }
        List<FileSignatureItem> items = List.copyOf(requestedItems);

        // 匿名读取保留空请求态；已认证请求最多扫描 Session 中的 n 个 Principal，固定为 0 条 SQL。
        if (currentRequest.authenticated() && currentRequest.getPrincipal() == null) {
            Subject subject = currentRequest.requireSubject();
            Principal defaultPrincipal = null;
            for (Principal candidate : currentRequest.requireSession().getPrincipals()) {
                if (candidate.getPrincipalType() == PrincipalType.DEFAULT
                        && subject.getSubjectId().equals(candidate.getSubjectId())
                        && candidate.getTenantId() == null) {
                    defaultPrincipal = candidate;
                    break;
                }
            }
            if (defaultPrincipal == null) {
                throw new ForbiddenException(CommonErrorCode.AUTHENTICATION_DEFAULT_PRINCIPAL_UNAVAILABLE);
            }
            currentRequest.setPrincipal(defaultPrincipal);
        }

        // 纯内存完成 n 项策略分组；每个不同 Policy 仅调用一次批量 authorize，禁止 Policy 内逐项回源。
        Map<FileSignaturePolicy, List<FileSignatureItem>> policyItems = new LinkedHashMap<>();
        for (FileSignatureItem item : items) {
            if (item.getOperation() == FileSignatureOperation.UPLOAD && !currentRequest.authenticated()) {
                throw new UnauthenticatedException();
            }
            FileSignaturePolicy policy = registry.require(item.getScene());
            policyItems.computeIfAbsent(policy, ignored -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<FileSignaturePolicy, List<FileSignatureItem>> entry : policyItems.entrySet()) {
            entry.getKey().authorize(currentRequest, List.copyOf(entry.getValue()));
        }

        List<Long> readFileIds = items.stream()
                .filter(item -> item.getOperation() == FileSignatureOperation.READ)
                .map(FileSignatureItem::getFileId)
                .distinct()
                .toList();
        Map<Long, FileAccessUrl> accessUrls = new LinkedHashMap<>();
        // 全部去重 READ fileId 只调用一次批量文件查询，空集合也不拆成逐项调用。
        for (FileAccessUrl accessUrl : fileService.createAccessUrls(readFileIds)) {
            accessUrls.put(accessUrl.getFileId(), accessUrl);
        }

        // 结果循环固定为 n 次：READ 只查内存 Map；每个 UPLOAD 恰好创建 1 条文件记录并签发 1 份授权。
        OffsetDateTime grantExpiresAt = TimeSupport.now().plus(properties.getGrantTtl());
        List<FileSignatureItemResult> results = new ArrayList<>();
        int readCount = 0;
        int uploadCount = 0;
        for (FileSignatureItem item : items) {
            FileSignatureItemResult result = new FileSignatureItemResult()
                    .setRequestId(item.getRequestId())
                    .setOperation(item.getOperation());
            if (item.getOperation() == FileSignatureOperation.READ) {
                readCount++;
                FileAccessUrl accessUrl = accessUrls.get(item.getFileId());
                if (accessUrl == null) {
                    throw new BizException(FileErrorCode.FILE_NOT_FOUND);
                }
                result.setFiles(List.of(accessUrl));
            } else {
                uploadCount++;
                Long uploadPrincipalId = currentRequest.requirePrincipal().requirePrincipalId();
                FileUpload upload = fileService.createUpload(
                        uploadPrincipalId,
                        item.getOriginalFilename(),
                        item.getContentType(),
                        item.getSizeBytes());
                String grant = grantSigner.issueUpload(
                        upload.getFileId(),
                        uploadPrincipalId,
                        item.getScene(),
                        item.getResourceId(),
                        grantExpiresAt);
                result.setUpload(upload).setUploadGrant(grant);
            }
            results.add(result);
        }
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "文件授权批次完成，上传记录已写入事务 | readCount={} uploadCount={} policyCount={} durationMs={}",
                readCount,
                uploadCount,
                policyItems.size(),
                durationMs);
        return new FileSignatureResult().setItems(List.copyOf(results));
    }
}
