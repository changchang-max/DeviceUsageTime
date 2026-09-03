package top.primordialcode.backend.entity;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 应用使用记录实体（对应表 app_usage_records）
 */
@Data
public class AppUsageRecordEntity {

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
     * 应用名称
     */
    private String app_name;

    /**
     * 该应用当天总使用时长(秒)
     */
    private Long total_duration;

    /**
     * 使用次数（会话数）
     */
    private Long sessions;

    /**
     * 该应用当天最近一次前台窗口标题（服务端聚合辅助字段）
     */
    private String last_window_title;

    private Instant created_at;

    private Instant updated_at;
}
