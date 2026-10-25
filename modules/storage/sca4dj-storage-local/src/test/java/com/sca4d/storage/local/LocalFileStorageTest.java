package com.sca4d.storage.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.storage.StorageObject;
import com.sca4d.core.api.storage.StorageTransferSigner;
import com.sca4d.core.api.storage.testing.FileStorageContract;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** 验证本地目录中对象的写入、删除、精确字节数和路径逃逸防护。 */
class LocalFileStorageTest extends FileStorageContract {

    @TempDir
    private Path root;

    @TempDir
    private Path outside;

    private LocalFileStorage storage;

    @BeforeEach
    void createStorage() {
        LocalStorageProperties properties = new LocalStorageProperties();
        properties.setRoot(root);
        storage = new LocalFileStorage(properties, new FixedTransferSigner());
        storage.checkAvailable();
    }

    @Override
    protected LocalFileStorage storage() {
        return storage;
    }

    @Test
    @DisplayName("拒绝绝对对象键、上级目录和任一路径段中的符号链接")
    void rejectsAbsoluteTraversalAndSymbolicLinkPaths() throws Exception {
        Files.writeString(outside.resolve("secret.txt"), "outside");
        Files.createSymbolicLink(root.resolve("linked"), outside);

        assertThatThrownBy(() -> storage.inspect(new StorageObject("files", "/etc/passwd")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.inspect(new StorageObject("files", "safe/../secret.txt")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.inspect(new StorageObject("files", "linked/secret.txt")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(outside.resolve("secret.txt"))).isEqualTo("outside");
    }

    @Test
    @DisplayName("只在内容字节数与申请值完全相同时替换上传对象")
    void acceptsOnlyTheExactDeclaredLength() throws Exception {
        StorageObject object = new StorageObject("files", "uploads/exact.bin");

        assertThatThrownBy(
                () -> storage.write(
                        object,
                        "application/octet-stream",
                        3,
                        new ByteArrayInputStream(new byte[] {1, 2})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("上传内容字节数与申请不一致");
        assertThatThrownBy(
                () -> storage.write(
                        object,
                        "application/octet-stream",
                        3,
                        new ByteArrayInputStream(new byte[] {1, 2, 3, 4})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("上传内容字节数与申请不一致");
        assertThat(Files.exists(root.resolve("uploads/exact.bin"))).isFalse();

        storage.write(
                object,
                "application/octet-stream",
                3,
                new ByteArrayInputStream(new byte[] {1, 2, 3}));
        storage.write(
                object,
                "application/octet-stream",
                3,
                new ByteArrayInputStream(new byte[] {4, 5, 6}));
        try (var input = storage.open(object)) {
            assertThat(input.readAllBytes()).containsExactly(4, 5, 6);
        }
        try (var paths = Files.walk(root)) {
            assertThat(paths.filter(path -> {
                Path filename = path.getFileName();
                return filename != null && filename.toString().endsWith(".part");
            }))
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("没有文件扩展名时仍保留媒体类型，并随对象转正和删除")
    void preservesContentTypeWithoutAFileExtension() throws Exception {
        StorageObject upload = new StorageObject("files", "uploads/without-extension");
        StorageObject stored = new StorageObject("files", "objects/without-extension");
        byte[] content = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);

        storage.write(upload, "application/pdf", content.length, new ByteArrayInputStream(content));
        assertThat(storage.inspect(upload).orElseThrow().contentType()).isEqualTo("application/pdf");

        storage.promote(upload, stored);
        storage.promote(upload, stored);
        assertThat(storage.inspect(stored).orElseThrow().contentType()).isEqualTo("application/pdf");
        storage.delete(stored);

        try (var paths = Files.walk(root)) {
            assertThat(paths.filter(Files::isRegularFile)).isEmpty();
        }
    }

    /** 为本地存储契约测试返回固定的 App 传输地址。 */
    private static class FixedTransferSigner implements StorageTransferSigner {
        @Override
        public @NotNull URI uploadUrl(long fileId, @NotNull Instant expiresAt) {
            return URI.create("http://localhost/files/transfers/uploads?token=" + fileId);
        }

        @Override
        public @NotNull URI accessUrl(long fileId, @NotNull Instant expiresAt) {
            return URI.create("http://localhost/files/transfers/objects?token=" + fileId);
        }
    }
}
