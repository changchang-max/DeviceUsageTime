package top.primordialcode.backend.entity;

import lombok.Data;

import java.time.Instant;

/**
 * 应用窗口标题实体（对应表 app_window_titles）
 */
@Data
public class AppWindowTitleEntity {

    private Long id;

    /**
     * 关联的应用使用记录ID
     */
    private Long record_id;

    /**
     * 窗口标题
     */
    private String window_title;

    private Instant created_at;
}
