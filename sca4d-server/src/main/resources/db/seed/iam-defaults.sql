UPDATE iam_permission
SET granted_by_default = code IN (
        'realtime',
        'realtime:subscription',
        'realtime:subscription:read',
        'realtime:sse',
        'realtime:sse:connect'
    ),
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE deleted = 0
  AND granted_by_default IS DISTINCT FROM (code IN (
        'realtime',
        'realtime:subscription',
        'realtime:subscription:read',
        'realtime:sse',
        'realtime:sse:connect'
    ));

INSERT INTO iam_role (id, tenant_id, code, name, status, built_in)
SELECT nextval('iam_seed_id_seq'), NULL, 'PLATFORM_ADMIN', '平台管理员', 'ACTIVE', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM iam_role
    WHERE tenant_id IS NULL
      AND code = 'PLATFORM_ADMIN'
      AND deleted = 0
);

UPDATE iam_role
SET name = '平台管理员',
    built_in = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE tenant_id IS NULL
  AND code = 'PLATFORM_ADMIN'
  AND deleted = 0
  AND (name IS DISTINCT FROM '平台管理员' OR built_in IS DISTINCT FROM TRUE);

DELETE FROM iam_role_permission relation
USING iam_permission permission
WHERE relation.permission_id = permission.id
  AND permission.granted_by_default = TRUE;

WITH managed_roles AS (
    SELECT id, tenant_id
    FROM iam_role
    WHERE built_in = TRUE
      AND deleted = 0
      AND ((tenant_id IS NULL AND code = 'PLATFORM_ADMIN')
           OR (tenant_id IS NOT NULL AND code = 'TENANT_ADMIN'))
), desired_permissions AS (
    SELECT role.id AS role_id, permission.id AS permission_id
    FROM managed_roles role
    INNER JOIN iam_permission permission
            ON permission.deleted = 0
           AND permission.granted_by_default = FALSE
           AND (role.tenant_id IS NULL
                OR permission.code = 'tenant'
                OR permission.code LIKE 'tenant:%')
)
DELETE FROM iam_role_permission relation
USING managed_roles role
WHERE relation.role_id = role.id
  AND NOT EXISTS (
      SELECT 1
      FROM desired_permissions desired
      WHERE desired.role_id = relation.role_id
        AND desired.permission_id = relation.permission_id
  );

WITH managed_roles AS (
    SELECT id, tenant_id
    FROM iam_role
    WHERE built_in = TRUE
      AND deleted = 0
      AND ((tenant_id IS NULL AND code = 'PLATFORM_ADMIN')
           OR (tenant_id IS NOT NULL AND code = 'TENANT_ADMIN'))
), desired_permissions AS (
    SELECT role.id AS role_id, permission.id AS permission_id
    FROM managed_roles role
    INNER JOIN iam_permission permission
            ON permission.deleted = 0
           AND permission.granted_by_default = FALSE
           AND (role.tenant_id IS NULL
                OR permission.code = 'tenant'
                OR permission.code LIKE 'tenant:%')
)
INSERT INTO iam_role_permission (id, role_id, permission_id)
SELECT nextval('iam_seed_id_seq'), desired.role_id, desired.permission_id
FROM desired_permissions desired
WHERE NOT EXISTS (
    SELECT 1
    FROM iam_role_permission relation
    WHERE relation.role_id = desired.role_id
      AND relation.permission_id = desired.permission_id
)
ORDER BY desired.role_id, desired.permission_id;
