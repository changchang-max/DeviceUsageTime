package top.primordialcode.backend.dto.DataUpload;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import top.primordialcode.backend.utils.DataDateUtil;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Instant 字段的宽松反序列化器(用于数据上传的 timestamp)
 *
 * 客户端可能上传缺失时区或携带错误时区的时间字符串, 直接按标准 Instant
 * 反序列化会抛 DateTimeParseException 导致整次上传失败。本反序列化器:
 * - 标准 ISO-8601(带时区: Z / ±HH:MM) 正常解析;
 * - 无时区(如 2026-09-04T16:22:58.210)或时区格式错误(如 +99:00、+8:00):
 *   提取其中的本地日期时间部分, 按默认时区(Asia/Shanghai, 见 DataDateUtil)解析,
 *   并在所属 DTO 上标记 timestampFallbackUsed=true 与原始字符串,
 *   供服务层将该次上传标记为 error 并记录上传用户与上传信息日志。
 */
public class FlexibleInstantDeserializer extends JsonDeserializer<Instant> {

    /** 本地日期时间部分: 兼容 'T' 或空格分隔, 秒与小数秒可选 */
    private static final Pattern LOCAL_DATE_TIME_PART = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2})[T ](\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?)");

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }

        String text = parser.getValueAsString();
        if (text == null || text.isBlank()) {
            // 非文本或空串, 无法做时区补救, 按 null 处理
            return null;
        }

        String trimmed = text.trim();
        Instant instant = null;
        boolean fallback = false;

        // 1) 标准 ISO-8601(带时区: Z / ±HH:MM)
        try {
            instant = OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException e) {
            // 2) 缺失时区或时区格式错误: 提取本地日期时间部分, 按默认时区解析
            Matcher matcher = LOCAL_DATE_TIME_PART.matcher(trimmed);
            if (matcher.find()) {
                try {
                    LocalDateTime localDateTime = LocalDateTime.parse(
                            matcher.group(1) + "T" + matcher.group(2));
                    instant = localDateTime.atZone(DataDateUtil.DATA_ZONE).toInstant();
                    fallback = true;
                } catch (DateTimeParseException inner) {
                    throw new IOException("时间戳格式错误，无法解析: " + trimmed, e);
                }
            } else {
                throw new IOException("时间戳格式错误，无法解析: " + trimmed, e);
            }
        }

        markFallback(parser, trimmed, fallback);
        return instant;
    }

    /**
     * 把原始时间戳字符串与降级标记写回所属DTO, 供服务层记录日志
     */
    private void markFallback(JsonParser parser, String raw, boolean fallback) {
        Object owner = parser.getCurrentValue();
        if (owner instanceof DataUploadMainDTO dto) {
            dto.setTimestampRaw(raw);
            dto.setTimestampFallbackUsed(fallback);
        }
    }
}
