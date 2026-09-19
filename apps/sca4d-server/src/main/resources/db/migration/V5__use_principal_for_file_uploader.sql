ALTER TABLE storage_file
    ADD COLUMN uploader_principal_id BIGINT;

DO $$
DECLARE
    invalid_file_count BIGINT;
BEGIN
    SELECT COUNT(*)
    INTO invalid_file_count
    FROM storage_file file
    WHERE (
        SELECT COUNT(*)
        FROM iam_account_principal account_principal
        JOIN iam_principal principal ON principal.id = account_principal.principal_id
        WHERE account_principal.account_id = file.uploader_account_id
          AND principal.principal_type = 'iam.platform-principal'
    ) <> 1;

    IF invalid_file_count > 0 THEN
        RAISE EXCEPTION
            'storage_file 中有 % 条记录无法从 uploader_account_id 唯一确定 iam.platform-principal',
            invalid_file_count;
    END IF;
END
$$;

UPDATE storage_file file
SET uploader_principal_id = (
    SELECT account_principal.principal_id
    FROM iam_account_principal account_principal
    JOIN iam_principal principal ON principal.id = account_principal.principal_id
    WHERE account_principal.account_id = file.uploader_account_id
      AND principal.principal_type = 'iam.platform-principal'
);

ALTER TABLE storage_file
    ALTER COLUMN uploader_principal_id SET NOT NULL;

ALTER TABLE storage_file
    DROP COLUMN uploader_account_id;

COMMENT ON COLUMN storage_file.uploader_principal_id IS
    '创建文件的 Principal 标识快照；SYSTEM 使用固定标识 1';
