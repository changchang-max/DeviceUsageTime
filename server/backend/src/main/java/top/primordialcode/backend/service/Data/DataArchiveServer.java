package top.primordialcode.backend.service.Data;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;
import top.primordialcode.backend.entity.AppUsageRecordEntity;
import top.primordialcode.backend.entity.DataDateIndexEntity;
import top.primordialcode.backend.mapper.HistoryDataMapper;
import top.primordialcode.backend.utils.DataDateUtil;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 历史数据归档服务
 * 将客户端每次增量上传的最新累计值按「用户+日期」聚合写入 MySQL 冷数据表
 * （daily_statistics / app_usage_records / app_window_titles / data_date_index）
 */
@Slf4j
@Service
public class DataArchiveServer {

    @Autowired
    HistoryDataMapper historyDataMapper;

    /**
     * 归档一次增量上传数据。这些数据即为客户端上传的全部数据
     * @param user_email 用户邮箱
     * @param timestamp 上传时间戳(用于确定归属日期)
     * @param applications 应用列表(含变化的应用)
     * @param statistics 统计数据(可为null)
     */
    @Transactional(rollbackFor = Exception.class)
    public void archive(String user_email, Instant timestamp,
                        List<ApplicationDTO> applications, StatisticsDTO statistics) {
        // 时间戳为空时无法确定日期，直接忽略归档
        if (timestamp == null) {
            return;
        }
        // 归属日期统一换算到 Asia/Shanghai 时区(与Redis分桶、前端日历保持一致)，
        // 避免使用UTC把东八区凌晨上传的数据归到前一天
        LocalDate date = DataDateUtil.toDataDate(timestamp);

        boolean hasData = false;

        // 1. 归档每日统计数据(当天累计值，覆盖为最新)
        if (statistics != null) {
            hasData = true;
            Long keyboardCount = statistics.getKeyboardCount() != null ? statistics.getKeyboardCount() : 0L;
            Long mouseClickCount = statistics.getMouseClickCount() != null ? statistics.getMouseClickCount() : 0L;
            // Java的高精度小数类型
            BigDecimal mouseDistance = statistics.getMouseDistance() != null
                    ? BigDecimal.valueOf(statistics.getMouseDistance())
                    : BigDecimal.ZERO;
            // 客户端程序今日运行时长(秒)。旧客户端可能不携带该字段(null)，
            // 由SQL层在插入新行时补0、在更新已有行时保留原值，避免把当日运行时长误清零
            Long totalDuration = statistics.getTotalDuration();

            historyDataMapper.upsertDailyStatistics(
                    user_email, date, keyboardCount, mouseClickCount, mouseDistance, totalDuration);
        }

        // 2. 读取日期索引，获取当日最近一次前台应用(用于统计使用次数)
        DataDateIndexEntity dateIndex = historyDataMapper.selectDateIndex(user_email, date);
        String lastActiveApp = (dateIndex != null) ? dateIndex.getLast_active_app() : null;

        // 3. 逐应用归档(同一上传内同名应用去重，保留最后一条)
        if (applications != null && !applications.isEmpty()) {
            Set<String> processed = new HashSet<>();
            for (ApplicationDTO app : applications) {
                if (app == null || app.getName() == null || app.getName().isBlank()) {
                    continue;
                }
                if (!processed.add(app.getName())) {
                    continue;
                }
                hasData = true;

                boolean active = Boolean.TRUE.equals(app.getIsActive());
                Long duration = app.getDuration() != null ? app.getDuration() : 0L;
                String title = app.getWindowTitle();
                // 标题仅在应用处于前台且存在时更新
                String effectiveTitle = (active && title != null && !title.isBlank()) ? title : null;

                AppUsageRecordEntity record =
                        historyDataMapper.selectAppRecord(user_email, date, app.getName());

                if (record == null) {
                    // 该应用当天首次出现
                    AppUsageRecordEntity newRecord = new AppUsageRecordEntity();
                    newRecord.setUser_email(user_email);
                    newRecord.setStat_date(date);
                    newRecord.setApp_name(app.getName());
                    newRecord.setTotal_duration(duration);
                    newRecord.setSessions(active ? 1L : 0L);
                    newRecord.setLast_window_title(effectiveTitle);
                    historyDataMapper.insertAppRecord(newRecord);
                    record = newRecord;
                } else {
                    // 该应用当天已出现过：获得前台(且与上一次前台应用不同)视为一次新会话
                    int addSessions = 0;
                    if (active && !Objects.equals(app.getName(), lastActiveApp)) {
                        addSessions = 1;
                    }
                    historyDataMapper.updateAppRecord(
                            record.getId(), duration, addSessions, effectiveTitle);
                }

                // 4. 记录该应用当天使用过的窗口标题(自动去重)
                if (title != null && !title.isBlank()) {
                    historyDataMapper.insertIgnoreWindowTitle(record.getId(), title);
                }

                // 更新前台应用跟踪状态
                if (active) {
                    lastActiveApp = app.getName();
                }
            }
        }

        // 5. 有数据则维护日期索引(供后续"有数据日期列表"等接口使用)
        if (hasData) {
            historyDataMapper.insertIgnoreDateIndex(user_email, date);
            if (lastActiveApp != null) {
                historyDataMapper.updateLastActiveApp(user_email, date, lastActiveApp);
            }
        }
    }
}
