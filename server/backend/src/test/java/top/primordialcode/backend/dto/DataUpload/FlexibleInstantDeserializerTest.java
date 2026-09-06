package top.primordialcode.backend.dto.DataUpload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.primordialcode.backend.utils.DataDateUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("数据上传时间戳宽松解析测试")
class FlexibleInstantDeserializerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    }

    private DataUploadMainDTO parse(String json) throws Exception {
        return objectMapper.readValue(json, DataUploadMainDTO.class);
    }

    @Test
    @DisplayName("无时区时间戳: 按默认时区(Asia/Shanghai)解析并标记降级")
    void testMissingZoneUsesDefaultZone() throws Exception {
        // 该字符串此前会直接抛 DateTimeParseException
        String raw = "2026-09-04T16:22:58.210";
        DataUploadMainDTO dto = parse("{\"timestamp\":\"" + raw + "\"}");

        Instant expected = LocalDateTime.parse(raw)
                .atZone(DataDateUtil.DATA_ZONE).toInstant();
        assertEquals(expected, dto.getTimestamp());
        assertTrue(dto.isTimestampFallbackUsed());
        assertEquals(raw, dto.getTimestampRaw());
    }

    @Test
    @DisplayName("空格分隔的无时区时间戳: 同样按默认时区解析并标记降级")
    void testMissingZoneWithSpaceSeparator() throws Exception {
        DataUploadMainDTO dto = parse("{\"timestamp\":\"2026-09-04 16:22:58\"}");

        Instant expected = LocalDateTime.parse("2026-09-04T16:22:58")
                .atZone(DataDateUtil.DATA_ZONE).toInstant();
        assertEquals(expected, dto.getTimestamp());
        assertTrue(dto.isTimestampFallbackUsed());
    }

    @Test
    @DisplayName("携带错误时区(+99:00)的时间戳: 忽略错误时区, 按默认时区解析并标记降级")
    void testWrongZoneUsesDefaultZone() throws Exception {
        DataUploadMainDTO dto = parse("{\"timestamp\":\"2026-09-04T16:22:58.210+99:00\"}");

        Instant expected = LocalDateTime.parse("2026-09-04T16:22:58.210")
                .atZone(DataDateUtil.DATA_ZONE).toInstant();
        assertEquals(expected, dto.getTimestamp());
        assertTrue(dto.isTimestampFallbackUsed());
    }

    @Test
    @DisplayName("标准时区(+08:00)时间戳: 正常解析, 不标记降级")
    void testValidOffsetParsedNormally() throws Exception {
        String raw = "2026-09-04T16:22:58.210+08:00";
        DataUploadMainDTO dto = parse("{\"timestamp\":\"" + raw + "\"}");

        assertEquals(OffsetDateTime.parse(raw).toInstant(), dto.getTimestamp());
        assertFalse(dto.isTimestampFallbackUsed());
    }

    @Test
    @DisplayName("UTC(Z)时间戳: 正常解析, 不标记降级")
    void testUtcParsedNormally() throws Exception {
        String raw = "2026-09-04T16:22:58.210Z";
        DataUploadMainDTO dto = parse("{\"timestamp\":\"" + raw + "\"}");

        assertEquals(OffsetDateTime.parse(raw).toInstant(), dto.getTimestamp());
        assertFalse(dto.isTimestampFallbackUsed());
    }

    @Test
    @DisplayName("json为null: 时间戳为null, 不标记降级")
    void testNullTimestamp() throws Exception {
        DataUploadMainDTO dto = parse("{\"timestamp\":null}");

        assertNull(dto.getTimestamp());
        assertFalse(dto.isTimestampFallbackUsed());
    }

    @Test
    @DisplayName("完全无法识别的时间戳: 保持报错(400)")
    void testGarbageTimestampStillFails() {
        assertThrows(Exception.class,
                () -> parse("{\"timestamp\":\"not-a-timestamp\"}"));
    }
}
