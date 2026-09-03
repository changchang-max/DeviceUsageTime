package top.primordialcode.backend.entity;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 有数据日期索引实体（对应表 data_date_index）
 */
@Data
public class DataDateIndexEntity {

    private Long id;

    /**
     * 用户邮箱（本系统中作为用户全局唯一标识）
     */
    private String user_email;

    /**
     * 有数据的日期
     */
    private LocalDate stat_date;

    /**
     * 当日最近一次前台应用（服务端聚合辅助字段）
     */
    private String last_active_app;

    private Instant created_at;
}
