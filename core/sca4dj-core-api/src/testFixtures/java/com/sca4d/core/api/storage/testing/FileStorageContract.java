package com.sca4d.core.api.storage.testing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.storage.FileStorage;
import com.sca4d.core.api.storage.StorageObject;
import com.sca4d.core.api.storage.StorageObjectMetadata;
import com.sca4d.core.api.storage.StorageUpload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;

/** 对所有 `FileStorage` 实现执行相同的对象生命周期检查。 */
public abstract class FileStorageContract {

    /**
     * 返回已经通过可用性检查的存储实现。
     *
     * @return 当前测试使用的存储实现
     */
    protected abstract FileStorage storage();

    @Test
    @DisplayName("生成上传和读取地址，并完成对象写入、转正、读取及幂等删除")
    void followsTheFileStorageLifecycle() throws Exception {
        FileStorage storage = storage();
        StorageObject uploadObject = new StorageObject(storage.defaultContainer(), "contract/uploads/source.png");
        StorageObject storedObject = new StorageObject(storage.defaultContainer(), "contract/objects/target.png");
        byte[] content = new byte[] {
                (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0
        };
        Instant expiresAt = Instant.now().plusSeconds(300);

        StorageUpload upload = storage.prepareUpload(101L, uploadObject, "image/png", content.length, expiresAt);
        assertTrue(upload.url().isAbsolute());
        assertFalse(upload.headers().isEmpty());

        storage.write(uploadObject, "image/png", content.length, new ByteArrayInputStream(content));
        StorageObjectMetadata metadata = storage.inspect(uploadObject).orElseThrow();
        assertEquals("image/png", metadata.contentType());
        assertEquals(content.length, metadata.sizeBytes());

        storage.promote(uploadObject, storedObject);
        storage.promote(uploadObject, storedObject);
        assertTrue(storage.inspect(uploadObject).isEmpty());
        try (InputStream input = storage.open(storedObject)) {
            assertArrayEquals(content, input.readAllBytes());
        }

        assertNotNull(storage.prepareAccess(101L, storedObject, expiresAt));
        storage.delete(storedObject);
        storage.delete(storedObject);
        assertTrue(storage.inspect(storedObject).isEmpty());
    }

    @Test
    @DisplayName("拒绝短于或长于声明字节数的服务端内容流")
    void rejectsMismatchedServerContentLength() {
        FileStorage storage = storage();
        StorageObject shortObject = new StorageObject(
                storage.defaultContainer(),
                "contract/uploads/short.bin");
        StorageObject longObject = new StorageObject(
                storage.defaultContainer(),
                "contract/uploads/long.bin");

        assertThrows(
                IllegalArgumentException.class,
                () -> storage.write(
                        shortObject,
                        "application/octet-stream",
                        3,
                        new ByteArrayInputStream(new byte[] {1, 2})));
        assertTrue(storage.inspect(shortObject).isEmpty());

        assertThrows(
                IllegalArgumentException.class,
                () -> storage.write(
                        longObject,
                        "application/octet-stream",
                        2,
                        new ByteArrayInputStream(new byte[] {1, 2, 3})));
        assertTrue(storage.inspect(longObject).isEmpty());
    }
}
