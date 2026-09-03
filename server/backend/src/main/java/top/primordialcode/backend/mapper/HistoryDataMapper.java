package top.primordialcode.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.primordialcode.backend.entity.AppUsageRecordEntity;
import top.primordialcode.backend.entity.AppWindowTitleEntity;
import top.primordialcode.backend.entity.DailyStatisticsEntity;
import top.primordialcode.backend.entity.DataDateIndexEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 历史数据(冷数据)读写Mapper
 * 负责 daily_statistics / app_usage_records / app_window_titles / data_date_index 四张表的读写
 */
@Mapper
public interface HistoryDataMapper {

    /**
     * 每日统计：存在则覆盖为最新累计值，不存在则插入
     */
    int upsertDailyStatistics(@Param("user_email") String user_email,
                              @Param("stat_date") LocalDate stat_date,
                              @Param("keyboard_count") Long keyboard_count,
                              @Param("mouse_click_count") Long mouse_click_count,
                              @Param("mouse_distance") BigDecimal mouse_distance);

    /**
     * 查询某用户某天的每日统计
     * @return 无记录时返回null
     */
    DailyStatisticsEntity selectDailyStatistics(@Param("user_email") String user_email,
                                                @Param("stat_date") LocalDate stat_date);

    /**
     * 查询某用户某天某应用的记录
     * @return 无记录时返回null
     */
    AppUsageRecordEntity selectAppRecord(@Param("user_email") String user_email,
                                         @Param("stat_date") LocalDate stat_date,
                                         @Param("app_name") String app_name);

    /**
     * 插入应用使用记录，自动回填id
     */
    int insertAppRecord(AppUsageRecordEntity record);

    /**
     * 更新应用使用记录：总时长覆盖为最新累计值，sessions在原值上累加，标题仅在传入时更新
     */
    int updateAppRecord(@Param("id") Long id,
                        @Param("total_duration") Long total_duration,
                        @Param("add_sessions") int add_sessions,
                        @Param("last_window_title") String last_window_title);

    /**
     * 查询某用户某天的全部应用使用记录，按时长降序排列
     */
    List<AppUsageRecordEntity> selectAppRecords(@Param("user_email") String user_email,
                                                @Param("stat_date") LocalDate stat_date);

    /**
     * 按记录id列表批量查询窗口标题（按id升序）
     */
    List<AppWindowTitleEntity> selectTitlesByRecordIds(@Param("record_ids") List<Long> record_ids);

    /**
     * 插入窗口标题(重复的(record_id,window_title)自动忽略)
     */
    int insertIgnoreWindowTitle(@Param("record_id") Long record_id,
                                @Param("window_title") String window_title);

    /**
     * 插入有数据日期索引(重复自动忽略)
     */
    int insertIgnoreDateIndex(@Param("user_email") String user_email,
                              @Param("stat_date") LocalDate stat_date);

    /**
     * 查询某用户某天的日期索引
     * @return 无记录时返回null
     */
    DataDateIndexEntity selectDateIndex(@Param("user_email") String user_email,
                                        @Param("stat_date") LocalDate stat_date);

    /**
     * 查询某用户某个月份范围内(含首尾)有数据记录的日期索引，按日期升序
     * @param start_date 月份第一天
     * @param end_date 月份最后一天
     * @return 无记录时返回空列表
     */
    List<DataDateIndexEntity> selectDateIndexesInRange(@Param("user_email") String user_email,
                                                       @Param("start_date") LocalDate start_date,
                                                       @Param("end_date") LocalDate end_date);

    /**
     * 更新某用户某天索引中的最近前台应用
     */
    int updateLastActiveApp(@Param("user_email") String user_email,
                            @Param("stat_date") LocalDate stat_date,
                            @Param("last_active_app") String last_active_app);
}
