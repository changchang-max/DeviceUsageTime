package top.primordialcode.backend.vo.data;

import lombok.Data;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;

import java.time.Instant;
import java.util.List;

/**
 * 实时数据返回VO
 */
@Data
public class RealtimeDataVO {

    /**
     * 用户ID(本系统中以用户邮箱作为全局唯一标识)
     */
    private String userId;

    /**
     * 数据时间戳
     */
    private Instant timestamp;

    /**
     * 数据所属日期(YYYY-MM-DD)
     */
    private String date;

    /**
     * 应用使用情况列表
     */
    private List<ApplicationDTO> applications;

    /**
     * 统计数据
     */
    private StatisticsDTO statistics;
}
