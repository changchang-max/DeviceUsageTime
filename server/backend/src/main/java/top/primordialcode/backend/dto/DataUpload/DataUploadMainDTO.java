package top.primordialcode.backend.dto.DataUpload;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class DataUploadMainDTO {

    private String userEmail;

    private Instant timestamp;

    private List<ApplicationDTO> applications;

    private StatisticsDTO statistics;
}