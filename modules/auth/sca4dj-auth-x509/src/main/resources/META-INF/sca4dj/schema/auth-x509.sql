CREATE TABLE auth_x509_credential (
    id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    fingerprint_sha256 CHAR(64) NOT NULL UNIQUE,
    certificate_der BYTEA NOT NULL,
    serial_number VARCHAR(128) NOT NULL,
    subject_dn TEXT NOT NULL,
    not_before TIMESTAMP WITH TIME ZONE NOT NULL,
    not_after TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_auth_x509_credential_account
    ON auth_x509_credential (account_id, created_at DESC);

COMMENT ON TABLE auth_x509_credential IS '保存 Account X.509 认证使用的公钥证书和生命周期状态，不保存私钥';
COMMENT ON COLUMN auth_x509_credential.id IS 'X.509 Credential 标识';
COMMENT ON COLUMN auth_x509_credential.account_id IS 'X.509 Credential 所属 Account 标识';
COMMENT ON COLUMN auth_x509_credential.fingerprint_sha256 IS 'DER 证书 SHA-256 小写十六进制指纹';
COMMENT ON COLUMN auth_x509_credential.certificate_der IS '不包含私钥的 X.509 公钥证书 DER';
COMMENT ON COLUMN auth_x509_credential.serial_number IS 'X.509 证书序列号十六进制文本';
COMMENT ON COLUMN auth_x509_credential.subject_dn IS 'X.509 Subject DN';
COMMENT ON COLUMN auth_x509_credential.not_before IS '证书生效时间';
COMMENT ON COLUMN auth_x509_credential.not_after IS '证书失效时间';
COMMENT ON COLUMN auth_x509_credential.status IS 'ACTIVE 可认证，REVOKED 已撤销';
COMMENT ON COLUMN auth_x509_credential.version IS '撤销时递增的乐观版本';
COMMENT ON COLUMN auth_x509_credential.created_at IS 'Credential 登记时间';
COMMENT ON COLUMN auth_x509_credential.updated_at IS 'Credential 最近更新时间';
COMMENT ON COLUMN auth_x509_credential.revoked_at IS '撤销时间，ACTIVE 时为空';

CREATE TRIGGER set_auth_x509_credential_updated_at
    BEFORE UPDATE ON auth_x509_credential
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();
