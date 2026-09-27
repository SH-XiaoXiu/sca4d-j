-- 平台身份使用下划线节点；保留全部身份 ID、状态和关联关系。
UPDATE iam_principal
SET principal_type = 'IAM.PLATFORM_PRINCIPAL'
WHERE principal_type IN ('iam.platform-principal', 'IAM.PLATFORM-PRINCIPAL');
