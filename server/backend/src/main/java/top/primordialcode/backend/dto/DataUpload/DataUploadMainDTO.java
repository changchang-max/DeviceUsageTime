package top.primordialcode.backend.dto.DataUpload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class DataUploadMainDTO {

    private String userEmail;

    @NotNull(message = "时间戳不能为空")
    private Instant timestamp;

    @Valid
    private List<ApplicationDTO> applications;

    @Valid
    private StatisticsDTO statistics;
}
