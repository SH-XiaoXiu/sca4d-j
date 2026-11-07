-- 认证域键统一为大写：把脚手架自带的 platform、open、iot-device 迁移为 PLATFORM、OPEN、DEVICE。
-- 应用自定义的认证域键不在此处理，由应用自己的迁移改为大写。
UPDATE iam_account
SET realm_key = CASE realm_key
    WHEN 'platform' THEN 'PLATFORM'
    WHEN 'open' THEN 'OPEN'
    WHEN 'iot-device' THEN 'DEVICE'
END
WHERE realm_key IN ('platform', 'open', 'iot-device');

UPDATE iam_account_identifier
SET realm_key = CASE realm_key
    WHEN 'platform' THEN 'PLATFORM'
    WHEN 'open' THEN 'OPEN'
    WHEN 'iot-device' THEN 'DEVICE'
END
WHERE realm_key IN ('platform', 'open', 'iot-device');

COMMENT ON COLUMN iam_account.realm_key IS '账号所属认证域的稳定键，仅含大写字母、数字和下划线';
COMMENT ON COLUMN iam_account_identifier.realm_key IS '登录标识所属认证域的稳定键，仅含大写字母、数字和下划线';
