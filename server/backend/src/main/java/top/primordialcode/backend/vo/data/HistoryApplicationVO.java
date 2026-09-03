package top.primordialcode.backend.vo.data;

import lombok.Data;

import java.util.List;

/**
 * 历史数据-单个应用汇总VO
 */
@Data
public class HistoryApplicationVO {

    /**
     * 应用名称
     */
    private String name;

    /**
     * 该应用当天总时长(秒)
     */
    private Long totalDuration;

    /**
     * 使用次数
     */
    private Long sessions;

    /**
     * 该应用当天使用过的窗口标题列表
     */
    private List<String> windowTitles;
}
