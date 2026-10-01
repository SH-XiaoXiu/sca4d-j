-- 已保存的 Account 设置仍使用单段 owner 类型；迁移前拒绝与新类型重复的行。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.sys_setting old_setting
        INNER JOIN public.sys_setting new_setting
            ON new_setting.owner_type = 'IAM/ACCOUNT'
           AND new_setting.owner_id = old_setting.owner_id
           AND new_setting.setting_key = old_setting.setting_key
        WHERE old_setting.owner_type = 'account'
    ) THEN
        RAISE EXCEPTION 'Account 设置的旧 owner 与 IAM/ACCOUNT 已有行冲突';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM public.sys_setting
        WHERE owner_type = 'account' AND value_ciphertext IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'Account 设置包含加密旧值，不能只修改 owner 类型';
    END IF;
END
$$;

UPDATE public.sys_setting
SET owner_type = 'IAM/ACCOUNT'
WHERE owner_type = 'account';

-- Account 策略 JSON 的短方法编码与当前认证模块的 TreeCode 对齐。
WITH normalized AS (
    SELECT setting.owner_type,
           setting.owner_id,
           setting.setting_key,
           jsonb_set(
               setting.value_text::jsonb,
               '{methods}',
               (
                   SELECT jsonb_agg(
                       CASE
                           WHEN method.value ->> 'methodCode' ~ '^[a-z][a-z0-9-]*$'
                               THEN jsonb_set(
                                   method.value,
                                   '{methodCode}',
                                   to_jsonb('AUTHENTICATION/' || upper(replace(method.value ->> 'methodCode', '-', '_'))))
                           ELSE method.value
                       END
                       ORDER BY method.ordinality)
                   FROM jsonb_array_elements(setting.value_text::jsonb -> 'methods')
                       WITH ORDINALITY AS method(value, ordinality)
               ))::text AS value_text
    FROM public.sys_setting setting
    WHERE setting.setting_key = 'authentication.account-policy'
      AND setting.has_value
      AND setting.value_text IS NOT NULL
      AND EXISTS (
          SELECT 1
          FROM jsonb_array_elements(setting.value_text::jsonb -> 'methods') AS method(value)
          WHERE method.value ->> 'methodCode' ~ '^[a-z][a-z0-9-]*$'
      )
)
UPDATE public.sys_setting setting
SET value_text = normalized.value_text,
    version = setting.version + 1
FROM normalized
WHERE setting.owner_type = normalized.owner_type
  AND setting.owner_id = normalized.owner_id
  AND setting.setting_key = normalized.setting_key;

-- 旧审计目标的单段资源类型不能由当前 TreeCode 解码。
UPDATE public.audit_record
SET target_type = CASE target_type
    WHEN 'account' THEN 'IAM/ACCOUNT'
    WHEN 'role' THEN 'IAM/ROLE'
    WHEN 'file' THEN 'STORAGE/FILE'
    WHEN 'notification:email:sender' THEN 'NOTIFICATION/EMAIL/SENDERS'
    WHEN 'notification:email:template' THEN 'NOTIFICATION/EMAIL/TEMPLATES'
    WHEN 'NOTIFICATION/EMAIL/TEMPLATE' THEN 'NOTIFICATION/EMAIL/TEMPLATES'
    WHEN 'passkey:credential' THEN 'AUTHENTICATION/PASSKEY/CREDENTIALS'
    WHEN 'platform:user:profile' THEN 'PLATFORM:USERS:PROFILES'
END
WHERE target_type IN (
    'account', 'role', 'file', 'notification:email:sender',
    'notification:email:template', 'NOTIFICATION/EMAIL/TEMPLATE',
    'passkey:credential', 'platform:user:profile'
);
