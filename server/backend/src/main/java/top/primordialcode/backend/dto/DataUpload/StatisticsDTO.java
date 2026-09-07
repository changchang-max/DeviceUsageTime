package top.primordialcode.backend.dto.DataUpload;

import lombok.Data;

@Data
public class StatisticsDTO {

    private Long keyboardCount;

    private Long mouseClickCount;

    private Double mouseDistance;

    /**
     * 客户端程序今日运行总时长(秒) ≈ 设备总使用时长/被监控时长。
     * 由客户端每秒自行累计并上传，用于前端"今日应用使用时长"卡片的总时长，
     * 而不是把所有应用时长相加(多应用并发运行时会成倍虚高)。
     * 旧客户端上传的数据可能缺失该字段，为null。
     */
    private Long totalDuration;
}