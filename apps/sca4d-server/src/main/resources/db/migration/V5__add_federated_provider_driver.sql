-- 联邦 Provider 协议配置增加资料转换 Driver 编码。已有配置的 driver_code 为空，继续使用标准 OIDC claims 或 profile_mapping。
ALTER TABLE auth_federated_provider_configuration ADD COLUMN driver_code VARCHAR(64);

COMMENT ON COLUMN auth_federated_provider_configuration.driver_code IS '转换外部资料的 Driver 编码，冒号分隔的大写节点；为空时使用标准 OIDC claims 或 profile_mapping';
