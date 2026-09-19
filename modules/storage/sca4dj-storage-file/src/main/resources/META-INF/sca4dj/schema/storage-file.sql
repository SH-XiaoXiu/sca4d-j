CREATE TABLE storage_file (
    id BIGINT PRIMARY KEY,
    uploader_principal_id BIGINT NOT NULL,
    scene VARCHAR(64) NOT NULL,
    target_resource_type VARCHAR(128) NOT NULL,
    target_resource_id VARCHAR(128) NOT NULL,
    backend_id VARCHAR(64) NOT NULL,
    container_name VARCHAR(128) NOT NULL,
    upload_object_key VARCHAR(512) NOT NULL,
    object_key VARCHAR(512),
    filename VARCHAR(255) NOT NULL,
    declared_content_type VARCHAR(128) NOT NULL,
    declared_size_bytes BIGINT NOT NULL,
    content_type VARCHAR(128),
    size_bytes BIGINT,
    status VARCHAR(32) NOT NULL,
    upload_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ready_expires_at TIMESTAMP WITH TIME ZONE,
    upload_object_cleaned SMALLINT NOT NULL DEFAULT 0,
    delete_attempts INTEGER NOT NULL DEFAULT 0,
    next_delete_at TIMESTAMP WITH TIME ZONE,
    delete_lease_owner VARCHAR(128),
    delete_lease_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (backend_id, container_name, upload_object_key),
    UNIQUE (backend_id, container_name, object_key)
);

CREATE INDEX idx_storage_file_cleanup
    ON storage_file (status, next_delete_at, delete_lease_until);
CREATE INDEX idx_storage_file_upload_cleanup
    ON storage_file (upload_object_cleaned, upload_expires_at);

COMMENT ON TABLE storage_file IS '记录文件上传意图、业务用途和实际存储位置';
COMMENT ON COLUMN storage_file.id IS '文件记录全局标识';
COMMENT ON COLUMN storage_file.uploader_principal_id IS '创建文件的 Principal 标识快照；SYSTEM 使用固定标识 1';
COMMENT ON COLUMN storage_file.scene IS '文件在目标业务资源中的固定使用场景';
COMMENT ON COLUMN storage_file.target_resource_type IS '文件准备绑定或已经绑定的目标 Resource 类型';
COMMENT ON COLUMN storage_file.target_resource_id IS '文件准备绑定或已经绑定的目标 Resource 标识';
COMMENT ON COLUMN storage_file.backend_id IS '创建文件时选择的稳定存储实现标识';
COMMENT ON COLUMN storage_file.container_name IS '存储实现内保存对象的容器或 Bucket 名称';
COMMENT ON COLUMN storage_file.upload_object_key IS '客户端或服务端上传内容时使用的临时对象键';
COMMENT ON COLUMN storage_file.object_key IS '创建上传记录时预分配、完成后用于读取的正式对象键';
COMMENT ON COLUMN storage_file.filename IS '调用方提交并完成安全清理的原始文件名';
COMMENT ON COLUMN storage_file.declared_content_type IS '创建上传意图时声明的媒体类型';
COMMENT ON COLUMN storage_file.declared_size_bytes IS '创建上传意图时声明的文件字节数';
COMMENT ON COLUMN storage_file.content_type IS '完成上传后由存储实现确认的媒体类型';
COMMENT ON COLUMN storage_file.size_bytes IS '完成上传后由存储实现确认的文件字节数';
COMMENT ON COLUMN storage_file.status IS '文件从待上传到可绑定、已绑定或待清理的当前状态';
COMMENT ON COLUMN storage_file.upload_expires_at IS '临时上传意图和上传对象的过期时间';
COMMENT ON COLUMN storage_file.ready_expires_at IS '未及时绑定的 READY 文件过期时间';
COMMENT ON COLUMN storage_file.upload_object_cleaned IS '临时上传对象是否已经确认删除';
COMMENT ON COLUMN storage_file.delete_attempts IS '正式对象已经开始的删除尝试次数';
COMMENT ON COLUMN storage_file.next_delete_at IS '文件清理 Worker 下一次允许领取的时间';
COMMENT ON COLUMN storage_file.delete_lease_owner IS '当前删除租约的 Worker 标识';
COMMENT ON COLUMN storage_file.delete_lease_until IS '当前删除租约截止时间';
COMMENT ON COLUMN storage_file.created_at IS '文件上传意图创建时间';
COMMENT ON COLUMN storage_file.updated_at IS '文件状态最近更新时间，由数据库触发器维护';

CREATE TRIGGER set_storage_file_updated_at
    BEFORE UPDATE ON storage_file
    FOR EACH ROW EXECUTE FUNCTION sca4dj_set_updated_at();
