package top.primordialcode.backend.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 每日统计数据实体（对应表 daily_statistics）
 */
@Data
public class DailyStatisticsEntity {

    private Long id;

    /**
     * 用户邮箱（本系统中作为用户全局唯一标识）
     */
    private String user_email;

    /**
     * 统计日期
     */
    private LocalDate stat_date;

    /**
     * 键盘敲击总次数
     */
    private Long keyboard_count;

    /**
     * 鼠标点击总次数
     */
    private Long mouse_click_count;

    /**
     * 鼠标移动总距离(米)
     */
    private BigDecimal mouse_distance;

    /**
     * 滚轮滚动总距离
     */
    private BigDecimal scroll_distance;

    /**
     * 设备总使用时长(秒)
     */
    private Long total_duration;

    private Instant created_at;

    private Instant updated_at;
}
