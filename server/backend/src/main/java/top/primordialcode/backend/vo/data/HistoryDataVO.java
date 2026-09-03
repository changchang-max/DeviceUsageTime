package top.primordialcode.backend.vo.data;

import lombok.Data;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;

import java.util.List;

/**
 * 历史数据返回VO
 */
@Data
public class HistoryDataVO {

    /**
     * 数据所属日期(YYYY-MM-DD)
     */
    private String date;

    /**
     * 应用使用情况汇总列表
     */
    private List<HistoryApplicationVO> applications;

    /**
     * 统计数据
     */
    private StatisticsDTO statistics;
}
