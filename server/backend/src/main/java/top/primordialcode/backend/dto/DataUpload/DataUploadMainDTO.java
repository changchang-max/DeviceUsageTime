package top.primordialcode.backend.dto.DataUpload;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class DataUploadMainDTO {

    private String userEmail;

    @NotNull(message = "时间戳不能为空")
    // 告诉 Jackson：当 JSON 里的 timestamp 字段被反序列化成 Instant 时，
    // 不要使用 Jackson 默认的解析方式，而是交给你自己写的 FlexibleInstantDeserializer 来解析。
    @JsonDeserialize(using = FlexibleInstantDeserializer.class)
    private Instant timestamp;

    /**
     * 时间戳JSON原文。
     * 仅当 timestamp 缺失时区或时区格式错误被降级解析时才有意义,
     * 供服务层记录日志使用, 不参与JSON序列化。
     */
    @JsonIgnore
    private String timestampRaw;

    /**
     * 时间戳是否被降级解析(true: 原始字符串缺失时区或包含错误时区,
     * 已按默认时区处理)。供服务层将该次上传标记为error记录日志, 不参与JSON序列化。
     */
    @JsonIgnore
    private boolean timestampFallbackUsed;

    @Valid
    private List<ApplicationDTO> applications;

    @Valid
    private StatisticsDTO statistics;
}
