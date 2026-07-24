package top.primordialcode.backend.dto.DataUpload;

import lombok.Data;

@Data
public class StatisticsDTO {

    private Long keyboardCount;

    private Long mouseClickCount;

    private Double mouseDistance;
}