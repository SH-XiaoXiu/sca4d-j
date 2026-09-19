package com.sca4d.core.api.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class DataOpsTest {

    @Test
    @DisplayName("Processes Text Without Hiding Required Value Failures")
    void processesTextWithoutHidingRequiredValueFailures() {
        assertEquals("alpha", DataOps.s("  AlphaBeta  ").strip().lower().cut(5).get());
        assertEquals("ALPHA", DataOps.s("alpha").upper().get());
        assertEquals("", DataOps.s(null).get());
        assertNull(DataOps.s("  ").nil().opt());
        assertEquals("fallback", DataOps.s(" ").or("fallback").get());
        assertFalse(DataOps.s(null).has());

        DataAssertionException exception = assertThrows(
                DataAssertionException.class,
                () -> DataOps.s(" ").req("name"));
        assertEquals("name", exception.field());
        assertNull(exception.safeSample());
    }

    @Test
    @DisplayName("Propagates And Asserts Generic Values")
    void propagatesAndAssertsGenericValues() {
        assertNull(DataOps.v(null).map(Object::toString).get());
        assertEquals("42", DataOps.v(42).str().get());
        assertEquals(new BigDecimal("12.30"), DataOps.v("12.30").dec().get());
        assertEquals(42L, DataOps.v("42").lng().get());
        assertEquals(true, DataOps.v("yes").bool().get());
        assertEquals(
                new BigDecimal("12.30"),
                DataOps.v("12.30").min(new BigDecimal("10"), "amount").get());
        assertEquals("value", DataOps.v((Object) "value").as(String.class, "kind").get());
        assertEquals("fallback", DataOps.<String>v(null).or("fallback"));

        assertEquals(
                "amount",
                assertThrows(
                        DataAssertionException.class,
                        () -> DataOps.v("bad").min(BigDecimal.ZERO, "amount"))
                        .field());
        assertEquals(
                "enabled",
                assertThrows(
                        DataAssertionException.class,
                        () -> DataOps.v(false).ok(Boolean::booleanValue, "enabled", "必须启用"))
                        .field());
        assertEquals(
                "kind",
                assertThrows(
                        DataAssertionException.class,
                        () -> DataOps.v((Object) 1).as(String.class, "kind"))
                        .field());
    }

    @Test
    @DisplayName("Copies And Returns Unmodifiable Sequences")
    @SuppressWarnings("DataFlowIssue")
    void copiesAndReturnsUnmodifiableSequences() {
        List<String> source = new ArrayList<>(List.of(" a ", "b"));
        DataOps.SeqPipe<String> sequence = DataOps.seq(source);
        source.add("later");

        assertEquals(
                List.of("A", "B"),
                sequence.map(value -> DataOps.s(value).strip().upper().get())
                        .filter(value -> !value.isEmpty())
                        .list());
        assertEquals(2L, sequence.size());
        assertEquals(0L, DataOps.seq(null).size());
        assertThrows(UnsupportedOperationException.class, () -> sequence.list().add("blocked"));
        assertEquals(
                "records",
                assertThrows(
                        DataAssertionException.class,
                        () -> DataOps.seq(List.of()).req("records"))
                        .field());
    }

    @Test
    @DisplayName("Reads Binary Values With Bounds Byte Order And Checksums")
    void readsBinaryValuesWithBoundsByteOrderAndChecksums() {
        byte[] source = {(byte) 0xAB, 0x34, 0x12, 0x01, 0x02, 0x03, 0x04};
        DataOps.BytePipe bytes = DataOps.bytes(source)
                .requireSize(7, "frame")
                .requireSizeBetween(7, 16, "frame");
        source[0] = 0;

        assertEquals(0xAB, bytes.unsignedByte(0, "sync"));
        assertEquals(0x1234, bytes.readUnsignedShort(1, ByteOrder.LITTLE_ENDIAN, "value"));
        assertEquals(0x01020304, bytes.readInt(3, ByteOrder.BIG_ENDIAN, "counter"));
        assertEquals(0x01020304L, bytes.readUnsignedInt(3, ByteOrder.BIG_ENDIAN, "counter"));
        assertEquals("3412", bytes.slice(1, 2, "value").hex());
        assertEquals(0x8D, bytes.xor8(0, 3, "checksum"));
        assertEquals(0xF1, bytes.sum8(0, 3, "checksum"));
        assertArrayEquals(
                new byte[] {(byte) 0xAB, 0x34, 0x12, 0x01, 0x02, 0x03, 0x04},
                bytes.get());

        byte[] checksumInput = "123456789".getBytes(StandardCharsets.US_ASCII);
        assertEquals(0xCBF43926L, DataOps.bytes(checksumInput).crc32(0, checksumInput.length, "crc"));
        assertEquals(0xE3069283L, DataOps.bytes(checksumInput).crc32c(0, checksumInput.length, "crc"));

        assertEquals(
                "frame",
                assertThrows(
                        DataAssertionException.class,
                        () -> bytes.requireSize(6, "frame"))
                        .field());
        assertEquals(
                "value",
                assertThrows(
                        DataAssertionException.class,
                        () -> bytes.readLong(1, ByteOrder.BIG_ENDIAN, "value"))
                        .field());
    }

    @Test
    @DisplayName("Builds Fixed Binary Frames And Decodes Text Strictly")
    void buildsFixedBinaryFramesAndDecodesTextStrictly() {
        DataOps.ByteBuilder builder = DataOps.bytes(15)
                .writeUnsignedByte(0, 0x55, "sync")
                .writeUnsignedShort(1, 0x1234, ByteOrder.LITTLE_ENDIAN, "register")
                .writeInt(3, 0x01020304, ByteOrder.BIG_ENDIAN, "counter")
                .writeLong(7, 0x010203040506L, ByteOrder.LITTLE_ENDIAN, "messageId");

        byte[] result = builder.get();
        assertEquals(0x55, DataOps.bytes(result).unsignedByte(0, "sync"));
        assertEquals(0x1234, DataOps.bytes(result).readUnsignedShort(1, ByteOrder.LITTLE_ENDIAN, "register"));
        assertEquals(0x70, builder.xor8(0, 15, "checksum"));
        result[0] = 0;
        assertEquals(0x55, builder.get()[0]);

        assertEquals(
                "设备",
                DataOps.bytes("设备".getBytes(StandardCharsets.UTF_8))
                        .text(StandardCharsets.UTF_8, "name")
                        .get());
        assertEquals(
                "name",
                assertThrows(
                        DataAssertionException.class,
                        () -> DataOps.bytes(new byte[] {(byte) 0xC3, 0x28})
                                .text(StandardCharsets.UTF_8, "name"))
                        .field());
    }

    @Test
    @DisplayName("Parses Json Strictly And Maps Objects")
    @SuppressWarnings("DataFlowIssue") // 本测试故意修改不可变结果，验证它会拒绝写入。
    void parsesJsonStrictlyAndMapsObjects() {
        assertNull(DataOps.j(null).get());
        assertEquals("{}", DataOps.j(" ").orObj().get());
        assertThrows(DataAssertionException.class, () -> DataOps.j("not-json"));
        assertThrows(DataAssertionException.class, () -> DataOps.j("[]").req("payload"));

        DataOps.JsonPipe json = DataOps.j("{\"name\":\"alpha\",\"amount\":12.5}");
        assertEquals("ALPHA", json.text("name").upper().get());
        assertEquals(new BigDecimal("12.5"), json.dec("amount").get());
        SamplePayload payload = DataOps.j("{\"name\":\"alpha\"}")
                .as(SamplePayload.class, "payload")
                .get();
        assertNotNull(payload);
        assertEquals("alpha", payload.getName());

        DataOps.JsonPipe binaryJson = DataOps.json(
                ("{\"meta\":{\"version\":1},\"enabled\":true,"
                        + "\"amount\":12.30,\"name\":\"device\",\"id\":9007199254740991,"
                        + "\"data\":{\"values\":[1,null]}}")
                        .getBytes(StandardCharsets.UTF_8));
        assertEquals(1, binaryJson.requiredInteger("meta.version"));
        assertEquals(true, binaryJson.requiredBoolean("enabled"));
        assertEquals(new BigDecimal("12.3"), binaryJson.requiredDecimal("amount"));
        assertEquals("device", binaryJson.requiredText("name"));
        assertEquals(9007199254740991L, binaryJson.requiredLong("id"));
        assertEquals(
                java.util.Arrays.asList(1L, null),
                binaryJson.requiredObject("data").objectValues().get("values"));
        String binaryJsonText = binaryJson.get();
        assertNotNull(binaryJsonText);
        assertArrayEquals(binaryJsonText.getBytes(StandardCharsets.UTF_8), binaryJson.bytes());
        assertThrows(
                UnsupportedOperationException.class,
                () -> binaryJson.requiredObject("data").objectValues().put("blocked", true));
        assertThrows(DataAssertionException.class, () -> binaryJson.requiredInteger("amount"));
        assertEquals(
                "payload",
                assertThrows(
                        DataAssertionException.class,
                        () -> DataOps.j("{\"name\":{}}").as(StrictPayload.class, "payload"))
                        .field());
    }

    @Test
    @DisplayName("Recursively Merges Objects And Protects Published Fields")
    void recursivelyMergesObjectsAndProtectsPublishedFields() {
        String merged = DataOps.j("{\"operator\":\"system\",\"nested\":{\"left\":1}}")
                .pub("operator")
                .merge("{\"operator\":\"system\",\"nested\":{\"right\":2}}")
                .get();

        assertEquals(
                "{\"operator\":\"system\",\"nested\":{\"left\":1,\"right\":2}}",
                merged);
        DataAssertionException exception = assertThrows(
                DataAssertionException.class,
                () -> DataOps.j("{\"operator\":\"system\"}")
                        .pub("operator")
                        .merge("{\"operator\":\"other\"}"));
        assertEquals("operator", exception.field());
        assertNull(exception.safeSample());
        assertThrows(DataAssertionException.class, () -> DataOps.j("[]").merge("{}"));
        assertThrows(DataAssertionException.class, () -> DataOps.j("{}").merge("[]"));
    }

    @Test
    @DisplayName("Writes Removes And Atomically Renames Dotted Paths")
    void writesRemovesAndAtomicallyRenamesDottedPaths() {
        DataOps.JsonPipe base = DataOps.j("{}").put("profile.name", "Ada");
        assertEquals("{\"profile\":{\"name\":\"Ada\"}}", base.get());
        assertEquals(
                "{\"profile\":{}}",
                base.remove("profile.name").get());
        assertEquals(
                "{\"profile\":{\"displayName\":\"Ada\"}}",
                base.rename("profile.name", "profile.displayName").get());
        assertEquals(base.get(), base.rename("missing", "target").get());
        assertThrows(IllegalArgumentException.class, () -> base.put("profile..name", "bad"));

        DataOps.JsonPipe blocked = DataOps.j("{\"source\":1,\"target\":\"scalar\"}");
        assertThrows(
                DataAssertionException.class,
                () -> blocked.rename("source", "target.value"));
        assertEquals("{\"source\":1,\"target\":\"scalar\"}", blocked.get());
        assertThrows(IllegalArgumentException.class, () -> base.rename("profile", "profile.name.next"));
        assertThrows(IllegalArgumentException.class, () -> base.rename("profile.name", "profile"));
        assertEquals("{\"profile\":{\"name\":\"Ada\"}}", base.get());
        assertThrows(DataAssertionException.class, () -> DataOps.j("{\"profile\":1}").put("profile.name", "Ada"));
    }

    @Test
    @DisplayName("Edits Nested Objects And Object Array Elements")
    void editsNestedObjectsAndObjectArrayElements() {
        DataOps.JsonPipe edited = DataOps.j("{\"items\":[{\"name\":\"a\"},2,null,{\"name\":\"b\"}]}")
                .edit("meta", value -> value.put("count", 2))
                .list("items", value -> value.rename("name", "label"));

        assertEquals(
                "{\"items\":[{\"label\":\"a\"},2,null,{\"label\":\"b\"}],\"meta\":{\"count\":2}}",
                edited.get());
        assertEquals(edited.get(), edited.list("missing", value -> value).get());
        assertThrows(DataAssertionException.class, () -> edited.list("meta", value -> value));
        assertThrows(DataAssertionException.class, () -> edited.edit("items", value -> value));
    }

    @Test
    @DisplayName("Builds Strict Json Objects AndSnapshots Mutable Inputs")
    void buildsStrictJsonObjectsAndSnapshotsMutableInputs() {
        Map<String, Object> mutable = new LinkedHashMap<>();
        mutable.put("count", 1);
        DataOps.JsonObjectPipe object = DataOps.obj()
                .put("name", "alpha")
                .opt("ignored", null)
                .put("snapshot", mutable)
                .json("detail", "[1,2]");
        mutable.put("count", 2);

        assertEquals(
                "{\"name\":\"alpha\",\"snapshot\":{\"count\":1},\"detail\":[1,2]}",
                object.req("payload").get());
        assertThrows(DataAssertionException.class, () -> DataOps.obj().req("payload"));
        assertThrows(DataAssertionException.class, () -> DataOps.obj().json("detail", "bad"));
        assertEquals("{}", DataOps.obj().json("ignored", " ").get());
        assertArrayEquals(object.get().getBytes(StandardCharsets.UTF_8), object.bytes());
    }

    @Test
    @DisplayName("Generates Ids Summaries Arithmetic And ResourceUrls")
    @SuppressWarnings("ConstantValue")
    void generatesIdsSummariesArithmeticAndResourceUrls() {
        assertEquals(12, DataOps.id().get().length());
        assertEquals(8, DataOps.id().get(8).length());
        assertEquals(16, DataOps.id().get(16).length());
        assertTrue(DataOps.id("job-").get().matches("job-[a-f0-9]{12}"));
        assertThrows(IllegalArgumentException.class, () -> DataOps.id().get(0));

        assertNull(DataOps.add(null, null));
        assertEquals(2L, DataOps.add(null, 2L));
        assertEquals(3L, DataOps.add(1L, 2L));
        assertTrue(DataOps.nil(null));
        assertTrue(DataOps.nil(List.of()));
        assertEquals(
                "{\"stageCode\":\"FETCH\",\"detail\":{\"records\":3}}",
                DataOps.summary("FETCH", "{\"records\":3}"));
        assertEquals(
                "http://localhost/root/path/file.json",
                DataOps.resourceUrl("http://localhost/root/", "/path/file.json", "base", "path"));
        assertEquals(
                "https://cdn.example/file.json",
                DataOps.resourceUrl(
                        "http://localhost/root",
                        "https://cdn.example/file.json",
                        "base",
                        "path"));
        assertEquals(
                "http://localhost/root/http:relative",
                DataOps.resourceUrl("http://localhost/root", "http:relative", "base", "path"));
    }

    @SuppressWarnings("unused")
    public static final class SamplePayload {

        private String name;

        public SamplePayload() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @SuppressWarnings("unused")
    public static final class StrictPayload {

        private String name;

        public StrictPayload() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            if (name == null) {
                throw new IllegalArgumentException("name required");
            }
            this.name = name;
        }
    }
}
